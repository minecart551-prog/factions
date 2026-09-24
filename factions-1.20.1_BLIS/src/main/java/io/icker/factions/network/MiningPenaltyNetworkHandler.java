package io.icker.factions.network;

import io.icker.factions.core.PenaltyMiningState;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * S2C: tells the client whether the block it just started mining is a
 * penalized foreign-claim break so client crack prediction matches the server.
 */
public final class MiningPenaltyNetworkHandler {
    public static final Identifier PACKET_ID = new Identifier("factions", "mining_penalty");

    private MiningPenaltyNetworkHandler() {}

    public static void send(ServerPlayerEntity player, BlockPos pos, boolean penalized) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(penalized);
        ServerPlayNetworking.send(player, PACKET_ID, buf);
    }

    /** Client-side read of the packet payload. */
    public static void handleClient(PacketByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean penalized = buf.readBoolean();
        PenaltyMiningState.set(pos, penalized);
    }
}
