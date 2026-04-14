package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship;
import io.icker.factions.api.persistents.Relationship.Permissions;
import io.icker.factions.api.persistents.User;
import io.icker.factions.core.InteractionsUtil.InteractionsUtilActions;
import io.icker.factions.mixin.BucketItemAccessor;
import io.icker.factions.mixin.ItemInvoker;

import net.minecraft.registry.Registries;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
//import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
//import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.World;

public class InteractionManager {
    public static boolean DEBUG_PERMISSIONS = false;

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(InteractionManager::onBreakBlock);
        // PlayerEvents.EXPLODE_BLOCK.register(InteractionManager::onExplodeBlock);
        // PlayerEvents.EXPLODE_DAMAGE.register(InteractionManager::onExplodeDamage);
        UseBlockCallback.EVENT.register(InteractionManager::onUseBlock);
        UseItemCallback.EVENT.register(InteractionManager::onUseBucket);
        AttackEntityCallback.EVENT.register(InteractionManager::onAttackEntity);
        PlayerEvents.IS_INVULNERABLE.register(InteractionManager::isInvulnerableTo);
        PlayerEvents.USE_ENTITY.register(InteractionManager::onUseEntity);
        PlayerEvents.USE_INVENTORY.register(InteractionManager::onUseInventory);
        PlayerEvents.PLACE_BLOCK.register(InteractionManager::onPlaceBlock);
    }

    private static boolean onBreakBlock(World world, PlayerEntity player, BlockPos pos,
            BlockState state, BlockEntity blockEntity) {
        if (world.isClient()) return true;
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        if (isBlockExempt(blockId)) return true;
        
        // Check if block is blacklisted by the claiming faction
        if (isBlockBlacklisted(player, pos, world, blockId)) {
            InteractionsUtil.warn(player, InteractionsUtilActions.BREAK_BLOCKS);
            return false;
        }
        
        boolean result =
                checkPermissions(player, pos, world, Permissions.BREAK_BLOCKS) == ActionResult.FAIL;
        if (result) {
            InteractionsUtil.warn(player, InteractionsUtilActions.BREAK_BLOCKS);
        }
        return !result;
    }

    // private static ActionResult onExplodeBlock(Explosion explosion, BlockView world, BlockPos pos, BlockState state) {
    //     Entity causingEntity = explosion.getCausingEntity();
    //     World actualWorld = causingEntity != null ? causingEntity.getWorld() : null;

    //     if (explosion.getCausingEntity() != null && explosion.getCausingEntity() instanceof PlayerEntity) {
    //         ActionResult result =
    //                 checkPermissions((PlayerEntity) explosion.getCausingEntity(), pos, actualWorld, Permissions.BREAK_BLOCKS);
    //         if (result == ActionResult.FAIL) {
    //             InteractionsUtil.warn((PlayerEntity) explosion.getCausingEntity(), InteractionsUtilActions.BREAK_BLOCKS);
    //         }
    //         return result;
    //     } else {
    //         if (!FactionsMod.CONFIG.BLOCK_TNT) return ActionResult.PASS;

    //         String dimension = actualWorld.getRegistryKey().getValue().toString();
    //         ChunkPos chunkPosition = actualWorld.getChunk(pos).getPos();

    //         Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
    //         if (claim == null) return ActionResult.PASS;

    //         Faction claimFaction = claim.getFaction();

    //         if (claimFaction.getClaims().size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT
    //                 > claimFaction.getPower()) {
    //             return ActionResult.PASS;
    //         }

    //         if (claimFaction.guest_permissions.contains(Permissions.BREAK_BLOCKS)) {
    //             return ActionResult.PASS;
    //         }

    //         return ActionResult.FAIL;
    //     }
    // }

    // private static ActionResult onExplodeDamage(Explosion explosion, Entity entity) {
    //     Entity causingEntity = explosion.getCausingEntity();
    //     World actualWorld = causingEntity != null ? causingEntity.getWorld() : null;

    //     if (explosion.getCausingEntity() != null && explosion.getCausingEntity() instanceof PlayerEntity) {
    //         ActionResult result =
    //                 checkPermissions((PlayerEntity) explosion.getCausingEntity(), entity.getBlockPos(), actualWorld, Permissions.ATTACK_ENTITIES);
    //         if (result == ActionResult.FAIL) {
    //             InteractionsUtil.warn((PlayerEntity) explosion.getCausingEntity(), InteractionsUtilActions.BREAK_BLOCKS);
    //         }
    //         return result;
    //     } else {
    //         if (!FactionsMod.CONFIG.BLOCK_TNT) return ActionResult.PASS;

    //         String dimension = actualWorld.getRegistryKey().getValue().toString();
    //         ChunkPos chunkPosition = actualWorld.getChunk(entity.getBlockPos()).getPos();

    //         Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
    //         if (claim == null) return ActionResult.PASS;

    //         Faction claimFaction = claim.getFaction();

    //         if (claimFaction.getClaims().size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT
    //                 > claimFaction.getPower()) {
    //             return ActionResult.PASS;
    //         }

    //         if (claimFaction.guest_permissions.contains(Permissions.ATTACK_ENTITIES)) {
    //             return ActionResult.PASS;
    //         }

    //         return ActionResult.FAIL;
    //     }
    // }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);

        BlockPos hitPos = hitResult.getBlockPos();
        String blockId = Registries.BLOCK.getId(world.getBlockState(hitPos).getBlock()).toString();
        if (isBlockExempt(blockId)) return ActionResult.PASS;

        if (listContains(FactionsMod.CONFIG.INVENTORY_BLOCKS, blockId)) {
            if (checkPermissions(player, hitPos, world, Permissions.USE_INVENTORIES) == ActionResult.FAIL) {
                InteractionsUtil.warn(player, InteractionsUtilActions.USE_INVENTORY);
                InteractionsUtil.sync(player, stack, hand);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        }

        if (checkPermissions(player, hitPos, world, Permissions.USE_BLOCKS) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_BLOCKS);
            InteractionsUtil.sync(player, stack, hand);
            return ActionResult.FAIL;
        }

        BlockPos placePos = hitPos.add(hitResult.getSide().getVector());
        if (checkPermissions(player, placePos, world,
                Permissions.USE_BLOCKS) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_BLOCKS);
            InteractionsUtil.sync(player, stack, hand);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult onPlaceBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) return ActionResult.PASS;
        if (!(context.getStack().getItem() instanceof BlockItem blockItem)) return ActionResult.PASS;
        String blockId = Registries.BLOCK.getId(blockItem.getBlock()).toString();
        if (isBlockExempt(blockId)) return ActionResult.PASS;
        
        // Check if block is blacklisted by the claiming faction
        if (isBlockBlacklisted(context.getPlayer(), context.getBlockPos(), context.getWorld(), blockId)) {
            InteractionsUtil.warn(context.getPlayer(), InteractionsUtilActions.PLACE_BLOCKS);
            InteractionsUtil.sync(context.getPlayer(), context.getStack(), context.getHand());
            return ActionResult.FAIL;
        }
        
        if (checkPermissions(context.getPlayer(), context.getBlockPos(), context.getWorld(),
                Permissions.PLACE_BLOCKS) == ActionResult.FAIL) {
            InteractionsUtil.warn(context.getPlayer(), InteractionsUtilActions.PLACE_BLOCKS);
            InteractionsUtil.sync(context.getPlayer(), context.getStack(), context.getHand());
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static TypedActionResult<ItemStack> onUseBucket(PlayerEntity player, World world,
            Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(player.getStackInHand(hand));
        Item item = player.getStackInHand(hand).getItem();

        if (item instanceof BucketItem) {
            Fluid fluid = ((BucketItemAccessor) item).getFluid();
            FluidHandling handling =
                    fluid == Fluids.EMPTY ? RaycastContext.FluidHandling.SOURCE_ONLY
                            : RaycastContext.FluidHandling.NONE;

            BlockHitResult raycastResult = ItemInvoker.raycast(world, player, handling);

            if (raycastResult.getType() != BlockHitResult.Type.MISS) {
                BlockPos raycastPos = raycastResult.getBlockPos();
                String blockId = Registries.BLOCK.getId(world.getBlockState(raycastPos).getBlock()).toString();
                if (!isBlockExempt(blockId) && checkPermissions(player, raycastPos, world,
                        Permissions.PLACE_BLOCKS) == ActionResult.FAIL) {
                    InteractionsUtil.warn(player, InteractionsUtilActions.PLACE_OR_PICKUP_LIQUIDS);
                    InteractionsUtil.sync(player, player.getStackInHand(hand), hand);
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }
            }
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand,
            Entity entity, EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (entity != null) {
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (!isMobExempt(entityId)) {
                Permissions permission = (entity instanceof Monster)
                        ? Permissions.ATTACK_MOBS
                        : Permissions.ATTACK_ENTITIES;
                InteractionsUtilActions action = (entity instanceof Monster)
                        ? InteractionsUtilActions.ATTACK_MOBS
                        : InteractionsUtilActions.ATTACK_ENTITIES;
                if (checkPermissions(player, player.getBlockPos(), world, permission) == ActionResult.FAIL) {
                    InteractionsUtil.warn(player, action);
                    return ActionResult.FAIL;
                }
            }
        }

        return ActionResult.PASS;
    }

    private static ActionResult onUseEntity(PlayerEntity player, Entity entity, World world) {
        if (world.isClient()) return ActionResult.PASS;
        BlockPos pos;
        if (entity == null) {
            pos = player.getBlockPos();
        } else {
            pos = entity.getBlockPos();
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (isMobExempt(entityId)) return ActionResult.PASS;
        }

        if (checkPermissions(player, pos, world, Permissions.USE_ENTITIES) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_ENTITIES);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult onUseInventory(PlayerEntity player, BlockPos pos, World world) {
        if (world.isClient()) return ActionResult.PASS;
        String blockId = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        if (isBlockExempt(blockId)) return ActionResult.PASS;
        if (checkPermissions(player, pos, world,
                Permissions.USE_INVENTORIES) == ActionResult.FAIL) {
            InteractionsUtil.warn(player, InteractionsUtilActions.USE_INVENTORY);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult isInvulnerableTo(Entity source, Entity target) {
        if (source.getWorld().isClient()) return ActionResult.PASS;
        if (!source.isPlayer() || FactionsMod.CONFIG.FRIENDLY_FIRE)
            return ActionResult.PASS;

        User sourceUser = User.get(source.getUuid());
        User targetUser = User.get(target.getUuid());

        if (!sourceUser.isInFaction() || !targetUser.isInFaction()) {
            return ActionResult.PASS;
        }

        Faction sourceFaction = sourceUser.getFaction();
        Faction targetFaction = targetUser.getFaction();

        if (sourceFaction.getID() == targetFaction.getID()) {
            return ActionResult.SUCCESS;
        }

        if (sourceFaction.isMutualAllies(targetFaction.getID())) {
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    private static ActionResult checkPermissions(PlayerEntity player, BlockPos position,
            World world, Permissions permission) {
        boolean dbg = DEBUG_PERMISSIONS;
        String prefix = dbg ? String.format("[PermDebug] %s %s pos=(%d,%d,%d) dim=%s | ",
                player.getName().getString(), permission,
                position.getX(), position.getY(), position.getZ(),
                world.getRegistryKey().getValue()) : null;

        if (!FactionsMod.CONFIG.CLAIM_PROTECTION) {
            if (dbg) FactionsMod.LOGGER.info("{}claim_protection=false -> PASS", prefix);
            return ActionResult.PASS;
        }

        User user = User.get(player.getUuid());
        if (user.bypass) {
            if (dbg) FactionsMod.LOGGER.info("{}bypass=true -> PASS", prefix);
            return ActionResult.PASS;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        ChunkPos chunkPosition = world.getChunk(position).getPos();

        Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
        if (claim == null) {
            if (FactionsMod.CONFIG.RESTRICTED_WILDERNESS
                    && FactionsMod.CONFIG.WILDERNESS_RESTRICTED_DIMENSIONS.contains(dimension)) {
                boolean allowed = FactionsMod.CONFIG.WILDERNESS_PERMISSIONS.contains(permission);
                if (dbg) FactionsMod.LOGGER.info("{}wilderness(restricted) perm_allowed={} -> {}",
                        prefix, allowed, allowed ? "PASS" : "FAIL");
                return allowed ? ActionResult.PASS : ActionResult.FAIL;
            }
            if (dbg) FactionsMod.LOGGER.info("{}no claim (wilderness) -> PASS", prefix);
            return ActionResult.PASS;
        }

        Faction claimFaction = claim.getFaction();

        if (!claimFaction.isAdminProtected() && claimFaction.getClaims().size() * FactionsMod.CONFIG.POWER.CLAIM_WEIGHT > claimFaction
                .getPower()) {
            if (dbg) FactionsMod.LOGGER.info("{}claim faction \"{}\" underpowered -> PASS",
                    prefix, claimFaction.getName());
            return ActionResult.PASS;
        }

        if (!user.isInFaction()) {
            boolean allowed = claimFaction.guest_permissions.contains(permission);
            if (dbg) FactionsMod.LOGGER.info("{}player has no faction, claim=\"{}\", guest_perm={} -> {}",
                    prefix, claimFaction.getName(), allowed, allowed ? "SUCCESS" : "FAIL");
            return allowed ? ActionResult.SUCCESS : ActionResult.FAIL;
        }

        Faction userFaction = user.getFaction();
        if (userFaction == null) {
            boolean allowed = claimFaction.guest_permissions.contains(permission);
            if (dbg) FactionsMod.LOGGER.info("{}userFaction null, claim=\"{}\", guest_perm={} -> {}",
                    prefix, claimFaction.getName(), allowed, allowed ? "SUCCESS" : "FAIL");
            return allowed ? ActionResult.SUCCESS : ActionResult.FAIL;
        }

        if (claimFaction.getID().equals(userFaction.getID())) {
            boolean rankOk = getRankLevel(claim.accessLevel) <= getRankLevel(user.rank);
            boolean guestOk = user.rank == User.Rank.GUEST
                    && claimFaction.guest_permissions.contains(permission)
                    && claim.accessLevel == User.Rank.MEMBER;
            boolean allowed = rankOk || guestOk;
            if (dbg) FactionsMod.LOGGER.info("{}own faction, rank={} accessLevel={} rankOk={} guestOk={} -> {}",
                    prefix, user.rank, claim.accessLevel, rankOk, guestOk, allowed ? "SUCCESS" : "FAIL");
            return allowed ? ActionResult.SUCCESS : ActionResult.FAIL;
        }

        if (FactionsMod.CONFIG.RELATIONSHIPS.ALLY_OVERRIDES_PERMISSIONS
                && claimFaction.isMutualAllies(userFaction.getID())
                && claim.accessLevel == User.Rank.MEMBER) {
            if (dbg) FactionsMod.LOGGER.info("{}ally_overrides_permissions + mutual ally -> SUCCESS", prefix);
            return ActionResult.SUCCESS;
        }

        Relationship rel = claimFaction.getRelationship(userFaction.getID());
        boolean hasExplicit = claimFaction.hasExplicitRelationship(userFaction.getID());

        if (!hasExplicit) {
            boolean useGuest =
                (rel.status == Relationship.Status.NEUTRAL && FactionsMod.CONFIG.RELATIONSHIPS.NEUTRAL_AS_GUEST)
                || (rel.status == Relationship.Status.FRIENDLY && FactionsMod.CONFIG.RELATIONSHIPS.FRIENDLY_AS_GUEST)
                || (rel.status == Relationship.Status.ALLY && !FactionsMod.CONFIG.RELATIONSHIPS.ALLY_OVERRIDES_PERMISSIONS && FactionsMod.CONFIG.RELATIONSHIPS.ALLY_AS_GUEST);
            if (useGuest) {
                boolean allowed = claimFaction.guest_permissions.contains(permission);
                if (dbg) FactionsMod.LOGGER.info("{}rel={} treated as guest, guest_perm={} -> {}",
                        prefix, rel.status, allowed, allowed ? "SUCCESS" : "FAIL");
                return allowed ? ActionResult.SUCCESS : ActionResult.FAIL;
            }
        }

        if (rel.permissions.contains(permission) && claim.accessLevel == User.Rank.MEMBER) {
            if (dbg) FactionsMod.LOGGER.info("{}rel={} explicit={} has perm -> SUCCESS",
                    prefix, rel.status, hasExplicit);
            return ActionResult.SUCCESS;
        }

        if (dbg) FactionsMod.LOGGER.info("{}rel={} explicit={} no perm -> FAIL",
                prefix, rel.status, hasExplicit);
        return ActionResult.FAIL;
    }

    /**
     * Public entry point for compat mixins (e.g. CarryOn) to check PLACE_BLOCKS permission
     * at a given position, including block-list filtering.
     */
    public static ActionResult checkCarryOnPlacement(PlayerEntity player, BlockPos pos, World world) {
        String blockId = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        if (isBlockExempt(blockId)) return ActionResult.PASS;
        return checkPermissions(player, pos, world, Permissions.PLACE_BLOCKS);
    }

    /**
     * Returns true if the given registry id matches the pattern.
     * Supported patterns:
     *   "@modid"          – any id whose namespace equals modid
     *   "namespace:pre*"  – any id that starts with the prefix before the trailing '*'
     *   "namespace:exact" – exact match
     */
    private static boolean idMatchesPattern(String id, String pattern) {
        if (pattern.startsWith("@")) {
            String namespace = pattern.substring(1);
            return id.startsWith(namespace + ":");
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return id.startsWith(prefix);
        }
        return id.equals(pattern);
    }

    private static boolean listContains(java.util.List<String> list, String id) {
        for (String pattern : list) {
            if (idMatchesPattern(id, pattern)) return true;
        }
        return false;
    }

    private static boolean isBlockExempt(String blockId) {
        var list = FactionsMod.CONFIG.BLOCK_LIST;
        if (list.WHITELIST_ENABLED && listContains(list.WHITELIST, blockId)) return true;
        if (list.BLACKLIST_ENABLED && !listContains(list.BLACKLIST, blockId)) return true;
        return false;
    }

    /**
     * Check if a block is blacklisted by the claiming faction.
     * Admins with /f admin bypass enabled can always bypass.
     * Only OWNER, COMMANDER, and LEADER can bypass the blacklist.
     * Members, guests, and players from other factions are blocked.
     * 
     * @return true if the block is blacklisted and the player cannot interact with it
     */
    private static boolean isBlockBlacklisted(PlayerEntity player, BlockPos position, World world, String blockId) {
        if (!FactionsMod.CONFIG.CLAIM_PROTECTION) {
            return false;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        ChunkPos chunkPosition = world.getChunk(position).getPos();

        Claim claim = Claim.get(chunkPosition.x, chunkPosition.z, dimension);
        if (claim == null) {
            // No claim = wilderness, no faction blacklist applies
            return false;
        }

        Faction claimFaction = claim.getFaction();
        
        // Check if the block is in the claiming faction's blacklist
        if (!claimFaction.blockBlacklist.contains(blockId)) {
            // Block is not blacklisted
            return false;
        }

        // Block IS blacklisted. Check if player can bypass.
        User user = User.get(player.getUuid());
        // Admin bypass - if enabled, can place/break anything
        if (user.bypass) {
            return false;
        }

        // Player must be in the claiming faction AND have sufficient rank
        if (!user.isInFaction()) {
            // Player has no faction - they're a guest, BLOCK
            return true;
        }

        Faction userFaction = user.getFaction();
        if (userFaction == null || !userFaction.getID().equals(claimFaction.getID())) {
            // Player is in a different faction - BLOCK
            return true;
        }

        // Player is in the claiming faction. Check rank.
        // Only OWNER, COMMANDER, LEADER can bypass blacklist
        return user.rank != User.Rank.OWNER 
            && user.rank != User.Rank.COMMANDER 
            && user.rank != User.Rank.LEADER;
    }

    private static boolean isMobExempt(String entityId) {
        var list = FactionsMod.CONFIG.MOB_LIST;
        if (list.WHITELIST_ENABLED && listContains(list.WHITELIST, entityId)) return true;
        if (list.BLACKLIST_ENABLED && !listContains(list.BLACKLIST, entityId)) return true;
        return false;
    }

    private static int getRankLevel(User.Rank rank) {
        switch (rank) {
            case OWNER -> {
                return 3;
            }
            case LEADER -> {
                return 2;
            }
            case COMMANDER -> {
                return 1;
            }
            case MEMBER -> {
                return 0;
            }
            case GUEST -> {
                return -1;
            }
            default -> {
                return -2;
            }
        }
    }
}
