package io.icker.factions.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.icker.factions.api.persistents.BlacklistedDimension;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.network.DimensionFilePacket;

/**
 * JSON serialization, gzip/chunk helpers, and merge logic for
 * /f dimension export and /f dimension import.
 */
public final class RegionTransfer {
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RegionTransfer() {
    }

    public static class ExportData {
        public int version = VERSION;
        public String faction;
        public long exportedAt;
        public List<BlacklistedDimension> blacklist = new ArrayList<>();
        public List<BlacklistedDimension> whitelist = new ArrayList<>();
    }

    public static class ImportStats {
        public int blacklistAdded;
        public int whitelistAdded;
        public int croppedToClaims;
        public int croppedToOpposite;
        public int duplicatesSkipped;
        public int dropped;
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null) return null;
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replace("..", "");
        // Allow letters, digits, underscore, hyphen, and dots (extension only)
        name = name.replaceAll("[^a-zA-Z0-9_.-]", "");
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isEmpty()) return null;

        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String base = name.substring(0, dot);
            String ext = name.substring(dot + 1);
            ext = ext.replaceAll("[^a-zA-Z0-9]", "");
            if (base.isEmpty()) return null;
            if (base.length() > 64) base = base.substring(0, 64);
            if (!ext.equalsIgnoreCase("json")) {
                return base + ".json";
            }
            return base + ".json";
        }

        if (name.length() > 64) name = name.substring(0, 64);
        return name + ".json";
    }

    public static String defaultFilename(Faction faction) {
        String base = sanitizeFilename(faction != null ? faction.getName() : null);
        return base != null ? base : "regions.json";
    }

    public static String buildJson(Faction faction) {
        ExportData data = new ExportData();
        data.faction = faction.getName();
        data.exportedAt = System.currentTimeMillis();
        data.blacklist = new ArrayList<>(faction.dimensionBlacklist);
        data.whitelist = new ArrayList<>(faction.dimensionWhitelist);
        return GSON.toJson(data);
    }

    public static ExportData parseJson(String json) {
        ExportData data = GSON.fromJson(json, ExportData.class);
        if (data == null) throw new IllegalArgumentException("Empty export data");
        if (data.version < 1 || data.version > VERSION) {
            throw new IllegalArgumentException("Unsupported export version: " + data.version);
        }
        if (data.blacklist == null) data.blacklist = new ArrayList<>();
        if (data.whitelist == null) data.whitelist = new ArrayList<>();
        data.blacklist.removeIf(d -> d == null || d.world == null || d.world.isEmpty());
        data.whitelist.removeIf(d -> d == null || d.world == null || d.world.isEmpty());
        return data;
    }

    public static byte[] gzip(String json) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compress export data", e);
        }
    }

    public static String gunzip(byte[] compressed) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = gzip.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decompress transfer data", e);
        }
    }

    /**
     * Split a compressed payload into chunk packets. Always at least 1 chunk.
     */
    public static List<DimensionFilePacket> chunkPayload(UUID sessionId, String filename, byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            List<DimensionFilePacket> single = new ArrayList<>();
            single.add(new DimensionFilePacket(sessionId, 1, 0, filename, new byte[0]));
            return single;
        }

        int total = (compressed.length + DimensionFilePacket.CHUNK_DATA_SIZE - 1) / DimensionFilePacket.CHUNK_DATA_SIZE;
        if (total > DimensionFilePacket.MAX_CHUNKS) {
            throw new IllegalStateException("Export data too large (" + total + " chunks)");
        }

        List<DimensionFilePacket> chunks = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int offset = i * DimensionFilePacket.CHUNK_DATA_SIZE;
            int len = Math.min(DimensionFilePacket.CHUNK_DATA_SIZE, compressed.length - offset);
            byte[] slice = new byte[len];
            System.arraycopy(compressed, offset, slice, 0, len);
            chunks.add(new DimensionFilePacket(sessionId, total, i, filename, slice));
        }
        return chunks;
    }

    /**
     * Merge imported regions into the faction:
     * crop to claims, crop against current opposite-type regions (import always loses),
     * skip exact duplicates, append survivors.
     */
    public static ImportStats mergeImport(Faction faction, ExportData imported) {
        faction.loadDimensionBlacklistFromJson();
        faction.loadDimensionWhitelistFromJson();

        ImportStats stats = new ImportStats();
        List<Claim> claims = Claim.getByFaction(faction.getID());

        List<BlacklistedDimension> addedBl = new ArrayList<>();
        for (BlacklistedDimension dim : imported.blacklist) {
            processImported(dim, claims, faction.dimensionWhitelist, null,
                    faction.dimensionBlacklist, addedBl, stats, true);
        }

        List<BlacklistedDimension> oppositeForWl = new ArrayList<>(faction.dimensionBlacklist);
        oppositeForWl.addAll(addedBl);

        List<BlacklistedDimension> addedWl = new ArrayList<>();
        for (BlacklistedDimension dim : imported.whitelist) {
            processImported(dim, claims, oppositeForWl, null,
                    faction.dimensionWhitelist, addedWl, stats, false);
        }

        faction.dimensionBlacklist.addAll(addedBl);
        faction.dimensionWhitelist.addAll(addedWl);
        stats.blacklistAdded = addedBl.size();
        stats.whitelistAdded = addedWl.size();
        return stats;
    }

    private static void processImported(BlacklistedDimension dim, List<Claim> claims,
            List<BlacklistedDimension> oppositeCurrent, List<BlacklistedDimension> oppositeExtra,
            List<BlacklistedDimension> sameTypeCurrent, List<BlacklistedDimension> addedSameType,
            ImportStats stats, boolean isBlacklist) {
        if (dim == null || dim.world == null || dim.world.isEmpty()) {
            stats.dropped++;
            return;
        }

        List<BlacklistedDimension> carved = RegionClipper.carveToClaims(dim, claims);
        if (carved.isEmpty()) {
            stats.croppedToClaims++;
            stats.dropped++;
            return;
        }
        if (!isSameAsOriginal(carved, dim)) {
            stats.croppedToClaims++;
        }

        List<BlacklistedDimension> opposite = new ArrayList<>(oppositeCurrent);
        if (oppositeExtra != null) opposite.addAll(oppositeExtra);

        boolean anyOppositeCut = false;
        for (BlacklistedDimension frag : carved) {
            List<BlacklistedDimension> pieces = RegionClipper.subtractAll(frag, opposite);
            if (pieces.isEmpty()) {
                anyOppositeCut = true;
                stats.dropped++;
                continue;
            }
            if (!isSameAsOriginal(pieces, frag)) {
                anyOppositeCut = true;
            }
            for (BlacklistedDimension piece : pieces) {
                if (RegionClipper.containsExact(sameTypeCurrent, piece)
                        || RegionClipper.containsExact(addedSameType, piece)) {
                    stats.duplicatesSkipped++;
                    continue;
                }
                addedSameType.add(piece);
            }
        }
        if (anyOppositeCut) stats.croppedToOpposite++;
    }

    private static boolean isSameAsOriginal(List<BlacklistedDimension> pieces, BlacklistedDimension original) {
        if (pieces.size() != 1) return false;
        return RegionClipper.isSameRegion(pieces.get(0), original);
    }
}
