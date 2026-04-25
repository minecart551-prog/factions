package io.icker.factions.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.icker.factions.client.event.SelectionManager;
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
            LOGGER.debug("render(): mc.player or mc.world is null");
            return;
        }

        if (mc.player.getMainHandStack().getItem() != io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL) {
            LOGGER.debug("render(): Not holding tool");
            return;
        }

        LOGGER.info("render() called! Camera at ({}, {}, {})", camX, camY, camZ);

        SelectionManager selectionMgr = SelectionManager.getInstance();
        
        matrices.push();

        // Render all pending selections (created regions) in GREEN
        for (io.icker.factions.api.persistents.BlacklistedDimension pending : selectionMgr.getPendingSelections()) {
            LOGGER.info("Rendering pending selection: {}", pending);
            renderBoxOutline(
                pending.minX - camX, pending.minY - camY, pending.minZ - camZ,
                pending.maxX + 1 - camX, pending.maxY + 1 - camY, pending.maxZ + 1 - camZ,
                0.0f, 1.0f, 0.0f, 1.0f, matrices
            );
        }

        // Render current selection (first/second positions) in GREEN
        BlockPos firstPos = selectionMgr.getFirstPos();
        BlockPos secondPos = selectionMgr.getSecondPos();

        LOGGER.info("First pos: {}, Second pos: {}", firstPos, secondPos);

        if (firstPos == null) {
            LOGGER.info("No first pos!");
            matrices.pop();
            return;
        }

        LOGGER.info("Rendering from {} to {}", firstPos, secondPos);

        if (secondPos != null) {
            int minX = Math.min(firstPos.getX(), secondPos.getX());
            int minY = Math.min(firstPos.getY(), secondPos.getY());
            int minZ = Math.min(firstPos.getZ(), secondPos.getZ());
            int maxX = Math.max(firstPos.getX(), secondPos.getX()) + 1;
            int maxY = Math.max(firstPos.getY(), secondPos.getY()) + 1;
            int maxZ = Math.max(firstPos.getZ(), secondPos.getZ()) + 1;
            
            LOGGER.info("Drawing current region box from ({},{},{}) to ({},{},{})", minX, minY, minZ, maxX, maxY, maxZ);
            // Current selection (green)
            renderBoxOutline(minX - camX, minY - camY, minZ - camZ, maxX - camX, maxY - camY, maxZ - camZ, 0.0f, 1.0f, 0.0f, 1.0f, matrices);
        } else {
            LOGGER.info("Drawing first block outline at {}", firstPos);
            int x = firstPos.getX();
            int y = firstPos.getY();
            int z = firstPos.getZ();
            // First position in green (not red)
            renderBoxOutline(x - camX, y - camY, z - camZ, x + 1 - camX, y + 1 - camY, z + 1 - camZ, 0.0f, 1.0f, 0.0f, 1.0f, matrices);
        }

        // Render delete mode target in RED if in delete mode
        if (selectionMgr.isDeleteMode()) {
            io.icker.factions.api.persistents.BlacklistedDimension deleteTarget = selectionMgr.getDeleteTargetRegion();
            if (deleteTarget != null) {
                LOGGER.info("Rendering delete mode target: {}", deleteTarget);
                renderBoxOutline(
                    deleteTarget.minX - camX, deleteTarget.minY - camY, deleteTarget.minZ - camZ,
                    deleteTarget.maxX + 1 - camX, deleteTarget.maxY + 1 - camY, deleteTarget.maxZ + 1 - camZ,
                    1.0f, 0.0f, 0.0f, 1.0f, matrices
                );
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

    private void renderCornerPoint(BlockPos pos, float r, float g, float b, float a, MatrixStack matrices, double camX, double camY, double camZ) {
        startDrawingLines();
        
        var tessellator = Tessellator.getInstance();
        var buffer = tessellator.getBuffer();
        
        double x = pos.getX() + 0.5 - camX;
        double y = pos.getY() + 0.5 - camY;
        double z = pos.getZ() + 0.5 - camZ;
        double size = 0.3;
        
        // Draw a small cube at the corner position
        drawLine(buffer, matrices, x - size, y - size, z - size, x + size, y - size, z - size, r, g, b, a);
        drawLine(buffer, matrices, x + size, y - size, z - size, x + size, y - size, z + size, r, g, b, a);
        drawLine(buffer, matrices, x + size, y - size, z + size, x - size, y - size, z + size, r, g, b, a);
        drawLine(buffer, matrices, x - size, y - size, z + size, x - size, y - size, z - size, r, g, b, a);
        
        drawLine(buffer, matrices, x - size, y + size, z - size, x + size, y + size, z - size, r, g, b, a);
        drawLine(buffer, matrices, x + size, y + size, z - size, x + size, y + size, z + size, r, g, b, a);
        drawLine(buffer, matrices, x + size, y + size, z + size, x - size, y + size, z + size, r, g, b, a);
        drawLine(buffer, matrices, x - size, y + size, z + size, x - size, y + size, z - size, r, g, b, a);
        
        drawLine(buffer, matrices, x - size, y - size, z - size, x - size, y + size, z - size, r, g, b, a);
        drawLine(buffer, matrices, x + size, y - size, z - size, x + size, y + size, z - size, r, g, b, a);
        drawLine(buffer, matrices, x + size, y - size, z + size, x + size, y + size, z + size, r, g, b, a);
        drawLine(buffer, matrices, x - size, y - size, z + size, x - size, y + size, z + size, r, g, b, a);
        
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
}
