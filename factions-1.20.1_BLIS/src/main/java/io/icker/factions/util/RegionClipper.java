package io.icker.factions.util;

import java.util.ArrayList;
import java.util.List;

import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.api.persistents.Claim;

/**
 * Geometry helpers for cropping dimension regions to faction claims and
 * subtracting one axis-aligned box from another.
 */
public final class RegionClipper {
    private RegionClipper() {
    }

    public static boolean isSameRegion(BlacklistedDimension a, BlacklistedDimension b) {
        if (a == null || b == null) return false;
        if (a.world == null || b.world == null || !a.world.equals(b.world)) return false;
        return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ
                && a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }

    public static boolean containsExact(List<BlacklistedDimension> list, BlacklistedDimension dim) {
        if (list == null || dim == null) return false;
        for (BlacklistedDimension existing : list) {
            if (isSameRegion(existing, dim)) return true;
        }
        return false;
    }

    /**
     * Carve out all unclaimed chunks from a region by splitting it into fragments
     * that only cover claimed chunks. Uses per-chunk box subtraction.
     */
    public static List<BlacklistedDimension> carveToClaims(BlacklistedDimension dim, List<Claim> claims) {
        List<BlacklistedDimension> fragments = new ArrayList<>();
        if (dim == null || dim.world == null || dim.world.isEmpty()) return fragments;
        fragments.add(dim);

        if (claims == null || claims.isEmpty()) return new ArrayList<>();

        int minChunkX = Math.floorDiv(dim.minX, 16);
        int maxChunkX = Math.floorDiv(dim.maxX, 16);
        int minChunkZ = Math.floorDiv(dim.minZ, 16);
        int maxChunkZ = Math.floorDiv(dim.maxZ, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX && !fragments.isEmpty(); chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ && !fragments.isEmpty(); chunkZ++) {
                boolean isClaimed = false;
                for (Claim claim : claims) {
                    if (claim.x == chunkX && claim.z == chunkZ && claim.level.equals(dim.world)) {
                        isClaimed = true;
                        break;
                    }
                }
                if (isClaimed) continue;

                int chunkMinX = chunkX * 16;
                int chunkMaxX = (chunkX + 1) * 16 - 1;
                int chunkMinZ = chunkZ * 16;
                int chunkMaxZ = (chunkZ + 1) * 16 - 1;

                List<BlacklistedDimension> newFragments = new ArrayList<>();
                for (BlacklistedDimension frag : fragments) {
                    if (frag.world.equals(dim.world)
                            && frag.minX <= chunkMaxX && frag.maxX >= chunkMinX
                            && frag.minZ <= chunkMaxZ && frag.maxZ >= chunkMinZ) {
                        newFragments.addAll(subtractBox(frag,
                                new BlacklistedDimension(frag.world,
                                        chunkMinX, frag.minY, chunkMinZ,
                                        chunkMaxX, frag.maxY, chunkMaxZ,
                                        frag.name)));
                    } else {
                        newFragments.add(frag);
                    }
                }
                fragments = newFragments;
            }
        }

        return fragments;
    }

    /**
     * Subtract cut from dim (3D AABB). Returns up to 6 non-empty boxes.
     * If they do not overlap in the same world, returns dim unchanged.
     */
    public static List<BlacklistedDimension> subtractBox(BlacklistedDimension dim, BlacklistedDimension cut) {
        List<BlacklistedDimension> result = new ArrayList<>();
        if (dim == null || cut == null) {
            if (dim != null) result.add(dim);
            return result;
        }
        if (dim.world == null || cut.world == null || !dim.world.equals(cut.world) || !dim.overlaps(cut)) {
            result.add(dim);
            return result;
        }

        String world = dim.world;
        String name = dim.name;

        // Left / right X strips (full Y/Z of dim)
        if (dim.minX < cut.minX) {
            result.add(new BlacklistedDimension(world,
                    dim.minX, dim.minY, dim.minZ,
                    cut.minX - 1, dim.maxY, dim.maxZ, name));
        }
        if (dim.maxX > cut.maxX) {
            result.add(new BlacklistedDimension(world,
                    cut.maxX + 1, dim.minY, dim.minZ,
                    dim.maxX, dim.maxY, dim.maxZ, name));
        }

        int x1 = Math.max(dim.minX, cut.minX);
        int x2 = Math.min(dim.maxX, cut.maxX);
        if (x1 > x2) return result;

        // Front / back Z strips (X clamped to overlap, full Y of dim)
        if (dim.minZ < cut.minZ) {
            result.add(new BlacklistedDimension(world,
                    x1, dim.minY, dim.minZ,
                    x2, dim.maxY, cut.minZ - 1, name));
        }
        if (dim.maxZ > cut.maxZ) {
            result.add(new BlacklistedDimension(world,
                    x1, dim.minY, cut.maxZ + 1,
                    x2, dim.maxY, dim.maxZ, name));
        }

        int z1 = Math.max(dim.minZ, cut.minZ);
        int z2 = Math.min(dim.maxZ, cut.maxZ);
        if (z1 > z2) return result;

        // Bottom / top Y strips (X/Z clamped to overlap)
        if (dim.minY < cut.minY) {
            result.add(new BlacklistedDimension(world,
                    x1, dim.minY, z1,
                    x2, cut.minY - 1, z2, name));
        }
        if (dim.maxY > cut.maxY) {
            result.add(new BlacklistedDimension(world,
                    x1, cut.maxY + 1, z1,
                    x2, dim.maxY, z2, name));
        }

        return result;
    }

    /**
     * Subtract every box in cuts from dim, cascading fragments.
     */
    public static List<BlacklistedDimension> subtractAll(BlacklistedDimension dim, List<BlacklistedDimension> cuts) {
        List<BlacklistedDimension> fragments = new ArrayList<>();
        if (dim == null) return fragments;
        fragments.add(dim);
        if (cuts == null || cuts.isEmpty()) return fragments;

        for (BlacklistedDimension cut : cuts) {
            if (cut == null || cut.world == null || dim.world == null || !cut.world.equals(dim.world)) continue;
            List<BlacklistedDimension> next = new ArrayList<>();
            for (BlacklistedDimension frag : fragments) {
                next.addAll(subtractBox(frag, cut));
            }
            fragments = next;
            if (fragments.isEmpty()) break;
        }
        return fragments;
    }
}
