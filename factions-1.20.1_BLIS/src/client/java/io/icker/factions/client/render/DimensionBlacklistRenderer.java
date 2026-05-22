package io.icker.factions.client.render;

import io.icker.factions.client.event.SelectionManager;
import io.icker.factions.client.util.VoxelGrid;
import io.icker.factions.client.util.FaceMerger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Renders dimension blacklist/whitelist boxes as semi-transparent filled quads
 * (outer surface only, no edge lines).
 * Double-buffered async building - geometry builds on background thread, no freeze.
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistRenderer {
    private static final DimensionBlacklistRenderer INSTANCE = new DimensionBlacklistRenderer();

    // Double-buffered rendering contexts for async building
    private final FactionsRenderingContext[] ctxPool = new FactionsRenderingContext[] {
        new FactionsRenderingContext(),
        new FactionsRenderingContext()
    };
    private int activeContextIndex = 0;
    private boolean contextReady = false;

    // Frustum for distance-based culling
    private static Frustum currentFrustum = null;

    // Background executor for async building
    private static final Executor BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor(
        r -> { Thread t = new Thread(r, "Factions-Build-Thread"); t.setDaemon(true); return t; }
    );

    private volatile boolean needsRebuild = true;
    private volatile CompletableFuture<Void> buildingFuture = null;

    // Maximum render distance in blocks
    private static final int MAX_RENDER_DISTANCE = 64;

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
     */
    private boolean isVisibleInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (currentFrustum == null) return true;
        double camX = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().x;
        double camZ = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().z;
        double closestX = Math.max(minX, Math.min(camX, maxX));
        double closestZ = Math.max(minZ, Math.min(camZ, maxZ));
        double distX = Math.abs(camX - closestX);
        double distZ = Math.abs(camZ - closestZ);
        if (distX > MAX_RENDER_DISTANCE || distZ > MAX_RENDER_DISTANCE) return false;
        return currentFrustum.isVisible(new Box(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * Build geometry on a background context. Called from async thread.
     */
    private void buildAsync(FactionsRenderingContext buildCtx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        SelectionManager selectionMgr = SelectionManager.getInstance();
        List<io.icker.factions.api.persistents.BlacklistedDimension> allRegions = selectionMgr.getPendingSelections();

        // Use camera position for culling
        double camX = mc.gameRenderer.getCamera().getPos().x;
        double camY = mc.gameRenderer.getCamera().getPos().y;
        double camZ = mc.gameRenderer.getCamera().getPos().z;

        buildCtx.reset(camX, camY, camZ);
        buildCtx.beginBatch();

        if (!allRegions.isEmpty()) {
            VoxelGrid grid = new VoxelGrid();
            for (io.icker.factions.api.persistents.BlacklistedDimension region : allRegions) {
                grid.fillBox(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ);
            }
            if (!grid.isEmpty() && isVisibleInFrustum(grid.getMinX(), grid.getMinY(), grid.getMinZ(),
                                                       grid.getMaxX(), grid.getMaxY(), grid.getMaxZ())) {
                List<FaceMerger.Face> boundaryFaces = FaceMerger.extractAndMergeBoundaryFaces(grid);
                for (FaceMerger.Face face : boundaryFaces) {
                    renderFaceAsFilledQuad(buildCtx, face, BLACKLIST_GREEN, FILL_ALPHA);
                }
            }
        }

        // Selection box
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
                renderBoxAsFilledQuads(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, SELECTION_GREEN, FILL_ALPHA);
            } else {
                int x = firstPos.getX();
                int y = firstPos.getY();
                int z = firstPos.getZ();
                renderBoxAsFilledQuads(buildCtx, x, y, z, x + 1, y + 1, z + 1, SELECTION_GREEN, FILL_ALPHA);
            }
        }

        // Delete mode box
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
                    renderBoxAsFilledQuads(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, DELETE_RED, FILL_ALPHA);
                } else {
                    int x = delFirstPos.getX();
                    int y = delFirstPos.getY();
                    int z = delFirstPos.getZ();
                    renderBoxAsFilledQuads(buildCtx, x, y, z, x + 1, y + 1, z + 1, DELETE_RED, FILL_ALPHA);
                }
            }
        }

        // endBatch() must run on render thread (GL context), done in the future completion
    }

    /**
     * Render a single merged face as a filled quad via the rendering context
     */
    private void renderFaceAsFilledQuad(FactionsRenderingContext ctx, FaceMerger.Face face, Color color, int alpha) {
        double x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4;
        if (face.plane == 0) {
            double px = face.planeValue;
            double y_min = face.u1, y_max = face.u2;
            double z_min = face.v1, z_max = face.v2;
            x1 = x2 = x3 = x4 = px;
            y1 = y2 = y_min; y3 = y4 = y_max;
            z1 = z4 = z_min; z2 = z3 = z_max;
        } else if (face.plane == 1) {
            double py = face.planeValue;
            double x_min = face.u1, x_max = face.u2;
            double z_min = face.v1, z_max = face.v2;
            y1 = y2 = y3 = y4 = py;
            x1 = x2 = x_min; x3 = x4 = x_max;
            z1 = z4 = z_min; z2 = z3 = z_max;
        } else {
            double pz = face.planeValue;
            double x_min = face.u1, x_max = face.u2;
            double y_min = face.v1, y_max = face.v2;
            z1 = z2 = z3 = z4 = pz;
            x1 = x2 = x_min; x3 = x4 = x_max;
            y1 = y4 = y_min; y2 = y3 = y_max;
        }
        ctx.drawFilledQuad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, color, alpha);
    }

    private void renderBoxAsFilledQuads(FactionsRenderingContext ctx,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         Color color, int alpha) {
        boolean sameX = minX == maxX, sameY = minY == maxY, sameZ = minZ == maxZ;
        if (!sameY) {
            ctx.drawFilledQuad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
            ctx.drawFilledQuad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color, alpha);
        }
        if (!sameZ) {
            ctx.drawFilledQuad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color, alpha);
            ctx.drawFilledQuad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        }
        if (!sameX) {
            ctx.drawFilledQuad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color, alpha);
            ctx.drawFilledQuad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color, alpha);
        }
    }

    /**
     * Main render method called every frame. Draws from active context.
     * Async rebuilds happen on background thread.
     */
    public void render(double camX, double camY, double camZ, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getMainHandStack().getItem() != io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL) return;

        // Start async build if needed (and not already building)
        if (needsRebuild && (buildingFuture == null || buildingFuture.isDone())) {
            needsRebuild = false;
            int buildIndex = (activeContextIndex + 1) % 2;
            FactionsRenderingContext buildCtx = ctxPool[buildIndex];

            buildingFuture = CompletableFuture.runAsync(() -> buildAsync(buildCtx), BACKGROUND_EXECUTOR)
                .thenRunAsync(() -> {
                    // Upload to GPU on render thread
                    buildCtx.endBatch();
                    // Swap contexts
                    activeContextIndex = buildIndex;
                    contextReady = true;
                }, runnable -> {
                    if (RenderSystem.isOnRenderThread()) runnable.run();
                    else RenderSystem.recordRenderCall(runnable::run);
                });
        }

        // Draw from active cached context
        FactionsRenderingContext activeCtx = ctxPool[activeContextIndex];
        if (!contextReady) return; // Nothing built yet on first frame

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        matrices.push();
        matrices.translate(
            activeCtx.getBaseX() - camX,
            activeCtx.getBaseY() - camY,
            activeCtx.getBaseZ() - camZ
        );
        activeCtx.doDrawing(matrices);
        matrices.pop();

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    public void cleanup() {
        for (FactionsRenderingContext c : ctxPool) c.cleanup();
    }
}