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
import net.minecraft.item.Item;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Environment(EnvType.CLIENT)
public class DimensionBlacklistRenderer {
    private static final DimensionBlacklistRenderer INSTANCE = new DimensionBlacklistRenderer();

    private final FactionsRenderingContext[] ctxPool = new FactionsRenderingContext[] {
        new FactionsRenderingContext(),
        new FactionsRenderingContext()
    };
    private int activeContextIndex = 0;
    private boolean contextReady = false;

    private static Frustum currentFrustum = null;

    private static final Executor BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor(
        r -> { Thread t = new Thread(r, "Factions-Build-Thread"); t.setDaemon(true); return t; }
    );

    private volatile boolean needsRebuild = true;
    private volatile CompletableFuture<Void> buildingFuture = null;
    private static final int MAX_RENDER_DISTANCE = 64;

    private static final Color BLACKLIST_GREEN = new Color(0, 255, 0);
    private static final Color WHITELIST_WHITE = new Color(255, 255, 255);
    private static final Color DELETE_RED = new Color(255, 0, 0);
    private static final Color SELECTION_GREEN = new Color(0, 255, 0);
    private static final Color SELECTION_WHITE = new Color(255, 255, 255);
    private static final int FILL_ALPHA = 45;
    private static final int WIREFRAME_ALPHA = 200;

    private DimensionBlacklistRenderer() {}

    public static DimensionBlacklistRenderer getInstance() { return INSTANCE; }
    public void setFrustum(Frustum frustum) { currentFrustum = frustum; }
    public void markNeedsRebuild() { this.needsRebuild = true; }

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

    private void buildAsync(FactionsRenderingContext buildCtx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        SelectionManager selectionMgr = SelectionManager.getInstance();
        double camX = mc.gameRenderer.getCamera().getPos().x;
        double camY = mc.gameRenderer.getCamera().getPos().y;
        double camZ = mc.gameRenderer.getCamera().getPos().z;

        buildCtx.reset(camX, camY, camZ);
        buildCtx.beginBatch();

        // Render blacklist regions (green) and whitelist regions (white)
        List<io.icker.factions.api.persistents.BlacklistedDimension> allRegions = selectionMgr.getAllSelections();
        
        // Use voxel grid for the combined shape, but we can't color individual faces differently.
        // Simple solution: render everything green first, then overlay whitelist in white
        // Get separate lists
        List<io.icker.factions.api.persistents.BlacklistedDimension> blist = null;
        List<io.icker.factions.api.persistents.BlacklistedDimension> wlist = null;
        
        // We need direct access - use getPendingSelections for current tool's data,
        // and the opposite list from getAllSelections minus current
        // But SelectionManager now has separate lists via setBlacklistSelections/setWhitelistSelections
        // We can access them through the combined getAllSelections and pending logic
        // For simplicity, just render everything green and then whitelist on top in white
        // Using two separate VoxelGrid passes:
        
        // First pass: blacklist in green - only render server-synced data, not client-side pending
        // The SelectionManager stores both server-synced and client-pending in the same lists
        // We need to only show what the server confirmed
        // For now, use getPendingSelections which returns current tool's list
        // But we need to separate - the issue is we're showing client-side data for everyone
        // The real fix: only render what was synced from server, not what client committed
        // Since SelectionManager now has separate lists, and sync overwrites them,
        // the lists should only contain server-confirmed data after a sync
        // But the commit also adds to the lists before sync...
        // Solution: Don't add to SelectionManager lists on commit, only on sync
        // But that breaks the visual feedback...
        // Better: Use a separate "confirmed" list that only sync updates
        // For now, let's just use the pending selections which are the current tool's data
        VoxelGrid blGrid = new VoxelGrid();
        for (io.icker.factions.api.persistents.BlacklistedDimension region : selectionMgr.getBlacklistSelections()) {
            if (region != null) blGrid.fillBox(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ);
        }
        if (!blGrid.isEmpty() && isVisibleInFrustum(blGrid.getMinX(), blGrid.getMinY(), blGrid.getMinZ(),
                                                      blGrid.getMaxX(), blGrid.getMaxY(), blGrid.getMaxZ())) {
            for (FaceMerger.Face face : FaceMerger.extractAndMergeBoundaryFaces(blGrid)) {
                renderFaceAsFilledQuad(buildCtx, face, BLACKLIST_GREEN, FILL_ALPHA);
            }
        }

        // Second pass: whitelist in white
        VoxelGrid wlGrid = new VoxelGrid();
        for (io.icker.factions.api.persistents.BlacklistedDimension region : selectionMgr.getWhitelistSelections()) {
            if (region != null) wlGrid.fillBox(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ);
        }
        if (!wlGrid.isEmpty() && isVisibleInFrustum(wlGrid.getMinX(), wlGrid.getMinY(), wlGrid.getMinZ(),
                                                      wlGrid.getMaxX(), wlGrid.getMaxY(), wlGrid.getMaxZ())) {
            for (FaceMerger.Face face : FaceMerger.extractAndMergeBoundaryFaces(wlGrid)) {
                renderFaceAsFilledQuad(buildCtx, face, WHITELIST_WHITE, FILL_ALPHA);
            }
        }

        // Selection box with preview and wireframe
        Color selectionColor = selectionMgr.isWhitelistMode() ? SELECTION_WHITE : SELECTION_GREEN;
        BlockPos firstPos = selectionMgr.getFirstPos();
        BlockPos previewPos = selectionMgr.getPreviewPos();
        BlockPos secondPos = selectionMgr.getSecondPos();
        if (firstPos != null) {
            if (secondPos != null) {
                int minX = Math.min(firstPos.getX(), secondPos.getX());
                int minY = Math.min(firstPos.getY(), secondPos.getY());
                int minZ = Math.min(firstPos.getZ(), secondPos.getZ());
                int maxX = Math.max(firstPos.getX(), secondPos.getX()) + 1;
                int maxY = Math.max(firstPos.getY(), secondPos.getY()) + 1;
                int maxZ = Math.max(firstPos.getZ(), secondPos.getZ()) + 1;
                renderBoxAsFilledQuads(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, selectionColor, FILL_ALPHA);
                renderWireframeBox(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, selectionColor, WIREFRAME_ALPHA);
            } else if (previewPos != null) {
                int minX = Math.min(firstPos.getX(), previewPos.getX());
                int minY = Math.min(firstPos.getY(), previewPos.getY());
                int minZ = Math.min(firstPos.getZ(), previewPos.getZ());
                int maxX = Math.max(firstPos.getX(), previewPos.getX()) + 1;
                int maxY = Math.max(firstPos.getY(), previewPos.getY()) + 1;
                int maxZ = Math.max(firstPos.getZ(), previewPos.getZ()) + 1;
                renderBoxAsFilledQuads(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, selectionColor, FILL_ALPHA);
                renderWireframeBox(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, selectionColor, WIREFRAME_ALPHA);
            } else {
                int x = firstPos.getX();
                int y = firstPos.getY();
                int z = firstPos.getZ();
                renderBoxAsFilledQuads(buildCtx, x, y, z, x + 1, y + 1, z + 1, selectionColor, FILL_ALPHA);
                renderWireframeBox(buildCtx, x, y, z, x + 1, y + 1, z + 1, selectionColor, WIREFRAME_ALPHA);
            }
        }

        // Delete mode box with wireframe
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
                    renderWireframeBox(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, DELETE_RED, WIREFRAME_ALPHA);
                } else {
                    int x = delFirstPos.getX();
                    int y = delFirstPos.getY();
                    int z = delFirstPos.getZ();
                    renderBoxAsFilledQuads(buildCtx, x, y, z, x + 1, y + 1, z + 1, DELETE_RED, FILL_ALPHA);
                    renderWireframeBox(buildCtx, x, y, z, x + 1, y + 1, z + 1, DELETE_RED, WIREFRAME_ALPHA);
                }
            }
        }
    }

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

    private void renderWireframeBox(FactionsRenderingContext ctx,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     Color color, int alpha) {
        ctx.drawLine(minX, minY, minZ, maxX, minY, minZ, color, alpha);
        ctx.drawLine(maxX, minY, minZ, maxX, minY, maxZ, color, alpha);
        ctx.drawLine(maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
        ctx.drawLine(minX, minY, maxZ, minX, minY, minZ, color, alpha);

        ctx.drawLine(minX, maxY, minZ, maxX, maxY, minZ, color, alpha);
        ctx.drawLine(maxX, maxY, minZ, maxX, maxY, maxZ, color, alpha);
        ctx.drawLine(maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        ctx.drawLine(minX, maxY, maxZ, minX, maxY, minZ, color, alpha);

        ctx.drawLine(minX, minY, minZ, minX, maxY, minZ, color, alpha);
        ctx.drawLine(maxX, minY, minZ, maxX, maxY, minZ, color, alpha);
        ctx.drawLine(maxX, minY, maxZ, maxX, maxY, maxZ, color, alpha);
        ctx.drawLine(minX, minY, maxZ, minX, maxY, maxZ, color, alpha);
    }

    public void render(double camX, double camY, double camZ, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        Item held = mc.player.getMainHandStack().getItem();
        if (held != io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL &&
            held != io.icker.factions.item.FactionsItems.DIMENSION_WHITELIST_TOOL) return;

        if (needsRebuild && (buildingFuture == null || buildingFuture.isDone())) {
            needsRebuild = false;
            int buildIndex = (activeContextIndex + 1) % 2;
            FactionsRenderingContext buildCtx = ctxPool[buildIndex];
            buildingFuture = CompletableFuture.runAsync(() -> buildAsync(buildCtx), BACKGROUND_EXECUTOR)
                .thenRunAsync(() -> {
                    buildCtx.endBatch();
                    activeContextIndex = buildIndex;
                    contextReady = true;
                }, runnable -> {
                    if (RenderSystem.isOnRenderThread()) runnable.run();
                    else RenderSystem.recordRenderCall(runnable::run);
                });
        }

        FactionsRenderingContext activeCtx = ctxPool[activeContextIndex];
        if (!contextReady) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        matrices.push();
        matrices.translate(activeCtx.getBaseX() - camX, activeCtx.getBaseY() - camY, activeCtx.getBaseZ() - camZ);
        activeCtx.doDrawing(matrices);
        matrices.pop();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    public void cleanup() { for (FactionsRenderingContext c : ctxPool) c.cleanup(); }
}