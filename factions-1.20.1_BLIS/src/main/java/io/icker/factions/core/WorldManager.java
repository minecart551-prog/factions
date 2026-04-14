package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.MiscEvents;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship;
import io.icker.factions.api.persistents.User;
import io.icker.factions.config.Config.TerritoryNotificationConfig;
import io.icker.factions.util.Message;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

public class WorldManager {
    public static void register() {
        PlayerEvents.ON_MOVE.register(WorldManager::onMove);
        MiscEvents.ON_MOB_SPAWN_ATTEMPT.register(WorldManager::onMobSpawnAttempt);
    }

    private static void onMobSpawnAttempt() {
        // TO DO Implement this
    }

    private static void onMove(ServerPlayerEntity player) {
        User user = User.get(player.getUuid());
        ServerWorld world = (ServerWorld) player.getWorld();
        String dimension = world.getRegistryKey().getValue().toString();

        ChunkPos chunkPos = world.getChunk(player.getBlockPos()).getPos();

        Claim claim = Claim.get(chunkPos.x, chunkPos.z, dimension);
        if (user.autoclaim && claim == null) {
            Faction faction = user.getFaction();
            int requiredPower =
                    (faction.getClaims().size() + 1) * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT;
            int maxPower = faction.getUsers().size() * FactionsMod.CONFIG.POWER.MEMBER
                    + FactionsMod.CONFIG.POWER.BASE
                    + faction.getAdminPower();

            if (maxPower < requiredPower) {
                new Message("Not enough faction power to claim chunk, autoclaim toggled off").fail()
                        .send(player, false);
                user.autoclaim = false;
            } else {
                int cooldownSeconds = FactionsMod.CONFIG.POWER.UNCLAIM_COOLDOWN_SECONDS;
                long remaining = (cooldownSeconds > 0 && faction.lastUnclaimTime > 0)
                        ? Math.max(0, (cooldownSeconds * 1000L) - (System.currentTimeMillis() - faction.lastUnclaimTime))
                        : 0;
                if (remaining > 0) {
                    new Message("Your faction cannot claim chunks for another %ds, autoclaim toggled off",
                            remaining / 1000 + 1).fail().send(player, false);
                    user.autoclaim = false;
                } else {
                    faction.addClaim(chunkPos.x, chunkPos.z, dimension);
                    claim = Claim.get(chunkPos.x, chunkPos.z, dimension);
                    new Message("Chunk (%d, %d) claimed by %s", chunkPos.x, chunkPos.z,
                            player.getName().getString()).send(faction);
                }
            }
        }

        String currentKey = claim != null ? claim.factionID.toString() : "wilderness";
        if (user.lastTerritoryKey == null) {
            user.lastTerritoryKey = currentKey;
        } else if (!currentKey.equals(user.lastTerritoryKey)) {
            user.lastTerritoryKey = currentKey;
            notifyTerritoryChange(player, claim);
        }

        if (user.radar) {
            if (claim != null) {
                new Message(claim.getFaction().getName()).format(claim.getFaction().getColor())
                        .send(player, true);
            } else {
                new Message("Wilderness").format(Formatting.GRAY).send(player, true);
            }
        }
    }

    private static void notifyTerritoryChange(ServerPlayerEntity player, Claim claim) {
        TerritoryNotificationConfig notif = FactionsMod.CONFIG.DISPLAY.TERRITORY_NOTIFICATION;
        if (notif == null) return;

        Message nameMessage;
        Message statusMessage = null;

        if (claim != null) {
            Faction claimFaction = claim.getFaction();
            Formatting color;
            String label;

            User user = User.get(player.getUuid());
            if (!user.isInFaction()) {
                color = Formatting.GRAY;
                label = "Neutral";
            } else {
                Faction userFaction = user.getFaction();
                if (userFaction.getID().equals(claimFaction.getID())) {
                    color = Formatting.WHITE;
                    label = "Your territory";
                } else {
                    Relationship.Status status = claimFaction.getRelationship(userFaction.getID()).status;
                    color = switch (status) {
                        case ALLY -> Formatting.GREEN;
                        case FRIENDLY -> Formatting.AQUA;
                        case ENEMY -> Formatting.RED;
                        default -> Formatting.GRAY;
                    };
                    label = switch (status) {
                        case ALLY -> "Ally";
                        case FRIENDLY -> "Friendly";
                        case ENEMY -> "Enemy";
                        default -> "Neutral";
                    };
                }
            }

            nameMessage = new Message(claimFaction.getName()).format(claimFaction.getColor());
            statusMessage = new Message(label).format(color);
        } else {
            nameMessage = new Message("Wilderness").format(Formatting.GRAY);
        }

        if (notif.CHAT) {
            Message msg = new Message("Entering ").add(nameMessage);
            if (statusMessage != null) msg.add(", ").add(statusMessage);
            msg.send(player, false);
        }
        if (notif.ACTION_BAR) {
            Message msg = new Message("Entering ").add(nameMessage);
            if (statusMessage != null) msg.add(", ").add(statusMessage);
            msg.send(player, true);
        }
        if (notif.TITLE) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(notif.TITLE_FADE_IN, notif.TITLE_STAY, notif.TITLE_FADE_OUT));
            player.networkHandler.sendPacket(new TitleS2CPacket(nameMessage.raw()));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                statusMessage != null ? statusMessage.raw() : new Message("").raw()
            ));
        }
    }
}
