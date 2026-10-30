package io.icker.factions.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
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

        // Coalesce bursts of changes (mass claiming, decays, ...) into one sync per tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (dirty) {
                dirty = false;
                List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                for (ServerPlayerEntity player : players) {
                    sendClaims(player);
                }
            }
        });
    }

    /**
     * Send the current claim snapshot to a single player.
     */
    public static void sendClaims(ServerPlayerEntity player) {
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

            PacketByteBuf buf = new PacketByteBuf(
                    io.netty.buffer.Unpooled.copiedBuffer(compressed.toByteArray()));
            ServerPlayNetworking.send(player, CLAIM_SYNC_ID, buf);
        } catch (Exception e) {
            FactionsMod.LOGGER.warn("Failed to send claim sync to " + player.getName().getString(), e);
        }
    }
}
