package io.icker.factions.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.compat.compatSkillDamageProtectionfor;
import io.icker.factions.api.persistents.Relationship;
import net.fabricmc.loader.api.FabricLoader;

public class Config {
    private static final int REQUIRED_VERSION = 3;
    private static final File file = FabricLoader.getInstance().getGameDir().resolve("config")
            .resolve("factions.json").toFile();

    public static Config load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
                .registerTypeAdapter(HomeConfig.class, new Deserializer<>(HomeConfig.class))
                .registerTypeAdapter(PowerConfig.class, new Deserializer<>(PowerConfig.class))
                .registerTypeAdapter(SafeConfig.class, new Deserializer<>(SafeConfig.class))
                .registerTypeAdapter(VassalConfig.class, new Deserializer<>(VassalConfig.class))
                .registerTypeAdapter(GodsConfig.class, new Deserializer<>(GodsConfig.class))
                .registerTypeAdapter(FilterListConfig.class, new Deserializer<>(FilterListConfig.class))
                .create();

        try {
            if (!file.exists()) {
                file.getParentFile().mkdir();

                Config defaults = new Config();

                FileWriter writer = new FileWriter(file);
                gson.toJson(defaults, writer);
                writer.close();

                return defaults;
            }

            Config config = gson.fromJson(new FileReader(file), Config.class);
            Config defaults = new Config();

            if (config.POWER == null) {
                config.POWER = defaults.POWER;
            }

            if (config.RELATIONSHIPS == null) {
                config.RELATIONSHIPS = defaults.RELATIONSHIPS;
            } else {
                if (config.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR == null) {
                    config.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR = defaults.RELATIONSHIPS.COMPAT_SKILL_DAMAGE_PROTECTION_FOR;
                }
            }

            if (config.DISPLAY == null) {
                config.DISPLAY = defaults.DISPLAY;
            } else {
                if (config.DISPLAY.TERRITORY_NOTIFICATION == null) {
                    config.DISPLAY.TERRITORY_NOTIFICATION = defaults.DISPLAY.TERRITORY_NOTIFICATION;
                }
            }

            if (config.VASSAL == null) {
                config.VASSAL = defaults.VASSAL;
            }

            if (config.GODS == null) {
                config.GODS = defaults.GODS;
            }

            if (config.BLUEMAP == null) {
                config.BLUEMAP = defaults.BLUEMAP;
            }

            if (config.BLOCK_LIST == null) {
                config.BLOCK_LIST = defaults.BLOCK_LIST;
            }

            if (config.MOB_LIST == null) {
                config.MOB_LIST = defaults.MOB_LIST;
            }

            if (config.VERSION != REQUIRED_VERSION) {
                FactionsMod.LOGGER.error(String.format(
                        "Config file incompatible (requires version %d)", REQUIRED_VERSION));
            }

            FileWriter writer = new FileWriter(file);
            gson.toJson(config, writer);
            writer.close();

            return config;

        } catch (Exception e) {
            FactionsMod.LOGGER.error("An error occurred reading the factions config file", e);
            return new Config();
        }
    }

    public static void save(Config config) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
                    .registerTypeAdapter(HomeConfig.class, new Deserializer<>(HomeConfig.class))
                    .registerTypeAdapter(PowerConfig.class, new Deserializer<>(PowerConfig.class))
                    .registerTypeAdapter(SafeConfig.class, new Deserializer<>(SafeConfig.class))
                    .registerTypeAdapter(VassalConfig.class, new Deserializer<>(VassalConfig.class))
                    .registerTypeAdapter(GodsConfig.class, new Deserializer<>(GodsConfig.class))
                    .create();

            FileWriter writer = new FileWriter(file);
            gson.toJson(config, writer);
            writer.close();

        } catch (Exception e) {
            FactionsMod.LOGGER.error("An error occurred saving the factions config file", e);
        }
    }

    public void save() {
        save(this);
    }

    @SerializedName("version")
    public int VERSION = REQUIRED_VERSION;

    @SerializedName("_comment_blockTNT")
    public String _COMMENT_BLOCK_TNT = "Whether TNT explosions are blocked in claimed territory";

    @SerializedName("blockTNT")
    public boolean BLOCK_TNT = false;

    @SerializedName("power")
    public PowerConfig POWER = new PowerConfig();

    @SerializedName("safe")
    @Nullable
    public SafeConfig SAFE = new SafeConfig();

    @SerializedName("home")
    @Nullable
    public HomeConfig HOME = new HomeConfig();

    @SerializedName("display")
    public DisplayConfig DISPLAY = new DisplayConfig();

    @SerializedName("relationships")
    public RelationshipConfig RELATIONSHIPS = new RelationshipConfig();

    @SerializedName("vassal")
    public VassalConfig VASSAL = new VassalConfig();

    @SerializedName("gods")
    public GodsConfig GODS = new GodsConfig();

    @SerializedName("bluemap")
    public BlueMapConfig BLUEMAP = new BlueMapConfig();

    @SerializedName("_comment_carryOnEntityPlacement")
    public String _COMMENT_CARRY_ON_ENTITY_PLACEMENT = "Whether CarryOn mod entity placement (putting down a carried mob/player) is blocked by claim protection";

    @SerializedName("carryOnEntityPlacement")
    public boolean CARRY_ON_ENTITY_PLACEMENT = false;

    @SerializedName("_comment_inventoryBlocks")
    public String _COMMENT_INVENTORY_BLOCKS = "Blocks treated as inventories for permission purposes (USE_INVENTORIES instead of USE_BLOCKS). Supports @modid, namespace:prefix*, and exact patterns. Example: numismatic-overhaul:piggy_bank";

    @SerializedName("inventoryBlocks")
    public ArrayList<String> INVENTORY_BLOCKS = new ArrayList<>();

    @SerializedName("_comment_blockList")
    public String _COMMENT_BLOCK_LIST = "Block filter lists. Blacklist: only listed blocks are subject to claim protection. Whitelist: listed blocks always bypass protection. Whitelist takes priority when both enabled.";

    @SerializedName("blockList")
    public FilterListConfig BLOCK_LIST = new FilterListConfig();

    @SerializedName("_comment_mobList")
    public String _COMMENT_MOB_LIST = "Mob filter lists. Blacklist: only listed mobs are subject to claim protection. Whitelist: listed mobs always bypass protection. Whitelist takes priority when both enabled.";

    @SerializedName("mobList")
    public FilterListConfig MOB_LIST = new FilterListConfig();

    @SerializedName("_comment_maxFactionSize")
    public String _COMMENT_MAX_FACTION_SIZE = "Maximum members per faction (-1 = unlimited)";

    @SerializedName("maxFactionSize")
    public int MAX_FACTION_SIZE = -1;

    @SerializedName("_comment_friendlyFire")
    public String _COMMENT_FRIENDLY_FIRE = "Whether faction members can damage each other";

    @SerializedName("friendlyFire")
    public boolean FRIENDLY_FIRE = false;

    @SerializedName("_comment_requiredBypassLevel")
    public String _COMMENT_REQUIRED_BYPASS_LEVEL = "Permission level required for admin commands (0-4)";

    @SerializedName("requiredBypassLevel")
    public int REQUIRED_BYPASS_LEVEL = 2;

    @SerializedName("_comment_claimProtections")
    public String _COMMENT_CLAIM_PROTECTION = "Whether claimed chunks are protected from non-members";

    @SerializedName("claimProtections")
    public boolean CLAIM_PROTECTION = true;

    @SerializedName("_comment_restrictedWilderness")
    public String _COMMENT_RESTRICTED_WILDERNESS = "When enabled, unclaimed wilderness follows wildernessPermissions instead of being fully open";

    @SerializedName("restrictedWilderness")
    public boolean RESTRICTED_WILDERNESS = false;

    @SerializedName("wildernessPermissions")
    public List<Relationship.Permissions> WILDERNESS_PERMISSIONS =
            List.of(Relationship.Permissions.USE_BLOCKS, Relationship.Permissions.USE_ENTITIES, Relationship.Permissions.ATTACK_ENTITIES, Relationship.Permissions.ATTACK_MOBS);

    @SerializedName("wildernessRestrictedDimensions")
    public List<String> WILDERNESS_RESTRICTED_DIMENSIONS = List.of("minecraft:overworld");

    public static class TerritoryNotificationConfig {
        @SerializedName("chat")
        public boolean CHAT = false;

        @SerializedName("actionBar")
        public boolean ACTION_BAR = false;

        @SerializedName("title")
        public boolean TITLE = true;

        @SerializedName("_comment_titleFade")
        public String _COMMENT_TITLE_FADE = "Title animation durations in ticks (20 ticks = 1 second)";

        @SerializedName("titleFadeIn")
        public int TITLE_FADE_IN = 10;

        @SerializedName("titleStay")
        public int TITLE_STAY = 50;

        @SerializedName("titleFadeOut")
        public int TITLE_FADE_OUT = 20;
    }

    public static class DisplayConfig {
        @SerializedName("factionNameMaxLength")
        public int NAME_MAX_LENGTH = -1;

        @SerializedName("changeChat")
        public boolean MODIFY_CHAT = true;

        @SerializedName("tabMenu")
        public boolean TAB_MENU = true;

        @SerializedName("nameBlackList")
        public List<String> NAME_BLACKLIST = List.of("wilderness", "factionless");

        @SerializedName("powerMessage")
        public boolean POWER_MESSAGE = true;

        @SerializedName("_comment_territoryNotification")
        public String _COMMENT_TERRITORY_NOTIFICATION = "Territory entry/exit notifications: chat, actionBar and title can be enabled independently";

        @SerializedName("territoryNotification")
        public TerritoryNotificationConfig TERRITORY_NOTIFICATION = new TerritoryNotificationConfig();
    }

    public static class RelationshipConfig {
        @SerializedName("allyOverridesPermissions")
        public boolean ALLY_OVERRIDES_PERMISSIONS = true;

        @SerializedName("_comment_xAsGuest")
        public String _COMMENT_X_AS_GUEST = "When enabled and no explicit relationship permissions are set, the faction's guest permissions are used as a fallback";

        @SerializedName("neutralAsGuest")
        public boolean NEUTRAL_AS_GUEST = true;

        @SerializedName("friendlyAsGuest")
        public boolean FRIENDLY_AS_GUEST = true;

        @SerializedName("allyAsGuest")
        public boolean ALLY_AS_GUEST = false;

        @SerializedName("defaultGuestPermissions")
        public List<Relationship.Permissions> DEFAULT_GUEST_PERMISSIONS =
                List.of(Relationship.Permissions.USE_BLOCKS, Relationship.Permissions.USE_ENTITIES, Relationship.Permissions.ATTACK_MOBS);

        @SerializedName("compatSkillDamageProtectionfor")
        public compatSkillDamageProtectionfor COMPAT_SKILL_DAMAGE_PROTECTION_FOR = compatSkillDamageProtectionfor.NEUTRAL;
    }

    public static class BlueMapConfig {
        @SerializedName("_comment_markerMinY")
        public String _COMMENT_MARKER_MIN_Y = "Minimum Y value for extruded claim markers on BlueMap";

        @SerializedName("markerMinY")
        public int MARKER_MIN_Y = -64;

        @SerializedName("_comment_markerMaxY")
        public String _COMMENT_MARKER_MAX_Y = "Maximum Y value for extruded claim markers on BlueMap";

        @SerializedName("markerMaxY")
        public int MARKER_MAX_Y = 320;
    }

    public static class FilterListConfig {
        @SerializedName("blacklistEnabled")
        public boolean BLACKLIST_ENABLED = false;

        @SerializedName("blacklist")
        public ArrayList<String> BLACKLIST = new ArrayList<>();

        @SerializedName("whitelistEnabled")
        public boolean WHITELIST_ENABLED = false;

        @SerializedName("whitelist")
        public ArrayList<String> WHITELIST = new ArrayList<>();
    }

    public static class Deserializer<T> implements JsonDeserializer<T> {
        final Class<T> clazz;

        public Deserializer(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject() && !json.getAsBoolean()) {
                return null;
            }

            return new Gson().fromJson(json, clazz);
        }
    }
}
