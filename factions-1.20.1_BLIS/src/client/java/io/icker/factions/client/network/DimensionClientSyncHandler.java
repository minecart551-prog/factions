package io.icker.factions.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import io.icker.factions.network.DimensionNetworkHandler;
import io.icker.factions.client.event.SelectionManager;

/**
 * Client-side handler for receiving dimension sync packets from server
 */
public class DimensionClientSyncHandler {
    public static void register() {
        // Register handler for dimension sync packets from server
        ClientPlayNetworking.registerGlobalReceiver(DimensionNetworkHandler.SYNC_PACKET_ID, 
            (client, handler, buf, responseSender) -> {
                handleSyncPacket(buf);
            });
    }

    /**
     * Handle incoming sync packet from server
     */
    private static void handleSyncPacket(PacketByteBuf buf) {
        try {
            // Read the NBT data
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);
            
            java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.ByteArrayInputStream(nbtBytes));
            net.minecraft.nbt.NbtCompound nbtCompound = net.minecraft.nbt.NbtIo.read(dis);
            
            if (nbtCompound != null) {
                io.icker.factions.network.DimensionSyncPacket packet = 
                    io.icker.factions.network.DimensionSyncPacket.fromNbt(nbtCompound);
                
                // Filter out any dimensions with null world
                java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> validDimensions = 
                    new java.util.ArrayList<>();
                for (io.icker.factions.api.persistents.BlacklistedDimension dim : packet.dimensions) {
                    if (dim != null && dim.world != null && !dim.world.isEmpty()) {
                        validDimensions.add(dim);
                    } else {
                        System.out.println("[Factions] Warning: Skipping invalid dimension during sync: world=" + (dim != null ? dim.world : "null"));
                    }
                }
                
                // Update client-side pending selections with synced data
                SelectionManager selectionMgr = SelectionManager.getInstance();
                selectionMgr.setPendingSelections(validDimensions);
                

            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}
