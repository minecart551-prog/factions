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
    
    /**
     * Send pending selections to server for commitment
     */
    public static void commitDimensions(List<BlacklistedDimension> dimensions) {
        try {
            byte[] serialized = serializeDimensions(dimensions);
            PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(serialized));
            ClientPlayNetworking.send(DimensionNetworkHandler.COMMIT_PACKET_ID, buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Serialize dimensions to bytes for network transmission
     */
    private static byte[] serializeDimensions(List<BlacklistedDimension> dimensions) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            io.icker.factions.network.DimensionCommitPacket packet = 
                new io.icker.factions.network.DimensionCommitPacket(dimensions);
            net.minecraft.nbt.NbtIo.write(packet.toNbt(), dos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
