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
    public static final Identifier USER_SYNC_REQUEST_PACKET_ID = new Identifier("factions", "user_sync_request");
    public static final Identifier USER_SYNC_PACKET_ID = new Identifier("factions", "user_sync");

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
        
        // Register the server-side packet receiver for client→server user sync requests
        ServerPlayNetworking.registerGlobalReceiver(USER_SYNC_REQUEST_PACKET_ID,
            (server, player, handler, buf, responseSender) -> {
                handleUserSyncRequestPacket(server, player);
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
                    return;
                }
                
                Faction faction = user.getFaction();
                if (faction == null) {
                    return;
                }
                
                // Only OWNER, COMMANDER, LEADER can see/edit dimension blacklist
                if (user.rank != User.Rank.OWNER && user.rank != User.Rank.COMMANDER && user.rank != User.Rank.LEADER) {

                    return;  // Silently reject - non-leadership can't access dimensions
                }
                
                // Send the current dimensions from the server to the client
                syncDimensionsToPlayer(player, faction.dimensionBlacklist);
            } catch (Exception e) {
    
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

            
            // Now queue the actual work on the server thread
            server.execute(() -> {
                try {
                    // Parse NBT from the bytes we extracted earlier
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(nbtBytes));
                    net.minecraft.nbt.NbtCompound nbtCompound = NbtIo.read(dis);
                    
                    if (nbtCompound != null) {
                        DimensionCommitPacket packet = DimensionCommitPacket.fromNbt(nbtCompound);
                        
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
                        
                        // Only OWNER, COMMANDER, LEADER can edit dimension blacklist
                        if (user.rank != User.Rank.OWNER && user.rank != User.Rank.COMMANDER && user.rank != User.Rank.LEADER) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal("§cOnly faction leadership can edit dimension blacklist!"), false);
                            return;
                        }
                        
                        // Validate all dimensions are within faction claims
                        if (!areAllDimensionsInClaims(faction, packet.dimensions)) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal("§cAll selected regions must be within your faction's claimed chunks!"), false);
                            return;
                        }
                        
                        // Replace the entire list with what the client sent (to ensure deletions are reflected)
                        faction.dimensionBlacklist.clear();
                        faction.dimensionBlacklist.addAll(packet.dimensions);
                        
                        // Sync the ArrayList to the JSON field immediately
                        faction.saveDimensionBlacklistToJson();
                        
                        // Trigger MODIFY event and save all factions
                        io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
                        Faction.save();
                        
                        // Broadcast dimension changes to all online faction members
                        broadcastDimensionsToFaction(faction);
                    } else {
                        player.sendMessage(
                            net.minecraft.text.Text.literal("§cError: Failed to parse NBT data!"), false);
                    }
                } catch (Exception e) {

                    e.printStackTrace();
                    player.sendMessage(
                        net.minecraft.text.Text.literal("§cError saving dimension selections: " + e.getMessage()), false);
                }
            });
        } catch (Exception e) {

            e.printStackTrace();
            player.sendMessage(
                net.minecraft.text.Text.literal("§cError reading packet: " + e.getMessage()), false);
        }
    }

    /**
     * Handle user sync request from client - send current user faction data back
     */
    private static void handleUserSyncRequestPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
        server.execute(() -> {
            try {
                User user = User.get(player.getUuid());
                if (user == null) {
                    return;
                }
                
                Faction faction = user.getFaction();
                
                // Send user sync packet with faction info
                syncUserDataToPlayer(player, user, faction);
            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }

    /**
     * Send user sync packet from server to client
     */
    public static void syncUserDataToPlayer(ServerPlayerEntity player, User user, Faction faction) {
        try {
            UserSyncPacket packet = new UserSyncPacket(user, faction);
            net.minecraft.nbt.NbtCompound nbt = packet.toNbt();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(nbt, dos);
            
            byte[] nbtBytes = baos.toByteArray();
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.copiedBuffer(nbtBytes);
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            
            ServerPlayNetworking.send(player, USER_SYNC_PACKET_ID, buf);
        } catch (Exception e) {

            e.printStackTrace();
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

    /**
     * Validate that all dimensions are within the faction's claimed chunks
     */
    private static boolean areAllDimensionsInClaims(Faction faction, java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> dimensions) {
        java.util.List<io.icker.factions.api.persistents.Claim> claims = io.icker.factions.api.persistents.Claim.getByFaction(faction.getID());
        
        for (io.icker.factions.api.persistents.BlacklistedDimension dim : dimensions) {
            if (dim == null || dim.world == null || dim.world.isEmpty()) {
                return false; // Invalid dimension
            }
            
            // Check if this dimension region is entirely within claimed chunks
            if (!isDimensionInClaims(dim, claims)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a dimension region is entirely within the faction's claims
     */
    private static boolean isDimensionInClaims(io.icker.factions.api.persistents.BlacklistedDimension dim, 
                                               java.util.List<io.icker.factions.api.persistents.Claim> claims) {
        // Convert block coordinates to chunk coordinates using floor division
        // (Java's / operator truncates toward zero, which breaks for negative numbers)
        int minChunkX = Math.floorDiv(dim.minX, 16);
        int maxChunkX = Math.floorDiv(dim.maxX, 16);
        int minChunkZ = Math.floorDiv(dim.minZ, 16);
        int maxChunkZ = Math.floorDiv(dim.maxZ, 16);
        
        // Check if all chunks in the region are claimed
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Find a claim for this chunk
                boolean foundClaim = false;
                for (io.icker.factions.api.persistents.Claim claim : claims) {
                    if (claim.x == chunkX && claim.z == chunkZ && claim.level.equals(dim.world)) {
                        foundClaim = true;
                        break;
                    }
                }
                
                if (!foundClaim) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Broadcast dimension updates to all online faction members
     */
    public static void broadcastDimensionsToFaction(Faction faction) {
        if (faction == null) {
            return;
        }

        // Use the server reference from WorldUtils
        net.minecraft.server.MinecraftServer server = io.icker.factions.util.WorldUtils.server;
        if (server == null) {
            return;
        }

        try {
            // Get all online players
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                User user = User.get(player.getUuid());
                Faction userFaction = user != null ? user.getFaction() : null;
                if (userFaction != null && userFaction.getID().equals(faction.getID())) {
                    // Only send to players who have the tool equipped and permission to view
                    if (user.rank == User.Rank.OWNER || user.rank == User.Rank.COMMANDER || user.rank == User.Rank.LEADER) {
                        syncDimensionsToPlayer(player, faction.dimensionBlacklist);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
