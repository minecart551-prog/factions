package io.icker.factions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.icker.factions.FactionsMod;
import io.icker.factions.core.InteractionManager;
import io.icker.factions.core.PenaltyMiningState;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;

/**
 * Forces a uniform break-time delta for penalized foreign-claim breaks so
 * tool/block hardness does not matter. Server checks live permissions; the
 * client uses PenaltyMiningState (set from the server's START packet).
 */
@Mixin(AbstractBlock.class)
public class AbstractBlockBreakingDeltaMixin {
    @Inject(method = "calcBlockBreakingDelta", at = @At("HEAD"), cancellable = true)
    private void factions$uniformPenaltyDelta(BlockState state, PlayerEntity player,
            BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        var penalty = FactionsMod.CONFIG.BREAK_PENALTY;
        if (penalty == null || !penalty.ENABLED || penalty.UNIFORM_BREAK_TIME_SECONDS <= 0) {
            return;
        }

        // Preserve unbreakable blocks (hardness < 0 → vanilla returns 0)
        if (state.getHardness(world, pos) < 0) {
            return;
        }

        boolean apply;
        if (world instanceof ServerWorld serverWorld) {
            apply = InteractionManager.shouldApplyBreakPenalty(player, pos, serverWorld);
        } else {
            apply = PenaltyMiningState.matches(pos);
        }

        if (apply) {
            float seconds = (float) penalty.UNIFORM_BREAK_TIME_SECONDS;
            cir.setReturnValue(1.0f / (seconds * 20.0f));
        }
    }
}
