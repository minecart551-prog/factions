package io.icker.factions.client.util;

import java.util.*;

public class FaceMerger {

    public static class Face implements Comparable<Face> {
        public final int plane;
        public final int planeValue;
        public int u1, v1, u2, v2;

        public Face(int plane, int planeValue, int u1, int v1, int u2, int v2) {
            this.plane = plane;
            this.planeValue = planeValue;
            this.u1 = Math.min(u1, u2);
            this.v1 = Math.min(v1, v2);
            this.u2 = Math.max(u1, u2);
            this.v2 = Math.max(v1, v2);
        }

        public int getWidth() { return u2 - u1; }
        public int getHeight() { return v2 - v1; }
        public int getArea() { return getWidth() * getHeight(); }

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

    public static List<Face> extractAndMergeBoundaryFaces(VoxelGrid grid) {
        Set<Face> boundaryFaces = extractBoundaryFaces(grid);
        List<Face> mergedFaces = mergeFacesOnPlanes(new ArrayList<>(boundaryFaces));
        return mergedFaces;
    }

    public static List<Face> extractAndMergeBoundaryFacesFromBoxes(List<int[]> subBoxes) {
        if (subBoxes.isEmpty()) return List.of();
        if (subBoxes.size() == 1) {
            int[] b = subBoxes.get(0);
            List<Face> faces = new ArrayList<>(6);
            faces.add(new Face(0, b[0],     b[1], b[2], b[4]+1, b[5]+1));
            faces.add(new Face(0, b[3] + 1, b[1], b[2], b[4]+1, b[5]+1));
            faces.add(new Face(1, b[1],     b[0], b[2], b[3]+1, b[5]+1));
            faces.add(new Face(1, b[4] + 1, b[0], b[2], b[3]+1, b[5]+1));
            faces.add(new Face(2, b[2],     b[0], b[1], b[3]+1, b[4]+1));
            faces.add(new Face(2, b[5] + 1, b[0], b[1], b[3]+1, b[4]+1));
            return faces;
        }

        List<Face> allFaces = new ArrayList<>();
        for (int i = 0; i < subBoxes.size(); i++) {
            int[] b = subBoxes.get(i);
            int minX = b[0], minY = b[1], minZ = b[2];
            int maxX = b[3], maxY = b[4], maxZ = b[5];

            addSplitFaces(allFaces, subBoxes, i, 0, minX,     false, minY, minZ, maxY+1, maxZ+1);
            addSplitFaces(allFaces, subBoxes, i, 0, maxX + 1, true,  minY, minZ, maxY+1, maxZ+1);
            addSplitFaces(allFaces, subBoxes, i, 1, minY,     false, minX, minZ, maxX+1, maxZ+1);
            addSplitFaces(allFaces, subBoxes, i, 1, maxY + 1, true,  minX, minZ, maxX+1, maxZ+1);
            addSplitFaces(allFaces, subBoxes, i, 2, minZ,     false, minX, minY, maxX+1, maxY+1);
            addSplitFaces(allFaces, subBoxes, i, 2, maxZ + 1, true,  minX, minY, maxX+1, maxY+1);
        }
        return mergeOverlappingFaces(allFaces);
    }

    private static void addSplitFaces(List<Face> out, List<int[]> subBoxes, int skipIdx,
                                       int plane, int planeValue, boolean isMaxSide,
                                       int faceU1, int faceV1, int faceU2, int faceV2) {
        List<int[]> rects = new ArrayList<>();
        rects.add(new int[]{faceU1, faceV1, faceU2, faceV2});

        for (int i = 0; i < subBoxes.size(); i++) {
            if (i == skipIdx) continue;
            int[] c = subBoxes.get(i);

            int[] cProj = getCoveringProjection(c, plane, planeValue, isMaxSide);
            if (cProj == null) continue;

            List<int[]> next = new ArrayList<>();
            for (int[] r : rects) {
                next.addAll(subtractRect(r, cProj));
            }
            rects = next;
            if (rects.isEmpty()) break;
        }

        for (int[] r : rects) {
            if (r[0] < r[2] && r[1] < r[3]) {
                out.add(new Face(plane, planeValue, r[0], r[1], r[2], r[3]));
            }
        }
    }

    private static int[] getCoveringProjection(int[] c, int plane, int planeValue, boolean isMaxSide) {
        int cMinX = c[0], cMinY = c[1], cMinZ = c[2];
        int cMaxX = c[3], cMaxY = c[4], cMaxZ = c[5];

        int adj = isMaxSide ? planeValue : planeValue - 1;

        switch (plane) {
            case 0: {
                if (cMinX > adj || cMaxX < adj) return null;
                return new int[]{cMinY, cMinZ, cMaxY + 1, cMaxZ + 1};
            }
            case 1: {
                if (cMinY > adj || cMaxY < adj) return null;
                return new int[]{cMinX, cMinZ, cMaxX + 1, cMaxZ + 1};
            }
            case 2: {
                if (cMinZ > adj || cMaxZ < adj) return null;
                return new int[]{cMinX, cMinY, cMaxX + 1, cMaxY + 1};
            }
        }
        return null;
    }

    private static List<int[]> subtractRect(int[] R, int[] C) {
        if (R[0] >= C[2] || C[0] >= R[2] || R[1] >= C[3] || C[1] >= R[3]) {
            return List.of(R);
        }

        List<int[]> result = new ArrayList<>(4);

        if (R[0] < C[0]) {
            result.add(new int[]{R[0], R[1], C[0], R[3]});
        }
        if (C[2] < R[2]) {
            result.add(new int[]{C[2], R[1], R[2], R[3]});
        }
        int uStart = Math.max(R[0], C[0]);
        int uEnd = Math.min(R[2], C[2]);
        if (R[1] < C[1]) {
            result.add(new int[]{uStart, R[1], uEnd, C[1]});
        }
        if (C[3] < R[3]) {
            result.add(new int[]{uStart, C[3], uEnd, R[3]});
        }
        return result;
    }

    private static List<Face> mergeOverlappingFaces(List<Face> faces) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < faces.size() && !changed; i++) {
                Face a = faces.get(i);
                for (int j = i + 1; j < faces.size() && !changed; j++) {
                    Face b = faces.get(j);
                    if (a.plane != b.plane || a.planeValue != b.planeValue) continue;
                    if (a.u1 < b.u2 && b.u1 < a.u2 && a.v1 < b.v2 && b.v1 < a.v2) {
                        faces.set(i, new Face(a.plane, a.planeValue,
                            Math.min(a.u1, b.u1), Math.min(a.v1, b.v1),
                            Math.max(a.u2, b.u2), Math.max(a.v2, b.v2)));
                        faces.remove(j);
                        changed = true;
                    }
                }
            }
        }
        return mergeFacesOnPlanes(faces);
    }

    private static Set<Face> extractBoundaryFaces(VoxelGrid grid) {
        Set<Face> faces = new HashSet<>();
        for (int x = grid.getMinX(); x <= grid.getMaxX(); x++) {
            for (int y = grid.getMinY(); y <= grid.getMaxY(); y++) {
                for (int z = grid.getMinZ(); z <= grid.getMaxZ(); z++) {
                    if (!grid.isOccupied(x, y, z)) continue;
                    if (!grid.isOccupied(x - 1, y, z)) faces.add(new Face(0, x, y, z, y + 1, z + 1));
                    if (!grid.isOccupied(x + 1, y, z)) faces.add(new Face(0, x + 1, y, z, y + 1, z + 1));
                    if (!grid.isOccupied(x, y - 1, z)) faces.add(new Face(1, y, x, z, x + 1, z + 1));
                    if (!grid.isOccupied(x, y + 1, z)) faces.add(new Face(1, y + 1, x, z, x + 1, z + 1));
                    if (!grid.isOccupied(x, y, z - 1)) faces.add(new Face(2, z, x, y, x + 1, y + 1));
                    if (!grid.isOccupied(x, y, z + 1)) faces.add(new Face(2, z + 1, x, y, x + 1, y + 1));
                }
            }
        }
        return faces;
    }

    private static List<Face> mergeFacesOnPlanes(List<Face> faces) {
        Map<String, List<Face>> groups = new HashMap<>();
        for (Face face : faces) {
            String key = face.plane + ":" + face.planeValue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(face);
        }
        List<Face> merged = new ArrayList<>();
        for (List<Face> group : groups.values()) {
            merged.addAll(mergeFacesOnSamePlane(group));
        }
        return merged;
    }

    private static List<Face> mergeFacesOnSamePlane(List<Face> faces) {
        if (faces.isEmpty()) return faces;
        faces.sort(Comparator.comparingInt((Face f) -> f.u1).thenComparingInt(f -> f.v1));
        List<Face> working = new ArrayList<>(faces);

        boolean merged = true;
        while (merged && !working.isEmpty()) {
            merged = false;
            List<Face> nextWorking = new ArrayList<>();
            boolean[] processed = new boolean[working.size()];

            for (int i = 0; i < working.size(); i++) {
                if (processed[i]) continue;
                Face current = working.get(i);
                Face mergedFace = null;
                int mergedIdx = -1;

                for (int j = i + 1; j < working.size(); j++) {
                    if (processed[j]) continue;
                    Face candidate = working.get(j);
                    Face newFace = tryMergeFaces(current, candidate);
                    if (newFace != null) {
                        mergedFace = newFace;
                        mergedIdx = j;
                        merged = true;
                        break;
                    }
                }

                if (mergedFace != null) {
                    processed[i] = true;
                    processed[mergedIdx] = true;
                    nextWorking.add(mergedFace);
                } else {
                    processed[i] = true;
                    nextWorking.add(current);
                }
            }
            working = nextWorking;
        }
        return working;
    }

    private static Face tryMergeFaces(Face f1, Face f2) {
        if (f1.plane != f2.plane || f1.planeValue != f2.planeValue) return null;

        if (f1.v1 == f2.v1 && f1.v2 == f2.v2) {
            if (f1.u2 == f2.u1) return new Face(f1.plane, f1.planeValue, f1.u1, f1.v1, f2.u2, f2.v2);
            if (f2.u2 == f1.u1) return new Face(f1.plane, f1.planeValue, f2.u1, f1.v1, f1.u2, f2.v2);
        }

        if (f1.u1 == f2.u1 && f1.u2 == f2.u2) {
            if (f1.v2 == f2.v1) return new Face(f1.plane, f1.planeValue, f1.u1, f1.v1, f2.u2, f2.v2);
            if (f2.v2 == f1.v1) return new Face(f1.plane, f1.planeValue, f2.u1, f2.v1, f1.u2, f1.v2);
        }

        return null;
    }
}
