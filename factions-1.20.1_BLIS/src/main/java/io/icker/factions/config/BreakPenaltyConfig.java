package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class BreakPenaltyConfig {
    public enum DamageMode {
        NORMAL, ARMOR_BYPASS
    }

    @SerializedName("_comment_enabled")
    public String _COMMENT_ENABLED = "Whether unauthorized breaks in foreign claims deal damage and cost the claim owner's faction bank";

    @SerializedName("enabled")
    public boolean ENABLED = true;

    @SerializedName("_comment_bankCostPerBlock")
    public String _COMMENT_BANK_COST_PER_BLOCK = "Amount deducted from the claim owner's faction bank per denied break (up to 2 decimals)";

    @SerializedName("bankCostPerBlock")
    public double BANK_COST_PER_BLOCK = 5.0;

    @SerializedName("_comment_damageMode")
    public String _COMMENT_DAMAGE_MODE = "NORMAL = reduced by armor; ARMOR_BYPASS = ignores armor";

    @SerializedName("damageMode")
    public DamageMode DAMAGE_MODE = DamageMode.NORMAL;

    @SerializedName("_comment_normalDamage")
    public String _COMMENT_NORMAL_DAMAGE = "Damage dealt when damageMode is NORMAL";

    @SerializedName("normalDamage")
    public double NORMAL_DAMAGE = 2.0;

    @SerializedName("_comment_armorBypassDamage")
    public String _COMMENT_ARMOR_BYPASS_DAMAGE = "Damage dealt when damageMode is ARMOR_BYPASS";

    @SerializedName("armorBypassDamage")
    public double ARMOR_BYPASS_DAMAGE = 4.0;

    @SerializedName("_comment_uniformBreakTimeSeconds")
    public String _COMMENT_UNIFORM_BREAK_TIME_SECONDS = "Fixed seconds to complete each penalized break attempt regardless of block/tool (<= 0 disables)";

    @SerializedName("uniformBreakTimeSeconds")
    public double UNIFORM_BREAK_TIME_SECONDS = 2.0;
}
