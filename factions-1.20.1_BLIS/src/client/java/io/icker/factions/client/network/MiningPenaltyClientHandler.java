package io.icker.factions.client.network;

import io.icker.factions.network.MiningPenaltyNetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side receiver for the mining penalty flag packet.
 */
public class MiningPenaltyClientHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MiningPenaltyNetworkHandler.PACKET_ID,
            (client, handler, buf, responseSender) -> {
                MiningPenaltyNetworkHandler.handleClient(buf);
            });
    }
}
