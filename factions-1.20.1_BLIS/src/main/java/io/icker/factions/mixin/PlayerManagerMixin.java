package io.icker.factions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.StyledChatCompatibility;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Redirect(
            method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V"))
    public void sendChatMessage(ServerPlayerEntity player, SentMessage message, boolean bl,
            MessageType.Parameters parameters) {
        if (message instanceof SentMessage.Profileless
                || (FabricLoader.getInstance().isModLoaded("styledchat")
                        && StyledChatCompatibility.isNotPlayer(message))) {
            player.sendChatMessage(message, bl, parameters);
            return;
        }

        User sender;

        if (FabricLoader.getInstance().isModLoaded("styledchat")) {
            sender = User.get(StyledChatCompatibility.getSender(message));
        } else {
            sender = User.get(((SentMessage.Chat) message).message().link().sender());
        }

        User target = User.get(player.getUuid());

        boolean shouldSend = false;

        if (sender.chat == User.ChatMode.GLOBAL && target.chat != User.ChatMode.FOCUS) {
            shouldSend = true;
        }

        if ((sender.chat == User.ChatMode.FACTION || sender.chat == User.ChatMode.FOCUS)
                && sender.getFaction().equals(target.getFaction())) {
            shouldSend = true;
        }

        if (!shouldSend) return;

        if (sender.isInFaction() && sender.chat == User.ChatMode.GLOBAL
                && FactionsMod.CONFIG.DISPLAY.MODIFY_CHAT) {
            Faction faction = sender.getFaction();
            String name = player.getDisplayName().getString();

            Text hoverText = Text.empty()
                    .append(Text.literal("Faction: ").formatted(Formatting.GRAY))
                    .append(Text.literal(faction.getName()).formatted(Formatting.BOLD, faction.getColor()));

            MutableText modified = Text.empty()
                    .append(Text.literal("<").formatted(Formatting.GRAY))
                    .append(Text.literal(name)
                            .styled(s -> s
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                                    .withColor(Formatting.WHITE)))
                    .append(Text.literal("> ").formatted(Formatting.GRAY))
                    .append(Text.literal(((SentMessage.Chat) message).message().getContent().getString())
                            .formatted(Formatting.WHITE));

            player.sendMessage(modified, false);
        } else {
            player.sendChatMessage(message, bl, parameters);
        }
    }
}
