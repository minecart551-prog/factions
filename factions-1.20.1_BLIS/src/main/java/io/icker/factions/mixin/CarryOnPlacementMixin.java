package io.icker.factions.mixin;

import io.icker.factions.FactionsMod;
import io.icker.factions.core.InteractionManager;
import io.icker.factions.core.InteractionsUtil;
import io.icker.factions.core.InteractionsUtil.InteractionsUtilActions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public class CarryOnPlacementMixin {

    @Inject(method = "tryPlaceBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onTryPlaceBlock(
            ServerPlayerEntity player,
            BlockPos pos,
            Direction facing,
            BiFunction<?, ?, ?> placementCallback,
            CallbackInfoReturnable<Boolean> cir) {
        if (player.getWorld().isClient()) return;
        // Check pos first; also check the adjacent position (pos.offset(facing)) since
        // CarryOn shifts the target there if the clicked block can't be replaced.
        if (InteractionManager.checkCarryOnPlacement(player, pos, player.getWorld()) == ActionResult.FAIL
                || InteractionManager.checkCarryOnPlacement(player, pos.offset(facing), player.getWorld()) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.PLACE_BLOCKS);
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "tryPlaceEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onTryPlaceEntity(
            ServerPlayerEntity player,
            BlockPos pos,
            Direction facing,
            BiFunction<?, ?, ?> placementCallback,
            CallbackInfoReturnable<Boolean> cir) {
        if (!FactionsMod.CONFIG.CARRY_ON_ENTITY_PLACEMENT) return;
        if (player.getWorld().isClient()) return;
        if (InteractionManager.checkCarryOnPlacement(player, pos, player.getWorld()) == ActionResult.FAIL
                || InteractionManager.checkCarryOnPlacement(player, pos.offset(facing), player.getWorld()) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.PLACE_BLOCKS);
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
