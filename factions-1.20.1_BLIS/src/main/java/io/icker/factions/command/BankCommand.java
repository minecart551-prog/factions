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

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class BankCommand implements Command {

    private int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        new Message(Formatting.GOLD + "=== Faction Bank ===").send(player, false);
        new Message(Formatting.GRAY + "  Balance: $" + Formatting.AQUA + Money.format(faction.getBankBalance())).send(player, false);

        if (!FactionsMod.CONFIG.BANK.ENABLED) {
            new Message(Formatting.RED + "  Bank system is disabled").send(player, false);
        }

        if (FactionsMod.CONFIG.BANK.MAX_BALANCE >= 0) {
            new Message(Formatting.GRAY + "  Max Balance: $" + Formatting.YELLOW + Money.format(FactionsMod.CONFIG.BANK.MAX_BALANCE)).send(player, false);
        }

        return 1;
    }

    private int withdraw(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        double amount = Money.round(DoubleArgumentType.getDouble(context, "amount"));

        double withdrawn = faction.withdrawFromBank(amount);
        if (withdrawn <= 0) {
            new Message("Not enough money in the bank (have $" + Money.format(faction.getBankBalance()) + ")").fail().send(player, false);
            return 0;
        }

        PowerConfig.SacrificeItem[] items = FactionsMod.CONFIG.POWER.WEALTH.ITEMS;
        if (items.length == 0) {
            new Message("No sacrifice items configured").fail().send(player, false);
            faction.depositToBank(withdrawn);
            return 0;
        }

        // Give items using the first configured sacrifice item
        Identifier itemId = Identifier.tryParse(items[0].ITEM_ID);
        Item item = Registries.ITEM.get(itemId);
        if (item == null || item == net.minecraft.item.Items.AIR) {
            new Message("Invalid sacrifice item configured: %s", items[0].ITEM_ID).fail().send(player, false);
            faction.depositToBank(withdrawn);
            return 0;
        }

        int valuePerItem = items[0].VALUE;
        if (valuePerItem <= 0) {
            new Message("Invalid sacrifice item value configured").fail().send(player, false);
            faction.depositToBank(withdrawn);
            return 0;
        }

        int itemsToGive = (int) Math.floor(withdrawn / valuePerItem);
        double leftover = Money.round(withdrawn - itemsToGive * valuePerItem);

        if (itemsToGive > 0) {
            ItemStack stack = new ItemStack(item, itemsToGive);
            player.giveItemStack(stack);
        }

        if (leftover > 0) {
            faction.depositToBank(leftover);
        }

        double spent = Money.round(withdrawn - leftover);
        new Message("%s withdrew $%s from faction bank (%d %s, bank: $%s)",
                player.getName().getString(),
                Money.format(spent),
                itemsToGive,
                items[0].ITEM_ID,
                Money.format(faction.getBankBalance())).send(faction);

        return 1;
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("bank")
                .requires(Requires.isMember())
                .executes(this::info)
                .then(CommandManager.literal("withdraw")
                        .requires(Requires.isLeader())
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(this::withdraw)))
                .build();
    }
}
