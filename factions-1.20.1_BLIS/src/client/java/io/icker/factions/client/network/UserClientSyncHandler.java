package io.icker.factions.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import io.icker.factions.network.DimensionNetworkHandler;
import io.icker.factions.network.UserSyncPacket;

/**
 * Client-side handler for receiving user sync packets from server
 */
public class UserClientSyncHandler {
    // Static cache for user data
    private static UserSyncPacket cachedUserData = new UserSyncPacket();

    public static void register() {
        // Register handler for user sync packets from server
        ClientPlayNetworking.registerGlobalReceiver(DimensionNetworkHandler.USER_SYNC_PACKET_ID, 
            (client, handler, buf, responseSender) -> {
                handleUserSyncPacket(buf);
            });
    }

    /**
     * Handle incoming user sync packet from server
     */
    private static void handleUserSyncPacket(PacketByteBuf buf) {
        try {
            // Read the NBT data
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);
            
            java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.ByteArrayInputStream(nbtBytes));
            net.minecraft.nbt.NbtCompound nbtCompound = net.minecraft.nbt.NbtIo.read(dis);
            
            if (nbtCompound != null) {
                cachedUserData = UserSyncPacket.fromNbt(nbtCompound);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the cached user data
     */
    public static UserSyncPacket getCachedUserData() {
        return cachedUserData;
    }
}
