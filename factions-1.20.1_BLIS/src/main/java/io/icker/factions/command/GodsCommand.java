package io.icker.factions.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.config.GodsConfig;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class GodsCommand implements Command {

    private int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        if (FactionsMod.CONFIG.GODS == null || !FactionsMod.CONFIG.GODS.ENABLED) {
            new Message("God's Blessings system is disabled").fail().send(player, false);
            return 0;
        }

        GodsConfig.God[] gods = FactionsMod.CONFIG.GODS.GODS;

        if (gods == null || gods.length == 0) {
            new Message("No gods are configured").fail().send(player, false);
            return 0;
        }

        new Message(Formatting.GOLD + "=== Available Gods ===").send(player, false);

        for (GodsConfig.God god : gods) {
            String effectName = god.EFFECT.replace("minecraft:", "");
            int level = god.AMPLIFIER + 1;
            new Message(Formatting.YELLOW + god.NAME)
                    .hover(String.format(
                        "Effect: %s %d\nDuration: %d seconds\nCost: %d wealth power",
                        effectName, level, god.DURATION_SECONDS, god.POWER_COST))
                    .click("/factions gods pray " + god.NAME)
                    .send(player, false);
        }

        return 1;
    }

    private int pray(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String godName = StringArgumentType.getString(context, "god");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        if (FactionsMod.CONFIG.GODS == null || !FactionsMod.CONFIG.GODS.ENABLED) {
            new Message("God's Blessings system is disabled").fail().send(player, false);
            return 0;
        }

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        // Find the god
        GodsConfig.God god = findGod(godName);
        if (god == null) {
            new Message("Unknown god: " + godName).fail().send(player, false);
            return 0;
        }

        // Check cooldown
        long lastPrayer = faction.getLastPrayer();
        long cooldownMs = FactionsMod.CONFIG.GODS.GLOBAL_COOLDOWN_SECONDS * 1000L;
        long elapsed = System.currentTimeMillis() - lastPrayer;
        if (lastPrayer > 0 && elapsed < cooldownMs) {
            long remaining = (cooldownMs - elapsed) / 1000;
            new Message("Must wait %d seconds before praying again", remaining)
                    .fail().send(player, false);
            return 0;
        }

        // Check MAX_ACTIVE_EFFECTS
        int maxEffects = FactionsMod.CONFIG.GODS.MAX_ACTIVE_EFFECTS;
        if (maxEffects >= 0) {
            // Count active blessings excluding the same god (since it will be replaced)
            long activeCount = faction.getActiveBlessings().stream()
                    .filter(b -> !b.godName.equals(god.NAME))
                    .count();
            if (activeCount >= maxEffects) {
                new Message("Faction already has maximum active blessings (%d)", maxEffects)
                        .fail().send(player, false);
                return 0;
            }
        }

        // Check wealth power
        int currentWealth = faction.getWealthPower();
        if (currentWealth < god.POWER_COST) {
            new Message("Not enough wealth power (need %d, have %d)",
                    god.POWER_COST, currentWealth).fail().send(player, false);
            return 0;
        }

        // Find the status effect
        Identifier effectId = new Identifier(god.EFFECT);
        StatusEffect statusEffect = Registries.STATUS_EFFECT.get(effectId);
        if (statusEffect == null) {
            new Message("Invalid effect configured for " + god.NAME).fail().send(player, false);
            return 0;
        }

        // Spend wealth power
        faction.spendWealthPower(god.POWER_COST);
        faction.setLastPrayer(System.currentTimeMillis());

        // Track active blessing
        long expiresAt = System.currentTimeMillis() + (god.DURATION_SECONDS * 1000L);
        faction.addActiveBlessing(god.NAME, god.EFFECT, god.AMPLIFIER, expiresAt);

        int durationTicks = god.DURATION_SECONDS * 20;
        StatusEffectInstance effectInstance = new StatusEffectInstance(
                statusEffect, durationTicks, god.AMPLIFIER, false, false, true);

        // Apply to faction members
        int applied = applyEffectToFaction(faction, effectInstance);

        // Apply to vassals if enabled
        int vassalApplied = 0;
        if (FactionsMod.CONFIG.GODS.APPLY_TO_VASSALS) {
            for (Faction vassal : faction.getVassals()) {
                vassalApplied += applyEffectToFaction(vassal, effectInstance);
            }
        }

        // Send message
        String effectName = god.EFFECT.replace("minecraft:", "");
        new Message("%s prayed to %s! %s %d granted to %d members for %d seconds",
                player.getName().getString(),
                god.NAME,
                effectName,
                god.AMPLIFIER + 1,
                applied + vassalApplied,
                god.DURATION_SECONDS)
                .format(Formatting.GOLD)
                .send(faction);

        if (vassalApplied > 0) {
            for (Faction vassal : faction.getVassals()) {
                new Message("Your overlord's blessing from %s has been bestowed upon you!",
                        god.NAME)
                        .format(Formatting.LIGHT_PURPLE)
                        .send(vassal);
            }
        }

        return 1;
    }

    private GodsConfig.God findGod(String name) {
        if (FactionsMod.CONFIG.GODS == null || FactionsMod.CONFIG.GODS.GODS == null) {
            return null;
        }
        for (GodsConfig.God god : FactionsMod.CONFIG.GODS.GODS) {
            if (god.NAME.equalsIgnoreCase(name)) {
                return god;
            }
        }
        return null;
    }

    private int applyEffectToFaction(Faction faction, StatusEffectInstance effect) {
        int count = 0;
        for (User member : faction.getUsers()) {
            ServerPlayerEntity memberPlayer = Message.manager.getPlayer(member.getID());
            if (memberPlayer != null) {
                memberPlayer.addStatusEffect(new StatusEffectInstance(effect));
                count++;
            }
        }
        return count;
    }

    private static SuggestionProvider<ServerCommandSource> godSuggestions() {
        return (context, builder) -> {
            if (FactionsMod.CONFIG.GODS != null && FactionsMod.CONFIG.GODS.GODS != null) {
                for (GodsConfig.God god : FactionsMod.CONFIG.GODS.GODS) {
                    builder.suggest(god.NAME);
                }
            }
            return builder.buildFuture();
        };
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
                .literal("gods")
                .requires(Requires.hasPerms("factions.gods", 0))
                .executes(this::list)
                .then(CommandManager.literal("list")
                        .requires(Requires.hasPerms("factions.gods.list", 0))
                        .executes(this::list))
                .then(CommandManager.literal("pray")
                        .requires(Requires.multiple(
                                Requires.isLeader(),
                                Requires.hasPerms("factions.gods.pray", 0)))
                        .then(CommandManager.argument("god", StringArgumentType.word())
                                .suggests(godSuggestions())
                                .executes(this::pray)))
                .build();
    }
}
