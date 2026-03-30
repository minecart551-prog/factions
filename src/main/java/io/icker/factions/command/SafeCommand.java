package io.icker.factions.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class SafeCommand implements Command {

    private int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        if (!FactionsMod.CONFIG.SAFE.SAFE_ENABLED) {
            new Message("Safe is disabled").fail().send(player, false);
            return 0;
        }

        PlayerEvents.OPEN_SAFE.invoker().onOpenSafe(player, Command.getUser(player).getFaction());
        return 1;
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager.literal("safe")
                .requires(Requires.multiple(Requires.hasPerms("faction.safe", 0),
                        Requires.isMember(), s -> FactionsMod.CONFIG.SAFE != null))
                .executes(this::run).build();
    }
}
