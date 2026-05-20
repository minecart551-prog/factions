package io.icker.factions.client.render;

import io.icker.factions.client.event.SelectionManager;
import io.icker.factions.client.util.VoxelGrid;
import io.icker.factions.client.util.FaceMerger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.List;

/**
 * Renders dimension blacklist/whitelist boxes as semi-transparent filled quads
 * (outer surface only, no edge lines).
 * Uses GPU VertexBuffer caching + frustum culling for high performance.
 * Geometry is built once and only rebuilt when data changes.
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistRenderer {
    private static final DimensionBlacklistRenderer INSTANCE = new DimensionBlacklistRenderer();
    
    private final FactionsRenderingContext ctx = new FactionsRenderingContext();
    
    // Frustum for distance-based culling
    private static Frustum currentFrustum = null;
    
    // Cache: the voxel grid is rebuilt only when data changes, then stored in GPU buffer
    private boolean needsRebuild = true;
    
    // Maximum render distance in blocks (halved from default render distance)
    private static final int MAX_RENDER_DISTANCE = 64; // ~4 chunks
    
    // Color constants
    private static final Color BLACKLIST_GREEN = new Color(0, 255, 0);
    private static final Color DELETE_RED = new Color(255, 0, 0);
    private static final Color SELECTION_GREEN = new Color(0, 255, 0);
    private static final int FILL_ALPHA = 45;

    private DimensionBlacklistRenderer() {
    }

    public static DimensionBlacklistRenderer getInstance() {
        return INSTANCE;
    }

    public void setFrustum(Frustum frustum) {
        currentFrustum = frustum;
    }

    public void markNeedsRebuild() {
        this.needsRebuild = true;
    }

    /**
     * Check if a region box is close enough to render (distance culling).
     * Uses closest-point-to-box distance so large shapes don't vanish when far from center.
     */
    private boolean isVisibleInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (currentFrustum == null) return true;
        
        // Hard distance limit using closest point on box to camera
        // This way, if you're standing inside a huge shape, distance is 0 and it renders.
        double camX = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().x;
        double camZ = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().z;
        
        // Closest point on the box to the camera (clamp camera position to box bounds)
        double closestX = Math.max(minX, Math.min(camX, maxX));
        double closestZ = Math.max(minZ, Math.min(camZ, maxZ));
        
        double distX = Math.abs(camX - closestX);
        double distZ = Math.abs(camZ - closestZ);
        if (distX > MAX_RENDER_DISTANCE || distZ > MAX_RENDER_DISTANCE) {
            return false;
        }
        
        return currentFrustum.isVisible(new Box(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * Rebuild all geometry and upload to GPU VertexBuffer.
     * Called only when data changes.
     */
    private void rebuild(double camX, double camY, double camZ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        SelectionManager selectionMgr = SelectionManager.getInstance();
        List<io.icker.factions.api.persistents.BlacklistedDimension> allRegions = selectionMgr.getPendingSelections();

        ctx.reset(camX, camY, camZ);
        ctx.beginBatch();

        // Render all blacklist regions as filled merged boundary quads (if visible)
        if (!allRegions.isEmpty()) {
            VoxelGrid grid = new VoxelGrid();
            for (io.icker.factions.api.persistents.BlacklistedDimension region : allRegions) {
                grid.fillBox(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ);
            }
            
            if (!grid.isEmpty()) {
                // Culling: skip entire grid if too far away / not visible
                if (isVisibleInFrustum(grid.getMinX(), grid.getMinY(), grid.getMinZ(), 
                                        grid.getMaxX(), grid.getMaxY(), grid.getMaxZ())) {
                    List<FaceMerger.Face> boundaryFaces = FaceMerger.extractAndMergeBoundaryFaces(grid);
                    for (FaceMerger.Face face : boundaryFaces) {
                        renderFaceAsFilledQuad(ctx, face, BLACKLIST_GREEN, FILL_ALPHA);
                    }
                }
            }
        }

        // Render current selection box (always, even if far away - it's the player's current edit)
        BlockPos firstPos = selectionMgr.getFirstPos();
        BlockPos secondPos = selectionMgr.getSecondPos();

        if (firstPos != null) {
            if (secondPos != null) {
                int minX = Math.min(firstPos.getX(), secondPos.getX());
                int minY = Math.min(firstPos.getY(), secondPos.getY());
                int minZ = Math.min(firstPos.getZ(), secondPos.getZ());
                int maxX = Math.max(firstPos.getX(), secondPos.getX()) + 1;
                int maxY = Math.max(firstPos.getY(), secondPos.getY()) + 1;
                int maxZ = Math.max(firstPos.getZ(), secondPos.getZ()) + 1;
                renderBoxAsFilledQuads(ctx, minX, minY, minZ, maxX, maxY, maxZ, SELECTION_GREEN, FILL_ALPHA);
            } else {
                int x = firstPos.getX();
                int y = firstPos.getY();
                int z = firstPos.getZ();
                renderBoxAsFilledQuads(ctx, x, y, z, x + 1, y + 1, z + 1, SELECTION_GREEN, FILL_ALPHA);
            }
        }

        // Render delete mode red box
        if (selectionMgr.isDeleteMode()) {
            BlockPos delFirstPos = selectionMgr.getDeleteFirstPos();
            BlockPos delSecondPos = selectionMgr.getDeleteSecondPos();
            
            if (delFirstPos != null) {
                if (delSecondPos != null) {
                    int minX = Math.min(delFirstPos.getX(), delSecondPos.getX());
                    int minY = Math.min(delFirstPos.getY(), delSecondPos.getY());
                    int minZ = Math.min(delFirstPos.getZ(), delSecondPos.getZ());
                    int maxX = Math.max(delFirstPos.getX(), delSecondPos.getX()) + 1;
                    int maxY = Math.max(delFirstPos.getY(), delSecondPos.getY()) + 1;
                    int maxZ = Math.max(delFirstPos.getZ(), delSecondPos.getZ()) + 1;
                    renderBoxAsFilledQuads(ctx, minX, minY, minZ, maxX, maxY, maxZ, DELETE_RED, FILL_ALPHA);
                } else {
                    int x = delFirstPos.getX();
                    int y = delFirstPos.getY();
                    int z = delFirstPos.getZ();
                    renderBoxAsFilledQuads(ctx, x, y, z, x + 1, y + 1, z + 1, DELETE_RED, FILL_ALPHA);
                }
            }
        }

        ctx.endBatch();
        needsRebuild = false;
    }

    /**
     * Render a single merged face as a filled quad via the rendering context
     */
    private void renderFaceAsFilledQuad(FactionsRenderingContext ctx, FaceMerger.Face face, Color color, int alpha) {
        double x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4;
        
        if (face.plane == 0) {  // X-plane (constant X, variable Y and Z)
            double px = face.planeValue;
            double y_min = face.u1;
            double y_max = face.u2;
            double z_min = face.v1;
            double z_max = face.v2;
            
            x1 = x2 = x3 = x4 = px;
            y1 = y2 = y_min;
            y3 = y4 = y_max;
            z1 = z4 = z_min;
            z2 = z3 = z_max;
        } else if (face.plane == 1) {  // Y-plane (constant Y, variable X and Z)
            double py = face.planeValue;
            double x_min = face.u1;
            double x_max = face.u2;
            double z_min = face.v1;
            double z_max = face.v2;
            
            y1 = y2 = y3 = y4 = py;
            x1 = x2 = x_min;
            x3 = x4 = x_max;
            z1 = z4 = z_min;
            z2 = z3 = z_max;
        } else {  // Z-plane (constant Z, variable X and Y)
            double pz = face.planeValue;
            double x_min = face.u1;
            double x_max = face.u2;
            double y_min = face.v1;
            double y_max = face.v2;
            
            z1 = z2 = z3 = z4 = pz;
            x1 = x2 = x_min;
            x3 = x4 = x_max;
            y1 = y4 = y_min;
            y2 = y3 = y_max;
        }
        
        ctx.drawFilledQuad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, color, alpha);
    }

    /**
     * Render a box as 6 filled quads via the rendering context
     */
    private void renderBoxAsFilledQuads(FactionsRenderingContext ctx,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         Color color, int alpha) {
        boolean sameX = minX == maxX;
        boolean sameY = minY == maxY;
        boolean sameZ = minZ == maxZ;

        // Bottom face (y = minY)
        if (!sameY) {
            ctx.drawFilledQuad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
        }
        // Top face (y = maxY)
        if (!sameY) {
            ctx.drawFilledQuad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color, alpha);
        }
        // North face (z = minZ)
        if (!sameZ) {
            ctx.drawFilledQuad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color, alpha);
        }
        // South face (z = maxZ)
        if (!sameZ) {
            ctx.drawFilledQuad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        }
        // West face (x = minX)
        if (!sameX) {
            ctx.drawFilledQuad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color, alpha);
        }
        // East face (x = maxX)
        if (!sameX) {
            ctx.drawFilledQuad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color, alpha);
        }
    }

    /**
     * Main render method called every frame by the item handler.
     * Uses cached GPU VertexBuffer - only rebuilds when data changes.
     */
    public void render(double camX, double camY, double camZ, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getMainHandStack().getItem() != io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL) {
            return;
        }

        // Rebuild geometry if data changed or this is the first render
        if (needsRebuild) {
            rebuild(camX, camY, camZ);
        }

        // Draw cached GPU geometry
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        
        matrices.push();
        matrices.translate(
            ctx.getBaseX() - camX,
            ctx.getBaseY() - camY,
            ctx.getBaseZ() - camZ
        );
        ctx.doDrawing(matrices);
        matrices.pop();
        
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * Clean up GPU resources
     */
    public void cleanup() {
        ctx.cleanup();
    }
}