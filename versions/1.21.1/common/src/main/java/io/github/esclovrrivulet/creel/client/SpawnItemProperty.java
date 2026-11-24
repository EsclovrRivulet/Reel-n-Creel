package io.github.esclovrrivulet.creel.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.item.FishSpawnItem;

/** (1.21.1 build only) The Fish Spawn item's picture follows its species. 26.x chooses it in the item's model definition
 *  (assets/creel/items/fish_spawn.json, by the stack's block_state); 1.21.1 has no model definitions, only a model's overrides, which
 *  choose by a number an item property gives. This is that property, "creel:species", registered by each loader's client entry point;
 *  the overrides are made from the 26.x definition by tools/convert_data.py (the item_models rule), with the same numbers. */
public final class SpawnItemProperty {
    private SpawnItemProperty() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CreelMod.MOD_ID, "species");

    /** a stack's species as (its place in the species table + 1) / 128, exact in a float; 0 for a stack without one (the overrides'
     *  default picture, the definition's fallback) */
    public static float value(ItemStack stack) {
        String id = FishSpawnItem.species(stack);
        Species s = id == null ? null : FishTable.BY_ID.get(id);
        return s == null ? 0.0F : (FishTable.ALL.indexOf(s) + 1) / 128.0F;
    }
}
