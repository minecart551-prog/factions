package io.icker.factions.core;

import net.minecraft.util.math.BlockPos;

/**
 * Server-confirmed flag for the block currently being penalty-mined.
 * Written by the client network handler; read by the break-delta mixin so
 * client prediction matches the server's uniform break time.
 */
public final class PenaltyMiningState {
    private static BlockPos pos;
    private static boolean penalized;

    private PenaltyMiningState() {}

    public static void set(BlockPos position, boolean isPenalized) {
        pos = position == null ? null : position.toImmutable();
        penalized = isPenalized;
    }

    public static void clear() {
        pos = null;
        penalized = false;
    }

    public static boolean matches(BlockPos position) {
        return penalized && pos != null && pos.equals(position);
    }
}
