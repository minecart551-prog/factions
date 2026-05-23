package io.icker.factions.client.event;

import java.util.ArrayList;
import java.util.List;

import io.icker.factions.api.persistents.BlacklistedDimension;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;

/**
 * Manages temporary selection state for the dimension blacklist/whitelist tools.
 * Maintains separate lists for blacklist and whitelist regions.
 */
public class SelectionManager {
    private static final SelectionManager INSTANCE = new SelectionManager();

    private BlockPos firstPos = null;
    private BlockPos secondPos = null;
    
    // Separate lists for blacklist and whitelist
    private List<BlacklistedDimension> blacklistSelections = new ArrayList<>();
    private List<BlacklistedDimension> whitelistSelections = new ArrayList<>();
    
    private String currentWorld = null;
    private boolean deleteMode = false;
    private BlockPos deleteFirstPos = null;
    private BlockPos deleteSecondPos = null;
    private String lastClaimError = null;
    private long lastClaimErrorTime = 0;
    private boolean isWhitelistMode = false;

    private SelectionManager() {
    }

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    public boolean isWhitelistMode() { return isWhitelistMode; }
    public void setWhitelistMode(boolean wl) { this.isWhitelistMode = wl; }

    public void setFirstPos(BlockPos pos, String world) {
        this.firstPos = pos;
        this.secondPos = null;
        this.currentWorld = world;
        this.deleteMode = false;
    }

    public void setSecondPos(BlockPos pos) {
        this.secondPos = pos;
        if (this.firstPos != null && this.currentWorld != null) {
            // Create the new dimension but DON'T add to SelectionManager yet
            // The server will validate and then broadcast back via sync
            BlacklistedDimension newDim = new BlacklistedDimension(
                    this.currentWorld,
                    this.firstPos.getX(), this.firstPos.getY(), this.firstPos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    "Region_" + System.currentTimeMillis()
            );

            // Get the current list (from server-synced data) and add the new dimension
            List<BlacklistedDimension> currentList = isWhitelistMode ? 
                new ArrayList<>(whitelistSelections) : new ArrayList<>(blacklistSelections);
            currentList.add(newDim);
            
            // Send to server for validation - server will broadcast back if accepted
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(
                currentList, isWhitelistMode);
            
            this.firstPos = null;
            this.secondPos = null;
        }
    }

    public void clearSelection() {
        this.firstPos = null;
        this.secondPos = null;
        this.currentWorld = null;
        this.deleteMode = false;
        this.deleteFirstPos = null;
        this.deleteSecondPos = null;
    }

    public void enterDeleteMode(BlockPos pos, String world) {
        if (!deleteMode) {
            this.deleteMode = true;
            this.deleteFirstPos = pos;
            this.deleteSecondPos = null;
            this.currentWorld = world;
            this.firstPos = null;
            this.secondPos = null;
        } else if (this.deleteFirstPos != null && this.deleteSecondPos == null) {
            this.deleteSecondPos = pos;
            // Delete from BOTH lists
            confirmDelete();
        }
    }

    public void confirmDelete() {
        if (this.deleteMode && this.deleteFirstPos != null && this.deleteSecondPos != null && this.currentWorld != null) {
            int delMinX = Math.min(this.deleteFirstPos.getX(), this.deleteSecondPos.getX());
            int delMaxX = Math.max(this.deleteFirstPos.getX(), this.deleteSecondPos.getX());
            int delMinY = Math.min(this.deleteFirstPos.getY(), this.deleteSecondPos.getY());
            int delMaxY = Math.max(this.deleteFirstPos.getY(), this.deleteSecondPos.getY());
            int delMinZ = Math.min(this.deleteFirstPos.getZ(), this.deleteSecondPos.getZ());
            int delMaxZ = Math.max(this.deleteFirstPos.getZ(), this.deleteSecondPos.getZ());

            // Compute the subtracted lists but DON'T modify SelectionManager yet
            // Server will validate and broadcast back via sync
            List<BlacklistedDimension> newBlacklist = subtractFromList(blacklistSelections, delMinX, delMaxX, delMinY, delMaxY, delMinZ, delMaxZ);
            List<BlacklistedDimension> newWhitelist = subtractFromList(whitelistSelections, delMinX, delMaxX, delMinY, delMaxY, delMinZ, delMaxZ);
            
            // Send to server for validation - server will broadcast back if accepted
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(newBlacklist, false);
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(newWhitelist, true);
            
            resetDeleteMode();
        }
    }
    
    private List<BlacklistedDimension> subtractFromList(List<BlacklistedDimension> list, 
            int delMinX, int delMaxX, int delMinY, int delMaxY, int delMinZ, int delMaxZ) {
        List<BlacklistedDimension> result = new ArrayList<>();
        for (BlacklistedDimension dim : list) {
            if (dim.world.equals(this.currentWorld)) {
                if (dim.minX <= delMaxX && dim.maxX >= delMinX &&
                    dim.minY <= delMaxY && dim.maxY >= delMinY &&
                    dim.minZ <= delMaxZ && dim.maxZ >= delMinZ) {
                    result.addAll(subtractBox(dim, delMinX, delMaxX, delMinY, delMaxY, delMinZ, delMaxZ));
                } else {
                    result.add(dim);
                }
            } else {
                result.add(dim);
            }
        }
        return result;
    }

    private List<BlacklistedDimension> subtractBox(BlacklistedDimension region, int delMinX, int delMaxX, int delMinY, int delMaxY, int delMinZ, int delMaxZ) {
        List<BlacklistedDimension> result = new ArrayList<>();
        if (region.minX < delMinX) result.add(new BlacklistedDimension(region.world, region.minX, region.minY, region.minZ, delMinX - 1, region.maxY, region.maxZ, region.name + "_left"));
        if (region.maxX > delMaxX) result.add(new BlacklistedDimension(region.world, delMaxX + 1, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ, region.name + "_right"));
        if (region.minY < delMinY) result.add(new BlacklistedDimension(region.world, Math.max(region.minX, delMinX), region.minY, region.minZ, Math.min(region.maxX, delMaxX), delMinY - 1, region.maxZ, region.name + "_bottom"));
        if (region.maxY > delMaxY) result.add(new BlacklistedDimension(region.world, Math.max(region.minX, delMinX), delMaxY + 1, region.minZ, Math.min(region.maxX, delMaxX), region.maxY, region.maxZ, region.name + "_top"));
        if (region.minZ < delMinZ) result.add(new BlacklistedDimension(region.world, Math.max(region.minX, delMinX), Math.max(region.minY, delMinY), region.minZ, Math.min(region.maxX, delMaxX), Math.min(region.maxY, delMaxY), delMinZ - 1, region.name + "_front"));
        if (region.maxZ > delMaxZ) result.add(new BlacklistedDimension(region.world, Math.max(region.minX, delMinX), Math.max(region.minY, delMinY), delMaxZ + 1, Math.min(region.maxX, delMaxX), Math.min(region.maxY, delMaxY), region.maxZ, region.name + "_back"));
        return result;
    }

    public void resetDeleteMode() {
        this.deleteMode = false;
        this.deleteFirstPos = null;
        this.deleteSecondPos = null;
    }

    /**
     * Returns all regions for rendering (both blacklist and whitelist combined)
     */
    public List<BlacklistedDimension> getAllSelections() {
        List<BlacklistedDimension> all = new ArrayList<>(blacklistSelections);
        all.addAll(whitelistSelections);
        return all;
    }

    public List<BlacklistedDimension> getPendingSelections() {
        return new ArrayList<>(isWhitelistMode ? whitelistSelections : blacklistSelections);
    }

    public List<BlacklistedDimension> getBlacklistSelections() {
        return new ArrayList<>(blacklistSelections);
    }
    
    public List<BlacklistedDimension> getWhitelistSelections() {
        return new ArrayList<>(whitelistSelections);
    }

    public void setBlacklistSelections(List<BlacklistedDimension> selections) {
        this.blacklistSelections = new ArrayList<>(selections);
    }
    
    public void setWhitelistSelections(List<BlacklistedDimension> selections) {
        this.whitelistSelections = new ArrayList<>(selections);
    }

    public BlockPos getFirstPos() { return this.firstPos; }
    public BlockPos getSecondPos() { return this.secondPos; }
    public BlockPos getDeleteFirstPos() { return this.deleteFirstPos; }
    public BlockPos getDeleteSecondPos() { return this.deleteSecondPos; }
    public boolean isDeleteMode() { return this.deleteMode; }

    public String getLastClaimError() {
        if (lastClaimError != null && System.currentTimeMillis() - lastClaimErrorTime < 3000) return lastClaimError;
        lastClaimError = null;
        return null;
    }
}