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
    private BlockPos deleteFirstPos = null;  // First position for delete box
    private BlockPos deleteSecondPos = null; // Second position for delete box

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

            // Add the new region as-is without merging - keep them as separate connected shapes
            this.pendingSelections.add(newDimension);

            // Auto-commit the updated pending selections to the server
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(this.pendingSelections);

            // Clear the selection state so the merged shape is visible immediately
            this.firstPos = null;
            this.secondPos = null;
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
        this.deleteFirstPos = null;
        this.deleteSecondPos = null;
    }

    /**
     * Enter delete mode - first right-click sets first corner
     */
    public void enterDeleteMode(BlockPos pos, String world) {
        if (!deleteMode) {
            // First right-click in delete mode - clear selection state so it doesn't interfere
            this.deleteMode = true;
            this.deleteFirstPos = pos;
            this.deleteSecondPos = null;
            this.currentWorld = world;
            this.firstPos = null;  // Clear selection so old boxes don't interfere
            this.secondPos = null;
        } else if (this.deleteFirstPos != null && this.deleteSecondPos == null) {
            // Second right-click - set second corner and delete
            this.deleteSecondPos = pos;
            confirmDelete();
        }
    }

    /**
     * Confirm delete - subtract the delete box from overlapping shapes
     */
    public void confirmDelete() {
        if (this.deleteMode && this.deleteFirstPos != null && this.deleteSecondPos != null && this.currentWorld != null) {
            int delMinX = Math.min(this.deleteFirstPos.getX(), this.deleteSecondPos.getX());
            int delMaxX = Math.max(this.deleteFirstPos.getX(), this.deleteSecondPos.getX());
            int delMinY = Math.min(this.deleteFirstPos.getY(), this.deleteSecondPos.getY());
            int delMaxY = Math.max(this.deleteFirstPos.getY(), this.deleteSecondPos.getY());
            int delMinZ = Math.min(this.deleteFirstPos.getZ(), this.deleteSecondPos.getZ());
            int delMaxZ = Math.max(this.deleteFirstPos.getZ(), this.deleteSecondPos.getZ());
            
            // For each pending selection, subtract the delete box
            List<BlacklistedDimension> updatedSelections = new ArrayList<>();
            for (BlacklistedDimension dim : this.pendingSelections) {
                if (dim.world.equals(this.currentWorld)) {
                    // Check if this region overlaps with delete box
                    if (dim.minX <= delMaxX && dim.maxX >= delMinX &&
                        dim.minY <= delMaxY && dim.maxY >= delMinY &&
                        dim.minZ <= delMaxZ && dim.maxZ >= delMinZ) {
                        
                        // Region overlaps - need to subtract the delete box
                        // Generate the resulting boxes after subtraction
                        List<BlacklistedDimension> subtractedBoxes = subtractBox(dim, delMinX, delMaxX, delMinY, delMaxY, delMinZ, delMaxZ);
                        updatedSelections.addAll(subtractedBoxes);
                    } else {
                        // No overlap - keep it
                        updatedSelections.add(dim);
                    }
                } else {
                    // Different world - keep it
                    updatedSelections.add(dim);
                }
            }
            
            this.pendingSelections = updatedSelections;
            
            // Auto-commit the updated pending selections to the server
            io.icker.factions.client.network.DimensionClientNetworkHandler.commitDimensions(this.pendingSelections);
            
            resetDeleteMode();
        }
    }
    
    /**
     * Subtract a delete box from a region, returning resulting boxes
     * A box subtraction can result in 0-6 boxes (if no overlap, returns empty list)
     */
    private List<BlacklistedDimension> subtractBox(BlacklistedDimension region, 
                                                   int delMinX, int delMaxX, 
                                                   int delMinY, int delMaxY,
                                                   int delMinZ, int delMaxZ) {
        List<BlacklistedDimension> result = new ArrayList<>();
        
        // Left box (x: region.minX to delMinX)
        if (region.minX < delMinX) {
            result.add(new BlacklistedDimension(
                region.world,
                region.minX, region.minY, region.minZ,
                delMinX - 1, region.maxY, region.maxZ,
                region.name + "_left"
            ));
        }
        
        // Right box (x: delMaxX+1 to region.maxX)
        if (region.maxX > delMaxX) {
            result.add(new BlacklistedDimension(
                region.world,
                delMaxX + 1, region.minY, region.minZ,
                region.maxX, region.maxY, region.maxZ,
                region.name + "_right"
            ));
        }
        
        // Bottom box (y: region.minY to delMinY, x: delMinX to delMaxX)
        if (region.minY < delMinY) {
            result.add(new BlacklistedDimension(
                region.world,
                Math.max(region.minX, delMinX), region.minY, region.minZ,
                Math.min(region.maxX, delMaxX), delMinY - 1, region.maxZ,
                region.name + "_bottom"
            ));
        }
        
        // Top box (y: delMaxY+1 to region.maxY, x: delMinX to delMaxX)
        if (region.maxY > delMaxY) {
            result.add(new BlacklistedDimension(
                region.world,
                Math.max(region.minX, delMinX), delMaxY + 1, region.minZ,
                Math.min(region.maxX, delMaxX), region.maxY, region.maxZ,
                region.name + "_top"
            ));
        }
        
        // Front box (z: region.minZ to delMinZ, x: delMinX to delMaxX, y: delMinY to delMaxY)
        if (region.minZ < delMinZ) {
            result.add(new BlacklistedDimension(
                region.world,
                Math.max(region.minX, delMinX), Math.max(region.minY, delMinY), region.minZ,
                Math.min(region.maxX, delMaxX), Math.min(region.maxY, delMaxY), delMinZ - 1,
                region.name + "_front"
            ));
        }
        
        // Back box (z: delMaxZ+1 to region.maxZ, x: delMinX to delMaxX, y: delMinY to delMaxY)
        if (region.maxZ > delMaxZ) {
            result.add(new BlacklistedDimension(
                region.world,
                Math.max(region.minX, delMinX), Math.max(region.minY, delMinY), delMaxZ + 1,
                Math.min(region.maxX, delMaxX), Math.min(region.maxY, delMaxY), region.maxZ,
                region.name + "_back"
            ));
        }
        
        return result;
    }

    /**
     * Reset delete mode
     */
    public void resetDeleteMode() {
        this.deleteMode = false;
        this.deleteFirstPos = null;
        this.deleteSecondPos = null;
    }

    /**
     * Get all pending selections for rendering
     */
    public List<BlacklistedDimension> getPendingSelections() {
        return new ArrayList<>(this.pendingSelections);
    }
    
    /**
     * Set pending selections (called from server sync)
     */
    public void setPendingSelections(List<BlacklistedDimension> selections) {
        this.pendingSelections = new ArrayList<>(selections);
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
     * Get delete mode first position (for rendering red box preview)
     */
    public BlockPos getDeleteFirstPos() {
        return this.deleteFirstPos;
    }

    /**
     * Get delete mode second position (for rendering red box preview)
     */
    public BlockPos getDeleteSecondPos() {
        return this.deleteSecondPos;
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
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail if faction data unavailable
        }
    }
}
