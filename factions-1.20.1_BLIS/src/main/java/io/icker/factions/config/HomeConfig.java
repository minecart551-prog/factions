package io.icker.factions.config;

import com.google.gson.annotations.SerializedName;

public class HomeConfig {
    @SerializedName("_comment_warpEnabled")
    public String _COMMENT_WARP_ENABLED = "If false, players cannot teleport to faction home (but leaders can still set home)";

    @SerializedName("warpEnabled")
    public boolean WARP_ENABLED = true;

    @SerializedName("_comment_claimOnly")
    public String _COMMENT_CLAIM_ONLY = "If true, faction home can only be set within claimed territory";

    @SerializedName("claimOnly")
    public boolean CLAIM_ONLY = true;

    @SerializedName("_comment_damageTickCooldown")
    public String _COMMENT_DAMAGE_COOLDOWN = "Number of ticks after taking damage before home teleport is allowed (20 ticks = 1 second)";

    @SerializedName("damageTickCooldown")
    public int DAMAGE_COOLDOWN = 100;

    @SerializedName("_comment_homeWarpCooldownSecond")
    public String _COMMENT_HOME_WARP_COOLDOWN_SECOND = "Cooldown in seconds between home teleports";

    @SerializedName("homeWarpCooldownSecond")
    public int HOME_WARP_COOLDOWN_SECOND = 15;
}
