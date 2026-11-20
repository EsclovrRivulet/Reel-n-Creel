package io.github.esclovrrivulet.creel.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.block.FishSpawnBlock;
import io.github.esclovrrivulet.creel.entity.FishEntity;

/** (0.23.0) The one item of Fish Spawn, creel:fish_spawn (docs/SPAWN.md), for creative mode: vanilla's frogspawn item in all it does
 *  (PlaceOnWaterBlockItem, registered as Items registers frogspawn's), with the species in vanilla's own block_state component, so one
 *  item serves every fish that breeds (no item per species). The creative tab holds a stack of each species (CreelMod.creativeTabContents),
 *  pick-block on a spawn gives its species' stack (FishSpawnBlock.getCloneItemStack); nothing else makes one: the block drops nothing
 *  and there is no recipe. Placing it applies the species as BlockItem applies any block_state component; a stack without one places
 *  the first species (the block's default state). Its name is "<Fish> Spawn" for a stack with a species, "Fish Spawn" without. Its
 *  picture is chosen by the item model definition (assets/creel/items/fish_spawn.json, tools/spawn_art.py) on the species. */
public class FishSpawnItem extends PlaceOnWaterBlockItem {
    public FishSpawnItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** a stack of the spawn of one species (its id, one of FishSpawnBlock.SPECIES's values) */
    public static ItemStack of(String species) {
        ItemStack stack = new ItemStack(CreelMod.FISH_SPAWN_ITEM);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(FishSpawnBlock.SPECIES, species));
        return stack;
    }

    /** the species a stack carries, or null */
    public static String species(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        return props == null ? null : props.properties().get(FishSpawnBlock.SPECIES.getName());
    }

    /** "Brook Trout Spawn": the fish's own name in block.creel.fish_spawn.of; a stack without a species keeps the block's name */
    @Override
    public Component getName(ItemStack stack) {
        String id = species(stack);
        EntityType<FishEntity> type = id == null ? null : CreelMod.FISH.get(id);
        return type == null ? super.getName(stack) : Component.translatable("block.creel.fish_spawn.of", type.getDescription());
    }
}
