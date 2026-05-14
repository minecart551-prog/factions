package io.icker.factions.client.render;

import io.icker.factions.client.event.SelectionManager;
import io.icker.factions.client.util.VoxelGrid;
import io.icker.factions.client.util.FaceMerger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import java.util.List;

/**
 * Renders dimension blacklist boxes as 3D wireframe outlines
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistRenderer {
    private static final DimensionBlacklistRenderer INSTANCE = new DimensionBlacklistRenderer();

    private DimensionBlacklistRenderer() {
    }

    public static DimensionBlacklistRenderer getInstance() {
        return INSTANCE;
    }

    public void render(double camX, double camY, double camZ, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (mc.player.getMainHandStack().getItem() != io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL) {
            return;
        }

        SelectionManager selectionMgr = SelectionManager.getInstance();
        
        matrices.push();

        // Build voxel grid from all pending selections
        VoxelGrid grid = new VoxelGrid();
        java.util.List<io.icker.factions.api.persistents.BlacklistedDimension> allRegions = selectionMgr.getPendingSelections();
        
        for (io.icker.factions.api.persistents.BlacklistedDimension region : allRegions) {
            grid.fillBox(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ);
        }

        // Extract unmerged boundary faces (each 1x1 block face on the outer surface rendered as its own square)
        if (!allRegions.isEmpty()) {
            List<FaceMerger.Face> boundaryFaces = FaceMerger.extractUnmergedBoundaryFaces(grid);
            renderBoundaryFacesAsOutline(boundaryFaces, camX, camY, camZ, 0.0f, 1.0f, 0.0f, 0.8f, matrices);
        }

        // Render current selection (first/second positions) as GREEN outlined boxes
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
                
                renderBoxOutline(minX - camX, minY - camY, minZ - camZ, maxX - camX, maxY - camY, maxZ - camZ,
                                 0.0f, 1.0f, 0.0f, 0.8f, matrices);
            } else {
                int x = firstPos.getX();
                int y = firstPos.getY();
                int z = firstPos.getZ();
                renderBoxOutline(x - camX, y - camY, z - camZ, x + 1 - camX, y + 1 - camY, z + 1 - camZ,
                                 0.0f, 1.0f, 0.0f, 0.8f, matrices);
            }
        }

        // Render delete mode red box outline
        if (selectionMgr.isDeleteMode()) {
            BlockPos delFirstPos = selectionMgr.getDeleteFirstPos();
            BlockPos delSecondPos = selectionMgr.getDeleteSecondPos();
            
            if (delFirstPos != null) {
                if (delSecondPos != null) {
                    // Both positions set - show completed red box outline
                    int minX = Math.min(delFirstPos.getX(), delSecondPos.getX());
                    int minY = Math.min(delFirstPos.getY(), delSecondPos.getY());
                    int minZ = Math.min(delFirstPos.getZ(), delSecondPos.getZ());
                    int maxX = Math.max(delFirstPos.getX(), delSecondPos.getX()) + 1;
                    int maxY = Math.max(delFirstPos.getY(), delSecondPos.getY()) + 1;
                    int maxZ = Math.max(delFirstPos.getZ(), delSecondPos.getZ()) + 1;
                    
                    renderBoxOutline(
                        minX - camX, minY - camY, minZ - camZ,
                        maxX - camX, maxY - camY, maxZ - camZ,
                        1.0f, 0.0f, 0.0f, 0.8f, matrices
                    );
                } else {
                    // Only first position set - show single block outline
                    int x = delFirstPos.getX();
                    int y = delFirstPos.getY();
                    int z = delFirstPos.getZ();
                    renderBoxOutline(x - camX, y - camY, z - camZ, x + 1 - camX, y + 1 - camY, z + 1 - camZ, 1.0f, 0.0f, 0.0f, 0.8f, matrices);
                }
            }
        }

        matrices.pop();
    }

    /**
     * Render a box as its 12 edge lines (wireframe outline, no fill)
     */
    private void renderBoxOutline(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                  float r, float g, float b, float a, MatrixStack matrices) {
        // Enable blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Disable depth test to render through blocks (X-ray vision)
        RenderSystem.disableDepthTest();
        
        // Disable backface culling
        RenderSystem.disableCull();
        
        // Set line width for visibility
        RenderSystem.lineWidth(2.0f);
        
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.applyModelViewMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        
        var matrix = matrices.peek().getPositionMatrix();
        
        // Bottom face edges
        drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        
        // Top face edges
        drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        
        // Vertical edges
        drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
        
        tessellator.draw();
        
        // Reset line width
        RenderSystem.lineWidth(1.0f);
        
        // Disable blending, re-enable backface culling, and re-enable depth test
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * Draw a single line between two points
     */
    private void drawLine(BufferBuilder buffer, org.joml.Matrix4f matrix, 
                          double x1, double y1, double z1, 
                          double x2, double y2, double z2, 
                          float r, float g, float b, float a) {
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
    }

    /**
     * Render merged boundary faces as wireframe outlines (rectangle edges only, no fill)
     */
    private void renderBoundaryFacesAsOutline(List<FaceMerger.Face> faces, double camX, double camY, double camZ,
                                              float r, float g, float b, float a, MatrixStack matrices) {
        if (faces.isEmpty()) return;
        
        // Enable blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Disable depth test to render through blocks (X-ray vision)
        RenderSystem.disableDepthTest();
        
        // Disable backface culling so we can see both sides
        RenderSystem.disableCull();
        
        // Set line width for visibility
        RenderSystem.lineWidth(2.0f);
        
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.applyModelViewMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        
        var matrix = matrices.peek().getPositionMatrix();
        
        // Render each merged face as its 4 edge lines
        for (FaceMerger.Face face : faces) {
            renderFaceAsOutline(buffer, matrix, face, camX, camY, camZ, r, g, b, a);
        }
        
        tessellator.draw();
        
        // Reset line width
        RenderSystem.lineWidth(1.0f);
        
        // Disable blending, re-enable backface culling, and re-enable depth test
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }
    
    /**
     * Render a single merged face as its 4 edge lines (rectangle outline)
     */
    private void renderFaceAsOutline(BufferBuilder buffer, org.joml.Matrix4f matrix, FaceMerger.Face face,
                                     double camX, double camY, double camZ,
                                     float r, float g, float b, float a) {
        double x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4;
        
        if (face.plane == 0) {  // X-plane (constant X, variable Y and Z)
            double px = face.planeValue - camX;
            double y_min = face.u1 - camY;
            double y_max = face.u2 - camY;
            double z_min = face.v1 - camZ;
            double z_max = face.v2 - camZ;
            
            x1 = x2 = x3 = x4 = px;
            y1 = y2 = y_min;
            y3 = y4 = y_max;
            z1 = z4 = z_min;
            z2 = z3 = z_max;
        } else if (face.plane == 1) {  // Y-plane (constant Y, variable X and Z)
            double py = face.planeValue - camY;
            double x_min = face.u1 - camX;
            double x_max = face.u2 - camX;
            double z_min = face.v1 - camZ;
            double z_max = face.v2 - camZ;
            
            y1 = y2 = y3 = y4 = py;
            x1 = x2 = x_min;
            x3 = x4 = x_max;
            z1 = z4 = z_min;
            z2 = z3 = z_max;
        } else {  // Z-plane (constant Z, variable X and Y)
            double pz = face.planeValue - camZ;
            double x_min = face.u1 - camX;
            double x_max = face.u2 - camX;
            double y_min = face.v1 - camY;
            double y_max = face.v2 - camY;
            
            z1 = z2 = z3 = z4 = pz;
            x1 = x2 = x_min;
            x3 = x4 = x_max;
            y1 = y4 = y_min;
            y2 = y3 = y_max;
        }
        
        // Draw the 4 edges of the rectangle as lines
        drawLine(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        drawLine(buffer, matrix, x2, y2, z2, x3, y3, z3, r, g, b, a);
        drawLine(buffer, matrix, x3, y3, z3, x4, y4, z4, r, g, b, a);
        drawLine(buffer, matrix, x4, y4, z4, x1, y1, z1, r, g, b, a);
    }
}