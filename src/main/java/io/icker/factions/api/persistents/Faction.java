package io.icker.factions.api.persistents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import io.icker.factions.util.WorldUtils;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;

@Name("Faction")
public class Faction {
    private static final HashMap<UUID, Faction> STORE =
            Database.load(Faction.class, Faction::getID);

    @Field("ID")
    private UUID id;

    @Field("Name")
    private String name;

    @Field("Description")
    private String description;

    @Field("MOTD")
    private String motd;

    @Field("Color")
    private String color;

    /**
     * Whether a player can join without an invitation
     */
    @Field("Open")
    private boolean open;

    @Field("Power")
    private int power;

    @Field("AdminPower")
    private int adminPower;

    @Field("WealthPower")
    private int wealthPower;

    @Field("LastSacrifice")
    private long lastSacrifice;

    @Field("WarPower")
    private int warPower;

    @Field("LastWarKill")
    private long lastWarKill;

    @Field("FamePower")
    private int famePower;

    @Field("LastFameGain")
    private long lastFameGain;

    @Field("AdminProtected")
    private boolean adminProtected;

    @Field("Home")
    private Home home;

    @Field("Safe")
    private SimpleInventory safe = new SimpleInventory(54);

    @Field("Invites")
    public ArrayList<UUID> invites = new ArrayList<>();

    @Field("Relationships")
    private ArrayList<Relationship> relationships = new ArrayList<>();

    @Field("GuestPermissions")
    public ArrayList<Relationship.Permissions> guest_permissions =
            new ArrayList<>(FactionsMod.CONFIG.RELATIONSHIPS.DEFAULT_GUEST_PERMISSIONS);

    @Field("OverlordId")
    private UUID overlordId;

    @Field("VassalRequests")
    public ArrayList<UUID> vassalRequests = new ArrayList<>();

    @Field("LastPrayer")
    private long lastPrayer;

    @Field("ActiveBlessingsJson")
    private String activeBlessingsJson = "[]";

    private static final Gson GSON = new Gson();

    public Faction(String name, String description, String motd, Formatting color, boolean open,
            int power) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.motd = motd;
        this.description = description;
        this.color = color.getName();
        this.open = open;
        this.power = power;
    }

    public Faction() {}

    public String getKey() {
        return id.toString();
    }

    @Nullable
    public static Faction get(UUID id) {
        return STORE.get(id);
    }

    @Nullable
    public static Faction getByName(String name) {
        return STORE.values().stream().filter(f -> f.name.equals(name)).findFirst().orElse(null);
    }

    public static void add(Faction faction) {
        STORE.put(faction.id, faction);
    }

    public static Collection<Faction> all() {
        return STORE.values();
    }

    public static List<Faction> allBut(UUID id) {
        return STORE.values().stream().filter(f -> f.id != id).toList();
    }

    public UUID getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Formatting getColor() {
        return Formatting.byName(color);
    }

    public String getDescription() {
        return description;
    }

    public String getMOTD() {
        return motd;
    }

    public int getPower() {
        int basePowerMax = getBasePowerMax();
        int basePower = Math.min(power, basePowerMax);
        return basePower + adminPower + getWealthPower() + getWarPower() + getFamePower() + getVassalPowerBonus();
    }

    public int getBasePowerMax() {
        return FactionsMod.CONFIG.POWER.BASE
                + getMemberPower()
                + (getMutualAllies().size() * FactionsMod.CONFIG.POWER.POWER_PER_ALLY);
    }

    public SimpleInventory getSafe() {
        return safe;
    }

    public DefaultedList<ItemStack> clearSafe() {
        DefaultedList<ItemStack> stacks = this.safe.stacks;
        this.safe = new SimpleInventory(54);
        return stacks;
    }

    public boolean isOpen() {
        return open;
    }

    public void setName(String name) {
        this.name = name;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setDescription(String description) {
        this.description = description;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setMOTD(String motd) {
        this.motd = motd;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setColor(Formatting color) {
        this.color = color.getName();
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setOpen(boolean open) {
        this.open = open;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public int adjustPower(int adjustment) {
        int maxPower = calculateMaxPower();
        int newPower = Math.min(Math.max(0, power + adjustment), maxPower);
        int oldPower = this.power;

        if (newPower == oldPower)
            return 0;

        power = newPower;
        FactionEvents.POWER_CHANGE.invoker().onPowerChange(this, oldPower);
        return Math.abs(newPower - oldPower);
    }

    public int getAdminPower() {
        return adminPower;
    }

    public void addAdminPower(int amount) {
        adminPower += amount;
    }

    public boolean isAdminProtected() {
        return adminProtected;
    }

    public void setAdminProtected(boolean adminProtected) {
        this.adminProtected = adminProtected;
    }

    public int getWealthPower() {
        if (wealthPower == 0) return 0;

        // Calculate decay based on days since last sacrifice
        long now = System.currentTimeMillis();
        long daysSinceLastSacrifice = (now - lastSacrifice) / (1000L * 60 * 60 * 24);
        int decay = (int) (daysSinceLastSacrifice * FactionsMod.CONFIG.POWER.WEALTH.DECAY_PER_DAY);

        return Math.max(0, wealthPower - decay);
    }

    public int addWealthPower(int amount) {
        int maxValue = FactionsMod.CONFIG.POWER.WEALTH.MAX_VALUE;
        int currentPower = getWealthPower();
        int newPower = Math.min(currentPower + amount, maxValue);

        // Store the new power and reset the sacrifice timestamp
        wealthPower = newPower;
        lastSacrifice = System.currentTimeMillis();

        return newPower - currentPower; // Return the actual amount added
    }

    public long getDaysSinceLastSacrifice() {
        if (lastSacrifice == 0) return -1; // Never sacrificed
        long now = System.currentTimeMillis();
        return (now - lastSacrifice) / (1000L * 60 * 60 * 24);
    }

    public int getWarPower() {
        if (warPower == 0) return 0;

        // Calculate decay based on days since last kill
        long now = System.currentTimeMillis();
        long daysSinceLastKill = (now - lastWarKill) / (1000L * 60 * 60 * 24);
        int decay = (int) (daysSinceLastKill * FactionsMod.CONFIG.POWER.WAR.DECAY_PER_DAY);

        return Math.max(0, warPower - decay);
    }

    public int addWarPower(int amount) {
        int maxValue = FactionsMod.CONFIG.POWER.WAR.MAX_VALUE;
        int currentPower = getWarPower();
        int newPower = Math.min(currentPower + amount, maxValue);

        // Store the new power and reset the kill timestamp
        warPower = newPower;
        lastWarKill = System.currentTimeMillis();

        return newPower - currentPower; // Return the actual amount added
    }

    public long getDaysSinceLastWarKill() {
        if (lastWarKill == 0) return -1; // Never killed
        long now = System.currentTimeMillis();
        return (now - lastWarKill) / (1000L * 60 * 60 * 24);
    }

    public int getFamePower() {
        if (famePower == 0) return 0;

        // Calculate decay based on days since last fame gain
        long now = System.currentTimeMillis();
        long daysSinceLastGain = (now - lastFameGain) / (1000L * 60 * 60 * 24);
        int decay = (int) (daysSinceLastGain * FactionsMod.CONFIG.POWER.FAME.DECAY_PER_DAY);

        return Math.max(0, famePower - decay);
    }

    public int addFamePower(int amount) {
        int maxValue = FactionsMod.CONFIG.POWER.FAME.MAX_VALUE;
        int currentPower = getFamePower();
        int newPower = Math.min(currentPower + amount, maxValue);

        // Store the new power and reset the timestamp
        famePower = newPower;
        lastFameGain = System.currentTimeMillis();

        return newPower - currentPower; // Return the actual amount added
    }

    public long getDaysSinceLastFameGain() {
        if (lastFameGain == 0) return -1; // Never gained fame
        long now = System.currentTimeMillis();
        return (now - lastFameGain) / (1000L * 60 * 60 * 24);
    }

    public int getMemberPower() {
        int memberPower = 0;
        for (User user : getUsers()) {
            double multiplier = user.getActivityMultiplier();
            memberPower += (int) (user.getPower() * multiplier);
        }
        return memberPower;
    }

    public List<User> getUsers() {
        return User.getByFaction(id);
    }

    public List<Claim> getClaims() {
        return Claim.getByFaction(id);
    }

    /**
     * Gets the home center point in chunk coordinates.
     * If faction has a home set, uses home location.
     * Otherwise, calculates center of all claims as fallback.
     * @return int array [chunkX, chunkZ] or null if no home and no claims
     */
    public int[] getHomeChunkCenter() {
        // If home is set, use home location (convert block coords to chunk coords)
        if (home != null) {
            return new int[] { (int) Math.floor(home.x / 16), (int) Math.floor(home.z / 16) };
        }

        // Fallback: calculate center of all claims
        List<Claim> claims = getClaims();
        if (claims.isEmpty()) {
            return null;
        }

        int sumX = 0;
        int sumZ = 0;
        for (Claim claim : claims) {
            sumX += claim.x;
            sumZ += claim.z;
        }

        return new int[] { sumX / claims.size(), sumZ / claims.size() };
    }

    /**
     * Gets claims sorted by decay priority (farthest from home center first).
     * Claims in different dimensions than the home are considered highest priority for decay.
     * @return List of claims sorted by decay priority (first = decay first)
     */
    public List<Claim> getClaimsByDecayPriority() {
        List<Claim> claims = getClaims();
        int[] center = getHomeChunkCenter();

        if (center == null || claims.isEmpty()) {
            return claims;
        }

        String homeLevel = home != null ? home.level : claims.get(0).level;
        int centerX = center[0];
        int centerZ = center[1];

        return claims.stream()
                .sorted((a, b) -> {
                    // Claims in different dimensions have highest decay priority
                    boolean aDifferentDim = !a.level.equals(homeLevel);
                    boolean bDifferentDim = !b.level.equals(homeLevel);
                    if (aDifferentDim != bDifferentDim) {
                        return aDifferentDim ? -1 : 1; // Different dimension first
                    }

                    // Sort by distance (farthest first)
                    double distA = Math.sqrt(Math.pow(a.x - centerX, 2) + Math.pow(a.z - centerZ, 2));
                    double distB = Math.sqrt(Math.pow(b.x - centerX, 2) + Math.pow(b.z - centerZ, 2));
                    return Double.compare(distB, distA); // Descending order
                })
                .toList();
    }

    public void removeAllClaims() {
        Claim.getByFaction(id).stream().forEach(Claim::remove);
        FactionEvents.REMOVE_ALL_CLAIMS.invoker().onRemoveAllClaims(this);
    }

    public void addClaim(int x, int z, String level) {
        Claim.add(new Claim(x, z, level, id));
    }

    /**
     * Checks if faction has enough power for its claims and removes excess claims if needed.
     * Claims are removed based on decay priority (farthest from home first).
     * @return List of removed claims, empty if no decay occurred
     */
    public List<Claim> checkAndDecayClaims() {
        // Skip if claim decay is disabled globally
        if (!FactionsMod.CONFIG.POWER.CLAIM_DECAY_ENABLED) {
            return List.of();
        }

        // Skip if admin protected
        if (adminProtected) {
            return List.of();
        }

        List<Claim> claims = getClaims();
        if (claims.isEmpty()) {
            return List.of();
        }

        int currentPower = getPower();
        int claimWeight = FactionsMod.CONFIG.POWER.CLAIM_WEIGHT;
        int requiredPower = claims.size() * claimWeight;

        if (currentPower >= requiredPower) {
            return List.of();
        }

        // Need to remove claims - get them in decay priority order
        List<Claim> sortedClaims = getClaimsByDecayPriority();
        List<Claim> removedClaims = new ArrayList<>();

        for (Claim claim : sortedClaims) {
            if (currentPower >= requiredPower) {
                break;
            }
            claim.remove();
            removedClaims.add(claim);
            requiredPower -= claimWeight;
        }

        return removedClaims;
    }

    public boolean isInvited(UUID playerID) {
        return invites.stream().anyMatch(invite -> invite.equals(playerID));
    }

    public Home getHome() {
        return home;
    }

    public void setHome(Home home) {
        this.home = home;
        FactionEvents.SET_HOME.invoker().onSetHome(this, home);
    }

    public Relationship getRelationship(UUID target) {
        return relationships.stream().filter(rel -> rel.target.equals(target)).findFirst()
                .orElse(new Relationship(target, Relationship.Status.NEUTRAL));
    }

    public Relationship getReverse(Relationship rel) {
        return Faction.get(rel.target).getRelationship(id);
    }

    public boolean isMutualAllies(UUID target) {
        Relationship rel = getRelationship(target);
        return rel.status == Relationship.Status.ALLY
                && getReverse(rel).status == Relationship.Status.ALLY;
    }

    public List<Relationship> getMutualAllies() {
        return relationships.stream().filter(rel -> isMutualAllies(rel.target)).toList();
    }
    
    public boolean isMutualFriendly(UUID target) {
        Relationship rel = getRelationship(target);
        return rel.status == Relationship.Status.FRIENDLY
                && getReverse(rel).status == Relationship.Status.FRIENDLY;
    }

    public List<Relationship> getMutualFriendly() {
        return relationships.stream().filter(rel -> isMutualFriendly(rel.target)).toList();
    }

    public List<Relationship> getFriendlyWith() {
        return relationships.stream().filter(rel -> rel.status == Relationship.Status.FRIENDLY)
                .toList();
    }

    public List<Relationship> getFriendlyOf() {
        return relationships.stream()
                .filter(rel -> getReverse(rel).status == Relationship.Status.FRIENDLY).toList();
    }

    public List<Relationship> getEnemiesWith() {
        return relationships.stream().filter(rel -> rel.status == Relationship.Status.ENEMY)
                .toList();
    }

    public List<Relationship> getEnemiesOf() {
        return relationships.stream()
                .filter(rel -> getReverse(rel).status == Relationship.Status.ENEMY).toList();
    }

    public void removeRelationship(UUID target) {
        relationships = new ArrayList<>(
                relationships.stream().filter(rel -> !rel.target.equals(target)).toList());
    }

    public void setRelationship(Relationship relationship) {
        if (getRelationship(relationship.target) != null) {
            removeRelationship(relationship.target);
        }
        if (relationship.status != Relationship.Status.NEUTRAL
                || !relationship.permissions.isEmpty())
            relationships.add(relationship);
    }

    // Vassal/Overlord methods

    @Nullable
    public UUID getOverlordId() {
        return overlordId;
    }

    @Nullable
    public Faction getOverlord() {
        return overlordId != null ? Faction.get(overlordId) : null;
    }

    public boolean isVassal() {
        return overlordId != null;
    }

    public boolean isOverlord() {
        return !getVassals().isEmpty();
    }

    public List<Faction> getVassals() {
        return STORE.values().stream()
                .filter(f -> id.equals(f.overlordId))
                .toList();
    }

    public boolean canBecomeVassal(Faction overlord) {
        // Can't be vassal if already a vassal
        if (isVassal()) return false;

        // Can't be vassal of yourself
        if (id.equals(overlord.id)) return false;

        // Overlord can't be a vassal themselves (one level only)
        if (overlord.isVassal()) return false;

        // Check if ally requirement is enabled
        if (FactionsMod.CONFIG.VASSAL.REQUIRE_ALLY && !isMutualAllies(overlord.id)) {
            return false;
        }

        return true;
    }

    public boolean becomeVassal(Faction overlord) {
        if (!canBecomeVassal(overlord)) return false;

        this.overlordId = overlord.id;
        return true;
    }

    public void releaseFromOverlord() {
        this.overlordId = null;
    }

    public void releaseAllVassals() {
        for (Faction vassal : getVassals()) {
            vassal.releaseFromOverlord();
        }
    }

    public int getVassalPowerBonus() {
        if (!FactionsMod.CONFIG.VASSAL.ENABLED) return 0;

        int bonus = 0;
        int percent = FactionsMod.CONFIG.VASSAL.POWER_PERCENT;
        for (Faction vassal : getVassals()) {
            // Get vassal's own power (excluding their vassal bonus since one-level only)
            int vassalPower = vassal.getBasePowerMax() + vassal.getWealthPower() + vassal.getWarPower();
            bonus += (vassalPower * percent) / 100;
        }
        return bonus;
    }

    public boolean hasVassalRequest(UUID factionId) {
        return vassalRequests.contains(factionId);
    }

    public void addVassalRequest(UUID factionId) {
        if (!vassalRequests.contains(factionId)) {
            vassalRequests.add(factionId);
        }
    }

    public void removeVassalRequest(UUID factionId) {
        vassalRequests.remove(factionId);
    }

    public void remove() {
        // Release all vassals first (if this faction is an overlord)
        releaseAllVassals();

        // Release from overlord (if this faction is a vassal)
        releaseFromOverlord();

        for (User user : getUsers()) {
            user.leaveFaction();
        }
        for (Relationship rel : relationships) {
            Faction target = Faction.get(rel.target);
            if (target != null) {
                target.removeRelationship(id);
            }
        }
        removeAllClaims();
        STORE.remove(id);
        FactionEvents.DISBAND.invoker().onDisband(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Faction faction = (Faction) o;
        return id.equals(faction.id);
    }

    public static void audit() {
        STORE.values().removeIf((faction) -> {
            if (faction.home != null && !WorldUtils.isValid(faction.home.level)) {
                faction.setHome(null);
            }

            faction.relationships.removeIf((rel) -> Faction.get(rel.target) == null);

            return faction.getUsers().stream().noneMatch((user) -> user.rank == User.Rank.OWNER);
        });
    }

    public static void save() {
        Database.save(Faction.class, STORE.values().stream().toList());
    }

    public void fillBasePower() {
        power = getBasePowerMax();
    }

    public int calculateMaxPower() {
        return getBasePowerMax() + adminPower + FactionsMod.CONFIG.POWER.WEALTH.MAX_VALUE + FactionsMod.CONFIG.POWER.WAR.MAX_VALUE + FactionsMod.CONFIG.POWER.FAME.MAX_VALUE + getVassalPowerBonus();
    }

    public Collection<User> getRelationships() {
        // TO DO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRelationships'");
    }

    // God's Blessings methods

    public long getLastPrayer() {
        return lastPrayer;
    }

    public void setLastPrayer(long timestamp) {
        this.lastPrayer = timestamp;
    }

    public List<ActiveBlessing> getActiveBlessings() {
        // Deserialize from JSON
        List<ActiveBlessing> blessings;
        try {
            blessings = GSON.fromJson(activeBlessingsJson,
                new TypeToken<ArrayList<ActiveBlessing>>(){}.getType());
            if (blessings == null) {
                blessings = new ArrayList<>();
            }
        } catch (Exception e) {
            blessings = new ArrayList<>();
        }

        // Clean up expired blessings
        boolean changed = blessings.removeIf(ActiveBlessing::isExpired);
        if (changed) {
            activeBlessingsJson = GSON.toJson(blessings);
        }

        return new ArrayList<>(blessings);
    }

    public void addActiveBlessing(String godName, String effect, int amplifier, long expiresAt) {
        // Deserialize current blessings
        List<ActiveBlessing> blessings;
        try {
            blessings = GSON.fromJson(activeBlessingsJson,
                new TypeToken<ArrayList<ActiveBlessing>>(){}.getType());
            if (blessings == null) {
                blessings = new ArrayList<>();
            }
        } catch (Exception e) {
            blessings = new ArrayList<>();
        }

        // Remove any existing blessing from the same god (replace mode)
        blessings.removeIf(b -> b.godName.equals(godName));
        blessings.add(new ActiveBlessing(godName, effect, amplifier, expiresAt));

        // Serialize back to JSON
        activeBlessingsJson = GSON.toJson(blessings);
    }

    /**
     * Spend wealth power (for gods system).
     * Unlike addWealthPower, this directly reduces the stored value without resetting decay.
     * @param amount Amount to spend
     * @return true if successful, false if not enough power
     */
    public boolean spendWealthPower(int amount) {
        int currentPower = getWealthPower();
        if (currentPower < amount) {
            return false;
        }
        // Just reduce the stored value, don't touch lastSacrifice
        wealthPower = currentPower - amount;
        return true;
    }

    public static class ActiveBlessing {
        public String godName;
        public String effect;
        public int amplifier;
        public long expiresAt;

        public ActiveBlessing() {}

        public ActiveBlessing(String godName, String effect, int amplifier, long expiresAt) {
            this.godName = godName;
            this.effect = effect;
            this.amplifier = amplifier;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public int getRemainingDurationTicks() {
            long remainingMs = expiresAt - System.currentTimeMillis();
            return (int) Math.max(0, remainingMs / 50); // 50ms per tick
        }
    }
}
