package io.icker.factions.client.network;

import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.network.DimensionNetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

/**
 * Client-side handler for sending dimension selections to server
 */
public class DimensionClientNetworkHandler {
    public static final Identifier SYNC_REQUEST_PACKET_ID = new Identifier("factions", "dimension_sync_request");
    
    /**
     * Send pending selections to server for commitment
     */
    public static void commitDimensions(List<BlacklistedDimension> dimensions) {
        try {
            // Serialize NBT to byte array first
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            io.icker.factions.network.DimensionCommitPacket packet = 
                new io.icker.factions.network.DimensionCommitPacket(dimensions);
            net.minecraft.nbt.NbtIo.write(packet.toNbt(), dos);
            
            byte[] nbtBytes = baos.toByteArray();
            
            // Create buffer and write data
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.copiedBuffer(nbtBytes);
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            
            ClientPlayNetworking.send(DimensionNetworkHandler.COMMIT_PACKET_ID, buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Request the server to send the current dimensions for this player's faction
     */
    public static void requestDimensionSync() {
        try {
            // Send empty packet to request sync
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
