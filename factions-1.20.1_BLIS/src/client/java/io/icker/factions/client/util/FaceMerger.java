package io.icker.factions.client.util;

import java.util.*;

/**
 * Extracts and merges boundary faces from a voxel grid.
 * Creates a unified outer shell outline without internal voxel details.
 */
public class FaceMerger {
    
    /**
     * Represents a rectangular face on a plane
     */
    public static class Face implements Comparable<Face> {
        public final int plane;  // Which plane this face is on (X, Y, or Z)
        public final int planeValue;  // The constant coordinate on that plane
        public int u1, v1, u2, v2;  // Rectangle bounds on the plane
        
        public Face(int plane, int planeValue, int u1, int v1, int u2, int v2) {
            this.plane = plane;
            this.planeValue = planeValue;
            this.u1 = Math.min(u1, u2);
            this.v1 = Math.min(v1, v2);
            this.u2 = Math.max(u1, u2);
            this.v2 = Math.max(v1, v2);
        }
        
        public int getWidth() {
            return u2 - u1;
        }
        
        public int getHeight() {
            return v2 - v1;
        }
        
        public int getArea() {
            return getWidth() * getHeight();
        }
        
        @Override
        public int compareTo(Face other) {
            if (this.plane != other.plane) return this.plane - other.plane;
            if (this.planeValue != other.planeValue) return this.planeValue - other.planeValue;
            if (this.u1 != other.u1) return this.u1 - other.u1;
            return this.v1 - other.v1;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Face)) return false;
            Face f = (Face) o;
            return plane == f.plane && planeValue == f.planeValue && 
                   u1 == f.u1 && v1 == f.v1 && u2 == f.u2 && v2 == f.v2;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(plane, planeValue, u1, v1, u2, v2);
        }
    }
    
    /**
     * Extract boundary faces from voxel grid and merge adjacent ones
     */
    public static List<Face> extractAndMergeBoundaryFaces(VoxelGrid grid) {
        // Step 1: Extract all boundary faces
        Set<Face> boundaryFaces = extractBoundaryFaces(grid);
        
        // Step 2: Group by plane and merge adjacent faces
        List<Face> mergedFaces = mergeFacesOnPlanes(new ArrayList<>(boundaryFaces));
        
        return mergedFaces;
    }
    
    /**
     * Extract all boundary faces (where voxel is occupied and adjacent is empty)
     */
    private static Set<Face> extractBoundaryFaces(VoxelGrid grid) {
        Set<Face> faces = new HashSet<>();
        
        for (int x = grid.getMinX(); x <= grid.getMaxX(); x++) {
            for (int y = grid.getMinY(); y <= grid.getMaxY(); y++) {
                for (int z = grid.getMinZ(); z <= grid.getMaxZ(); z++) {
                    if (!grid.isOccupied(x, y, z)) continue;
                    
                    // Check all 6 directions for boundary faces
                    // X-plane faces (yz rectangles)
                    if (!grid.isOccupied(x - 1, y, z)) {
                        faces.add(new Face(0, x, y, z, y + 1, z + 1));  // Left face
                    }
                    if (!grid.isOccupied(x + 1, y, z)) {
                        faces.add(new Face(0, x + 1, y, z, y + 1, z + 1));  // Right face
                    }
                    
                    // Y-plane faces (xz rectangles)
                    if (!grid.isOccupied(x, y - 1, z)) {
                        faces.add(new Face(1, y, x, z, x + 1, z + 1));  // Bottom face
                    }
                    if (!grid.isOccupied(x, y + 1, z)) {
                        faces.add(new Face(1, y + 1, x, z, x + 1, z + 1));  // Top face
                    }
                    
                    // Z-plane faces (xy rectangles)
                    if (!grid.isOccupied(x, y, z - 1)) {
                        faces.add(new Face(2, z, x, y, x + 1, y + 1));  // Front face
                    }
                    if (!grid.isOccupied(x, y, z + 1)) {
                        faces.add(new Face(2, z + 1, x, y, x + 1, y + 1));  // Back face
                    }
                }
            }
        }
        
        return faces;
    }
    
    /**
     * Merge adjacent faces on the same plane
     */
    private static List<Face> mergeFacesOnPlanes(List<Face> faces) {
        // Group faces by plane and plane value
        Map<String, List<Face>> groups = new HashMap<>();
        for (Face face : faces) {
            String key = face.plane + ":" + face.planeValue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(face);
        }
        
        List<Face> merged = new ArrayList<>();
        
        // Merge each group
        for (List<Face> group : groups.values()) {
            merged.addAll(mergeFacesOnSamePlane(group));
        }
        
        return merged;
    }
    
    /**
     * Merge faces that are on the same plane and adjacent
     */
    private static List<Face> mergeFacesOnSamePlane(List<Face> faces) {
        if (faces.isEmpty()) return faces;
        
        // Sort faces for easier merging
        faces.sort(Comparator.comparingInt((Face f) -> f.u1).thenComparingInt(f -> f.v1));
        
        List<Face> result = new ArrayList<>();
        List<Face> working = new ArrayList<>(faces);
        
        // Repeatedly try to merge faces until no more merges are possible
        boolean merged = true;
        while (merged && !working.isEmpty()) {
            merged = false;
            List<Face> nextWorking = new ArrayList<>();
            boolean[] processed = new boolean[working.size()];
            
            for (int i = 0; i < working.size(); i++) {
                if (processed[i]) continue;
                
                Face current = working.get(i);
                Face merged_face = null;
                int merged_idx = -1;
                
                // Try to merge with other faces
                for (int j = i + 1; j < working.size(); j++) {
                    if (processed[j]) continue;
                    
                    Face candidate = working.get(j);
                    Face newFace = tryMergeFaces(current, candidate);
                    
                    if (newFace != null) {
                        merged_face = newFace;
                        merged_idx = j;
                        merged = true;
                        break;
                    }
                }
                
                if (merged_face != null) {
                    processed[i] = true;
                    processed[merged_idx] = true;
                    nextWorking.add(merged_face);
                } else {
                    processed[i] = true;
                    nextWorking.add(current);
                }
            }
            
            working = nextWorking;
        }
        
        return working;
    }
    
    /**
     * Try to merge two faces if they are adjacent
     */
    private static Face tryMergeFaces(Face f1, Face f2) {
        // Must be on same plane and plane value
        if (f1.plane != f2.plane || f1.planeValue != f2.planeValue) {
            return null;
        }
        
        // Try to merge horizontally (same v range, adjacent u range)
        if (f1.v1 == f2.v1 && f1.v2 == f2.v2) {
            if (f1.u2 == f2.u1) {
                return new Face(f1.plane, f1.planeValue, f1.u1, f1.v1, f2.u2, f2.v2);
            }
            if (f2.u2 == f1.u1) {
                return new Face(f1.plane, f1.planeValue, f2.u1, f1.v1, f1.u2, f2.v2);
            }
        }
        
        // Try to merge vertically (same u range, adjacent v range)
        if (f1.u1 == f2.u1 && f1.u2 == f2.u2) {
            if (f1.v2 == f2.v1) {
                return new Face(f1.plane, f1.planeValue, f1.u1, f1.v1, f2.u2, f2.v2);
            }
            if (f2.v2 == f1.v1) {
                return new Face(f1.plane, f1.planeValue, f2.u1, f2.v1, f1.u2, f1.v2);
            }
        }
        
        return null;
    }
}
