package io.icker.factions.command;

import java.util.stream.Collectors;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class BlockCommand implements Command {
    private int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String blockId = StringArgumentType.getString(context, "block");
        
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        
        if (player == null)
            return 0;
        
        User user = User.get(player.getUuid());
        Faction faction = user.getFaction();
        
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        
        // Only faction owner or admin with spoof can modify blacklist
        if (user.rank != User.Rank.OWNER && user.getSpoof() == null) {
            new Message("Only faction owner can modify the blacklist").fail().send(player, false);
            return 0;
        }
        
        // Validate block exists
        net.minecraft.util.Identifier blockIdentifier;
        try {
            blockIdentifier = new net.minecraft.util.Identifier(blockId);
        } catch (Exception e) {
            new Message("Invalid block ID format").fail().send(player, false);
            return 0;
        }
        
        if (!Registries.BLOCK.containsId(blockIdentifier)) {
            new Message("Invalid block ID: " + blockId).fail().send(player, false);
            return 0;
        }
        
        if (faction.blockBlacklist.contains(blockId)) {
            new Message("Block " + blockId + " is already blacklisted").fail().send(player, false);
            return 0;
        }
        
        faction.blockBlacklist.add(blockId);
        new Message("Added ").add(new Message(blockId).format(Formatting.YELLOW))
                .add(" to block blacklist").send(player, false);
        return 1;
    }
    
    private int remove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String blockId = StringArgumentType.getString(context, "block");
        
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        
        if (player == null)
            return 0;
        
        User user = User.get(player.getUuid());
        Faction faction = user.getFaction();
        
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        
        // Only faction owner or admin with spoof can modify blacklist
        if (user.rank != User.Rank.OWNER && user.getSpoof() == null) {
            new Message("Only faction owner can modify the blacklist").fail().send(player, false);
            return 0;
        }
        
        if (!faction.blockBlacklist.contains(blockId)) {
            new Message("Block " + blockId + " is not in the blacklist").fail().send(player, false);
            return 0;
        }
        
        faction.blockBlacklist.remove(blockId);
        new Message("Removed ").add(new Message(blockId).format(Formatting.YELLOW))
                .add(" from block blacklist").send(player, false);
        return 1;
    }
    
    private int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        
        if (player == null)
            return 0;
        
        User user = User.get(player.getUuid());
        Faction faction = user.getFaction();
        
        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }
        
        if (faction.blockBlacklist.isEmpty()) {
            new Message("No blocks are blacklisted").send(player, false);
            return 1;
        }
        
        String blocksList = faction.blockBlacklist.stream().collect(Collectors.joining(", "));
        new Message("Blacklisted blocks: ").add(new Message(blocksList).format(Formatting.YELLOW))
                .send(player, false);
        return 1;
    }
    
    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("block")
                .requires(Requires.multiple(Requires.isMember(),
                        Requires.hasPerms("factions.block", 0)))
                .then(CommandManager.literal("blacklist")
                        .requires(Requires.hasPerms("factions.block.blacklist", 0))
                        .then(CommandManager.literal("add")
                                .requires(Requires.multiple(Requires.isOwner(),
                                        Requires.hasPerms("factions.block.blacklist.add", 0)))
                                .then(CommandManager.argument("block", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            Registries.BLOCK.getIds().forEach(id -> 
                                                builder.suggest(id.toString())
                                            );
                                            return builder.buildFuture();
                                        })
                                        .executes(this::add)))
                        .then(CommandManager.literal("remove")
                                .requires(Requires.multiple(Requires.isOwner(),
                                        Requires.hasPerms("factions.block.blacklist.remove", 0)))
                                .then(CommandManager.argument("block", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            Faction faction = User.get(player.getUuid()).getFaction();
                                            if (faction != null) {
                                                faction.blockBlacklist.forEach(builder::suggest);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::remove)))
                        .then(CommandManager.literal("list")
                                .requires(Requires.hasPerms("factions.block.blacklist.list", 0))
                                .executes(this::list)))
                .build();
    }
}
