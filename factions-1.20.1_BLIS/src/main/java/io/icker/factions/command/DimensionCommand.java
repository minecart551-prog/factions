package io.icker.factions.command;

import java.util.stream.Collectors;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.network.DimensionNetworkHandler;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import io.icker.factions.util.RegionTransfer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

/**
 * Command for managing dimension blacklists and whitelists
 * /f dimension blacklist list - Show all blacklisted dimensions
 * /f dimension blacklist clear - Clear all blacklisted dimensions
 * /f dimension whitelist list - Show all whitelisted dimensions
 * /f dimension whitelist clear - Clear all whitelisted dimensions
 * /f dimension export [filename] - Export both region lists to a client-side file
 * /f dimension import [filename] - Merge regions from a client-side file
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
                .then(CommandManager.literal("export")
                    .requires(Requires.isCommander())
                    .executes(ctx -> export(ctx, null))
                    .then(CommandManager.argument("filename", StringArgumentType.string())
                        .executes(ctx -> export(ctx, StringArgumentType.getString(ctx, "filename")))))
                .then(CommandManager.literal("import")
                    .requires(Requires.isCommander())
                    .executes(ctx -> importFile(ctx, null))
                    .then(CommandManager.argument("filename", StringArgumentType.string())
                        .executes(ctx -> importFile(ctx, StringArgumentType.getString(ctx, "filename")))))
                .build();
        
        return node;
    }

    private int export(CommandContext<ServerCommandSource> context, String filename) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        if (user.rank != User.Rank.OWNER && user.rank != User.Rank.COMMANDER && user.rank != User.Rank.LEADER) {
            new Message("Only faction leadership can export dimension regions").fail().send(player, false);
            return 0;
        }

        String resolved = filename != null ? RegionTransfer.sanitizeFilename(filename) : RegionTransfer.defaultFilename(faction);
        if (resolved == null) {
            new Message("Invalid filename").fail().send(player, false);
            return 0;
        }

        faction.loadDimensionBlacklistFromJson();
        faction.loadDimensionWhitelistFromJson();
        String json = RegionTransfer.buildJson(faction);

        DimensionNetworkHandler.sendExportChunks(player, resolved, json);
        new Message("Exporting ")
                .add(new Message(faction.dimensionBlacklist.size() + "").format(Formatting.YELLOW))
                .add(" blacklist / ")
                .add(new Message(faction.dimensionWhitelist.size() + "").format(Formatting.YELLOW))
                .add(" whitelist regions to ")
                .add(new Message(resolved).format(Formatting.YELLOW))
                .send(player, false);
        return 1;
    }

    private int importFile(CommandContext<ServerCommandSource> context, String filename) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        if (user.rank != User.Rank.OWNER && user.rank != User.Rank.COMMANDER && user.rank != User.Rank.LEADER) {
            new Message("Only faction leadership can import dimension regions").fail().send(player, false);
            return 0;
        }

        String resolved = filename != null ? RegionTransfer.sanitizeFilename(filename) : RegionTransfer.defaultFilename(faction);
        if (resolved == null) {
            new Message("Invalid filename").fail().send(player, false);
            return 0;
        }

        DimensionNetworkHandler.sendImportRequest(player, resolved);
        return 1;
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