package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class BankConfig {
    @SerializedName("_comment_enabled")
    public String _COMMENT_ENABLED = "Whether the faction bank system is enabled";

    @SerializedName("enabled")
    public boolean ENABLED = true;

    @SerializedName("_comment_maxBalance")
    public String _COMMENT_MAX_BALANCE = "Maximum bank balance per faction (-1 = unlimited)";

    @SerializedName("maxBalance")
    public int MAX_BALANCE = -1;
}
