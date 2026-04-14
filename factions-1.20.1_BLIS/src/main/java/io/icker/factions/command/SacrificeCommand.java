package io.icker.factions.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.config.PowerConfig;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SacrificeCommand implements Command {

    private int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        ItemStack heldItem = player.getMainHandStack();

        if (heldItem.isEmpty()) {
            new Message("You must hold an item to sacrifice").fail().send(player, false);
            return 0;
        }

        // Get the item identifier
        Identifier itemId = Registries.ITEM.getId(heldItem.getItem());
        String itemIdString = itemId.toString();

        // Check if the item is a valid sacrifice item
        PowerConfig.SacrificeItem[] sacrificeItems = FactionsMod.CONFIG.POWER.WEALTH.ITEMS;
        PowerConfig.SacrificeItem matchedItem = null;

        for (PowerConfig.SacrificeItem sacrificeItem : sacrificeItems) {
            if (sacrificeItem.ITEM_ID.equals(itemIdString)) {
                matchedItem = sacrificeItem;
                break;
            }
        }

        if (matchedItem == null) {
            new Message("This item cannot be sacrificed").fail().send(player, false);
            return 0;
        }

        // Calculate total value based on stack size
        int stackSize = heldItem.getCount();
        int totalValue = matchedItem.VALUE * stackSize;

        // Add wealth power to faction
        int actualAdded = faction.addWealthPower(totalValue);

        if (actualAdded == 0) {
            new Message("Faction wealth power is already at maximum").fail().send(player, false);
            return 0;
        }

        // Consume the items
        int itemsConsumed = (int) Math.ceil((double) actualAdded / matchedItem.VALUE);
        heldItem.decrement(itemsConsumed);

        new Message("%s sacrificed %d %s for %d wealth power (now at %d/%d)",
                player.getName().getString(),
                itemsConsumed,
                itemIdString,
                actualAdded,
                faction.getWealthPower(),
                FactionsMod.CONFIG.POWER.WEALTH.MAX_VALUE).send(faction);

        return 1;
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("sacrifice")
                .requires(Requires.multiple(
                        Requires.hasPerms("faction.sacrifice", 0),
                        Requires.isMember()))
                .executes(this::run).build();
    }
}
