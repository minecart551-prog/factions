package io.icker.factions.network;

import java.util.UUID;

import net.minecraft.nbt.NbtCompound;

/**
 * Chunked file-transfer payload used by /f dimension export and import.
 * Data is gzip-compressed and split into &lt;{@link #CHUNK_DATA_SIZE}-byte chunks
 * so payloads stay under the vanilla custom-payload limits
 * (C2S 32767 bytes, S2C 1048576 bytes).
 */
public class DimensionFilePacket {
    public static final int CHUNK_DATA_SIZE = 30000;
    public static final int MAX_CHUNKS = 512;

    public UUID sessionId;
    public int totalChunks;
    public int chunkIndex;
    public String filename;
    public byte[] data;

    public DimensionFilePacket() {
        this.data = new byte[0];
    }

    public DimensionFilePacket(UUID sessionId, int totalChunks, int chunkIndex, String filename, byte[] data) {
        this.sessionId = sessionId;
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.filename = filename;
        this.data = data != null ? data : new byte[0];
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        if (sessionId != null) tag.putUuid("sessionId", sessionId);
        tag.putInt("totalChunks", totalChunks);
        tag.putInt("chunkIndex", chunkIndex);
        if (filename != null) tag.putString("filename", filename);
        tag.putByteArray("data", data);
        return tag;
    }

    public static DimensionFilePacket fromNbt(NbtCompound tag) {
        DimensionFilePacket packet = new DimensionFilePacket();
        if (tag.contains("sessionId")) packet.sessionId = tag.getUuid("sessionId");
        packet.totalChunks = tag.getInt("totalChunks");
        packet.chunkIndex = tag.getInt("chunkIndex");
        packet.filename = tag.contains("filename") ? tag.getString("filename") : null;
        packet.data = tag.getByteArray("data");
        if (packet.data == null) packet.data = new byte[0];
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
