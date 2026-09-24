package io.icker.factions.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Money;
import io.icker.factions.util.Message;

import java.util.List;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class WealthCommand implements Command {

    private int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        int wealthPower = faction.getWealthPower();
        int targetWealth = faction.getTargetWealthPower();
        List<Claim> claims = faction.getClaims();
        int requiredPower = claims.size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT;

        double decayDivisor = FactionsMod.CONFIG.POWER.WEALTH.DECAY_DIVISOR;
        int effectiveDecay = decayDivisor > 0
                ? FactionsMod.CONFIG.POWER.WEALTH.DECAY_PER_DAY + (int)(wealthPower / decayDivisor)
                : FactionsMod.CONFIG.POWER.WEALTH.DECAY_PER_DAY;

        new Message(Formatting.GOLD + "=== Faction Wealth ===").send(player, false);
        new Message(Formatting.GRAY + "  Wealth Power: " + Formatting.GREEN + wealthPower).send(player, false);
        new Message(Formatting.GRAY + "  Decay Rate: " + Formatting.RED + effectiveDecay + "/day").send(player, false);
        new Message(Formatting.GRAY + "  Bank Balance: $" + Formatting.AQUA + Money.format(faction.getBankBalance())).send(player, false);
        new Message(Formatting.GRAY + "  Required for Claims: " + Formatting.YELLOW + requiredPower).send(player, false);
        new Message(Formatting.GRAY + "  Target Wealth Power: " + Formatting.YELLOW + targetWealth).send(player, false);

        return 1;
    }

    private int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        int amount = IntegerArgumentType.getInteger(context, "amount");

        double withdrawn = faction.withdrawFromBank(amount);
        if (withdrawn <= 0) {
            new Message("Not enough money in the bank (have $" + Money.format(faction.getBankBalance()) + ")").fail().send(player, false);
            return 0;
        }

        int wealthGained = (int) Math.min(amount, Math.floor(withdrawn));
        if (wealthGained <= 0) {
            faction.depositToBank(withdrawn);
            new Message("Need at least $1 in the bank to convert to wealth power").fail().send(player, false);
            return 0;
        }

        double unused = Money.round(withdrawn - wealthGained);
        if (unused > 0) {
            faction.depositToBank(unused);
        }

        faction.addWealthPower(wealthGained);

        new Message("%s converted $%s from bank to wealth power (bank: $%s, wealth: %d)",
                player.getName().getString(),
                Money.format(wealthGained),
                Money.format(faction.getBankBalance()),
                faction.getWealthPower()).send(faction);

        return 1;
    }

    private int withdraw(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        int amount = IntegerArgumentType.getInteger(context, "amount");

        int currentWealth = faction.getWealthPower();
        if (amount > currentWealth) {
            new Message("Not enough wealth power (have %d)", currentWealth).fail().send(player, false);
            return 0;
        }

        faction.spendWealthPower(amount);
        faction.depositToBank(amount);

        new Message("%s withdrew %d wealth power to bank (bank: $%s, wealth: %d)",
                player.getName().getString(),
                amount,
                Money.format(faction.getBankBalance()),
                faction.getWealthPower()).send(faction);

        return 1;
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("wealth")
                .requires(Requires.isMember())
                .executes(this::info)
                .then(CommandManager.literal("add")
                        .requires(Requires.isLeader())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(this::add)))
                .then(CommandManager.literal("withdraw")
                        .requires(Requires.isLeader())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(this::withdraw)))
                .build();
    }
}
