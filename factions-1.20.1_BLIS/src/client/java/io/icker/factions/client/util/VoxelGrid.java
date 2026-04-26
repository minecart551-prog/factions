package io.icker.factions.client.util;

import java.util.HashSet;
import java.util.Set;

/**
 * A 3D grid for tracking occupied voxels (blocks)
 */
public class VoxelGrid {
    private Set<Long> occupiedVoxels = new HashSet<>();
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private boolean hasBounds = false;

    /**
     * Encode a 3D coordinate into a single long for storage
     * Uses 21 bits per coordinate (20 bits + 1 sign bit)
     */
    public static long encode(int x, int y, int z) {
        // Mask to 20 bits to allow both positive and negative coordinates
        return ((long)(x & 0xFFFFF) << 40) | ((long)(y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    /**
     * Decode a long back into x coordinate with sign extension
     */
    public static int decodeX(long encoded) {
        int value = (int)((encoded >> 40) & 0xFFFFF);
        // Sign-extend from 20 bits to 32 bits
        if ((value & 0x80000) != 0) {
            value |= 0xFFF00000;
        }
        return value;
    }

    /**
     * Decode a long back into y coordinate with sign extension
     */
    public static int decodeY(long encoded) {
        int value = (int)((encoded >> 20) & 0xFFFFF);
        // Sign-extend from 20 bits to 32 bits
        if ((value & 0x80000) != 0) {
            value |= 0xFFF00000;
        }
        return value;
    }

    /**
     * Decode a long back into z coordinate with sign extension
     */
    public static int decodeZ(long encoded) {
        int value = (int)(encoded & 0xFFFFF);
        // Sign-extend from 20 bits to 32 bits
        if ((value & 0x80000) != 0) {
            value |= 0xFFF00000;
        }
        return value;
    }

    /**
     * Mark a block as occupied
     */
    public void setOccupied(int x, int y, int z) {
        occupiedVoxels.add(encode(x, y, z));
        
        if (!hasBounds) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
            hasBounds = true;
        } else {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
    }

    /**
     * Check if a block is occupied
     */
    public boolean isOccupied(int x, int y, int z) {
        return occupiedVoxels.contains(encode(x, y, z));
    }

    /**
     * Mark a block as unoccupied (remove it)
     */
    public void setEmpty(int x, int y, int z) {
        occupiedVoxels.remove(encode(x, y, z));
    }

    /**
     * Get the minimum X coordinate
     */
    public int getMinX() {
        return hasBounds ? minX : 0;
    }

    /**
     * Get the maximum X coordinate
     */
    public int getMaxX() {
        return hasBounds ? maxX : 0;
    }

    /**
     * Get the minimum Y coordinate
     */
    public int getMinY() {
        return hasBounds ? minY : 0;
    }

    /**
     * Get the maximum Y coordinate
     */
    public int getMaxY() {
        return hasBounds ? maxY : 0;
    }

    /**
     * Get the minimum Z coordinate
     */
    public int getMinZ() {
        return hasBounds ? minZ : 0;
    }

    /**
     * Get the maximum Z coordinate
     */
    public int getMaxZ() {
        return hasBounds ? maxZ : 0;
    }

    /**
     * Check if grid is empty
     */
    public boolean isEmpty() {
        return occupiedVoxels.isEmpty();
    }

    /**
     * Get the set of occupied voxels (as encoded longs)
     */
    public Set<Long> getOccupiedVoxels() {
        return occupiedVoxels;
    }

    /**
     * Fill all voxels in a rectangular region
     */
    public void fillBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    setOccupied(x, y, z);
                }
            }
        }
    }
}
