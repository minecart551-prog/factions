package io.icker.factions.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Renders dimension blacklist boxes as 3D wireframes
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("FactionsClient");
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

        // Extract and render boundary faces with semi-transparent fill (no internal edges)
        if (!allRegions.isEmpty()) {
            List<FaceMerger.Face> boundaryFaces = FaceMerger.extractAndMergeBoundaryFaces(grid);
            renderBoundaryFacesWithFill(boundaryFaces, camX, camY, camZ, 0.0f, 1.0f, 0.0f, 0.25f, matrices);
        }

        // Render current selection (first/second positions) in GREEN
        BlockPos firstPos = selectionMgr.getFirstPos();
        BlockPos secondPos = selectionMgr.getSecondPos();

        if (firstPos == null) {
            matrices.pop();
            return;
        }

        if (secondPos != null) {
            int minX = Math.min(firstPos.getX(), secondPos.getX());
            int minY = Math.min(firstPos.getY(), secondPos.getY());
            int minZ = Math.min(firstPos.getZ(), secondPos.getZ());
            int maxX = Math.max(firstPos.getX(), secondPos.getX()) + 1;
            int maxY = Math.max(firstPos.getY(), secondPos.getY()) + 1;
            int maxZ = Math.max(firstPos.getZ(), secondPos.getZ()) + 1;
            
            renderBoxOutline(minX - camX, minY - camY, minZ - camZ, maxX - camX, maxY - camY, maxZ - camZ, 0.0f, 1.0f, 0.0f, 1.0f, matrices);
        } else {
            int x = firstPos.getX();
            int y = firstPos.getY();
            int z = firstPos.getZ();
            renderBoxOutline(x - camX, y - camY, z - camZ, x + 1 - camX, y + 1 - camY, z + 1 - camZ, 0.0f, 1.0f, 0.0f, 1.0f, matrices);
        }

        // Render delete mode red box preview
        if (selectionMgr.isDeleteMode()) {
            BlockPos delFirstPos = selectionMgr.getDeleteFirstPos();
            BlockPos delSecondPos = selectionMgr.getDeleteSecondPos();
            
            if (delFirstPos != null) {
                if (delSecondPos != null) {
                    // Both positions set - show completed red box
                    int minX = Math.min(delFirstPos.getX(), delSecondPos.getX());
                    int minY = Math.min(delFirstPos.getY(), delSecondPos.getY());
                    int minZ = Math.min(delFirstPos.getZ(), delSecondPos.getZ());
                    int maxX = Math.max(delFirstPos.getX(), delSecondPos.getX()) + 1;
                    int maxY = Math.max(delFirstPos.getY(), delSecondPos.getY()) + 1;
                    int maxZ = Math.max(delFirstPos.getZ(), delSecondPos.getZ()) + 1;
                    
                    renderFilledDeleteBox(
                        minX - camX, minY - camY, minZ - camZ,
                        maxX - camX, maxY - camY, maxZ - camZ,
                        1.0f, 0.0f, 0.0f, 0.25f, matrices
                    );
                } else {
                    // Only first position set - show single block or preview
                    int x = delFirstPos.getX();
                    int y = delFirstPos.getY();
                    int z = delFirstPos.getZ();
                    renderBoxOutline(x - camX, y - camY, z - camZ, x + 1 - camX, y + 1 - camY, z + 1 - camZ, 1.0f, 0.0f, 0.0f, 1.0f, matrices);
                }
            }
        }

        matrices.pop();
    }

    
    private void renderBoxOutline(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, 
                                  float r, float g, float b, float a, MatrixStack matrices) {
        startDrawingLines();
        
        var tessellator = Tessellator.getInstance();
        var buffer = tessellator.getBuffer();
        
        // Draw the 12 lines of the box outline
        // Bottom face
        drawLine(buffer, matrices, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(buffer, matrices, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrices, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrices, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        
        // Top face
        drawLine(buffer, matrices, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrices, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrices, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrices, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        
        // Vertical edges
        drawLine(buffer, matrices, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrices, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrices, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrices, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
        
        tessellator.draw();
    }

    
    private static void startDrawingLines() {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.applyModelViewMatrix();
        Tessellator.getInstance().getBuffer().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    private void drawLine(BufferBuilder buffer, MatrixStack matrices, double x1, double y1, double z1, 
                          double x2, double y2, double z2, float r, float g, float b, float a) {
        var matrix = matrices.peek().getPositionMatrix();
        
        LOGGER.debug("Drawing line from ({},{},{}) to ({},{},{})", x1, y1, z1, x2, y2, z2);
        
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
    }

    /**
     * Render a filled delete box (red semi-transparent)
     */
    private void renderFilledDeleteBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                       float r, float g, float b, float a, MatrixStack matrices) {
        // Enable blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Disable backface culling so we can see both sides
        RenderSystem.disableCull();
        
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.applyModelViewMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        
        var matrix = matrices.peek().getPositionMatrix();
        
        // Draw 6 faces with triangles
        // Front face (z = minZ)
        drawFilledQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
        // Back face (z = maxZ)
        drawFilledQuad(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        // Left face (x = minX)
        drawFilledQuad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        // Right face (x = maxX)
        drawFilledQuad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        // Bottom face (y = minY)
        drawFilledQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        // Top face (y = maxY)
        drawFilledQuad(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        
        tessellator.draw();
        
        // Disable blending and re-enable backface culling
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }
    
    /**
     * Draw a filled quad as two triangles
     */
    private void drawFilledQuad(BufferBuilder buffer, org.joml.Matrix4f matrix,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                float r, float g, float b, float a) {
        // Triangle 1: x1,y1,z1 -> x2,y2,z2 -> x3,y3,z3
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x3, (float)y3, (float)z3).color(r, g, b, a).next();
        
        // Triangle 2: x1,y1,z1 -> x3,y3,z3 -> x4,y4,z4
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x3, (float)y3, (float)z3).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x4, (float)y4, (float)z4).color(r, g, b, a).next();
    }

    /**     * Render merged boundary faces with semi-transparent fill (no internal edges)
     */
    private void renderBoundaryFacesWithFill(List<FaceMerger.Face> faces, double camX, double camY, double camZ,
                                             float r, float g, float b, float a, MatrixStack matrices) {
        if (faces.isEmpty()) return;
        
        // Enable blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Disable backface culling so we can see both sides
        RenderSystem.disableCull();
        
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.applyModelViewMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        
        var matrix = matrices.peek().getPositionMatrix();
        
        // Render each merged face as two triangles
        for (FaceMerger.Face face : faces) {
            renderMergedFaceAsFill(buffer, matrix, face, camX, camY, camZ, r, g, b, a);
        }
        
        tessellator.draw();
        
        // Disable blending and re-enable backface culling
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }
    
    /**
     * Render a single merged face as two filled triangles
     */
    private void renderMergedFaceAsFill(BufferBuilder buffer, org.joml.Matrix4f matrix, FaceMerger.Face face,
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
        
        // Draw two triangles to fill the rectangular face
        // Triangle 1: x1,y1,z1 -> x2,y2,z2 -> x3,y3,z3
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x3, (float)y3, (float)z3).color(r, g, b, a).next();
        
        // Triangle 2: x1,y1,z1 -> x3,y3,z3 -> x4,y4,z4
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x3, (float)y3, (float)z3).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x4, (float)y4, (float)z4).color(r, g, b, a).next();
    }

    /**     * Render only the outer surface boundary faces with semi-transparent fill
     */
    private void renderBoundaryFaces(VoxelGrid grid, double camX, double camY, double camZ,
                                     float r, float g, float b, float a, MatrixStack matrices) {
        // Enable blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
                // Disable backface culling so we can see both sides
        RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.applyModelViewMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        
        var matrix = matrices.peek().getPositionMatrix();
        
        // Iterate through all voxels and render only boundary faces
        for (int x = grid.getMinX(); x <= grid.getMaxX(); x++) {
            for (int y = grid.getMinY(); y <= grid.getMaxY(); y++) {
                for (int z = grid.getMinZ(); z <= grid.getMaxZ(); z++) {
                    if (!grid.isOccupied(x, y, z)) continue;
                    
                    double x0 = x - camX;
                    double y0 = y - camY;
                    double z0 = z - camZ;
                    double x1 = x0 + 1;
                    double y1 = y0 + 1;
                    double z1 = z0 + 1;
                    
                    // Front face (z = z0)
                    if (!grid.isOccupied(x, y, z - 1)) {
                        drawTriangleFace(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
                    }
                    
                    // Back face (z = z1)
                    if (!grid.isOccupied(x, y, z + 1)) {
                        drawTriangleFace(buffer, matrix, x0, y0, z1, x1, y1, z1, x1, y0, z1, x0, y1, z1, r, g, b, a);
                    }
                    
                    // Left face (x = x0)
                    if (!grid.isOccupied(x - 1, y, z)) {
                        drawTriangleFace(buffer, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
                    }
                    
                    // Right face (x = x1)
                    if (!grid.isOccupied(x + 1, y, z)) {
                        drawTriangleFace(buffer, matrix, x1, y0, z0, x1, y1, z1, x1, y1, z0, x1, y0, z1, r, g, b, a);
                    }
                    
                    // Bottom face (y = y0)
                    if (!grid.isOccupied(x, y - 1, z)) {
                        drawTriangleFace(buffer, matrix, x0, y0, z0, x1, y0, z1, x1, y0, z0, x0, y0, z1, r, g, b, a);
                    }
                    
                    // Top face (y = y1)
                    if (!grid.isOccupied(x, y + 1, z)) {
                        drawTriangleFace(buffer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
                    }
                }
            }
        }
        
        tessellator.draw();
        
        // Disable blending and re-enable backface culling
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }
    
    /**
     * Draw a single rectangular face as two triangles
     */
    private void drawTriangleFace(BufferBuilder buffer, org.joml.Matrix4f matrix,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  double x3, double y3, double z3,
                                  double x4, double y4, double z4,
                                  float r, float g, float b, float a) {
        // Triangle 1: x1,y1,z1 -> x2,y2,z2 -> x3,y3,z3
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x3, (float)y3, (float)z3).color(r, g, b, a).next();
        
        // Triangle 2: x1,y1,z1 -> x3,y3,z3 -> x4,y4,z4
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x3, (float)y3, (float)z3).color(r, g, b, a).next();
        buffer.vertex(matrix, (float)x4, (float)y4, (float)z4).color(r, g, b, a).next();
    }


}


