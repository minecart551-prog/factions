package io.icker.factions.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.config.PowerConfig;
import io.icker.factions.util.Command;
import io.icker.factions.util.Money;
import io.icker.factions.util.Message;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import io.icker.factions.api.persistents.Claim;

public class WealthCommand implements Command {

    private int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        double wealthPower = faction.getWealthPower();
        double targetWealth = faction.getTargetWealthPower();
        List<Claim> claims = faction.getClaims();
        int requiredPower = claims.size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT;
        String withdrawMode = faction.getWealthWithdrawMode();

        new Message(Formatting.GOLD + "=== Faction Wealth ===").send(player, false);
        new Message(Formatting.GRAY + "  Wealth Power: " + Formatting.GREEN + Money.format(wealthPower)).send(player, false);
        new Message(Formatting.GRAY + "  Required for Claims: " + Formatting.YELLOW + requiredPower).send(player, false);
        new Message(Formatting.GRAY + "  Target Wealth Power: " + Formatting.YELLOW + Money.format(targetWealth)).send(player, false);
        new Message(Formatting.GRAY + "  Withdraw Allowed: " + Formatting.AQUA + withdrawMode).send(player, false);

        return 1;
    }

    private int withdraw(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (!faction.canWithdrawWealth(user)) {
            new Message("You are not allowed to withdraw wealth").fail().send(player, false);
            return 0;
        }

        double amount = Money.round(DoubleArgumentType.getDouble(context, "amount"));
        if (amount <= 0) {
            new Message("Amount must be positive").fail().send(player, false);
            return 0;
        }

        PowerConfig.SacrificeItem[] items = FactionsMod.CONFIG.POWER.WEALTH.ITEMS;
        if (items.length == 0) {
            new Message("No sacrifice items configured").fail().send(player, false);
            return 0;
        }

        Identifier itemId = Identifier.tryParse(items[0].ITEM_ID);
        Item item = Registries.ITEM.get(itemId);
        if (item == null || item == net.minecraft.item.Items.AIR) {
            new Message("Invalid sacrifice item configured: %s", items[0].ITEM_ID).fail().send(player, false);
            return 0;
        }

        int valuePerItem = items[0].VALUE;
        if (valuePerItem <= 0) {
            new Message("Invalid sacrifice item value configured").fail().send(player, false);
            return 0;
        }

        double available = faction.getWealthPower();
        double toSpend = Math.min(amount, available);
        if (toSpend <= 0) {
            new Message("Not enough wealth power (have %s)", Money.format(available)).fail().send(player, false);
            return 0;
        }

        int itemsToGive = (int) Math.floor(toSpend / valuePerItem);
        double leftover = Money.round(toSpend - itemsToGive * (double) valuePerItem);

        if (itemsToGive <= 0) {
            new Message("Need at least $%s of wealth to withdraw items", Money.format(valuePerItem)).fail().send(player, false);
            return 0;
        }

        double spent = Money.round(itemsToGive * (double) valuePerItem);
        if (!faction.spendWealthPower(spent)) {
            new Message("Not enough wealth power").fail().send(player, false);
            return 0;
        }

        if (itemsToGive > 0) {
            ItemStack stack = new ItemStack(item, itemsToGive);
            player.giveItemStack(stack);
        }

        // leftover cents stay as wealth (not spent)
        new Message("%s withdrew $%s wealth (%d %s, wealth: %s)",
                player.getName().getString(),
                Money.format(spent),
                itemsToGive,
                items[0].ITEM_ID,
                Money.format(faction.getWealthPower())).send(faction);

        return 1;
    }

    private int allowCommander(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        if (user.rank != User.Rank.OWNER) {
            new Message("Only the faction owner can change withdraw permissions").fail().send(player, false);
            return 0;
        }
        Faction faction = user.getFaction();
        faction.setWealthWithdrawMode("COMMANDER");
        new Message("Wealth withdraw now allows commanders, leaders, and the owner").send(faction);
        return 1;
    }

    private int allowLeader(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        if (user.rank != User.Rank.OWNER) {
            new Message("Only the faction owner can change withdraw permissions").fail().send(player, false);
            return 0;
        }
        Faction faction = user.getFaction();
        faction.setWealthWithdrawMode("LEADER");
        new Message("Wealth withdraw now allows leaders and the owner").send(faction);
        return 1;
    }

    private int allowOwner(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        if (user.rank != User.Rank.OWNER) {
            new Message("Only the faction owner can change withdraw permissions").fail().send(player, false);
            return 0;
        }
        Faction faction = user.getFaction();
        faction.setWealthWithdrawMode("OWNER");
        new Message("Wealth withdraw now allows only the owner").send(faction);
        return 1;
    }

    private boolean canWithdrawSource(ServerCommandSource source) {
        ServerPlayerEntity entity = source.getPlayer();
        if (entity == null) return false;
        User user = Command.getUser(entity);
        if (!user.isInFaction()) return false;
        return user.getFaction().canWithdrawWealth(user);
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("wealth")
                .requires(Requires.isMember())
                .executes(this::info)
                .then(CommandManager.literal("withdraw")
                        .requires(this::canWithdrawSource)
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(this::withdraw))
                        .then(CommandManager.literal("allow")
                                .requires(Requires.isOwner())
                                .then(CommandManager.literal("commander")
                                        .executes(this::allowCommander))
                                .then(CommandManager.literal("leader")
                                        .executes(this::allowLeader))
                                .then(CommandManager.literal("owner")
                                        .executes(this::allowOwner))))
                .build();
    }
}
