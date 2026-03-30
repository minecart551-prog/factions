package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class SafeConfig {
    @SerializedName("_comment_safeEnabled")
    public String _COMMENT_SAFE_ENABLED = "If false, the safe command is disabled";

    @SerializedName("safeEnabled")
    public boolean SAFE_ENABLED = true;

    @SerializedName("_comment_enderChest")
    public String _COMMENT_ENDER_CHEST = "If true, ender chests acts as a faction chest, player ender chests won't be useable anymore";

    @SerializedName("enderChest")
    public boolean ENDER_CHEST = true;

    @SerializedName("_comment_double")
    public String _COMMENT_DOUBLE = "If true, faction safe is a double chest";

    @SerializedName("double")
    public boolean DOUBLE = true;
}
