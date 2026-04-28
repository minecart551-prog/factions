package io.icker.factions.network;

import java.util.ArrayList;
import java.util.List;

import io.icker.factions.api.persistents.BlacklistedDimension;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * Packet data for committing dimension selections from client to server
 */
public class DimensionCommitPacket {
    public List<BlacklistedDimension> dimensions;

    public DimensionCommitPacket(List<BlacklistedDimension> dimensions) {
        this.dimensions = new ArrayList<>(dimensions);
    }

    public DimensionCommitPacket() {
        this.dimensions = new ArrayList<>();
    }

    /**
     * Serialize dimensions to NBT for network transmission
     */
    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        NbtList dimensionsList = new NbtList();

        for (BlacklistedDimension dim : this.dimensions) {
            if (dim == null || dim.world == null) {

                continue;
            }
            
            NbtCompound dimTag = new NbtCompound();
            dimTag.putString("world", dim.world);
            dimTag.putInt("minX", dim.minX);
            dimTag.putInt("minY", dim.minY);
            dimTag.putInt("minZ", dim.minZ);
            dimTag.putInt("maxX", dim.maxX);
            dimTag.putInt("maxY", dim.maxY);
            dimTag.putInt("maxZ", dim.maxZ);
            dimTag.putString("name", dim.name != null ? dim.name : "Unknown");
            dimensionsList.add(dimTag);
        }

        tag.put("dimensions", dimensionsList);
        return tag;
    }

    /**
     * Deserialize dimensions from NBT
     */
    public static DimensionCommitPacket fromNbt(NbtCompound tag) {
        DimensionCommitPacket packet = new DimensionCommitPacket();
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
