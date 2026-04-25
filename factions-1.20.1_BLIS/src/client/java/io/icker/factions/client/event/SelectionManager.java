package io.icker.factions.client.event;

import java.util.ArrayList;
import java.util.List;

import io.icker.factions.api.persistents.BlacklistedDimension;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;

/**
 * Manages temporary selection state for the dimension blacklist tool
 * Tracks the current and pending selections during editing
 */
public class SelectionManager {
    private static final SelectionManager INSTANCE = new SelectionManager();

    private BlockPos firstPos = null;
    private BlockPos secondPos = null;
    private List<BlacklistedDimension> pendingSelections = new ArrayList<>();
    private String currentWorld = null;
    private boolean deleteMode = false;
    private int deleteModeCounter = 0; // Track right-clicks for delete confirmation

    private SelectionManager() {
    }

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set the first corner of a new box
     */
    public void setFirstPos(BlockPos pos, String world) {
        this.firstPos = pos;
        this.secondPos = null; // Reset second pos
        this.currentWorld = world;
        this.deleteMode = false;
        this.deleteModeCounter = 0;
    }

    /**
     * Set the second corner and create a box
     */
    public void setSecondPos(BlockPos pos) {
        this.secondPos = pos;
        if (this.firstPos != null && this.currentWorld != null) {
            // Create new dimension from the two points
            io.icker.factions.api.persistents.BlacklistedDimension newDimension = new io.icker.factions.api.persistents.BlacklistedDimension(
                    this.currentWorld,
                    this.firstPos.getX(),
                    this.firstPos.getY(),
                    this.firstPos.getZ(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    "Region_" + System.currentTimeMillis()
            );

            // Merge with overlapping existing selections
            List<io.icker.factions.api.persistents.BlacklistedDimension> toRemove = new ArrayList<>();
            for (io.icker.factions.api.persistents.BlacklistedDimension existing : this.pendingSelections) {
                try {
                    if (existing != null && existing.overlaps(newDimension)) {
                        newDimension = newDimension.merge(existing);
                        toRemove.add(existing);
                    }
                } catch (NullPointerException e) {
                    // Skip invalid regions
                    org.slf4j.LoggerFactory.getLogger("FactionsClient")
                        .warn("Skipping invalid region during overlap check", e);
                    toRemove.add(existing);
                }
            }
            this.pendingSelections.removeAll(toRemove);
            this.pendingSelections.add(newDimension);

            // Auto-commit the updated pending selections to the server
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(this.pendingSelections);

            // Keep displaying the region (don't reset)
        }
    }
    
    /**
     * Clear selection and start a new one
     */
    public void clearSelection() {
        this.firstPos = null;
        this.secondPos = null;
        this.currentWorld = null;
        this.deleteMode = false;
        this.deleteModeCounter = 0;
    }

    /**
     * Enter delete mode - next right-click will delete the confirmed region
     */
    public void enterDeleteMode(BlockPos pos, String world) {
        if (!this.deleteMode) {
            this.deleteMode = true;
            this.deleteModeCounter = 1;
            this.firstPos = pos;
            this.currentWorld = world;
        } else {
            this.deleteModeCounter++;
        }
    }

    /**
     * Confirm delete and remove region
     */
    public void confirmDelete(BlockPos pos) {
        if (this.deleteMode && this.deleteModeCounter >= 2) {
            // Find and remove the region at this position
            for (int i = this.pendingSelections.size() - 1; i >= 0; i--) {
                io.icker.factions.api.persistents.BlacklistedDimension dim = this.pendingSelections.get(i);
                if (dim.contains(currentWorld, pos.getX(), pos.getY(), pos.getZ())) {
                    this.pendingSelections.remove(i);
                    break;
                }
            }
            // Auto-commit the updated pending selections to the server
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(this.pendingSelections);
            resetDeleteMode();
        }
    }

    /**
     * Reset delete mode
     */
    public void resetDeleteMode() {
        this.deleteMode = false;
        this.deleteModeCounter = 0;
        this.firstPos = null;
        this.secondPos = null;
    }

    /**
     * Get all pending selections for rendering
     */
    public List<BlacklistedDimension> getPendingSelections() {
        return new ArrayList<>(this.pendingSelections);
    }

    /**
     * Get currently highlighted position (for rendering)
     */
    public BlockPos getFirstPos() {
        return this.firstPos;
    }

    public BlockPos getSecondPos() {
        return this.secondPos;
    }

    /**
     * Check if we're in delete mode
     */
    public boolean isDeleteMode() {
        return this.deleteMode;
    }

    /**
     * Load existing faction dimension blacklist into pending selections
     */
    public void loadFromFaction() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                io.icker.factions.api.persistents.User user = io.icker.factions.api.persistents.User.get(mc.player.getUuid());
                if (user != null) {
                    io.icker.factions.api.persistents.Faction faction = user.getFaction();
                    if (faction != null && !faction.dimensionBlacklist.isEmpty()) {
                        this.pendingSelections.clear();
                        // Filter out any invalid regions (null world or other null fields)
                        for (io.icker.factions.api.persistents.BlacklistedDimension dim : faction.dimensionBlacklist) {
                            if (dim != null && dim.world != null) {
                                this.pendingSelections.add(dim);
                            }
                        }
                        org.slf4j.LoggerFactory.getLogger("FactionsClient")
                            .info("Loaded {} valid dimension blacklists from faction (skipped {} invalid)", 
                                this.pendingSelections.size(), faction.dimensionBlacklist.size() - this.pendingSelections.size());
                    }
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("FactionsClient").error("Error loading faction dimension blacklist", e);
        }
    }

    /**
     * Get the region that will be deleted (for rendering)
     */
    public io.icker.factions.api.persistents.BlacklistedDimension getDeleteTargetRegion() {
        if (!this.deleteMode || this.firstPos == null || this.currentWorld == null) {
            return null;
        }
        // Find the region at the delete target position
        for (io.icker.factions.api.persistents.BlacklistedDimension dim : this.pendingSelections) {
            if (dim.contains(currentWorld, firstPos.getX(), firstPos.getY(), firstPos.getZ())) {
                return dim;
            }
        }
        return null;
    }

    /**
     * Check if delete is confirmed (ready to delete on next right-click)
     */
    public boolean isDeleteConfirmed() {
        return this.deleteMode && this.deleteModeCounter >= 1;
    }
}
