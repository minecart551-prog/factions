package io.icker.factions.command;

import java.util.List;
import java.util.stream.Collectors;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class VassalCommand implements Command {

    private int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = Command.getUser(player);
        if (!user.isInFaction()) {
            new Message("You must be in a faction to use this command").fail().send(player, false);
            return 0;
        }

        Faction faction = user.getFaction();

        new Message(Formatting.GOLD + "=== Vassal Status ===").send(player, false);

        // Show overlord if vassal
        if (faction.isVassal()) {
            Faction overlord = faction.getOverlord();
            new Message(Formatting.GOLD + "Overlord: ")
                    .add(overlord.getColor() + overlord.getName())
                    .send(player, false);
        } else {
            new Message(Formatting.GRAY + "Not a vassal of any faction").send(player, false);
        }

        // Show vassals if overlord
        List<Faction> vassals = faction.getVassals();
        if (!vassals.isEmpty()) {
            String vassalList = vassals.stream()
                    .map(v -> v.getColor() + v.getName())
                    .collect(Collectors.joining(Formatting.GRAY + ", "));
            new Message(Formatting.GOLD + "Vassals (" + vassals.size() + "): ")
                    .add(vassalList)
                    .send(player, false);

            int bonus = faction.getVassalPowerBonus();
            new Message(Formatting.GOLD + "Vassal Power Bonus: " + Formatting.GREEN + bonus)
                    .send(player, false);
        }

        // Show pending requests
        if (!faction.vassalRequests.isEmpty()) {
            String requests = faction.vassalRequests.stream()
                    .map(Faction::get)
                    .filter(f -> f != null)
                    .map(f -> f.getColor() + f.getName())
                    .collect(Collectors.joining(Formatting.GRAY + ", "));
            new Message(Formatting.GOLD + "Pending Vassal Requests: ")
                    .add(requests)
                    .send(player, false);
        }

        return 1;
    }

    private int offer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (!FactionsMod.CONFIG.VASSAL.ENABLED) {
            new Message("Vassal system is disabled").fail().send(player, false);
            return 0;
        }

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        Faction targetFaction = Faction.getByName(targetName);

        if (targetFaction == null) {
            new Message("Faction does not exist").fail().send(player, false);
            return 0;
        }

        if (faction.equals(targetFaction)) {
            new Message("Cannot become a vassal of yourself").fail().send(player, false);
            return 0;
        }

        if (!faction.canBecomeVassal(targetFaction)) {
            if (faction.isVassal()) {
                new Message("Your faction is already a vassal").fail().send(player, false);
            } else if (targetFaction.isVassal()) {
                new Message("Target faction is already a vassal (cannot have multiple levels)").fail().send(player, false);
            } else if (FactionsMod.CONFIG.VASSAL.REQUIRE_ALLY && !faction.isMutualAllies(targetFaction.getID())) {
                new Message("You must be mutual allies with " + targetFaction.getName() + " first").fail().send(player, false);
            } else {
                new Message("Cannot become a vassal of this faction").fail().send(player, false);
            }
            return 0;
        }

        if (targetFaction.hasVassalRequest(faction.getID())) {
            new Message("You have already sent a vassal request to this faction").fail().send(player, false);
            return 0;
        }

        targetFaction.addVassalRequest(faction.getID());

        new Message(faction.getName() + " has offered to become your vassal")
                .hover("Click to accept")
                .click("/factions vassal accept " + faction.getName())
                .format(Formatting.GOLD)
                .send(targetFaction);

        new Message("Vassal request sent to " + targetFaction.getName())
                .format(Formatting.GREEN)
                .send(player, false);

        return 1;
    }

    private int accept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (!FactionsMod.CONFIG.VASSAL.ENABLED) {
            new Message("Vassal system is disabled").fail().send(player, false);
            return 0;
        }

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        Faction targetFaction = Faction.getByName(targetName);

        if (targetFaction == null) {
            new Message("Faction does not exist").fail().send(player, false);
            return 0;
        }

        if (!faction.hasVassalRequest(targetFaction.getID())) {
            new Message("No pending vassal request from " + targetFaction.getName()).fail().send(player, false);
            return 0;
        }

        // Remove the request first
        faction.removeVassalRequest(targetFaction.getID());

        // Check if they can still become a vassal
        if (!targetFaction.canBecomeVassal(faction)) {
            new Message("This faction can no longer become your vassal").fail().send(player, false);
            return 0;
        }

        // Make them a vassal
        targetFaction.becomeVassal(faction);

        new Message(targetFaction.getName() + " is now your vassal!")
                .format(Formatting.GREEN)
                .send(faction);

        new Message("Your faction is now a vassal of " + faction.getName())
                .format(Formatting.GREEN)
                .send(targetFaction);

        return 1;
    }

    private int release(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = Command.getUser(player);
        Faction faction = user.getFaction();
        Faction targetFaction = Faction.getByName(targetName);

        if (targetFaction == null) {
            new Message("Faction does not exist").fail().send(player, false);
            return 0;
        }

        // Check if target is actually our vassal
        if (!faction.getID().equals(targetFaction.getOverlordId())) {
            new Message(targetFaction.getName() + " is not your vassal").fail().send(player, false);
            return 0;
        }

        targetFaction.releaseFromOverlord();

        new Message("Released " + targetFaction.getName() + " from vassalage")
                .format(Formatting.YELLOW)
                .send(faction);

        new Message("Your faction has been released from vassalage by " + faction.getName())
                .format(Formatting.YELLOW)
                .send(targetFaction);

        return 1;
    }

    private int leave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (!faction.isVassal()) {
            new Message("Your faction is not a vassal").fail().send(player, false);
            return 0;
        }

        Faction overlord = faction.getOverlord();
        faction.releaseFromOverlord();

        new Message("Your faction has left vassalage of " + overlord.getName())
                .format(Formatting.YELLOW)
                .send(faction);

        new Message(faction.getName() + " has left your vassalage")
                .format(Formatting.YELLOW)
                .send(overlord);

        return 1;
    }

    private static SuggestionProvider<ServerCommandSource> pendingVassalRequests() {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = User.get(entity.getUuid());
            if (!user.isInFaction()) return builder.buildFuture();

            Faction faction = user.getFaction();
            for (java.util.UUID requestId : faction.vassalRequests) {
                Faction requester = Faction.get(requestId);
                if (requester != null) {
                    builder.suggest(requester.getName());
                }
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<ServerCommandSource> currentVassals() {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = User.get(entity.getUuid());
            if (!user.isInFaction()) return builder.buildFuture();

            Faction faction = user.getFaction();
            for (Faction vassal : faction.getVassals()) {
                builder.suggest(vassal.getName());
            }
            return builder.buildFuture();
        };
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
                .literal("vassal")
                .requires(Requires.hasPerms("factions.vassal", 0))
                .then(CommandManager.literal("info")
                        .requires(Requires.multiple(Requires.isMember(), Requires.hasPerms("factions.vassal.info", 0)))
                        .executes(this::info))
                .then(CommandManager.literal("offer")
                        .requires(Requires.multiple(Requires.isLeader(), Requires.hasPerms("factions.vassal.offer", 0)))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(Suggests.allFactions(false))
                                .executes(this::offer)))
                .then(CommandManager.literal("accept")
                        .requires(Requires.multiple(Requires.isLeader(), Requires.hasPerms("factions.vassal.accept", 0)))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(pendingVassalRequests())
                                .executes(this::accept)))
                .then(CommandManager.literal("release")
                        .requires(Requires.multiple(Requires.isLeader(), Requires.hasPerms("factions.vassal.release", 0)))
                        .then(CommandManager.argument("faction", StringArgumentType.greedyString())
                                .suggests(currentVassals())
                                .executes(this::release)))
                .then(CommandManager.literal("leave")
                        .requires(Requires.multiple(Requires.isLeader(), Requires.hasPerms("factions.vassal.leave", 0)))
                        .executes(this::leave))
                .build();
    }
}
