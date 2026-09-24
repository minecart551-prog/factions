package io.icker.factions.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class Money {
    private static final DecimalFormat WHOLE = new DecimalFormat("0");
    private static final DecimalFormat CENTS = new DecimalFormat("0.##");

    static {
        WHOLE.setRoundingMode(RoundingMode.HALF_UP);
        CENTS.setRoundingMode(RoundingMode.HALF_UP);
    }

    private Money() {}

    /** Formats a money amount: whole values without decimals, fractions up to 2 dp. */
    public static String format(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return WHOLE.format(amount);
        }
        return CENTS.format(amount);
    }

    /** Rounds to 2 decimal places (half up). */
    public static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
