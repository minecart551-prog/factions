package io.icker.factions.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.icker.factions.client.event.SelectionManager;
import io.icker.factions.client.render.DimensionBlacklistRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.text.Text;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Handles item interactions for the dimension blacklist tool
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistItemHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("FactionsClient");
    private static long lastLeftClickTime = 0;
    private static long lastRightClickTime = 0;
    private static final long CLICK_COOLDOWN = 100;
    private static boolean lastLeftClickPressed = false;
    private static boolean lastRightClickPressed = false;
    private static int selectionStep = 0; // 0: not selecting, 1: first pos set, 2: region created
    private static boolean toolEquipped = false; // Track if tool is currently equipped

    public static void register() {
        try {
            LOGGER.info("Registering event listeners!");
            sendChat("§6[DimensionBlacklist]§r Registering event listeners...");
            ClientTickEvents.START_CLIENT_TICK.register(DimensionBlacklistItemHandler::onClientTick);
            WorldRenderEvents.LAST.register(DimensionBlacklistItemHandler::onWorldRenderLast);
            LOGGER.info("Event listeners registered!");
            sendChat("§6[DimensionBlacklist]§r Event listeners registered!");
        } catch (Exception e) {
            LOGGER.error("ERROR registering event listeners:", e);
            sendChat("§c[DimensionBlacklist] ERROR: " + e.getMessage());
        }
    }

    private static void sendChat(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(net.minecraft.text.Text.literal(message), false);
                }
            });
        }
    }

    /**
     * Called every client tick - check for mouse clicks and update display
     */
    private static void onClientTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        // Check if tool is being equipped/unequipped
        boolean holdingTool = isHoldingTool(mc.player);
        if (holdingTool && !toolEquipped) {
            // Tool just equipped - load existing blacklist
            toolEquipped = true;
            SelectionManager.getInstance().loadFromFaction();
            sendChat("§6[DimensionBlacklist]§r Tool equipped - loaded existing blacklists");
        } else if (!holdingTool && toolEquipped) {
            // Tool just unequipped - clear selection state
            toolEquipped = false;
            lastLeftClickPressed = false;
            lastRightClickPressed = false;
            selectionStep = 0;
        }
        
        if (!holdingTool) {
            lastLeftClickPressed = false;
            lastRightClickPressed = false;
            selectionStep = 0;
            return;
        }

        // Update action bar with current state
        SelectionManager selectionMgr = SelectionManager.getInstance();
        if (selectionStep == 0) {
            mc.player.sendMessage(Text.of("§eLeft click to add first position"), true);
        } else if (selectionStep == 1) {
            BlockPos firstPos = selectionMgr.getFirstPos();
            mc.player.sendMessage(Text.of("§eFirst position: " + firstPos + " - Left click another block"), true);
        } else if (selectionStep == 2) {
            BlockPos firstPos = selectionMgr.getFirstPos();
            BlockPos secondPos = selectionMgr.getSecondPos();
            mc.player.sendMessage(Text.of("§aRegion: " + firstPos + " to " + secondPos + " - Left click for next"), true);
        }

        // Handle left-click (attack key)
        boolean leftClickPressed = mc.options.attackKey.isPressed();
        if (leftClickPressed && !lastLeftClickPressed) {
            long now = System.currentTimeMillis();
            if (now - lastLeftClickTime > CLICK_COOLDOWN) {
                lastLeftClickTime = now;
                handleLeftClick(mc);
            }
        }
        lastLeftClickPressed = leftClickPressed;

        // Handle right-click (use key)
        boolean rightClickPressed = mc.options.useKey.isPressed();
        if (rightClickPressed && !lastRightClickPressed) {
            long now = System.currentTimeMillis();
            if (now - lastRightClickTime > CLICK_COOLDOWN) {
                lastRightClickTime = now;
                handleRightClick(mc);
            }
        }
        lastRightClickPressed = rightClickPressed;
    }
    
    /**
     * Handle left-click (set first or second position)
     */
    private static void handleLeftClick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = blockHit.getBlockPos();
        String worldKey = mc.world.getRegistryKey().getValue().toString();
        SelectionManager selectionMgr = SelectionManager.getInstance();
        
        if (selectionStep == 0 || selectionStep == 2) {
            // Set first position
            LOGGER.info("Left-click detected at {} in {} - setting FIRST position", pos, worldKey);
            selectionMgr.clearSelection();
            selectionMgr.setFirstPos(pos, worldKey);
            sendChat("§6[DimensionBlacklist]§r First position selected: " + pos);
            selectionStep = 1;
        } else if (selectionStep == 1) {
            // Set second position and create region
            LOGGER.info("Left-click detected at {} - setting SECOND position", pos);
            selectionMgr.setSecondPos(pos);
            sendChat("§6[DimensionBlacklist]§r Region created! Left click to add another");
            selectionStep = 2;
        }
    }
    
    /**
     * Handle right-click (delete mode)
     */
    private static void handleRightClick(MinecraftClient mc) {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = blockHit.getBlockPos();
        if (mc.player == null || mc.world == null) return;
        
        String worldKey = mc.world.getRegistryKey().getValue().toString();
        SelectionManager selectionMgr = SelectionManager.getInstance();
        
        // Check if already in delete mode (second right-click = confirm)
        if (selectionMgr.isDeleteMode()) {
            LOGGER.info("Delete confirmed at {}", pos);
            selectionMgr.confirmDelete(pos);
            sendChat("§6[DimensionBlacklist]§r Region deleted!");
            selectionStep = 0; // Reset state
        } else {
            // First right-click = enter delete mode
            LOGGER.info("Right-click detected at {} - DELETE mode", pos);
            selectionMgr.enterDeleteMode(pos, worldKey);
            sendChat("§6[DimensionBlacklist]§r Right click again to confirm DELETE");
        }
    }

    /**
     * Called after world rendering completes - render dimension blacklist boxes
     */
    private static void onWorldRenderLast(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isHoldingTool(mc.player)) return;

        LOGGER.info("onWorldRenderLast called!");
        
        var camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        var matrices = context.matrixStack();
        
        DimensionBlacklistRenderer.getInstance().render(camX, camY, camZ, matrices);
        
        RenderSystem.disableBlend();
    }

    /**
     * Check if player is holding the dimension blacklist tool
     */
    private static boolean isHoldingTool(PlayerEntity player) {
        if (player == null) return false;
        return player.getMainHandStack().getItem() == io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL;
    }
}
