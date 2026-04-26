package io.icker.factions.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.item.FactionsItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Handles network communication for dimension blacklist selections
 */
public class DimensionNetworkHandler {
    public static final Identifier COMMIT_PACKET_ID = new Identifier("factions", "dimension_commit");
    public static final Identifier SYNC_PACKET_ID = new Identifier("factions", "dimension_sync");
    public static final Identifier SYNC_REQUEST_PACKET_ID = new Identifier("factions", "dimension_sync_request");

    public static void registerHandlers() {
        // Register the server-side packet receiver for client→server commits
        ServerPlayNetworking.registerGlobalReceiver(COMMIT_PACKET_ID, 
            (server, player, handler, buf, responseSender) -> {
                handleCommitPacket(server, player, buf);
            });
        
        // Register the server-side packet receiver for client→server sync requests
        ServerPlayNetworking.registerGlobalReceiver(SYNC_REQUEST_PACKET_ID,
            (server, player, handler, buf, responseSender) -> {
                handleSyncRequestPacket(server, player);
            });
        
        // Prevent block breaking when holding the dimension blacklist tool
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.getStackInHand(hand).getItem() == FactionsItems.DIMENSION_BLACKLIST_TOOL) {
                return ActionResult.FAIL; // Prevent the attack
            }
            return ActionResult.PASS; // Allow other attacks
        });
    }
    
    /**
     * Handle sync request from client - send current dimensions back
     */
    private static void handleSyncRequestPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
        server.execute(() -> {
            try {
                User user = User.get(player.getUuid());
                if (user == null) {
                    System.out.println("[Factions] Sync request: User not found");
                    return;
                }
                
                Faction faction = user.getFaction();
                if (faction == null) {
                    System.out.println("[Factions] Sync request: Faction not found");
                    return;
                }
                
                // Send the current dimensions from the server to the client
                System.out.println("[Factions] Sync request: Sending " + faction.dimensionBlacklist.size() + " dimensions to player");
                syncDimensionsToPlayer(player, faction.dimensionBlacklist);
            } catch (Exception e) {
                System.err.println("[Factions] Error handling sync request:");
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Send dimensions sync packet from server to client for a specific player
     */
    public static void syncDimensionsToPlayer(ServerPlayerEntity player, java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> dimensions) {
        try {
            DimensionSyncPacket packet = new DimensionSyncPacket(dimensions);
            net.minecraft.nbt.NbtCompound nbt = packet.toNbt();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(nbt, dos);
            
            byte[] nbtBytes = baos.toByteArray();
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.copiedBuffer(nbtBytes);
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            
            ServerPlayNetworking.send(player, SYNC_PACKET_ID, buf);
        } catch (Exception e) {
            System.err.println("[Factions] Error sending dimension sync packet:");
            e.printStackTrace();
        }
    }

    /**
     * Handle incoming commit packet from client
     */
    private static void handleCommitPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player, 
                                           PacketByteBuf buf) {
        try {
            // IMPORTANT: Read the buffer data IMMEDIATELY, before any async operations
            // The buffer will be released after this handler returns
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);
            System.out.println("[Factions] Received " + nbtBytes.length + " bytes of NBT data");
            
            // Now queue the actual work on the server thread
            server.execute(() -> {
                try {
                    // Parse NBT from the bytes we extracted earlier
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(nbtBytes));
                    net.minecraft.nbt.NbtCompound nbtCompound = NbtIo.read(dis);
                    
                    if (nbtCompound != null) {
                        DimensionCommitPacket packet = DimensionCommitPacket.fromNbt(nbtCompound);
                        
                        // Debug: log what we received
                        System.out.println("[Factions] Parsed packet with " + packet.dimensions.size() + " dimensions:");
                        for (int i = 0; i < packet.dimensions.size(); i++) {
                            io.icker.factions.api.persistents.BlacklistedDimension dim = packet.dimensions.get(i);
                            System.out.println("[Factions]   Dim " + i + ": world=" + dim.world + ", name=" + dim.name + 
                                ", coords=[" + dim.minX + "," + dim.minY + "," + dim.minZ + "] to [" + 
                                dim.maxX + "," + dim.maxY + "," + dim.maxZ + "]");
                        }
                        
                        // Get faction and save dimensions
                        User user = User.get(player.getUuid());
                        if (user == null) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal("§cError: User not found!"), false);
                            return;
                        }
                        
                        Faction faction = user.getFaction();
                        if (faction == null) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal("§cError: Faction not found!"), false);
                            return;
                        }
                        
                        // Replace the entire list with what the client sent (to ensure deletions are reflected)
                        faction.dimensionBlacklist.clear();
                        faction.dimensionBlacklist.addAll(packet.dimensions);
                        
                        System.out.println("[Factions] Before save - faction has " + faction.dimensionBlacklist.size() + " dimensions:");
                        for (int i = 0; i < faction.dimensionBlacklist.size(); i++) {
                            io.icker.factions.api.persistents.BlacklistedDimension dim = faction.dimensionBlacklist.get(i);
                            System.out.println("[Factions]   Dim " + i + ": world=" + dim.world + ", name=" + dim.name);
                        }
                        
                        // Trigger MODIFY event and save all factions
                        io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
                        Faction.save();
                        
                        System.out.println("[Factions] After save - faction has " + faction.dimensionBlacklist.size() + " dimensions:");
                        for (int i = 0; i < faction.dimensionBlacklist.size(); i++) {
                            io.icker.factions.api.persistents.BlacklistedDimension dim = faction.dimensionBlacklist.get(i);
                            System.out.println("[Factions]   Dim " + i + ": world=" + dim.world + ", name=" + dim.name);
                        }
                        
                        player.sendMessage(
                            net.minecraft.text.Text.literal("§6Dimension selections committed!"), false);
                    } else {
                        player.sendMessage(
                            net.minecraft.text.Text.literal("§cError: Failed to parse NBT data!"), false);
                    }
                } catch (Exception e) {
                    System.err.println("[Factions] Error in handleCommitPacket async work:");
                    e.printStackTrace();
                    player.sendMessage(
                        net.minecraft.text.Text.literal("§cError saving dimension selections: " + e.getMessage()), false);
                }
            });
        } catch (Exception e) {
            System.err.println("[Factions] Error reading dimension commit packet:");
            e.printStackTrace();
            player.sendMessage(
                net.minecraft.text.Text.literal("§cError reading packet: " + e.getMessage()), false);
        }
    }

    /**
     * Serialize dimensions to bytes for network transmission
     */
    public static byte[] serializeDimensions(java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> dimensions) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            DimensionCommitPacket packet = new DimensionCommitPacket(dimensions);
            NbtIo.write(packet.toNbt(), dos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
