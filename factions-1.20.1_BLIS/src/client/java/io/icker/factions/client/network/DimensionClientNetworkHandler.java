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
     * Uses actual serialized size measurement for accuracy.
     */
    public static void commitDimensions(List<BlacklistedDimension> dimensions) {
        try {
            UUID sessionId = UUID.randomUUID();
            List<List<BlacklistedDimension>> chunks = new ArrayList<>();
            List<BlacklistedDimension> currentChunk = new ArrayList<>();
            
            // Batch dimensions by actual serialized size
            for (BlacklistedDimension dim : dimensions) {
                // Test size with this dimension added
                List<BlacklistedDimension> testBatch = new ArrayList<>(currentChunk);
                testBatch.add(dim);
                io.icker.factions.network.DimensionCommitPacket testPacket = 
                    new io.icker.factions.network.DimensionCommitPacket(testBatch);
                testPacket.isChunk = true;
                testPacket.sessionId = sessionId;
                
                if (testPacket.computeSerializedSize() > MAX_PACKET_SIZE && !currentChunk.isEmpty()) {
                    // Start new chunk
                    chunks.add(currentChunk);
                    currentChunk = new ArrayList<>();
                }
                
                currentChunk.add(dim);
            }
            
            // Add remaining
            if (!currentChunk.isEmpty()) {
                chunks.add(currentChunk);
            }
            
            // Send all chunks
            int totalChunks = chunks.size();
            if (totalChunks == 1) {
                sendSingleCommit(chunks.get(0), false, null, 0, 0);
            } else {
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
