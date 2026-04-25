package io.icker.factions.api.persistents;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a 3D region that is blacklisted for block placement/breaking in a faction
 */
public class BlacklistedDimension {
    @SerializedName("world")
    public String world; // World dimension key (e.g., "minecraft:overworld")

    @SerializedName("minX")
    public int minX;

    @SerializedName("minY")
    public int minY;

    @SerializedName("minZ")
    public int minZ;

    @SerializedName("maxX")
    public int maxX;

    @SerializedName("maxY")
    public int maxY;

    @SerializedName("maxZ")
    public int maxZ;

    @SerializedName("name")
    public String name;

    public BlacklistedDimension() {
    }

    public BlacklistedDimension(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String name) {
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.name = name;
    }

    /**
     * Check if a position is within this dimension
     */
    public boolean contains(String world, int x, int y, int z) {
        if (this.world == null || !this.world.equals(world)) {
            return false;
        }
        return x >= this.minX && x <= this.maxX &&
               y >= this.minY && y <= this.maxY &&
               z >= this.minZ && z <= this.maxZ;
    }

    /**
     * Check if this dimension overlaps with another
     */
    public boolean overlaps(BlacklistedDimension other) {
        if (this.world == null || other.world == null || !this.world.equals(other.world)) {
            return false;
        }
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY &&
               this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    /**
     * Merge this dimension with another (union of the two boxes)
     */
    public BlacklistedDimension merge(BlacklistedDimension other) {
        if (this.world == null || other.world == null || !this.world.equals(other.world)) {
            return this; // Can't merge from different worlds or if world is null
        }
        int newMinX = Math.min(this.minX, other.minX);
        int newMinY = Math.min(this.minY, other.minY);
        int newMinZ = Math.min(this.minZ, other.minZ);
        int newMaxX = Math.max(this.maxX, other.maxX);
        int newMaxY = Math.max(this.maxY, other.maxY);
        int newMaxZ = Math.max(this.maxZ, other.maxZ);
        return new BlacklistedDimension(world, newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ, "Merged");
    }

    /**
     * Get the volume of this dimension
     */
    public long getVolume() {
        return (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    @Override
    public String toString() {
        return name + " (" + world + "): [" + minX + "," + minY + "," + minZ + "] to [" + maxX + "," + maxY + "," + maxZ + "]";
    }
}
