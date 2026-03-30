package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class GodsConfig {
    @SerializedName("_comment_enabled")
    public String _COMMENT_ENABLED = "Whether the god's blessings system is enabled";

    @SerializedName("enabled")
    public boolean ENABLED = true;

    @SerializedName("_comment_applyToVassals")
    public String _COMMENT_APPLY_TO_VASSALS = "Whether blessings also apply to vassal faction members";

    @SerializedName("applyToVassals")
    public boolean APPLY_TO_VASSALS = false;

    @SerializedName("_comment_maxActiveEffects")
    public String _COMMENT_MAX_ACTIVE_EFFECTS = "Maximum number of active blessings per faction (-1 = unlimited)";

    @SerializedName("maxActiveEffects")
    public int MAX_ACTIVE_EFFECTS = 2;

    @SerializedName("_comment_globalCooldownSeconds")
    public String _COMMENT_GLOBAL_COOLDOWN_SECONDS = "Cooldown in seconds between prayers for each faction";

    @SerializedName("globalCooldownSeconds")
    public int GLOBAL_COOLDOWN_SECONDS = 300;

    @SerializedName("_comment_gods")
    public String _COMMENT_GODS = "List of gods that can be prayed to. Effect uses minecraft effect IDs (e.g. minecraft:strength)";

    @SerializedName("gods")
    public God[] GODS = new God[] {
        new God("Ares", "minecraft:strength", 300, 0, 25),
        new God("Athena", "minecraft:resistance", 300, 0, 30),
        new God("Hermes", "minecraft:speed", 300, 1, 20),
        new God("Apollo", "minecraft:regeneration", 180, 0, 35),
        new God("Hephaestus", "minecraft:haste", 300, 1, 15)
    };

    public static class God {
        @SerializedName("name")
        public String NAME;

        @SerializedName("effect")
        public String EFFECT;

        @SerializedName("durationSeconds")
        public int DURATION_SECONDS;

        @SerializedName("amplifier")
        public int AMPLIFIER;

        @SerializedName("powerCost")
        public int POWER_COST;

        public God() {}

        public God(String name, String effect, int durationSeconds, int amplifier, int powerCost) {
            this.NAME = name;
            this.EFFECT = effect;
            this.DURATION_SECONDS = durationSeconds;
            this.AMPLIFIER = amplifier;
            this.POWER_COST = powerCost;
        }
    }
}
