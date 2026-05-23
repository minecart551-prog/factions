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
import net.minecraft.item.Item;

/**
 * Handles item interactions for both dimension blacklist and whitelist tools
 */
@Environment(EnvType.CLIENT)
public class DimensionBlacklistItemHandler {
    private static long lastLeftClickTime = 0;
    private static long lastRightClickTime = 0;
    private static final long CLICK_COOLDOWN = 100;
    private static boolean lastLeftClickPressed = false;
    private static boolean lastRightClickPressed = false;
    private static int selectionStep = 0;
    private static boolean toolEquipped = false;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(DimensionBlacklistItemHandler::onClientTick);
        WorldRenderEvents.LAST.register(DimensionBlacklistItemHandler::onWorldRenderLast);
    }

    private static boolean isWhitelistTool(PlayerEntity p) {
        if (p == null) return false;
        return p.getMainHandStack().getItem() == io.icker.factions.item.FactionsItems.DIMENSION_WHITELIST_TOOL;
    }

    private static boolean isBlacklistTool(PlayerEntity p) {
        if (p == null) return false;
        return p.getMainHandStack().getItem() == io.icker.factions.item.FactionsItems.DIMENSION_BLACKLIST_TOOL;
    }

    private static boolean isHoldingTool(PlayerEntity player) {
        return isBlacklistTool(player) || isWhitelistTool(player);
    }

    private static void onClientTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        boolean holdingTool = isHoldingTool(mc.player);
        
        // Update SelectionManager with current tool type
        SelectionManager selectionMgr = SelectionManager.getInstance();
        if (holdingTool) {
            selectionMgr.setWhitelistMode(isWhitelistTool(mc.player));
        }
        
        if (holdingTool && !toolEquipped) {
            toolEquipped = true;
            io.icker.factions.client.network.DimensionClientNetworkHandler.requestDimensionSync();
            io.icker.factions.client.network.DimensionClientNetworkHandler.requestUserSync();
        } else if (!holdingTool && toolEquipped) {
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

        io.icker.factions.network.UserSyncPacket userData = 
            io.icker.factions.client.network.UserClientSyncHandler.getCachedUserData();
        
        if (!userData.inFaction || !userData.hasClaims || !userData.canEditDimensions) return;

        String toolName = isWhitelistTool(mc.player) ? "whitelist" : "blacklist";
        
        if (selectionMgr.isDeleteMode()) {
            BlockPos delSecondPos = selectionMgr.getDeleteSecondPos();
            if (delSecondPos != null) {
                mc.player.sendMessage(Text.of("§cDeletion complete!"), true);
            } else {
                mc.player.sendMessage(Text.of("§cRight-click second corner to delete"), true);
            }
        } else if (selectionStep == 0) {
            mc.player.sendMessage(Text.of("§eLeft-click first " + toolName + " corner"), true);
        } else if (selectionStep == 1) {
            mc.player.sendMessage(Text.of("§eLeft-click second " + toolName + " corner"), true);
        } else if (selectionStep == 2) {
            mc.player.sendMessage(Text.of("§a" + toolName.substring(0,1).toUpperCase() + toolName.substring(1) + " region created"), true);
        }

        boolean leftClickPressed = mc.options.attackKey.isPressed();
        if (leftClickPressed && !lastLeftClickPressed) {
            long now = System.currentTimeMillis();
            if (now - lastLeftClickTime > CLICK_COOLDOWN) {
                lastLeftClickTime = now;
                handleLeftClick(mc);
            }
        }
        lastLeftClickPressed = leftClickPressed;

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
    
    private static void handleLeftClick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        io.icker.factions.network.UserSyncPacket userData = 
            io.icker.factions.client.network.UserClientSyncHandler.getCachedUserData();
        if (!userData.canEditDimensions) return;
        
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = blockHit.getBlockPos();
        String worldKey = mc.world.getRegistryKey().getValue().toString();
        SelectionManager selectionMgr = SelectionManager.getInstance();
        
        if (selectionStep == 0 || selectionStep == 2) {
            selectionMgr.clearSelection();
            selectionMgr.setFirstPos(pos, worldKey);
            selectionStep = 1;
        } else if (selectionStep == 1) {
            selectionMgr.setSecondPos(pos);
            selectionStep = 2;
        }
        
        DimensionBlacklistRenderer.getInstance().markNeedsRebuild();
    }
    
    private static void handleRightClick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        io.icker.factions.network.UserSyncPacket userData = 
            io.icker.factions.client.network.UserClientSyncHandler.getCachedUserData();
        if (!userData.canEditDimensions) return;
        
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = blockHit.getBlockPos();
        String worldKey = mc.world.getRegistryKey().getValue().toString();
        SelectionManager selectionMgr = SelectionManager.getInstance();
        
        selectionMgr.enterDeleteMode(pos, worldKey);
        DimensionBlacklistRenderer.getInstance().markNeedsRebuild();
    }

    private static void onWorldRenderLast(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isHoldingTool(mc.player)) return;
        
        var camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        var matrices = context.matrixStack();
        
        DimensionBlacklistRenderer renderer = DimensionBlacklistRenderer.getInstance();
        renderer.setFrustum(context.frustum());
        renderer.render(camX, camY, camZ, matrices);
    }
}