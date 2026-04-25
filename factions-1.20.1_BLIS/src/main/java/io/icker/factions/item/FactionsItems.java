package io.icker.factions.item;

import io.icker.factions.FactionsMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

/**
 * Registry for custom factions items
 */
public class FactionsItems {
    
    public static final Item DIMENSION_BLACKLIST_TOOL = registerItem("dimension_blacklist_tool",
            new DimensionBlacklistTool(new Item.Settings()
                    .maxCount(1)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(FactionsMod.MODID, name), item);
    }

    public static void register() {
        // Add dimension blacklist tool to creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(new ItemStack(DIMENSION_BLACKLIST_TOOL));
        });
    }
}
