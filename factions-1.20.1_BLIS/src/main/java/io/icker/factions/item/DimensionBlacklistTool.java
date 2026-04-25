package io.icker.factions.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Custom item for dimension blacklist selection tool
 * This item is used by faction owners to create 3D box selections for blacklisting dimensions
 */
public class DimensionBlacklistTool extends Item {
    
    public DimensionBlacklistTool(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        stack.setCustomName(Text.literal("§6Dimension Blacklist Tool"));
        return stack;
    }
}
