package io.icker.factions.client.network;

import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.network.DimensionNetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side handler for sending dimension selections to server.
 * Automatically splits large payloads into chunks to avoid the 32767 byte packet limit.
 */
public class DimensionClientNetworkHandler {
    public static final Identifier SYNC_REQUEST_PACKET_ID = new Identifier("factions", "dimension_sync_request");
    
    // Maximum payload size (leaving room for headers)
    private static final int MAX_PACKET_SIZE = 30000;
    
    /**
     * Send pending selections to server for commitment.
     * Automatically splits into chunks if the data exceeds MAX_PACKET_SIZE.
     */
    public static void commitDimensions(List<BlacklistedDimension> dimensions) {
        try {
            // Estimate total size
            long estimatedSize = io.icker.factions.network.DimensionCommitPacket.estimateSize(dimensions.size());
            
            if (estimatedSize <= MAX_PACKET_SIZE) {
                // Small enough for a single packet
                sendSingleCommit(dimensions, false, null, 0, 0);
            } else {
                // Split into chunks
                int dimsPerChunk = Math.max(1, (int)(dimensions.size() * MAX_PACKET_SIZE / estimatedSize));
                UUID sessionId = UUID.randomUUID();
                List<List<BlacklistedDimension>> chunks = new ArrayList<>();
                
                for (int i = 0; i < dimensions.size(); i += dimsPerChunk) {
                    int end = Math.min(i + dimsPerChunk, dimensions.size());
                    chunks.add(dimensions.subList(i, end));
                }
                
                int totalChunks = chunks.size();
                for (int i = 0; i < totalChunks; i++) {
                    sendSingleCommit(chunks.get(i), true, sessionId, totalChunks, i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Send a single commit (or chunk) packet
     */
    private static void sendSingleCommit(List<BlacklistedDimension> dims, boolean isChunk, 
                                          UUID sessionId, int totalChunks, int chunkIndex) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            io.icker.factions.network.DimensionCommitPacket packet = 
                new io.icker.factions.network.DimensionCommitPacket(dims);
            
            if (isChunk) {
                packet.isChunk = true;
                packet.sessionId = sessionId;
                packet.totalChunks = totalChunks;
                packet.chunkIndex = chunkIndex;
            }
            
            net.minecraft.nbt.NbtIo.write(packet.toNbt(), dos);
            
            byte[] nbtBytes = baos.toByteArray();
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.copiedBuffer(nbtBytes);
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            
            Identifier targetId = isChunk ? 
                DimensionNetworkHandler.COMMIT_CHUNK_PACKET_ID : 
                DimensionNetworkHandler.COMMIT_PACKET_ID;
            
            ClientPlayNetworking.send(targetId, buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Request the server to send the current dimensions for this player's faction
     */
    public static void requestDimensionSync() {
        try {
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.buffer();
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            
            ClientPlayNetworking.send(SYNC_REQUEST_PACKET_ID, buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Request the server to send the current user faction data
     */
    public static void requestUserSync() {
        try {
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.buffer();
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            
            ClientPlayNetworking.send(io.icker.factions.network.DimensionNetworkHandler.USER_SYNC_REQUEST_PACKET_ID, buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
