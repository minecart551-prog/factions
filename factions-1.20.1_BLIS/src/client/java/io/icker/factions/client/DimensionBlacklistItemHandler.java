package io.icker.factions.client;

import io.icker.factions.client.event.SelectionManager;
import io.icker.factions.client.render.DimensionBlacklistRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.text.Text;

/**
 * Handles item interactions for the dimension blacklist tool
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistItemHandler {
    private static long lastLeftClickTime = 0;
    private static long lastRightClickTime = 0;
    private static final long CLICK_COOLDOWN = 100;
    private static boolean lastLeftClickPressed = false;
    private static boolean lastRightClickPressed = false;
    private static int selectionStep = 0; // 0: not selecting, 1: first pos set, 2: region created
    private static boolean toolEquipped = false; // Track if tool is currently equipped
    private static boolean permissionDenied = false; // Track if permission was denied

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(DimensionBlacklistItemHandler::onClientTick);
        WorldRenderEvents.LAST.register(DimensionBlacklistItemHandler::onWorldRenderLast);
    }

    /**
     * Check if player has permission to use dimension blacklist tool (OWNER, COMMANDER, or LEADER)
     */
    private static boolean hasPermission(MinecraftClient mc) {
        if (mc.player == null) return false;
        
        io.icker.factions.api.persistents.User user = io.icker.factions.api.persistents.User.get(mc.player.getUuid());
        if (user == null) return false;
        
        io.icker.factions.api.persistents.Faction faction = user.getFaction();
        if (faction == null) return false;
        
        return user.rank == io.icker.factions.api.persistents.User.Rank.OWNER
            || user.rank == io.icker.factions.api.persistents.User.Rank.COMMANDER
            || user.rank == io.icker.factions.api.persistents.User.Rank.LEADER;
    }

    /**
     * Called every client tick - check for mouse clicks and update display
     */
    private static void onClientTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        // Check if tool is being equipped/unequipped
        boolean holdingTool = isHoldingTool(mc.player);
        if (holdingTool && !toolEquipped) {
            // Tool just equipped - check permissions and request sync
            toolEquipped = true;
            permissionDenied = false;
            
            if (!hasPermission(mc)) {
                mc.player.sendMessage(net.minecraft.text.Text.of("§cOnly faction leadership can use the dimension blacklist tool!"), true);
                permissionDenied = true;
                return;
            }
            
            io.icker.factions.client.network.DimensionClientNetworkHandler.requestDimensionSync();
        } else if (!holdingTool && toolEquipped) {
            // Tool just unequipped - clear selection state but preserve pending selections
            toolEquipped = false;
            permissionDenied = false;
            lastLeftClickPressed = false;
            lastRightClickPressed = false;
            selectionStep = 0;
        }
        
        if (!holdingTool || permissionDenied) {
            lastLeftClickPressed = false;
            lastRightClickPressed = false;
            selectionStep = 0;
            return;
        }

        // Update action bar with current state
        SelectionManager selectionMgr = SelectionManager.getInstance();
        String lastClaimError = selectionMgr.getLastClaimError();
        
        if (lastClaimError != null) {
            mc.player.sendMessage(Text.of(lastClaimError), true);
        } else if (selectionMgr.isDeleteMode()) {
            BlockPos delSecondPos = selectionMgr.getDeleteSecondPos();
            if (delSecondPos != null) {
                mc.player.sendMessage(Text.of("§cDeletion complete!"), true);
            } else {
                mc.player.sendMessage(Text.of("§cRight-click second corner to delete"), true);
            }
        } else if (selectionStep == 0) {
            mc.player.sendMessage(Text.of("§eLeft-click first corner"), true);
        } else if (selectionStep == 1) {
            mc.player.sendMessage(Text.of("§eLeft-click second corner"), true);
        } else if (selectionStep == 2) {
            mc.player.sendMessage(Text.of("§aRegion created - Left-click next"), true);
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
            selectionMgr.clearSelection();
            selectionMgr.setFirstPos(pos, worldKey);
            selectionStep = 1;
        } else if (selectionStep == 1) {
            // Set second position and create region
            selectionMgr.setSecondPos(pos);
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
        
        // Enter delete mode - first right-click sets first corner, second right-click sets second and deletes
        selectionMgr.enterDeleteMode(pos, worldKey);
    }

    /**
     * Called after world rendering completes - render dimension blacklist boxes
     */
    private static void onWorldRenderLast(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isHoldingTool(mc.player)) return;
        
        var camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        var matrices = context.matrixStack();
        
        DimensionBlacklistRenderer.getInstance().render(camX, camY, camZ, matrices);
    }

    /**
     * Check if player is holding the dimension blacklist tool
     */
    private static boolean isHoldingTool(PlayerEntity player) {
        if (player == null) return false;
        return player.getMainHandStack().getItem() == io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL;
    }
}
