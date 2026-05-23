package io.icker.factions.command;

import java.util.stream.Collectors;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Command for managing dimension blacklists and whitelists
 * /f dimension blacklist list - Show all blacklisted dimensions
 * /f dimension blacklist clear - Clear all blacklisted dimensions
 * /f dimension whitelist list - Show all whitelisted dimensions
 * /f dimension whitelist clear - Clear all whitelisted dimensions
 */
public class DimensionCommand implements Command {
    
    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        LiteralCommandNode<ServerCommandSource> node = CommandManager.literal("dimension")
                .then(CommandManager.literal("blacklist")
                    .then(CommandManager.literal("list")
                        .executes(ctx -> list(ctx, false)))
                    .then(CommandManager.literal("clear")
                        .executes(ctx -> clear(ctx, false))))
                .then(CommandManager.literal("whitelist")
                    .then(CommandManager.literal("list")
                        .executes(ctx -> list(ctx, true)))
                    .then(CommandManager.literal("clear")
                        .executes(ctx -> clear(ctx, true))))
                .build();
        
        return node;
    }
    
    private int list(CommandContext<ServerCommandSource> context, boolean isWhitelist) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        
        if (player == null) return 0;
        
        // Use Command.getUser to properly handle /f admin spoof
        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        
        // Ensure data is loaded from JSON BEFORE accessing the list
        if (isWhitelist) faction.loadDimensionWhitelistFromJson();
        else faction.loadDimensionBlacklistFromJson();
        
        String typeLabel = isWhitelist ? "whitelisted" : "blacklisted";
        
        // Get the list AFTER loading from JSON
        java.util.List<BlacklistedDimension> list = isWhitelist ? faction.dimensionWhitelist : faction.dimensionBlacklist;
        
        if (list.isEmpty()) {
            new Message("No dimensions are " + typeLabel).send(player, false);
            return 0;
        }
        
        new Message((isWhitelist ? "White" : "Black") + "listed dimensions:").send(player, false);
        for (int i = 0; i < list.size(); i++) {
            BlacklistedDimension dim = list.get(i);
            new Message(i + ": ").add(new Message(dim.name).format(Formatting.YELLOW))
                    .add(" @ " + dim.world).send(player, false);
        }
        return 1;
    }

    
    private int clear(CommandContext<ServerCommandSource> context, boolean isWhitelist) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        
        if (player == null) return 0;
        
        // Use Command.getUser to properly handle /f admin spoof
        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        
        if (user.rank != User.Rank.OWNER) {
            new Message("Only faction owner can clear dimension " + (isWhitelist ? "whitelists" : "blacklists")).fail().send(player, false);
            return 0;
        }
        
        // Ensure data is loaded from JSON BEFORE accessing the list
        if (isWhitelist) faction.loadDimensionWhitelistFromJson();
        else faction.loadDimensionBlacklistFromJson();
        
        // Get the list AFTER loading from JSON
        java.util.List<BlacklistedDimension> list = isWhitelist ? faction.dimensionWhitelist : faction.dimensionBlacklist;
        String typeLabel = isWhitelist ? "whitelisted" : "blacklisted";
        
        int count = list.size();
        list.clear();
        if (isWhitelist) faction.saveDimensionWhitelistToJson(true);
        else faction.saveDimensionBlacklistToJson(true);
        
        io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
        Faction.save();
        
        io.icker.factions.network.DimensionNetworkHandler.broadcastDimensionsToFaction(faction);
        
        new Message("Cleared ").add(new Message(count + "").format(Formatting.YELLOW))
                .add(" " + typeLabel + " dimensions").send(player, false);
        return 1;
    }
}