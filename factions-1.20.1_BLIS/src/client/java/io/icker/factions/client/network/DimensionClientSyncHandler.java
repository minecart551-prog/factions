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
                
                // Update client-side pending selections with synced data
                SelectionManager selectionMgr = SelectionManager.getInstance();
                selectionMgr.setPendingSelections(packet.dimensions);
                
                System.out.println("[Factions] Synced " + packet.dimensions.size() + " dimensions from server");
            }
        } catch (Exception e) {
            System.err.println("[Factions] Error handling dimension sync packet:");
            e.printStackTrace();
        }
    }
}
