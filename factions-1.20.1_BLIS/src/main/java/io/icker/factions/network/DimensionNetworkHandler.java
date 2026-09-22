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
import net.minecraft.text.Text;
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
    public static final Identifier EXPORT_PACKET_ID = new Identifier("factions", "dimension_export");
    public static final Identifier IMPORT_REQUEST_PACKET_ID = new Identifier("factions", "dimension_import_request");
    public static final Identifier IMPORT_PACKET_ID = new Identifier("factions", "dimension_import");

    // Accumulates commit chunks per player: sessionId -> {totalChunks, dimensions, receivedChunkIndices}
    private static final java.util.Map<java.util.UUID, ChunkedCommit> pendingCommitChunks =
        new java.util.HashMap<>();

    // Accumulates import upload chunks: sessionId -> {totalChunks, data, receivedChunkIndices}
    private static final java.util.Map<java.util.UUID, FileChunkAssembly> pendingImportChunks =
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

    private static class FileChunkAssembly {
        final int totalChunks;
        final java.util.Map<Integer, byte[]> parts = new java.util.HashMap<>();
        final java.util.BitSet receivedChunks;
        FileChunkAssembly(int totalChunks) {
            this.totalChunks = totalChunks;
            this.receivedChunks = new java.util.BitSet(totalChunks);
        }
        boolean isComplete() {
            return receivedChunks.cardinality() == totalChunks;
        }
        byte[] assemble() {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] part = parts.get(i);
                if (part != null) baos.writeBytes(part);
            }
            return baos.toByteArray();
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

        // Register the server-side packet receiver for client→server import uploads
        ServerPlayNetworking.registerGlobalReceiver(IMPORT_PACKET_ID,
            (server, player, handler, buf, responseSender) -> {
                handleImportPacket(server, player, buf);
            });

        pendingCommitChunks.clear(); // Reset on server start
        pendingImportChunks.clear();

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
     * Handle chunked import upload from client. Reassembles gzip payload,
     * parses the export JSON, and merges regions into the faction.
     */
    private static void handleImportPacket(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player,
                                           PacketByteBuf buf) {
        try {
            byte[] nbtBytes = new byte[buf.readableBytes()];
            buf.readBytes(nbtBytes);

            server.execute(() -> {
                try {
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(nbtBytes));
                    net.minecraft.nbt.NbtCompound nbtCompound = NbtIo.read(dis);
                    if (nbtCompound == null) return;

                    DimensionFilePacket chunk = DimensionFilePacket.fromNbt(nbtCompound);
                    if (chunk.sessionId == null || chunk.totalChunks < 1
                            || chunk.totalChunks > DimensionFilePacket.MAX_CHUNKS
                            || chunk.chunkIndex < 0 || chunk.chunkIndex >= chunk.totalChunks) {
                        player.sendMessage(Text.literal("§cInvalid import packet"), false);
                        return;
                    }

                    FileChunkAssembly assembly = pendingImportChunks.get(chunk.sessionId);
                    if (assembly == null) {
                        if (pendingImportChunks.size() >= 32) pendingImportChunks.clear();
                        assembly = new FileChunkAssembly(chunk.totalChunks);
                        pendingImportChunks.put(chunk.sessionId, assembly);
                    }
                    if (assembly.totalChunks != chunk.totalChunks) {
                        pendingImportChunks.remove(chunk.sessionId);
                        return;
                    }
                    assembly.parts.put(chunk.chunkIndex, chunk.data);
                    assembly.receivedChunks.set(chunk.chunkIndex);

                    if (!assembly.isComplete()) return;
                    pendingImportChunks.remove(chunk.sessionId);

                    User user = Command.getUser(player);
                    if (user == null) return;
                    Faction faction = user.getFaction();
                    if (faction == null) {
                        player.sendMessage(Text.literal("§cYou must be in a faction"), false);
                        return;
                    }
                    if (user.rank != User.Rank.OWNER && user.rank != User.Rank.COMMANDER
                            && user.rank != User.Rank.LEADER) {
                        player.sendMessage(Text.literal("§cOnly faction leadership can import dimension regions!"), false);
                        return;
                    }

                    String json = io.icker.factions.util.RegionTransfer.gunzip(assembly.assemble());
                    io.icker.factions.util.RegionTransfer.ExportData imported =
                            io.icker.factions.util.RegionTransfer.parseJson(json);
                    io.icker.factions.util.RegionTransfer.ImportStats stats =
                            io.icker.factions.util.RegionTransfer.mergeImport(faction, imported);

                    faction.saveDimensionBlacklistToJson(true);
                    faction.saveDimensionWhitelistToJson(true);
                    io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
                    Faction.save();
                    broadcastDimensionsToFaction(faction);
                    syncDimensionsToPlayer(player, faction.dimensionBlacklist, false);
                    syncDimensionsToPlayer(player, faction.dimensionWhitelist, true);

                    new io.icker.factions.util.Message(
                            "Imported ").add(new io.icker.factions.util.Message(stats.blacklistAdded + "")
                                    .format(net.minecraft.util.Formatting.YELLOW))
                            .add(" blacklist / ")
                            .add(new io.icker.factions.util.Message(stats.whitelistAdded + "")
                                    .format(net.minecraft.util.Formatting.YELLOW))
                            .add(" whitelist regions")
                            .send(player, false);
                    if (stats.croppedToClaims > 0 || stats.croppedToOpposite > 0
                            || stats.duplicatesSkipped > 0 || stats.dropped > 0) {
                        new io.icker.factions.util.Message(
                                "Cropped " + stats.croppedToClaims + " to claims, "
                                + stats.croppedToOpposite + " to existing opposite regions, skipped "
                                + stats.duplicatesSkipped + " duplicates, dropped "
                                + stats.dropped + " outside claims/conflicts").send(player, false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    player.sendMessage(Text.literal("§cError importing regions: " + e.getMessage()), false);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Send gzip-chunked export payload from server to client.
     */
    public static void sendExportChunks(ServerPlayerEntity player, String filename, String json) {
        try {
            java.util.UUID sessionId = java.util.UUID.randomUUID();
            byte[] compressed = io.icker.factions.util.RegionTransfer.gzip(json);
            java.util.List<DimensionFilePacket> chunks =
                    io.icker.factions.util.RegionTransfer.chunkPayload(sessionId, filename, compressed);

            for (DimensionFilePacket packet : chunks) {
                sendNbt(player, EXPORT_PACKET_ID, packet.toNbt());
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(Text.literal("§cError exporting regions: " + e.getMessage()), false);
        }
    }

    /**
     * Ask the client to read a local export file and upload it.
     */
    public static void sendImportRequest(ServerPlayerEntity player, String filename) {
        try {
            DimensionFilePacket packet = new DimensionFilePacket(
                    java.util.UUID.randomUUID(), 1, 0, filename, new byte[0]);
            sendNbt(player, IMPORT_REQUEST_PACKET_ID, packet.toNbt());
            new io.icker.factions.util.Message("Requesting import of " + filename + " from your client...")
                    .send(player, false);
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(Text.literal("§cError requesting import: " + e.getMessage()), false);
        }
    }

    private static void sendNbt(ServerPlayerEntity player, Identifier id, net.minecraft.nbt.NbtCompound nbt) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(nbt, dos);
            byte[] nbtBytes = baos.toByteArray();
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.copiedBuffer(nbtBytes);
            PacketByteBuf buf = new PacketByteBuf(byteBuf);
            ServerPlayNetworking.send(player, id, buf);
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
            java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> carved =
                    io.icker.factions.util.RegionClipper.carveToClaims(dim, claims);
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
}
