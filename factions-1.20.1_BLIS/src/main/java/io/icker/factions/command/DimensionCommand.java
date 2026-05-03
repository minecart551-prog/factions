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
 * Command for managing dimension blacklists
 * /f dimension list - Show all blacklisted dimensions
 * /f dimension commit - Commit pending selections to the faction blacklist
 * /f dimension cancel - Cancel pending selections
 * /f dimension clear - Clear all blacklisted dimensions
 * /f dimension remove <id> - Remove a specific blacklisted dimension
 */
public class DimensionCommand implements Command {
    
    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        LiteralCommandNode<ServerCommandSource> node = CommandManager.literal("dimension")
                .then(CommandManager.literal("list")
                        .executes(this::list))
                .then(CommandManager.literal("clear")
                        .executes(this::clear))
                .executes(this::list)
                .build();
        
        return node;
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
        

        
        if (faction.dimensionBlacklist.isEmpty()) {
            new Message("No dimensions are blacklisted").send(player, false);
            return 1;
        }
        
        new Message("Blacklisted dimensions:").send(player, false);
        for (int i = 0; i < faction.dimensionBlacklist.size(); i++) {
            BlacklistedDimension dim = faction.dimensionBlacklist.get(i);
            new Message(i + ": ").add(new Message(dim.name).format(Formatting.YELLOW))
                    .add(" @ " + dim.world).send(player, false);
        }
        return 1;
    }

    
    private int clear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
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
        
        if (user.rank != User.Rank.OWNER) {
            new Message("Only faction owner can clear dimension blacklists").fail().send(player, false);
            return 0;
        }
        
        int count = faction.dimensionBlacklist.size();
        faction.dimensionBlacklist.clear();
        faction.saveDimensionBlacklistToJson();
        // Trigger MODIFY event and save all factions
        io.icker.factions.api.events.FactionEvents.MODIFY.invoker().onModify(faction);
        Faction.save();
        
        // Broadcast cleared dimensions to all faction members so their tools stay in sync
        io.icker.factions.network.DimensionNetworkHandler.broadcastDimensionsToFaction(faction);
        
        new Message("Cleared ").add(new Message(count + "").format(Formatting.YELLOW))
                .add(" blacklisted dimensions").send(player, false);
        return 1;
    }
}
