package io.icker.factions.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Client-side entry point for Factions mod
 * Handles all client-side features like rendering and item interactions
 */
public class FactionsClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("FactionsClient");

    static {
        System.out.println("================== FactionsClientMod CLASS LOADED ==================");
        System.out.println("This class is definitely being loaded");
    }

    @Override
    public void onInitializeClient() {
        System.out.println("================== onInitializeClient called! ==================");
        try {
            LOGGER.info("onInitializeClient called!");
            sendChatMessage("§6[FactionsClient]§r Initializing client mod...");
            // Register client-side event listeners
            DimensionBlacklistItemHandler.register();
            io.icker.factions.client.network.DimensionClientSyncHandler.register();
            io.icker.factions.client.network.UserClientSyncHandler.register();
            LOGGER.info("Client mod initialized!");
            sendChatMessage("§6[FactionsClient]§r Client mod initialized!");
        } catch (Exception e) {
            System.out.println("================== EXCEPTION IN onInitializeClient ==================");
            e.printStackTrace();
            LOGGER.error("ERROR during client initialization:", e);
            sendChatMessage("§c[FactionsClient] ERROR: " + e.getMessage());
        }
    }

    private static void sendChatMessage(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal(message), false);
                }
            });
        }
    }
}
