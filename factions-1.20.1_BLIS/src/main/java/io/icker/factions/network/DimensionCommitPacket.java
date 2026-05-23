package io.icker.factions.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.icker.factions.api.persistents.BlacklistedDimension;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * Packet data for committing dimension selections from client to server.
 * Supports chunking: large payloads are split into chunks with session IDs.
 */
public class DimensionCommitPacket {
    public List<BlacklistedDimension> dimensions;
    public UUID sessionId;
    public int totalChunks;
    public int chunkIndex;
    public boolean isChunk;
    public boolean isWhitelist;

    public DimensionCommitPacket(List<BlacklistedDimension> dimensions) {
        this.dimensions = new ArrayList<>(dimensions);
        this.isChunk = false;
        this.isWhitelist = false;
    }

    public DimensionCommitPacket() {
        this.dimensions = new ArrayList<>();
        this.isChunk = false;
        this.isWhitelist = false;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        NbtList dimensionsList = new NbtList();

        for (BlacklistedDimension dim : this.dimensions) {
            if (dim == null || dim.world == null) continue;
            
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
        tag.putBoolean("isWhitelist", isWhitelist);
        
        if (isChunk) {
            if (sessionId != null) tag.putUuid("sessionId", sessionId);
            tag.putInt("totalChunks", totalChunks);
            tag.putInt("chunkIndex", chunkIndex);
        }
        
        return tag;
    }

    public static DimensionCommitPacket fromNbt(NbtCompound tag) {
        DimensionCommitPacket packet = new DimensionCommitPacket();
        NbtList dimensionsList = tag.getList("dimensions", 10);

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

        packet.isWhitelist = tag.getBoolean("isWhitelist");
        
        if (tag.contains("sessionId")) {
            packet.isChunk = true;
            packet.sessionId = tag.getUuid("sessionId");
            packet.totalChunks = tag.getInt("totalChunks");
            packet.chunkIndex = tag.getInt("chunkIndex");
        }
        return packet;
    }
    
    public long computeSerializedSize() {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
            net.minecraft.nbt.NbtIo.write(toNbt(), dos);
            return baos.size();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }
}