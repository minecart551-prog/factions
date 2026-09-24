package io.icker.factions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.core.InteractionManager;
import io.icker.factions.network.MiningPenaltyNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Shadow
    protected ServerPlayerEntity player;

    @Shadow
    protected ServerWorld world;

    @Redirect(method = "interactBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;"))
    public ActionResult place(ItemStack instance, ItemUsageContext context) {
        if (PlayerEvents.PLACE_BLOCK.invoker().onPlaceBlock(context) == ActionResult.FAIL) {
            return ActionResult.FAIL;
        }
        return instance.useOnBlock(context);
    }

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
    private void factions$onStartMining(BlockPos pos, PlayerActionC2SPacket.Action action,
            net.minecraft.util.math.Direction direction, int worldHash, int sequence, CallbackInfo ci) {
        if (action != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            return;
        }
        if (world == null || player == null) {
            return;
        }

        var penalty = FactionsMod.CONFIG.BREAK_PENALTY;
        boolean uniformEnabled = penalty != null && penalty.ENABLED
                && penalty.UNIFORM_BREAK_TIME_SECONDS > 0;
        boolean penalized = uniformEnabled
                && InteractionManager.shouldApplyBreakPenalty(player, pos, world);
        MiningPenaltyNetworkHandler.send(player, pos, penalized);
    }
}
