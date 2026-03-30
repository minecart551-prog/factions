package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class VassalConfig {
    @SerializedName("_comment_enabled")
    public String _COMMENT_ENABLED = "Whether the vassal system is enabled";

    @SerializedName("enabled")
    public boolean ENABLED = true;

    @SerializedName("_comment_powerPercent")
    public String _COMMENT_POWER_PERCENT = "Percentage of vassal's power granted to overlord (0-100)";

    @SerializedName("powerPercent")
    public int POWER_PERCENT = 25;

    @SerializedName("_comment_requireAlly")
    public String _COMMENT_REQUIRE_ALLY = "Whether factions must be mutual allies before becoming vassal";

    @SerializedName("requireAlly")
    public boolean REQUIRE_ALLY = true;
}
