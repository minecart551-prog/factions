package io.icker.factions.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.core.FactionsManager;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;

import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.UUID;

public class AdminCommand implements Command {
    private int bypass(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        User user = User.get(player.getUuid());
        boolean bypass = !user.bypass;
        user.bypass = bypass;

        new Message("Successfully toggled claim bypass").filler("·")
                .add(new Message(user.bypass ? "ON" : "OFF")
                        .format(user.bypass ? Formatting.GREEN : Formatting.RED))
                .send(player, false);

        return 1;
    }

    private int reload(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        FactionsMod.dynmap.reloadAll();
        new Message("Reloaded dynmap marker").send(context.getSource().getPlayerOrThrow(), false);
        return 1;
    }

    private int power(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        Faction target = Faction.getByName(StringArgumentType.getString(context, "faction"));
        int power = IntegerArgumentType.getInteger(context, "power");

        target.addAdminPower(power);

        if (power != 0) {
            if (power > 0) {
                new Message("Admin %s added %d power", player.getName().getString(), power)
                        .send(target);
                new Message("Added %d power", power).send(player, false);
            } else {
                new Message("Admin %s removed %d power", player.getName().getString(), power)
                        .send(target);
                new Message("Removed %d power", power).send(player, false);
            }
        } else {
            new Message("No change to power").fail().send(player, false);
        }

        return 1;
    }

    private int powerRecalc(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int count = 0;
        for (Faction faction : Faction.all()) {
            faction.fillBasePower();
            count++;
        }
        new Message("Recalculated base power for %d factions", count).send(player, false);
        return 1;
    }

    private int spoof(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = User.get(player.getUuid());

        String name = StringArgumentType.getString(context, "player");

        User target;

        Optional<GameProfile> profile;
        if ((profile = source.getServer().getUserCache().findByName(name)).isPresent()) {
            target = User.get(profile.get().getId());
        } else {
            target = User.get(UUID.fromString(name));
        }

        user.setSpoof(target);

        new Message("Set spoof to player %s", name).send(player, false);

        return 1;
    }

    private int clearSpoof(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = User.get(player.getUuid());

        user.setSpoof(null);

        new Message("Cleared spoof").send(player, false);

        return 1;
    }

    private int audit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

        for (int i = 0; i < 4; i++) {
            Claim.audit();
            Faction.audit();
            User.audit();
        }

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player != null) {
            new Message("Successful audit").send(player, false);
        }

        return 1;
    }

    private int lastSeenSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        String name = StringArgumentType.getString(context, "player");
        int daysAgo = IntegerArgumentType.getInteger(context, "days");

        User target;
        Optional<GameProfile> profile;
        if ((profile = source.getServer().getUserCache().findByName(name)).isPresent()) {
            target = User.get(profile.get().getId());
        } else {
            target = User.get(UUID.fromString(name));
        }

        long millisAgo = daysAgo * 24L * 60L * 60L * 1000L;
        target.lastSeen = System.currentTimeMillis() - millisAgo;

        double multiplier = target.getActivityMultiplier();
        new Message("Set %s's lastSeen to %d days ago (activity multiplier: %.0f%%)",
                name, daysAgo, multiplier * 100).send(player, false);

        return 1;
    }

    private int lastSeenGet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        String name = StringArgumentType.getString(context, "player");

        User target;
        Optional<GameProfile> profile;
        if ((profile = source.getServer().getUserCache().findByName(name)).isPresent()) {
            target = User.get(profile.get().getId());
        } else {
            target = User.get(UUID.fromString(name));
        }

        long now = System.currentTimeMillis();
        long daysAgo = (now - target.lastSeen) / (1000L * 60 * 60 * 24);
        double multiplier = target.getActivityMultiplier();

        new Message("%s was last seen %d days ago (activity multiplier: %.0f%%)",
                name, daysAgo, multiplier * 100).send(player, false);

        return 1;
    }

    private int protection(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        Faction target = Faction.getByName(StringArgumentType.getString(context, "faction"));
        if (target == null) {
            new Message("Faction does not exist").fail().send(player, false);
            return 0;
        }

        boolean newStatus = !target.isAdminProtected();
        target.setAdminProtected(newStatus);

        new Message("Faction %s admin protection toggled", target.getName()).filler("·")
                .add(new Message(newStatus ? "ON" : "OFF")
                        .format(newStatus ? Formatting.GREEN : Formatting.RED))
                .send(player, false);

        if (newStatus) {
            new Message("Your faction is now under admin protection").send(target);
        } else {
            new Message("Your faction is no longer under admin protection").send(target);
        }

        return 1;
    }

    private int claimDecayTrigger(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        FactionsManager.checkAllFactionsForDecay();

        new Message("Claim decay check triggered for all factions").send(player, false);

        return 1;
    }

    private int claimDecayEnable(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        FactionsMod.CONFIG.POWER.CLAIM_DECAY_ENABLED = true;
        FactionsMod.CONFIG.save();

        new Message("Claim decay mechanic").filler("·")
                .add(new Message("ENABLED").format(Formatting.GREEN))
                .send(player, false);

        return 1;
    }

    private int claimDecayDisable(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        FactionsMod.CONFIG.POWER.CLAIM_DECAY_ENABLED = false;
        FactionsMod.CONFIG.save();

        new Message("Claim decay mechanic").filler("·")
                .add(new Message("DISABLED").format(Formatting.RED))
                .send(player, false);

        return 1;
    }

    private int showConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        new Message(Formatting.GOLD + "=== Factions Config ===").send(player, false);

        // General settings
        new Message(Formatting.YELLOW + "General Settings:").send(player, false);
        new Message(Formatting.GRAY + "  Block TNT: ")
                .add(new Message(FactionsMod.CONFIG.BLOCK_TNT ? "true" : "false")
                        .format(FactionsMod.CONFIG.BLOCK_TNT ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Friendly Fire: ")
                .add(new Message(FactionsMod.CONFIG.FRIENDLY_FIRE ? "true" : "false")
                        .format(FactionsMod.CONFIG.FRIENDLY_FIRE ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Claim Protection: ")
                .add(new Message(FactionsMod.CONFIG.CLAIM_PROTECTION ? "true" : "false")
                        .format(FactionsMod.CONFIG.CLAIM_PROTECTION ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Max Faction Size: " + Formatting.WHITE + FactionsMod.CONFIG.MAX_FACTION_SIZE).send(player, false);

        // Power settings
        new Message(Formatting.YELLOW + "Power Settings:").send(player, false);
        new Message(Formatting.GRAY + "  Base: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.BASE).send(player, false);
        new Message(Formatting.GRAY + "  Member: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.MEMBER).send(player, false);
        new Message(Formatting.GRAY + "  Claim Weight: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.CLAIM_WEIGHT).send(player, false);
        new Message(Formatting.GRAY + "  Death Penalty: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.DEATH_PENALTY).send(player, false);
        new Message(Formatting.GRAY + "  Enemy Death Multiplier: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.ENEMY_DEATH_MULTIPLIER).send(player, false);
        new Message(Formatting.GRAY + "  Power Per Ally: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.POWER_PER_ALLY).send(player, false);
        new Message(Formatting.GRAY + "  Claim Decay Enabled: ")
                .add(new Message(FactionsMod.CONFIG.POWER.CLAIM_DECAY_ENABLED ? "true" : "false")
                        .format(FactionsMod.CONFIG.POWER.CLAIM_DECAY_ENABLED ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Decay Check Ticks: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.DECAY_CHECK_TICKS).send(player, false);

        // Power Ticks
        new Message(Formatting.YELLOW + "Power Ticks:").send(player, false);
        new Message(Formatting.GRAY + "  Ticks: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.POWER_TICKS.TICKS).send(player, false);
        new Message(Formatting.GRAY + "  Reward: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.POWER_TICKS.REWARD).send(player, false);

        // Wealth settings
        new Message(Formatting.YELLOW + "Wealth Settings:").send(player, false);
        new Message(Formatting.GRAY + "  Max Value: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.WEALTH.MAX_VALUE).send(player, false);
        new Message(Formatting.GRAY + "  Decay Per Day: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.WEALTH.DECAY_PER_DAY).send(player, false);

        // War settings
        new Message(Formatting.YELLOW + "War Settings:").send(player, false);
        new Message(Formatting.GRAY + "  Max Value: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.WAR.MAX_VALUE).send(player, false);
        new Message(Formatting.GRAY + "  Kill Reward: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.WAR.KILL_REWARD).send(player, false);
        new Message(Formatting.GRAY + "  Enemy Multiplier: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.WAR.ENEMY_MULTIPLIER).send(player, false);
        new Message(Formatting.GRAY + "  Decay Per Day: " + Formatting.WHITE + FactionsMod.CONFIG.POWER.WAR.DECAY_PER_DAY).send(player, false);

        // Inactivity tiers
        new Message(Formatting.YELLOW + "Inactivity Tiers:").send(player, false);
        for (var tier : FactionsMod.CONFIG.POWER.INACTIVITY_TIERS) {
            new Message(Formatting.GRAY + "  " + tier.DAYS + " days: " + Formatting.WHITE + (int)(tier.MULTIPLIER * 100) + "%").send(player, false);
        }

        // Home settings
        if (FactionsMod.CONFIG.HOME != null) {
            new Message(Formatting.YELLOW + "Home Settings:").send(player, false);
            new Message(Formatting.GRAY + "  Warp Enabled: ")
                    .add(new Message(FactionsMod.CONFIG.HOME.WARP_ENABLED ? "true" : "false")
                            .format(FactionsMod.CONFIG.HOME.WARP_ENABLED ? Formatting.GREEN : Formatting.RED))
                    .send(player, false);
            new Message(Formatting.GRAY + "  Claim Only: ")
                    .add(new Message(FactionsMod.CONFIG.HOME.CLAIM_ONLY ? "true" : "false")
                            .format(FactionsMod.CONFIG.HOME.CLAIM_ONLY ? Formatting.GREEN : Formatting.RED))
                    .send(player, false);
            new Message(Formatting.GRAY + "  Damage Tick Cooldown: " + Formatting.WHITE + FactionsMod.CONFIG.HOME.DAMAGE_COOLDOWN).send(player, false);
            new Message(Formatting.GRAY + "  Home Warp Cooldown (sec): " + Formatting.WHITE + FactionsMod.CONFIG.HOME.HOME_WARP_COOLDOWN_SECOND).send(player, false);
        }

        // Safe settings
        if (FactionsMod.CONFIG.SAFE != null) {
            new Message(Formatting.YELLOW + "Safe Settings:").send(player, false);
            new Message(Formatting.GRAY + "  Safe Enabled: ")
                    .add(new Message(FactionsMod.CONFIG.SAFE.SAFE_ENABLED ? "true" : "false")
                            .format(FactionsMod.CONFIG.SAFE.SAFE_ENABLED ? Formatting.GREEN : Formatting.RED))
                    .send(player, false);
            new Message(Formatting.GRAY + "  Ender Chest: ")
                    .add(new Message(FactionsMod.CONFIG.SAFE.ENDER_CHEST ? "true" : "false")
                            .format(FactionsMod.CONFIG.SAFE.ENDER_CHEST ? Formatting.GREEN : Formatting.RED))
                    .send(player, false);
            new Message(Formatting.GRAY + "  Double: ")
                    .add(new Message(FactionsMod.CONFIG.SAFE.DOUBLE ? "true" : "false")
                            .format(FactionsMod.CONFIG.SAFE.DOUBLE ? Formatting.GREEN : Formatting.RED))
                    .send(player, false);
        }

        // Vassal settings
        new Message(Formatting.YELLOW + "Vassal Settings:").send(player, false);
        new Message(Formatting.GRAY + "  Enabled: ")
                .add(new Message(FactionsMod.CONFIG.VASSAL.ENABLED ? "true" : "false")
                        .format(FactionsMod.CONFIG.VASSAL.ENABLED ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Power Percent: " + Formatting.WHITE + FactionsMod.CONFIG.VASSAL.POWER_PERCENT + "%").send(player, false);
        new Message(Formatting.GRAY + "  Require Ally: ")
                .add(new Message(FactionsMod.CONFIG.VASSAL.REQUIRE_ALLY ? "true" : "false")
                        .format(FactionsMod.CONFIG.VASSAL.REQUIRE_ALLY ? Formatting.GREEN : Formatting.RED))
                .send(player, false);

        // Display settings
        new Message(Formatting.YELLOW + "Display Settings:").send(player, false);
        new Message(Formatting.GRAY + "  Faction Name Max Length: " + Formatting.WHITE + FactionsMod.CONFIG.DISPLAY.NAME_MAX_LENGTH).send(player, false);
        new Message(Formatting.GRAY + "  Change Chat: ")
                .add(new Message(FactionsMod.CONFIG.DISPLAY.MODIFY_CHAT ? "true" : "false")
                        .format(FactionsMod.CONFIG.DISPLAY.MODIFY_CHAT ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Tab Menu: ")
                .add(new Message(FactionsMod.CONFIG.DISPLAY.TAB_MENU ? "true" : "false")
                        .format(FactionsMod.CONFIG.DISPLAY.TAB_MENU ? Formatting.GREEN : Formatting.RED))
                .send(player, false);
        new Message(Formatting.GRAY + "  Power Message: ")
                .add(new Message(FactionsMod.CONFIG.DISPLAY.POWER_MESSAGE ? "true" : "false")
                        .format(FactionsMod.CONFIG.DISPLAY.POWER_MESSAGE ? Formatting.GREEN : Formatting.RED))
                .send(player, false);

        return 1;
    }

    // === Config setters ===

    private void sendConfigUpdate(ServerPlayerEntity player, String setting, Object value) {
        new Message("Config updated: " + setting + " = ")
                .add(new Message(String.valueOf(value)).format(Formatting.GREEN))
                .send(player, false);
        FactionsMod.CONFIG.save();
    }

    // Main config
    private int setBlockTNT(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.BLOCK_TNT = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "blockTNT", value);
        return 1;
    }

    private int setMaxFactionSize(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.MAX_FACTION_SIZE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "maxFactionSize", value);
        return 1;
    }

    private int setFriendlyFire(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.FRIENDLY_FIRE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "friendlyFire", value);
        return 1;
    }

    private int setClaimProtection(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.CLAIM_PROTECTION = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "claimProtection", value);
        return 1;
    }

    // Power config
    private int setPowerBase(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.BASE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.base", value);
        return 1;
    }

    private int setPowerMember(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.MEMBER = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.member", value);
        return 1;
    }

    private int setPowerClaimWeight(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.CLAIM_WEIGHT = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.claimWeight", value);
        return 1;
    }

    private int setPowerDeathPenalty(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.DEATH_PENALTY = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.deathPenalty", value);
        return 1;
    }

    private int setPowerEnemyDeathMultiplier(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.ENEMY_DEATH_MULTIPLIER = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.enemyDeathMultiplier", value);
        return 1;
    }

    private int setPowerPerAlly(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.POWER_PER_ALLY = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.powerPerAlly", value);
        return 1;
    }

    private int setPowerDecayCheckTicks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.DECAY_CHECK_TICKS = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.decayCheckTicks", value);
        return 1;
    }

    // PowerTicks
    private int setPowerTicksTicks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.POWER_TICKS.TICKS = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.powerTicks.ticks", value);
        return 1;
    }

    private int setPowerTicksReward(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.POWER_TICKS.REWARD = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.powerTicks.reward", value);
        return 1;
    }

    // Wealth
    private int setWealthMaxValue(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.WEALTH.MAX_VALUE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.wealth.maxValue", value);
        return 1;
    }

    private int setWealthDecayPerDay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.WEALTH.DECAY_PER_DAY = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.wealth.decayPerDay", value);
        return 1;
    }

    // War
    private int setWarMaxValue(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.WAR.MAX_VALUE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.war.maxValue", value);
        return 1;
    }

    private int setWarKillReward(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.WAR.KILL_REWARD = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.war.killReward", value);
        return 1;
    }

    private int setWarEnemyMultiplier(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.WAR.ENEMY_MULTIPLIER = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.war.enemyMultiplier", value);
        return 1;
    }

    private int setWarDecayPerDay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.POWER.WAR.DECAY_PER_DAY = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "power.war.decayPerDay", value);
        return 1;
    }

    // Home config
    private int setHomeWarpEnabled(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.HOME.WARP_ENABLED = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "home.warpEnabled", value);
        return 1;
    }

    private int setHomeClaimOnly(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.HOME.CLAIM_ONLY = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "home.claimOnly", value);
        return 1;
    }

    private int setHomeDamageCooldown(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.HOME.DAMAGE_COOLDOWN = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "home.damageTickCooldown", value);
        return 1;
    }

    private int setHomeWarpCooldown(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.HOME.HOME_WARP_COOLDOWN_SECOND = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "home.homeWarpCooldownSecond", value);
        return 1;
    }

    // Safe config
    private int setSafeEnabled(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.SAFE.SAFE_ENABLED = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "safe.safeEnabled", value);
        return 1;
    }

    private int setSafeEnderChest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.SAFE.ENDER_CHEST = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "safe.enderChest", value);
        return 1;
    }

    private int setSafeDouble(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.SAFE.DOUBLE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "safe.double", value);
        return 1;
    }

    // Display config
    private int setDisplayNameMaxLength(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.DISPLAY.NAME_MAX_LENGTH = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "display.factionNameMaxLength", value);
        return 1;
    }

    private int setDisplayModifyChat(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.DISPLAY.MODIFY_CHAT = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "display.changeChat", value);
        return 1;
    }

    private int setDisplayTabMenu(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.DISPLAY.TAB_MENU = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "display.tabMenu", value);
        return 1;
    }

    private int setDisplayPowerMessage(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.DISPLAY.POWER_MESSAGE = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "display.powerMessage", value);
        return 1;
    }

    // Vassal config
    private int setVassalEnabled(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.VASSAL.ENABLED = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "vassal.enabled", value);
        return 1;
    }

    private int setVassalPowerPercent(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        FactionsMod.CONFIG.VASSAL.POWER_PERCENT = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "vassal.powerPercent", value);
        return 1;
    }

    private int setVassalRequireAlly(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(context, "value");
        FactionsMod.CONFIG.VASSAL.REQUIRE_ALLY = value;
        sendConfigUpdate(context.getSource().getPlayerOrThrow(), "vassal.requireAlly", value);
        return 1;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("admin")
                .then(CommandManager.literal("bypass")
                        .requires(Requires.hasPerms("factions.admin.bypass",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .executes(this::bypass))
                .then(CommandManager.literal("reload")
                        .requires(Requires.multiple(
                                Requires.hasPerms("factions.admin.reload",
                                        FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL),
                                source -> FactionsMod.dynmap != null))
                        .executes(this::reload))
                .then(CommandManager.literal("power")
                        .requires(
                                Requires.hasPerms("factions.admin.power",
                                        FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .then(CommandManager.literal("recalc")
                                .executes(this::powerRecalc))
                        .then(CommandManager.argument("power", IntegerArgumentType.integer())
                                .then(CommandManager
                                        .argument("faction", StringArgumentType.greedyString())
                                        .suggests(Suggests.allFactions()).executes(this::power))))
                .then(CommandManager.literal("spoof")
                        .requires(Requires.hasPerms("factions.admin.spoof",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.string())
                                .suggests(Suggests.allPlayers()).executes(this::spoof))
                        .executes(this::clearSpoof))
                .then(CommandManager.literal("audit")
                        .requires(Requires.hasPerms("factions.admin.audit",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .executes(this::audit))
                .then(CommandManager.literal("lastSeen")
                        .requires(Requires.hasPerms("factions.admin.lastseen",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("days", IntegerArgumentType.integer(0))
                                        .then(CommandManager.argument("player", StringArgumentType.string())
                                                .suggests(Suggests.allPlayers())
                                                .executes(this::lastSeenSet))))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("player", StringArgumentType.string())
                                        .suggests(Suggests.allPlayers())
                                        .executes(this::lastSeenGet))))
                .then(CommandManager.literal("protection")
                        .requires(Requires.hasPerms("factions.admin.protection",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(Suggests.allFactions())
                                .executes(this::protection)))
                .then(CommandManager.literal("claimdecay")
                        .requires(Requires.hasPerms("factions.admin.claimdecay",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .then(CommandManager.literal("trigger")
                                .executes(this::claimDecayTrigger))
                        .then(CommandManager.literal("enable")
                                .executes(this::claimDecayEnable))
                        .then(CommandManager.literal("disable")
                                .executes(this::claimDecayDisable)))
                .then(CommandManager.literal("config")
                        .requires(Requires.hasPerms("factions.admin.config",
                                FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                        .executes(this::showConfig)
                        .then(CommandManager.literal("set")
                                // Main config
                                .then(CommandManager.literal("blockTNT")
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(this::setBlockTNT)))
                                .then(CommandManager.literal("maxFactionSize")
                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(-1))
                                                .executes(this::setMaxFactionSize)))
                                .then(CommandManager.literal("friendlyFire")
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(this::setFriendlyFire)))
                                .then(CommandManager.literal("claimProtection")
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(this::setClaimProtection)))
                                // Power config
                                .then(CommandManager.literal("power")
                                        .then(CommandManager.literal("base")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setPowerBase)))
                                        .then(CommandManager.literal("member")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setPowerMember)))
                                        .then(CommandManager.literal("claimWeight")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setPowerClaimWeight)))
                                        .then(CommandManager.literal("deathPenalty")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setPowerDeathPenalty)))
                                        .then(CommandManager.literal("enemyDeathMultiplier")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setPowerEnemyDeathMultiplier)))
                                        .then(CommandManager.literal("powerPerAlly")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setPowerPerAlly)))
                                        .then(CommandManager.literal("decayCheckTicks")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(1))
                                                        .executes(this::setPowerDecayCheckTicks)))
                                        .then(CommandManager.literal("powerTicks")
                                                .then(CommandManager.literal("ticks")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(1))
                                                                .executes(this::setPowerTicksTicks)))
                                                .then(CommandManager.literal("reward")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setPowerTicksReward))))
                                        .then(CommandManager.literal("wealth")
                                                .then(CommandManager.literal("maxValue")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setWealthMaxValue)))
                                                .then(CommandManager.literal("decayPerDay")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setWealthDecayPerDay))))
                                        .then(CommandManager.literal("war")
                                                .then(CommandManager.literal("maxValue")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setWarMaxValue)))
                                                .then(CommandManager.literal("killReward")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setWarKillReward)))
                                                .then(CommandManager.literal("enemyMultiplier")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setWarEnemyMultiplier)))
                                                .then(CommandManager.literal("decayPerDay")
                                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(this::setWarDecayPerDay)))))
                                // Home config
                                .then(CommandManager.literal("home")
                                        .then(CommandManager.literal("warpEnabled")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setHomeWarpEnabled)))
                                        .then(CommandManager.literal("claimOnly")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setHomeClaimOnly)))
                                        .then(CommandManager.literal("damageTickCooldown")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setHomeDamageCooldown)))
                                        .then(CommandManager.literal("homeWarpCooldownSecond")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(this::setHomeWarpCooldown))))
                                // Safe config
                                .then(CommandManager.literal("safe")
                                        .then(CommandManager.literal("safeEnabled")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setSafeEnabled)))
                                        .then(CommandManager.literal("enderChest")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setSafeEnderChest)))
                                        .then(CommandManager.literal("double")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setSafeDouble))))
                                // Display config
                                .then(CommandManager.literal("display")
                                        .then(CommandManager.literal("factionNameMaxLength")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(-1))
                                                        .executes(this::setDisplayNameMaxLength)))
                                        .then(CommandManager.literal("changeChat")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setDisplayModifyChat)))
                                        .then(CommandManager.literal("tabMenu")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setDisplayTabMenu)))
                                        .then(CommandManager.literal("powerMessage")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setDisplayPowerMessage))))
                                // Vassal config
                                .then(CommandManager.literal("vassal")
                                        .then(CommandManager.literal("enabled")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setVassalEnabled)))
                                        .then(CommandManager.literal("powerPercent")
                                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                                                        .executes(this::setVassalPowerPercent)))
                                        .then(CommandManager.literal("requireAlly")
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(this::setVassalRequireAlly))))))
                .build();
    }
}
