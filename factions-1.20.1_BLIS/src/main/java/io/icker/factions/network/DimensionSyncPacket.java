package io.icker.factions.network;

import java.util.ArrayList;
import java.util.List;

import io.icker.factions.api.persistents.BlacklistedDimension;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * Packet for syncing dimension blacklist from server to client
 */
public class DimensionSyncPacket {
    public List<BlacklistedDimension> dimensions;

    public DimensionSyncPacket(List<BlacklistedDimension> dimensions) {
        this.dimensions = new ArrayList<>(dimensions);
    }

    public DimensionSyncPacket() {
        this.dimensions = new ArrayList<>();
    }

    /**
     * Serialize dimensions to NBT for network transmission
     */
    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        NbtList dimensionsList = new NbtList();

        for (BlacklistedDimension dim : this.dimensions) {
            NbtCompound dimTag = new NbtCompound();
            dimTag.putString("world", dim.world);
            dimTag.putInt("minX", dim.minX);
            dimTag.putInt("minY", dim.minY);
            dimTag.putInt("minZ", dim.minZ);
            dimTag.putInt("maxX", dim.maxX);
            dimTag.putInt("maxY", dim.maxY);
            dimTag.putInt("maxZ", dim.maxZ);
            dimTag.putString("name", dim.name);
            dimensionsList.add(dimTag);
        }

        tag.put("dimensions", dimensionsList);
        return tag;
    }

    /**
     * Deserialize dimensions from NBT
     */
    public static DimensionSyncPacket fromNbt(NbtCompound tag) {
        DimensionSyncPacket packet = new DimensionSyncPacket();
        NbtList dimensionsList = tag.getList("dimensions", 10); // 10 = NBTTagCompound

        for (int i = 0; i < dimensionsList.size(); i++) {
            NbtCompound dimTag = dimensionsList.getCompound(i);
            BlacklistedDimension dim = new BlacklistedDimension(
                    dimTag.getString("world"),
                    dimTag.getInt("minX"),
                    dimTag.getInt("minY"),
                    dimTag.getInt("minZ"),
                    dimTag.getInt("maxX"),
                    dimTag.getInt("maxY"),
                    dimTag.getInt("maxZ"),
                    dimTag.getString("name")
            );
            packet.dimensions.add(dim);
        }

        return packet;
    }
}
