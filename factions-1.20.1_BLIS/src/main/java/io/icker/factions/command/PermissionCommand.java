package io.icker.factions.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship;
import io.icker.factions.api.persistents.Relationship.Permissions;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class PermissionCommand implements Command {

    // ========== FACTION sub-commands ==========

    private int changeFaction(CommandContext<ServerCommandSource> context, boolean add) throws CommandSyntaxException {
        String permissionName = StringArgumentType.getString(context, "permission");
        String factionName = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction sourceFaction = user.getFaction();
        Faction targetFaction = Faction.getByName(factionName);

        if (sourceFaction == null || targetFaction == null) {
            new Message("You must be in a faction and you must provide a valid faction").fail()
                    .send(player, false);
            return 0;
        }

        if (sourceFaction.getID().equals(targetFaction.getID())) {
            new Message("You cannot manage permissions on your own faction").fail()
                    .send(player, false);
            return 0;
        }

        Relationship rel = sourceFaction.getRelationship(targetFaction.getID());

        Permissions permission;
        try {
            permission = Permissions.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            new Message("Not a valid permission").fail().send(player, false);
            return 0;
        }

        if ((!rel.permissions.contains(permission) && !add)
                || (rel.permissions.contains(permission) && add)) {
            new Message(String.format("Could not change because the permission %s",
                    rel.permissions.contains(permission) ? "already exists" : "doesn't exist"))
                            .fail().send(player, false);
            return 0;
        }

        if (add) {
            rel.permissions.add(permission);
        } else {
            rel.permissions.remove(permission);
        }

        sourceFaction.setRelationship(rel);

        new Message("Successfully changed permissions").send(player, false);
        return 1;
    }

    private int addFaction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changeFaction(context, true);
    }

    private int removeFaction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changeFaction(context, false);
    }

    private int listFaction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String factionName = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction sourceFaction = user.getFaction();
        Faction targetFaction = Faction.getByName(factionName);

        if (sourceFaction == null || targetFaction == null) {
            new Message("You must be in a faction and you must provide a valid faction").fail()
                    .send(player, false);
            return 0;
        }

        if (sourceFaction.getID().equals(targetFaction.getID())) {
            new Message("You cannot manage permissions on your own faction").fail()
                    .send(player, false);
            return 0;
        }

        String permissionsList = sourceFaction.getRelationship(targetFaction.getID()).permissions
                .stream().map(Enum::toString).collect(Collectors.joining(","));

        new Message("")
                .add(new Message(targetFaction.getName()).format(targetFaction.getColor())
                        .format(Formatting.BOLD))
                .add(String.format(" has the permissions: %s", permissionsList))
                .send(player, false);

        return 1;
    }

    // ========== GUEST sub-commands ==========

    private int changeGuest(CommandContext<ServerCommandSource> context, boolean add) throws CommandSyntaxException {
        String permissionName = StringArgumentType.getString(context, "permission");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        Permissions permission;
        try {
            permission = Permissions.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            new Message("Not a valid permission").fail().send(player, false);
            return 0;
        }

        if ((!faction.guest_permissions.contains(permission) && !add)
                || (faction.guest_permissions.contains(permission) && add)) {
            new Message(String.format("Could not change because the permission %s",
                    faction.guest_permissions.contains(permission) ? "already exists"
                            : "doesn't exist")).fail().send(player, false);
            return 0;
        }

        if (add) {
            faction.guest_permissions.add(permission);
        } else {
            faction.guest_permissions.remove(permission);
        }

        new Message("Successfully changed permissions").send(player, false);
        return 1;
    }

    private int addGuest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changeGuest(context, true);
    }

    private int removeGuest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changeGuest(context, false);
    }

    private int listGuest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        String permissionsList = faction.guest_permissions.stream().map(Enum::toString)
                .collect(Collectors.joining(","));

        new Message(String.format("Guests have the permissions: %s", permissionsList)).send(player, false);
        return 1;
    }

    // ========== MEMBER sub-commands ==========

    private int changeMember(CommandContext<ServerCommandSource> context, boolean add) throws CommandSyntaxException {
        String permissionName = StringArgumentType.getString(context, "permission");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        Permissions permission;
        try {
            permission = Permissions.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            new Message("Not a valid permission").fail().send(player, false);
            return 0;
        }

        boolean hasPerm = faction.member_permissions.contains(permission);
        if ((!hasPerm && !add) || (hasPerm && add)) {
            new Message(String.format("Could not change because the permission %s",
                    hasPerm ? "already exists" : "doesn't exist")).fail().send(player, false);
            return 0;
        }

        if (add) {
            faction.member_permissions.add(permission);
        } else {
            faction.member_permissions.remove(permission);
        }

        new Message("Successfully changed permissions").send(player, false);
        return 1;
    }

    private int addMember(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changeMember(context, true);
    }

    private int removeMember(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changeMember(context, false);
    }

    private int listMember(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        String permissionsList = faction.member_permissions.stream().map(Enum::toString)
                .collect(Collectors.joining(","));

        new Message(String.format("Members have the permissions: %s", permissionsList)).send(player, false);
        return 1;
    }

    // ========== PLAYER sub-commands ==========

    private int changePlayer(CommandContext<ServerCommandSource> context, boolean add) throws CommandSyntaxException {
        String permissionName = StringArgumentType.getString(context, "permission");
        String playerName = StringArgumentType.getString(context, "player");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        // Resolve the target player (can be any player, inside or outside the faction)
        User target;
        Optional<GameProfile> profile;
        if ((profile = source.getServer().getUserCache().findByName(playerName)).isPresent()) {
            target = User.get(profile.get().getId());
        } else {
            try {
                target = User.get(UUID.fromString(playerName));
            } catch (IllegalArgumentException e) {
                new Message("Player not found").fail().send(player, false);
                return 0;
            }
        }

        Permissions permission;
        try {
            permission = Permissions.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            new Message("Not a valid permission").fail().send(player, false);
            return 0;
        }

        boolean hasPerm = target.permissionOverrides.contains(permission);
        if ((!hasPerm && !add) || (hasPerm && add)) {
            new Message(String.format("Could not change because the permission %s",
                    hasPerm ? "already exists" : "doesn't exist")).fail().send(player, false);
            return 0;
        }

        if (add) {
            target.permissionOverrides.add(permission);
        } else {
            target.permissionOverrides.remove(permission);
        }
        User.save();

        new Message("Successfully changed permission override for player %s", playerName).send(player, false);
        return 1;
    }

    private int addPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changePlayer(context, true);
    }

    private int removePlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return changePlayer(context, false);
    }

    private int listPlayerPerms(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        // Resolve the target player (can be any player)
        User target;
        Optional<GameProfile> profile;
        if ((profile = source.getServer().getUserCache().findByName(playerName)).isPresent()) {
            target = User.get(profile.get().getId());
        } else {
            try {
                target = User.get(UUID.fromString(playerName));
            } catch (IllegalArgumentException e) {
                new Message("Player not found").fail().send(player, false);
                return 0;
            }
        }

        if (target.permissionOverrides.isEmpty()) {
            new Message("Player %s has no permission overrides", playerName).send(player, false);
            return 1;
        }

        String permissionsList = target.permissionOverrides.stream().map(Enum::toString)
                .collect(Collectors.joining(","));

        new Message("Player %s has permission overrides: %s", playerName, permissionsList).send(player, false);
        return 1;
    }

    private int listAllPlayerPerms(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        if (player == null) return 0;

        User user = Command.getUser(player);
        Faction faction = user.getFaction();

        if (faction == null) {
            new Message("You must be in a faction").fail().send(player, false);
            return 0;
        }

        // Get all users that have permission overrides
        java.util.List<String> overridesInfo = new java.util.ArrayList<>();
        for (User targetUser : User.all()) {
            if (!targetUser.permissionOverrides.isEmpty()) {
                Optional<GameProfile> prof = source.getServer().getUserCache().getByUuid(targetUser.getID());
                String name = prof.map(GameProfile::getName).orElse(targetUser.getID().toString());
                String perms = targetUser.permissionOverrides.stream().map(Enum::toString)
                        .collect(Collectors.joining(","));
                overridesInfo.add(name + ": " + perms);
            }
        }

        if (overridesInfo.isEmpty()) {
            new Message("No players have permission overrides").send(player, false);
            return 1;
        }

        new Message("All player permission overrides:").send(player, false);
        for (String info : overridesInfo) {
            new Message("  " + info).send(player, false);
        }
        return 1;
    }

    // ========== Context-aware permission suggestion providers ==========

    /**
     * Suggests permissions that are NOT currently set for the given group.
     * Used for "add" operations.
     */
    private static SuggestionProvider<ServerCommandSource> suggestAddablePerms(
            java.util.function.Function<Faction, java.util.List<Permissions>> getCurrentPerms) {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = Command.getUser(entity);
            Faction faction = user.getFaction();
            if (faction == null) return builder.buildFuture();

            java.util.List<Permissions> current = getCurrentPerms.apply(faction);
            for (Permissions perm : Permissions.values()) {
                if (!current.contains(perm)) {
                    builder.suggest(perm.toString());
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests permissions that ARE currently set for the given group.
     * Used for "remove" operations.
     */
    private static SuggestionProvider<ServerCommandSource> suggestRemovablePerms(
            java.util.function.Function<Faction, java.util.List<Permissions>> getCurrentPerms) {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = Command.getUser(entity);
            Faction faction = user.getFaction();
            if (faction == null) return builder.buildFuture();

            java.util.List<Permissions> current = getCurrentPerms.apply(faction);
            for (Permissions perm : current) {
                builder.suggest(perm.toString());
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests permissions that are NOT currently set for a faction relationship.
     */
    private static SuggestionProvider<ServerCommandSource> suggestAddableFactionPerms() {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = Command.getUser(entity);
            Faction sourceFaction = user.getFaction();
            if (sourceFaction == null) return builder.buildFuture();

            // Try to get the faction name from the context
            String factionName = null;
            try {
                factionName = StringArgumentType.getString(context, "faction");
            } catch (IllegalArgumentException e) {
                return builder.buildFuture();
            }

            Faction targetFaction = Faction.getByName(factionName);
            if (targetFaction == null) return builder.buildFuture();

            Relationship rel = sourceFaction.getRelationship(targetFaction.getID());
            for (Permissions perm : Permissions.values()) {
                if (!rel.permissions.contains(perm)) {
                    builder.suggest(perm.toString());
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests permissions that ARE currently set for a faction relationship.
     */
    private static SuggestionProvider<ServerCommandSource> suggestRemovableFactionPerms() {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = Command.getUser(entity);
            Faction sourceFaction = user.getFaction();
            if (sourceFaction == null) return builder.buildFuture();

            String factionName = null;
            try {
                factionName = StringArgumentType.getString(context, "faction");
            } catch (IllegalArgumentException e) {
                return builder.buildFuture();
            }

            Faction targetFaction = Faction.getByName(factionName);
            if (targetFaction == null) return builder.buildFuture();

            Relationship rel = sourceFaction.getRelationship(targetFaction.getID());
            for (Permissions perm : rel.permissions) {
                builder.suggest(perm.toString());
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests permissions that are NOT currently set for a player's overrides.
     */
    private static SuggestionProvider<ServerCommandSource> suggestAddablePlayerPerms() {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = Command.getUser(entity);
            Faction faction = user.getFaction();
            if (faction == null) return builder.buildFuture();

            String playerName = null;
            try {
                playerName = StringArgumentType.getString(context, "player");
            } catch (IllegalArgumentException e) {
                return builder.buildFuture();
            }

            Optional<GameProfile> profile = context.getSource().getServer().getUserCache().findByName(playerName);
            if (profile.isEmpty()) return builder.buildFuture();

            User target = User.get(profile.get().getId());
            for (Permissions perm : Permissions.values()) {
                if (!target.permissionOverrides.contains(perm)) {
                    builder.suggest(perm.toString());
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests permissions that ARE currently set for a player's overrides.
     */
    private static SuggestionProvider<ServerCommandSource> suggestRemovablePlayerPerms() {
        return (context, builder) -> {
            ServerPlayerEntity entity = context.getSource().getPlayerOrThrow();
            User user = Command.getUser(entity);
            Faction faction = user.getFaction();
            if (faction == null) return builder.buildFuture();

            String playerName = null;
            try {
                playerName = StringArgumentType.getString(context, "player");
            } catch (IllegalArgumentException e) {
                return builder.buildFuture();
            }

            Optional<GameProfile> profile = context.getSource().getServer().getUserCache().findByName(playerName);
            if (profile.isEmpty()) return builder.buildFuture();

            User target = User.get(profile.get().getId());
            for (Permissions perm : target.permissionOverrides) {
                builder.suggest(perm.toString());
            }
            return builder.buildFuture();
        };
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("permissions")
                .requires(Requires.multiple(Requires.isLeader(),
                        Requires.hasPerms("factions.permission", 0)))

                // ===== FACTION =====
                .then(CommandManager.literal("faction")
                        .requires(Requires.hasPerms("factions.permission.faction", 0))
                        .then(CommandManager.argument("faction", StringArgumentType.word())
                                .suggests(Suggests.allFactions(false))
                                .then(CommandManager.literal("add")
                                        .requires(Requires.hasPerms("factions.permission.faction.add", 0))
                                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                                .suggests(suggestAddableFactionPerms())
                                                .executes(this::addFaction)))
                                .then(CommandManager.literal("remove")
                                        .requires(Requires.hasPerms("factions.permission.faction.remove", 0))
                                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                                .suggests(suggestRemovableFactionPerms())
                                                .executes(this::removeFaction)))
                                .then(CommandManager.literal("list")
                                        .requires(Requires.hasPerms("factions.permission.faction.list", 0))
                                        .executes(this::listFaction))))

                // ===== GUEST =====
                .then(CommandManager.literal("guest")
                        .requires(Requires.hasPerms("factions.permission.guest", 0))
                        .then(CommandManager.literal("add")
                                .requires(Requires.hasPerms("factions.permission.guest.add", 0))
                                .then(CommandManager.argument("permission", StringArgumentType.word())
                                        .suggests(suggestAddablePerms(f -> f.guest_permissions))
                                        .executes(this::addGuest)))
                        .then(CommandManager.literal("remove")
                                .requires(Requires.hasPerms("factions.permission.guest.remove", 0))
                                .then(CommandManager.argument("permission", StringArgumentType.word())
                                        .suggests(suggestRemovablePerms(f -> f.guest_permissions))
                                        .executes(this::removeGuest)))
                        .then(CommandManager.literal("list")
                                .requires(Requires.hasPerms("factions.permission.guest.list", 0))
                                .executes(this::listGuest)))

                // ===== MEMBER =====
                .then(CommandManager.literal("member")
                        .requires(Requires.hasPerms("factions.permission.member", 0))
                        .then(CommandManager.literal("add")
                                .requires(Requires.hasPerms("factions.permission.member.add", 0))
                                .then(CommandManager.argument("permission", StringArgumentType.word())
                                        .suggests(suggestAddablePerms(f -> f.member_permissions))
                                        .executes(this::addMember)))
                        .then(CommandManager.literal("remove")
                                .requires(Requires.hasPerms("factions.permission.member.remove", 0))
                                .then(CommandManager.argument("permission", StringArgumentType.word())
                                        .suggests(suggestRemovablePerms(f -> f.member_permissions))
                                        .executes(this::removeMember)))
                        .then(CommandManager.literal("list")
                                .requires(Requires.hasPerms("factions.permission.member.list", 0))
                                .executes(this::listMember)))

                // ===== PLAYER =====
                .then(CommandManager.literal("player")
                        .requires(Requires.hasPerms("factions.permission.player", 0))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .suggests(Suggests.allPlayers())
                                .then(CommandManager.literal("add")
                                        .requires(Requires.hasPerms("factions.permission.player.add", 0))
                                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                                .suggests(suggestAddablePlayerPerms())
                                                .executes(this::addPlayer)))
                                .then(CommandManager.literal("remove")
                                        .requires(Requires.hasPerms("factions.permission.player.remove", 0))
                                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                                .suggests(suggestRemovablePlayerPerms())
                                                .executes(this::removePlayer)))
                                .then(CommandManager.literal("list")
                                        .requires(Requires.hasPerms("factions.permission.player.list", 0))
                                        .executes(this::listPlayerPerms)))
                        .then(CommandManager.literal("all")
                                .then(CommandManager.literal("list")
                                        .requires(Requires.hasPerms("factions.permission.player.list", 0))
                                        .executes(this::listAllPlayerPerms))))
                .build();
    }
}