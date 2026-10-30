package io.icker.factions.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Home;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Formatting;

/**
 * Snapshot of every faction's claims and homes, sent to clients so map mods
 * (e.g. VoxelMap) can draw faction areas on their full screen map.
 *
 * <p>Layout of the root NBT:
 * <pre>
 * {
 *   "factions": [
 *     {
 *       "name": String,
 *       "description": String,
 *       "color": int (0xRRGGBB, -1 when the faction has no color),
 *       "homeLevel": String, "homeX": double, "homeZ": double   // absent when no home
 *       "claims": [
 *         { "level": String, "chunkX": int[], "chunkZ": int[] }  // one entry per dimension
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
public class ClaimSyncPacket {
    public static NbtCompound build() {
        NbtList factionList = new NbtList();
        for (Faction faction : Faction.all()) {
            Home home = faction.getHome();

            Map<String, List<Claim>> claimsByLevel = new HashMap<>();
            for (Claim claim : faction.getClaims()) {
                if (claim == null || claim.level == null) continue;
                claimsByLevel.computeIfAbsent(claim.level, level -> new ArrayList<>()).add(claim);
            }

            if (home == null && claimsByLevel.isEmpty()) continue;

            NbtCompound factionTag = new NbtCompound();

            String name = faction.getName();
            factionTag.putString("name", name != null ? name : "???");

            String description = faction.getDescription();
            factionTag.putString("description", description != null ? description : "");

            Formatting color = faction.getColor();
            Integer rgb = color != null ? color.getColorValue() : null;
            factionTag.putInt("color", rgb != null ? rgb : -1);

            if (home != null && home.level != null) {
                factionTag.putString("homeLevel", home.level);
                factionTag.putDouble("homeX", home.x);
                factionTag.putDouble("homeZ", home.z);
            }

            NbtList levelList = new NbtList();
            for (Map.Entry<String, List<Claim>> entry : claimsByLevel.entrySet()) {
                List<Claim> claims = entry.getValue();
                int[] xs = new int[claims.size()];
                int[] zs = new int[claims.size()];
                for (int i = 0; i < claims.size(); i++) {
                    xs[i] = claims.get(i).x;
                    zs[i] = claims.get(i).z;
                }

                NbtCompound levelTag = new NbtCompound();
                levelTag.putString("level", entry.getKey());
                levelTag.putIntArray("chunkX", xs);
                levelTag.putIntArray("chunkZ", zs);
                levelList.add(levelTag);
            }
            factionTag.put("claims", levelList);

            factionList.add(factionTag);
        }

        NbtCompound root = new NbtCompound();
        root.put("factions", factionList);
        return root;
    }
}
