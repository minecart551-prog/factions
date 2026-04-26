package io.icker.factions.client.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decomposes a voxel grid into minimal axis-aligned rectangular boxes
 */
public class BoxDecomposer {
    /**
     * Represents a decomposed box with min/max coordinates
     */
    public static class DecomposedBox {
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public DecomposedBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        @Override
        public String toString() {
            return String.format("Box[%d,%d,%d to %d,%d,%d]", minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /**
     * Decompose a voxel grid into a list of axis-aligned boxes
     * Uses a greedy algorithm: for each unvisited voxel, find the largest box and mark as visited
     */
    public static List<DecomposedBox> decompose(VoxelGrid grid) {
        List<DecomposedBox> boxes = new ArrayList<>();
        
        if (grid.isEmpty()) {
            return boxes;
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> occupied = grid.getOccupiedVoxels();

        // For each unvisited occupied voxel, find the largest box starting from it
        for (long voxel : occupied) {
            if (visited.contains(voxel)) {
                continue;
            }

            int startX = VoxelGrid.decodeX(voxel);
            int startY = VoxelGrid.decodeY(voxel);
            int startZ = VoxelGrid.decodeZ(voxel);

            // Find the largest box starting from this voxel
            DecomposedBox box = findLargestBox(grid, visited, startX, startY, startZ);
            if (box != null) {
                boxes.add(box);
                markBoxAsVisited(visited, box);
            }
        }

        return boxes;
    }

    /**
     * Find the largest axis-aligned box starting from (startX, startY, startZ)
     * that only contains unvisited occupied voxels
     */
    private static DecomposedBox findLargestBox(VoxelGrid grid, Set<Long> visited, int startX, int startY, int startZ) {
        // Start with a 1x1x1 box and expand in each direction
        int maxX = startX;
        int maxY = startY;
        int maxZ = startZ;

        // Expand in Z direction
        while (maxZ + 1 <= grid.getMaxZ() && canExpandZ(grid, visited, startX, maxX, startY, maxY, maxZ + 1)) {
            maxZ++;
        }

        // Expand in Y direction
        while (maxY + 1 <= grid.getMaxY() && canExpandY(grid, visited, startX, maxX, startY + 1, maxZ)) {
            maxY++;
        }

        // Expand in X direction
        while (maxX + 1 <= grid.getMaxX() && canExpandX(grid, visited, startX + 1, startY, maxY, maxZ)) {
            maxX++;
        }

        // Try to expand further in other directions (optimize box size)
        boolean expanded = true;
        while (expanded) {
            expanded = false;
            
            if (maxZ + 1 <= grid.getMaxZ() && canExpandZ(grid, visited, startX, maxX, startY, maxY, maxZ + 1)) {
                maxZ++;
                expanded = true;
            }
            
            if (maxY + 1 <= grid.getMaxY() && canExpandY(grid, visited, startX, maxX, maxY + 1, maxZ)) {
                maxY++;
                expanded = true;
            }
            
            if (maxX + 1 <= grid.getMaxX() && canExpandX(grid, visited, maxX + 1, startY, maxY, maxZ)) {
                maxX++;
                expanded = true;
            }
        }

        return new DecomposedBox(startX, startY, startZ, maxX, maxY, maxZ);
    }

    /**
     * Check if we can expand in X direction
     */
    private static boolean canExpandX(VoxelGrid grid, Set<Long> visited, int newMaxX, int startY, int maxY, int maxZ) {
        for (int y = startY; y <= maxY; y++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!grid.isOccupied(newMaxX, y, z) || visited.contains(VoxelGrid.encode(newMaxX, y, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if we can expand in Y direction
     */
    private static boolean canExpandY(VoxelGrid grid, Set<Long> visited, int startX, int maxX, int newMaxY, int maxZ) {
        for (int x = startX; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!grid.isOccupied(x, newMaxY, z) || visited.contains(VoxelGrid.encode(x, newMaxY, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if we can expand in Z direction
     */
    private static boolean canExpandZ(VoxelGrid grid, Set<Long> visited, int startX, int maxX, int startY, int maxY, int newMaxZ) {
        for (int x = startX; x <= maxX; x++) {
            for (int y = startY; y <= maxY; y++) {
                if (!grid.isOccupied(x, y, newMaxZ) || visited.contains(VoxelGrid.encode(x, y, newMaxZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Mark all voxels in a box as visited
     */
    private static void markBoxAsVisited(Set<Long> visited, DecomposedBox box) {
        for (int x = box.minX; x <= box.maxX; x++) {
            for (int y = box.minY; y <= box.maxY; y++) {
                for (int z = box.minZ; z <= box.maxZ; z++) {
                    visited.add(VoxelGrid.encode(x, y, z));
                }
            }
        }
    }

    /**
     * Utility method to encode coordinates
     */
    private static long encode(int x, int y, int z) {
        return ((long)(x & 0xFFFFF) << 40) | ((long)(y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
