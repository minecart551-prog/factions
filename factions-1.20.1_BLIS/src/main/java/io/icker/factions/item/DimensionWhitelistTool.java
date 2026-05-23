package io.icker.factions.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Custom item for dimension whitelist selection tool
 * This item is used by faction owners to create 3D box selections for whitelisting dimensions
 */
public class DimensionWhitelistTool extends Item {
    
    public DimensionWhitelistTool(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        stack.setCustomName(Text.literal("§fDimension Whitelist Tool"));
        return stack;
    }
}