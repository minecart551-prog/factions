package io.icker.factions.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPOutputStream;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.ClaimEvents;
import io.icker.factions.api.events.FactionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Server side of the faction claim sync for map mods.
 *
 * <p>Sends a gzipped {@link ClaimSyncPacket} snapshot to players when they join
 * and re-sends it (coalesced to at most once per tick) whenever claims, homes
 * or faction identity change. The payload is consumed by VoxelMap's full screen
 * map (channel {@code voxelmap:faction_claims}); clients without VoxelMap simply
 * ignore it.
 */
public class ClaimNetworkHandler {
    public static final Identifier CLAIM_SYNC_ID = new Identifier("voxelmap", "faction_claims");

    private static boolean dirty = false;

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendClaims(handler.player));

        ClaimEvents.ADD.register(claim -> dirty = true);
        ClaimEvents.REMOVE.register((x, z, level, faction) -> dirty = true);
        FactionEvents.CREATE.register((faction, user) -> dirty = true);
        FactionEvents.DISBAND.register(faction -> dirty = true);
        FactionEvents.MODIFY.register(faction -> dirty = true);
        FactionEvents.SET_HOME.register((faction, home) -> dirty = true);
        FactionEvents.REMOVE_ALL_CLAIMS.register(faction -> dirty = true);
        // power and member counts are part of the payload now
        FactionEvents.POWER_CHANGE.register((faction, oldPower) -> dirty = true);
        FactionEvents.MEMBER_JOIN.register((faction, user) -> dirty = true);
        FactionEvents.MEMBER_LEAVE.register((faction, user) -> dirty = true);

        // Coalesce bursts of changes (mass claiming, decays, ...) into one sync per tick.
        // The snapshot is built and compressed once and the same bytes go to everyone,
        // rather than rebuilding the whole payload per player.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!dirty) return;
            dirty = false;

            byte[] payload = buildPayload();
            if (payload == null) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                send(player, payload);
            }
        });
    }

    /**
     * Send the current claim snapshot to a single player, building it for them
     * alone. Used when a player joins, where there is no payload to share.
     */
    public static void sendClaims(ServerPlayerEntity player) {
        byte[] payload = buildPayload();
        if (payload != null) {
            send(player, payload);
        }
    }

    /**
     * Builds and compresses the snapshot, or null when it could not be built.
     */
    private static byte[] buildPayload() {
        try {
            NbtCompound nbt = ClaimSyncPacket.build();

            ByteArrayOutputStream nbtBytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(nbtBytes)) {
                NbtIo.write(nbt, out);
            }

            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(nbtBytes.toByteArray());
            }
            return compressed.toByteArray();
        } catch (Exception e) {
            FactionsMod.LOGGER.warn("Failed to build claim sync payload", e);
            return null;
        }
    }

    private static void send(ServerPlayerEntity player, byte[] payload) {
        try {
            // A fresh wrapper per player: the buffer's read position is consumed as the
            // packet is written, so sharing one buffer across sends would break the rest.
            PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload));
            ServerPlayNetworking.send(player, CLAIM_SYNC_ID, buf);
        } catch (Exception e) {
            FactionsMod.LOGGER.warn("Failed to send claim sync to " + player.getName().getString(), e);
        }
    }
}
