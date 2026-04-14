package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class PowerConfig {
    @SerializedName("_comment_base")
    public String _COMMENT_BASE = "Base power granted to every faction";

    @SerializedName("base")
    public int BASE = 20;

    @SerializedName("_comment_member")
    public String _COMMENT_MEMBER = "Max power each member contributes to the faction";

    @SerializedName("member")
    public int MEMBER = 20;

    @SerializedName("_comment_claimWeight")
    public String _COMMENT_CLAIM_WEIGHT = "Power required per claimed chunk";

    @SerializedName("claimWeight")
    public int CLAIM_WEIGHT = 5;

    @SerializedName("_comment_deathPenalty")
    public String _COMMENT_DEATH_PENALTY = "Power lost by a player when they die";

    @SerializedName("deathPenalty")
    public int DEATH_PENALTY = 10;

    @SerializedName("_comment_enemyDeathMultiplier")
    public String _COMMENT_ENEMY_DEATH_MULTIPLIER = "Multiplier applied to death penalty when killed by enemy faction";

    @SerializedName("enemyDeathMultiplier")
    public int ENEMY_DEATH_MULTIPLIER = 2;

    @SerializedName("powerTicks")
    public PowerTicks POWER_TICKS = new PowerTicks();

    @SerializedName("_comment_powerPerAlly")
    public String _COMMENT_POWER_PER_ALLY = "Bonus power for each mutual ally";

    @SerializedName("powerPerAlly")
    public int POWER_PER_ALLY = 0;

    @SerializedName("_comment_inactivityTiers")
    public String _COMMENT_INACTIVITY_TIERS = "Power multiplier based on days since last login (must be sorted by days ascending)";

    @SerializedName("inactivityTiers")
    public InactivityTier[] INACTIVITY_TIERS = new InactivityTier[] {
        new InactivityTier(7, 0.75),
        new InactivityTier(14, 0.50),
        new InactivityTier(30, 0.0)
    };

    @SerializedName("wealth")
    public WealthPower WEALTH = new WealthPower();

    @SerializedName("_comment_decayCheckTicks")
    public String _COMMENT_DECAY_CHECK_TICKS = "How often to check for claim decay in ticks (20 ticks = 1 second)";

    @SerializedName("decayCheckTicks")
    public int DECAY_CHECK_TICKS = 12000;

    @SerializedName("_comment_claimDecayEnabled")
    public String _COMMENT_CLAIM_DECAY_ENABLED = "Whether claims are automatically removed when faction power is insufficient";

    @SerializedName("claimDecayEnabled")
    public boolean CLAIM_DECAY_ENABLED = true;

    @SerializedName("_comment_unclaimCooldownSeconds")
    public String _COMMENT_UNCLAIM_COOLDOWN_SECONDS = "Cooldown in seconds before a recently unclaimed chunk can be claimed again by any faction (0 = disabled)";

    @SerializedName("unclaimCooldownSeconds")
    public int UNCLAIM_COOLDOWN_SECONDS = 0;

    @SerializedName("war")
    public WarPower WAR = new WarPower();

    @SerializedName("fame")
    public FamePower FAME = new FamePower();

    public static class FamePower {
        @SerializedName("_comment_maxValue")
        public String _COMMENT_MAX_VALUE = "Maximum fame power a faction can accumulate";

        @SerializedName("maxValue")
        public int MAX_VALUE = 100;

        @SerializedName("_comment_decayPerDay")
        public String _COMMENT_DECAY_PER_DAY = "Fame power lost per real day since last fame gain";

        @SerializedName("decayPerDay")
        public int DECAY_PER_DAY = 5;
    }

    public static class WarPower {
        @SerializedName("_comment_maxValue")
        public String _COMMENT_MAX_VALUE = "Maximum war power a faction can accumulate";

        @SerializedName("maxValue")
        public int MAX_VALUE = 100;

        @SerializedName("_comment_killReward")
        public String _COMMENT_KILL_REWARD = "Base war power gained when killing a player from another faction";

        @SerializedName("killReward")
        public int KILL_REWARD = 5;

        @SerializedName("_comment_enemyMultiplier")
        public String _COMMENT_ENEMY_MULTIPLIER = "Multiplier applied to kill reward when killing enemy faction members";

        @SerializedName("enemyMultiplier")
        public int ENEMY_MULTIPLIER = 2;

        @SerializedName("_comment_decayPerDay")
        public String _COMMENT_DECAY_PER_DAY = "War power lost per real day since last kill";

        @SerializedName("decayPerDay")
        public int DECAY_PER_DAY = 5;
    }

    public static class WealthPower {
        @SerializedName("_comment_maxValue")
        public String _COMMENT_MAX_VALUE = "Maximum wealth power a faction can accumulate";

        @SerializedName("maxValue")
        public int MAX_VALUE = 100;

        @SerializedName("_comment_decayPerDay")
        public String _COMMENT_DECAY_PER_DAY = "Wealth power lost per real day since last sacrifice";

        @SerializedName("decayPerDay")
        public int DECAY_PER_DAY = 5;

        @SerializedName("_comment_items")
        public String _COMMENT_ITEMS = "Items that can be sacrificed and their power value";

        @SerializedName("items")
        public SacrificeItem[] ITEMS = new SacrificeItem[] {
            new SacrificeItem("minecraft:diamond", 1),
            new SacrificeItem("minecraft:diamond_block", 9),
            new SacrificeItem("minecraft:netherite_ingot", 10),
            new SacrificeItem("minecraft:netherite_block", 90)
        };
    }

    public static class SacrificeItem {
        @SerializedName("itemId")
        public String ITEM_ID;

        @SerializedName("value")
        public int VALUE;

        public SacrificeItem() {}

        public SacrificeItem(String itemId, int value) {
            this.ITEM_ID = itemId;
            this.VALUE = value;
        }
    }

    public static class InactivityTier {
        @SerializedName("days")
        public int DAYS;

        @SerializedName("multiplier")
        public double MULTIPLIER;

        public InactivityTier() {}

        public InactivityTier(int days, double multiplier) {
            this.DAYS = days;
            this.MULTIPLIER = multiplier;
        }
    }

    public static class PowerTicks {
        @SerializedName("_comment_ticks")
        public String _COMMENT_TICKS = "Interval in ticks for power regeneration (20 ticks = 1 second)";

        @SerializedName("ticks")
        public int TICKS = 12000;

        @SerializedName("_comment_reward")
        public String _COMMENT_REWARD = "Power gained per tick interval while online";

        @SerializedName("reward")
        public int REWARD = 1;
    }
}
