package io.icker.factions.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

import java.awt.*;

/**
 * Efficient batching rendering context inspired by Bounding Box Outline Reloaded.
 * Pre-allocates BufferBuilders and uploads geometry to GPU VertexBuffers for efficient rendering.
 */
public class FactionsRenderingContext {
    private final BufferBuilder quadBuffer = new BufferBuilder(2097152); // 2MB
    private final BufferBuilder lineBuffer = new BufferBuilder(2097152); // 2MB

    // Deferred initialization: VertexBuffer requires OpenGL context (render thread).
    // Created lazily in beginBatch() instead of in the constructor.
    private VertexBuffer quadUploaded = null;
    private boolean quadEmpty = true;
    private VertexBuffer lineUploaded = null;
    private boolean lineEmpty = true;

    private double baseX;
    private double baseY;
    private double baseZ;

    private long quadCount;
    private long lineCount;

    public void reset(double camX, double camY, double camZ) {
        this.baseX = camX;
        this.baseY = camY;
        this.baseZ = camZ;
        this.quadCount = 0;
        this.lineCount = 0;
    }

    public void beginBatch() {
        quadBuffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        lineBuffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    /**
     * Draw a filled rectangular box face (quad) using camera-relative coordinates.
     * Each face is defined by its 4 corner points on any plane.
     */
    public void drawFilledQuad(double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                Color color, int alpha) {
        quadCount++;
        float fx1 = (float)(x1 - baseX);
        float fy1 = (float)(y1 - baseY);
        float fz1 = (float)(z1 - baseZ);
        float fx2 = (float)(x2 - baseX);
        float fy2 = (float)(y2 - baseY);
        float fz2 = (float)(z2 - baseZ);
        float fx3 = (float)(x3 - baseX);
        float fy3 = (float)(y3 - baseY);
        float fz3 = (float)(z3 - baseZ);
        float fx4 = (float)(x4 - baseX);
        float fy4 = (float)(y4 - baseY);
        float fz4 = (float)(z4 - baseZ);

        quadBuffer.vertex(fx1, fy1, fz1).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        quadBuffer.vertex(fx2, fy2, fz2).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        quadBuffer.vertex(fx3, fy3, fz3).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        quadBuffer.vertex(fx4, fy4, fz4).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
    }

    /**
     * Draw a filled box as 6 quads (one for each face).
     * Only draws faces that are on the boundary (no internal faces).
     */
    public void drawSolidBox(Box box, Color color, int alpha,
                             boolean skipX, boolean skipY, boolean skipZ) {
        float minX = (float)(box.minX - baseX);
        float minY = (float)(box.minY - baseY);
        float minZ = (float)(box.minZ - baseZ);
        float maxX = (float)(box.maxX - baseX);
        float maxY = (float)(box.maxY - baseY);
        float maxZ = (float)(box.maxZ - baseZ);
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        // Bottom face (y = minY) - only if we're not skipping Y
        if (!skipY) {
            quadCount++;
            quadBuffer.vertex(minX, minY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, minY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, minY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, minY, maxZ).color(r, g, b, alpha).next();
        }

        // Top face (y = maxY)
        if (!skipY) {
            quadCount++;
            quadBuffer.vertex(minX, maxY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, maxY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, maxY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, maxY, minZ).color(r, g, b, alpha).next();
        }

        // North face (z = minZ)
        if (!skipZ) {
            quadCount++;
            quadBuffer.vertex(minX, minY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, maxY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, maxY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, minY, minZ).color(r, g, b, alpha).next();
        }

        // South face (z = maxZ)
        if (!skipZ) {
            quadCount++;
            quadBuffer.vertex(minX, minY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, minY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, maxY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, maxY, maxZ).color(r, g, b, alpha).next();
        }

        // West face (x = minX)
        if (!skipX) {
            quadCount++;
            quadBuffer.vertex(minX, minY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, minY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, maxY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(minX, maxY, minZ).color(r, g, b, alpha).next();
        }

        // East face (x = maxX)
        if (!skipX) {
            quadCount++;
            quadBuffer.vertex(maxX, minY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, maxY, minZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, maxY, maxZ).color(r, g, b, alpha).next();
            quadBuffer.vertex(maxX, minY, maxZ).color(r, g, b, alpha).next();
        }
    }

    /**
     * Draw a line between two points
     */
    public void drawLine(double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          Color color, int alpha) {
        lineCount++;
        lineBuffer.vertex((float)(x1 - baseX), (float)(y1 - baseY), (float)(z1 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        lineBuffer.vertex((float)(x2 - baseX), (float)(y2 - baseY), (float)(z2 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
    }

    /**
     * Lazily create VertexBuffers on render thread (requires GL context).
     */
    private void ensureBuffers() {
        if (quadUploaded == null) {
            quadUploaded = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        if (lineUploaded == null) {
            lineUploaded = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
    }

    /**
     * End batch and upload to GPU synchronously.
     * Must be called on the render thread (which it always is since we're in WorldRender).
     */
    public void endBatch() {
        ensureBuffers();
        
        // Upload quads synchronously (already on render thread)
        BufferBuilder.BuiltBuffer quadBuilt = quadBuffer.end();
        quadEmpty = quadBuilt.isEmpty();
        if (!quadEmpty) {
            quadUploaded.bind();
            quadUploaded.upload(quadBuilt);
            VertexBuffer.unbind();
        } else {
            quadBuilt.release();
        }

        // Upload lines synchronously
        BufferBuilder.BuiltBuffer lineBuilt = lineBuffer.end();
        lineEmpty = lineBuilt.isEmpty();
        if (!lineEmpty) {
            lineUploaded.bind();
            lineUploaded.upload(lineBuilt);
            VertexBuffer.unbind();
        } else {
            lineBuilt.release();
        }
    }

    /**
     * Draw the uploaded geometry
     */
    public void doDrawing(MatrixStack stack) {
        // If buffers were never created (no data to draw), just bail out
        if (quadUploaded == null && lineUploaded == null) return;
        
        MatrixStack.Entry top = stack.peek();
        RenderSystem.depthMask(true);

        if (!lineEmpty && lineUploaded != null) {
            lineUploaded.bind();
            lineUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        }
        if (!quadEmpty && quadUploaded != null) {
            quadUploaded.bind();
            quadUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        }

        VertexBuffer.unbind();
        RenderSystem.depthMask(true);
    }

    public double getBaseX() { return baseX; }
    public double getBaseY() { return baseY; }
    public double getBaseZ() { return baseZ; }

    /**
     * Clean up GPU resources
     */
    public void cleanup() {
        if (quadUploaded != null) {
            quadUploaded.close();
            quadUploaded = null;
        }
        if (lineUploaded != null) {
            lineUploaded.close();
            lineUploaded = null;
        }
    }
}