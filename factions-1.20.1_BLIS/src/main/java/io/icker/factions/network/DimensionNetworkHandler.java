package io.icker.factions.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.item.FactionsItems;
import io.icker.factions.util.Command;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
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
    public static final Identifier COMMIT_CHUNK_PACKET_ID = new Identifier("factions", "dimension_commit_chunk");
    public static final Identifier SYNC_PACKET_ID = new Identifier("factions", "dimension_sync");
    public static final Identifier SYNC_REQUEST_PACKET_ID = new Identifier("factions", "dimension_sync_request");
    public static final Identifier USER_SYNC_REQUEST_PACKET_ID = new Identifier("factions", "user_sync_request");
    public static final Identifier USER_SYNC_PACKET_ID = new Identifier("factions", "user_sync");

    // Accumulates commit chunks per player: sessionId -> {totalChunks, dimensions, receivedChunkIndices}
    private static final java.util.Map<java.util.UUID, ChunkedCommit> pendingCommitChunks =
        new java.util.HashMap<>();
    
    private static class ChunkedCommit {
        int totalChunks;
        java.util.List<BlacklistedDimension> dimensions = new java.util.ArrayList<>();
        java.util.BitSet receivedChunks;
        ChunkedCommit(int totalChunks) { 
            this.totalChunks = totalChunks; 
            this.receivedChunks = new java.util.BitSet(totalChunks);
        }
    }

    public static void registerHandlers() {
        // Register the server-side packet receiver for client→server commits (single packet)
        ServerPlayNetworking.registerGlobalReceiver(COMMIT_PACKET_ID, 
            (server, player, handler, buf, responseSender) -> {
                handleCommitPacket(server, player, buf);
            });
        
        // Register the server-side packet receiver for client→server commit chunks (multi-packet)
        ServerPlayNetworking.registerGlobalReceiver(COMMIT_CHUNK_PACKET_ID,
            (server, player, handler, buf, responseSender) -> {
                handleCommitChunkPacket(server, player, buf);
            });
        
        pendingCommitChunks.clear(); // Reset on server start
        
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
        
        // Prevent block breaking when holding dimension tools
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            Item item = player.getStackInHand(hand).getItem();
            if (item == FactionsItems.DIMENSION_BLACKLIST_TOOL || item == FactionsItems.DIMENSION_WHITELIST_TOOL) {
                return ActionResult.FAIL; // Prevent the attack
            }
            return ActionResult.PASS; // Allow other attacks
        });
    }
    
    /**
     * Handle sync request from client - send current dimensions back.
     * Detects which tool the player is holding and sends the correct list.
     */
    private static void handleSyncRequestPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
        server.execute(() -> {
            try {
                User user = Command.getUser(player);
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
                
                // Clean up stale regions that are outside current claims
                // This handles the case where claim removal carving failed to persist
                // (due to the save guard bug) leaving regions referencing unclaimed chunks.
                faction.loadDimensionBlacklistFromJson();
                faction.loadDimensionWhitelistFromJson();
                boolean blChanged = cleanupStaleRegions(faction, faction.dimensionBlacklist);
                boolean wlChanged = cleanupStaleRegions(faction, faction.dimensionWhitelist);
                if (blChanged) faction.saveDimensionBlacklistToJson(true);
                if (wlChanged) faction.saveDimensionWhitelistToJson(true);
                if (blChanged || wlChanged) Faction.save();
                
                // Always send BOTH lists to the client so SelectionManager has complete data
                // regardless of which tool is held (fixes stale data when spoofing or switching tools)
                syncDimensionsToPlayer(player, faction.dimensionBlacklist, false);
                syncDimensionsToPlayer(player, faction.dimensionWhitelist, true);
            } catch (Exception e) {
    
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Send dimensions sync packet from server to client for a specific player
     */
    public static void syncDimensionsToPlayer(ServerPlayerEntity player, java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> dimensions) {
        syncDimensionsToPlayer(player, dimensions, false);
    }

    /**
     * Send dimensions sync packet from server to client with whitelist flag
     */
    public static void syncDimensionsToPlayer(ServerPlayerEntity player, java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> dimensions, boolean isWhitelist) {
        try {
            DimensionSyncPacket packet = new DimensionSyncPacket(dimensions);
            packet.isWhitelist = isWhitelist;
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
                        User user = Command.getUser(player);
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
                        
                        // Ensure both lists are loaded from JSON before any operation
                        faction.loadDimensionBlacklistFromJson();
                        faction.loadDimensionWhitelistFromJson();
                        
                        // Determine which regions are new (not present in existing data).
                        // Only validate new regions against claims - existing regions may be stale
                        // from a carving bug where fully-removed regions weren't saved as empty.
                        java.util.List<BlacklistedDimension> existingList = packet.isWhitelist ? 
                            new java.util.ArrayList<>(faction.dimensionWhitelist) : 
                            new java.util.ArrayList<>(faction.dimensionBlacklist);
                        java.util.List<BlacklistedDimension> newRegions = new java.util.ArrayList<>();
                        for (BlacklistedDimension dim : packet.dimensions) {
                            boolean isNew = true;
                            for (BlacklistedDimension existing : existingList) {
                                if (dim.minX == existing.minX && dim.minY == existing.minY && 
                                    dim.minZ == existing.minZ && dim.maxX == existing.maxX && 
                                    dim.maxY == existing.maxY && dim.maxZ == existing.maxZ &&
                                    dim.world != null && dim.world.equals(existing.world)) {
                                    isNew = false;
                                    break;
                                }
                            }
                            if (isNew) {
                                newRegions.add(dim);
                            }
                        }
                        
                        // Only validate new regions against claims
                        if (!newRegions.isEmpty() && !areAllDimensionsInClaims(faction, newRegions)) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal("§cAll selected regions must be within your faction's claimed chunks!"), false);
                            return;
                        }
                        
                        // Check for overlap with opposite type
                        if (packet.isWhitelist) {
                            for (BlacklistedDimension wlDim : packet.dimensions) {
                                for (BlacklistedDimension blDim : faction.dimensionBlacklist) {
                                    if (wlDim.overlaps(blDim)) {
                                        player.sendMessage(net.minecraft.text.Text.literal("§cWhitelist region cannot overlap blacklist region!"), false);
                                        return;
                                    }
                                }
                            }
                            faction.dimensionWhitelist.clear();
                            faction.dimensionWhitelist.addAll(packet.dimensions);
                            faction.saveDimensionWhitelistToJson();
                        } else {
                            for (BlacklistedDimension blDim : packet.dimensions) {
                                for (BlacklistedDimension wlDim : faction.dimensionWhitelist) {
                                    if (blDim.overlaps(wlDim)) {
                                        player.sendMessage(net.minecraft.text.Text.literal("§cBlacklist region cannot overlap whitelist region!"), false);
                                        return;
                                    }
                                }
                            }
                            faction.dimensionBlacklist.clear();
                            faction.dimensionBlacklist.addAll(packet.dimensions);
                            faction.saveDimensionBlacklistToJson();
                        }
                        
                        // Trigger MODIFY event and save all factions
                        io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
                        Faction.save();
                        
                        // Broadcast dimension changes to all online faction members
                        broadcastDimensionsToFaction(faction);
                        
                        // Also send direct sync to the committing player (handles spoofing case)
                        syncDimensionsToPlayer(player, faction.dimensionBlacklist, false);
                        syncDimensionsToPlayer(player, faction.dimensionWhitelist, true);
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
     * Handle incoming commit chunk packet from client (for multi-packet commits).
     * Accumulates chunks until all are received, then processes the full list.
     */
    private static void handleCommitChunkPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player, 
                                                PacketByteBuf buf) {
        try {
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);

            server.execute(() -> {
                try {
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(nbtBytes));
                    net.minecraft.nbt.NbtCompound nbtCompound = NbtIo.read(dis);
                    
                    if (nbtCompound != null) {
                        DimensionCommitPacket chunk = DimensionCommitPacket.fromNbt(nbtCompound);
                        
                        if (!chunk.isChunk || chunk.sessionId == null) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal("§cInvalid chunk packet"), false);
                            return;
                        }
                        
                        // Accumulate this chunk using ChunkedCommit wrapper
                        ChunkedCommit cc = pendingCommitChunks.get(chunk.sessionId);
                        if (cc == null) {
                            cc = new ChunkedCommit(chunk.totalChunks);
                            pendingCommitChunks.put(chunk.sessionId, cc);
                        }
                        cc.dimensions.addAll(chunk.dimensions);
                        cc.receivedChunks.set(chunk.chunkIndex);
                        
                        // Check if we've received all chunks
                        if (cc.receivedChunks.cardinality() == cc.totalChunks) {
                            pendingCommitChunks.remove(chunk.sessionId);
                            
                            // Now process the full accumulated list as a regular commit
                            User user = Command.getUser(player);
                            if (user == null) return;
                            Faction faction = user.getFaction();
                            if (faction == null) return;
                            if (user.rank != User.Rank.OWNER && user.rank != User.Rank.COMMANDER && user.rank != User.Rank.LEADER) {
                                player.sendMessage(
                                    net.minecraft.text.Text.literal("§cOnly faction leadership can edit dimension blacklist!"), false);
                                return;
                            }
                            // Ensure both lists are loaded from JSON before any operation
                            faction.loadDimensionBlacklistFromJson();
                            faction.loadDimensionWhitelistFromJson();
                            
                            // Determine which regions are new (not present in existing data)
                            // Only validate new regions against claims, not existing ones
                            java.util.List<BlacklistedDimension> existingList = chunk.isWhitelist ? 
                                new java.util.ArrayList<>(faction.dimensionWhitelist) : 
                                new java.util.ArrayList<>(faction.dimensionBlacklist);
                            java.util.List<BlacklistedDimension> newRegions = new java.util.ArrayList<>();
                            for (BlacklistedDimension dim : cc.dimensions) {
                                boolean isNew = true;
                                for (BlacklistedDimension existing : existingList) {
                                    if (dim.minX == existing.minX && dim.minY == existing.minY && 
                                        dim.minZ == existing.minZ && dim.maxX == existing.maxX && 
                                        dim.maxY == existing.maxY && dim.maxZ == existing.maxZ &&
                                        dim.world != null && dim.world.equals(existing.world)) {
                                        isNew = false;
                                        break;
                                    }
                                }
                                if (isNew) {
                                    newRegions.add(dim);
                                }
                            }
                            
                            // Only validate new regions against claims
                            if (!newRegions.isEmpty() && !areAllDimensionsInClaims(faction, newRegions)) {
                                player.sendMessage(
                                    net.minecraft.text.Text.literal("§cAll selected regions must be within your faction's claimed chunks!"), false);
                                return;
                            }
                            
                            // Check for overlap with opposite type
                            if (chunk.isWhitelist) {
                                for (BlacklistedDimension wlDim : cc.dimensions) {
                                    for (BlacklistedDimension blDim : faction.dimensionBlacklist) {
                                        if (wlDim.overlaps(blDim)) {
                                            player.sendMessage(net.minecraft.text.Text.literal("§cWhitelist region cannot overlap blacklist region!"), false);
                                            return;
                                        }
                                    }
                                }
                                faction.dimensionWhitelist.clear();
                                faction.dimensionWhitelist.addAll(cc.dimensions);
                                faction.saveDimensionWhitelistToJson();
                            } else {
                                for (BlacklistedDimension blDim : cc.dimensions) {
                                    for (BlacklistedDimension wlDim : faction.dimensionWhitelist) {
                                        if (blDim.overlaps(wlDim)) {
                                            player.sendMessage(net.minecraft.text.Text.literal("§cBlacklist region cannot overlap whitelist region!"), false);
                                            return;
                                        }
                                    }
                                }
                                faction.dimensionBlacklist.clear();
                                faction.dimensionBlacklist.addAll(cc.dimensions);
                                faction.saveDimensionBlacklistToJson();
                            }
                            io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
                            Faction.save();
                            broadcastDimensionsToFaction(faction);
                            
                            // Also send direct sync to the committing player (handles spoofing case)
                            syncDimensionsToPlayer(player, faction.dimensionBlacklist, false);
                            syncDimensionsToPlayer(player, faction.dimensionWhitelist, true);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle user sync request from client - send current user faction data back
     */
    private static void handleUserSyncRequestPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
        server.execute(() -> {
            try {
                User user = Command.getUser(player);
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
        if (faction == null) return;

        net.minecraft.server.MinecraftServer server = io.icker.factions.util.WorldUtils.server;
        if (server == null) return;

        try {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                User user = User.get(player.getUuid());
                Faction userFaction = user != null ? user.getFaction() : null;
                if (userFaction != null && userFaction.getID().equals(faction.getID())) {
                    if (user.rank == User.Rank.OWNER || user.rank == User.Rank.COMMANDER || user.rank == User.Rank.LEADER) {
                        // Always send both blacklist and whitelist
                        syncDimensionsToPlayer(player, faction.dimensionBlacklist, false);
                        syncDimensionsToPlayer(player, faction.dimensionWhitelist, true);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Carve out unclaimed chunks from dimension regions, preserving only the parts
     * that fall within the faction's claimed area.
     * This cleans up stale data and also handles the claim-removal carving 
     * (the carving in InteractionManager.onClaimRemove may fail to persist 
     * when Faction.save() overwrites with stale data).
     * @return true if any regions were modified
     */
    private static boolean cleanupStaleRegions(Faction faction, java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> list) {
        if (list == null || list.isEmpty()) return false;
        
        java.util.List<io.icker.factions.api.persistents.Claim> claims = io.icker.factions.api.persistents.Claim.getByFaction(faction.getID());
        java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> result = new java.util.ArrayList<>();
        boolean changed = false;
        
        for (io.icker.factions.api.persistents.BlacklistedDimension dim : list) {
            if (dim == null || dim.world == null || dim.world.isEmpty()) {
                changed = true;
                continue;
            }
            // Carve out unclaimed chunks from this region
            java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> carved = carveUnclaimedChunks(dim, claims);
            result.addAll(carved);
            // If the carved result has fewer/smaller regions, we changed something
            if (carved.size() == 1 && carved.get(0).minX == dim.minX && carved.get(0).maxX == dim.maxX
                && carved.get(0).minY == dim.minY && carved.get(0).maxY == dim.maxY
                && carved.get(0).minZ == dim.minZ && carved.get(0).maxZ == dim.maxZ) {
                // No change
            } else {
                changed = true;
            }
        }
        
        if (changed) {
            list.clear();
            list.addAll(result);
        }
        return changed;
    }
    
    /**
     * Carve out all unclaimed chunks from a dimension region by splitting it into fragments
     * that only cover claimed chunks. Uses box subtraction (same logic as InteractionManager).
     */
    private static java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> carveUnclaimedChunks(
            io.icker.factions.api.persistents.BlacklistedDimension dim,
            java.util.List<io.icker.factions.api.persistents.Claim> claims) {
        java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> fragments = new java.util.ArrayList<>();
        fragments.add(dim);
        
        // For each chunk this region covers (in the same world), check if it's claimed
        int minChunkX = Math.floorDiv(dim.minX, 16);
        int maxChunkX = Math.floorDiv(dim.maxX, 16);
        int minChunkZ = Math.floorDiv(dim.minZ, 16);
        int maxChunkZ = Math.floorDiv(dim.maxZ, 16);
        
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Check if this chunk is claimed by this faction
                boolean isClaimed = false;
                for (io.icker.factions.api.persistents.Claim claim : claims) {
                    if (claim.x == chunkX && claim.z == chunkZ && claim.level.equals(dim.world)) {
                        isClaimed = true;
                        break;
                    }
                }
                if (isClaimed) continue; // Claimed chunk, keep it
                
                // Unclaimed chunk - carve it out of all current fragments
                int claimMinX = chunkX * 16;
                int claimMaxX = (chunkX + 1) * 16 - 1;
                int claimMinZ = chunkZ * 16;
                int claimMaxZ = (chunkZ + 1) * 16 - 1;
                
                java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> newFragments = new java.util.ArrayList<>();
                for (io.icker.factions.api.persistents.BlacklistedDimension frag : fragments) {
                    // Check overlap
                    if (frag.world.equals(dim.world) && 
                        frag.minX <= claimMaxX && frag.maxX >= claimMinX &&
                        frag.minZ <= claimMaxZ && frag.maxZ >= claimMinZ) {
                        // Overlaps - subtract this chunk
                        newFragments.addAll(subtractChunkFromRegion(frag, claimMinX, claimMaxX, claimMinZ, claimMaxZ));
                    } else {
                        // No overlap, keep as-is
                        newFragments.add(frag);
                    }
                }
                fragments = newFragments;
            }
        }
        
        return fragments;
    }
    
    /**
     * Subtract a single claimed chunk from a region, splitting into up to 4 sub-regions.
     * Same logic as InteractionManager.subtractBlacklistedClaim.
     */
    private static java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> subtractChunkFromRegion(
            io.icker.factions.api.persistents.BlacklistedDimension dim,
            int claimMinX, int claimMaxX, int claimMinZ, int claimMaxZ) {
        java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> result = new java.util.ArrayList<>();
        
        // Left box
        if (dim.minX < claimMinX) {
            result.add(new io.icker.factions.api.persistents.BlacklistedDimension(
                dim.world, dim.minX, dim.minY, dim.minZ,
                claimMinX - 1, dim.maxY, dim.maxZ, dim.name
            ));
        }
        // Right box
        if (dim.maxX > claimMaxX) {
            result.add(new io.icker.factions.api.persistents.BlacklistedDimension(
                dim.world, claimMaxX + 1, dim.minY, dim.minZ,
                dim.maxX, dim.maxY, dim.maxZ, dim.name
            ));
        }
        // Front box (z: dim.minZ to claimMinZ-1, x clamped)
        if (dim.minZ < claimMinZ) {
            result.add(new io.icker.factions.api.persistents.BlacklistedDimension(
                dim.world,
                Math.max(dim.minX, claimMinX), dim.minY, dim.minZ,
                Math.min(dim.maxX, claimMaxX), dim.maxY, claimMinZ - 1,
                dim.name
            ));
        }
        // Back box (z: claimMaxZ+1 to dim.maxZ, x clamped)
        if (dim.maxZ > claimMaxZ) {
            result.add(new io.icker.factions.api.persistents.BlacklistedDimension(
                dim.world,
                Math.max(dim.minX, claimMinX), dim.minY, claimMaxZ + 1,
                Math.min(dim.maxX, claimMaxX), dim.maxY, dim.maxZ,
                dim.name
            ));
        }
        return result;
    }
}
