package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.MiscEvents;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Message;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class ServerManager {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(ServerManager::playerJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register(ServerManager::playerRespawn);
        MiscEvents.ON_SAVE.register(ServerManager::save);
    }

    private static void save(MinecraftServer server) {
        Claim.save();
        Faction.save();
        User.save();
    }

    private static void playerJoin(ServerPlayNetworkHandler handler, PacketSender sender,
            MinecraftServer server) {
        ServerPlayerEntity player = handler.getPlayer();
        User user = User.get(player.getUuid());
        user.lastSeen = System.currentTimeMillis();

        if (user.isInFaction()) {
            Faction faction = user.getFaction();
            new Message("Welcome back " + player.getName().getString() + "!").send(player, false);
            new Message(faction.getMOTD()).prependFaction(faction).send(player, false);

            // Apply active blessings from God's Blessings system
            applyActiveBlessings(player, user, faction);
        }
    }

    private static void playerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        User user = User.get(newPlayer.getUuid());
        if (user.isInFaction()) {
            Faction faction = user.getFaction();
            // Reapply active blessings after respawn
            applyActiveBlessings(newPlayer, user, faction);
        }
    }

    private static void applyActiveBlessings(ServerPlayerEntity player, User user, Faction faction) {
        if (FactionsMod.CONFIG.GODS == null || !FactionsMod.CONFIG.GODS.ENABLED) {
            return;
        }

        // Apply own faction's blessings
        applyBlessingsFromFaction(player, faction);

        // Apply overlord's blessings if vassal and APPLY_TO_VASSALS is enabled
        if (FactionsMod.CONFIG.GODS.APPLY_TO_VASSALS && faction.isVassal()) {
            Faction overlord = faction.getOverlord();
            if (overlord != null) {
                applyBlessingsFromFaction(player, overlord);
            }
        }
    }

    private static void applyBlessingsFromFaction(ServerPlayerEntity player, Faction faction) {
        for (Faction.ActiveBlessing blessing : faction.getActiveBlessings()) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(new Identifier(blessing.effect));
            if (effect != null) {
                int remainingTicks = blessing.getRemainingDurationTicks();
                if (remainingTicks > 0) {
                    // Remove old effect first to avoid stale duration from save/load
                    player.removeStatusEffect(effect);
                    player.addStatusEffect(new StatusEffectInstance(
                            effect, remainingTicks, blessing.amplifier, false, false, true));
                }
            }
        }
    }
}
