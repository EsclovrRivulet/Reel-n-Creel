package io.github.esclovrrivulet.creel.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.compat.Compat;


/** One bucket for every fish of the mod (creel:fish_bucket): which fish it holds is in its bucket data, as vanilla keeps a bucketed
 *  fish's name and health there (CreelSpecies, CreelSize, CreelScale beside them). Its name is "Bucket of <fish>" with the size word
 *  (item_name), its look the species' own fish over vanilla's water bucket (custom_model_data string 0 = the species id, chosen by
 *  assets/creel/items/fish_bucket.json). Emptying it, by a player or a dispenser, places the water and the same fish, from a bucket:
 *  it never despawns and counts in no budget, as vanilla's bucketed fish. */
public class FishBucketItem extends MobBucketItem {
    public FishBucketItem(Properties properties) {
        super(CreelMod.FISH.get("perch"), Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, properties);
    }

    /** writes the species and size into a bucket's data and gives it the fish's name and look */
    public static void fill(ItemStack stack, Species species, Size size, float factor) {
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putString("CreelSpecies", species.id());
            tag.putString("CreelSize", size.key);
            tag.putFloat("CreelScale", factor);
        });
        Component fish = size.name(Component.translatable("entity." + CreelMod.MOD_ID + "." + species.id()));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.creel.fish_bucket.of", fish));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, Compat.modelData(species.id(), FishTable.ALL.indexOf(species) + 1));
    }

    /** a bucket holding a fresh fish of that species and size (the creative tab's example) */
    public static ItemStack of(Species species, Size size, float factor) {
        ItemStack stack = new ItemStack(CreelMod.FISH_BUCKET);
        fill(stack, species, size, factor);
        return stack;
    }

    /** the species a bucket holds, the perch for a bucket without one */
    public static Species species(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag().getStringOr("CreelSpecies", "");
        return FishTable.BY_ID.getOrDefault(id, FishTable.BY_ID.get("perch"));
    }

    /** vanilla's MobBucketItem places its one type; this places the species the bucket holds, the same way */
    @Override
    public void checkExtraContent(LivingEntity user, Level level, ItemStack stack, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            EntityType<FishEntity> type = CreelMod.FISH.get(species(stack).id());
            Entity e = type.create(server, EntityType.createDefaultStackConfig(server, stack, null), pos, EntitySpawnReason.BUCKET, true, false);
            if (e instanceof FishEntity fish) {
                fish.loadFromBucketTag(stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag());
                fish.setFromBucket(true);
                server.addFreshEntityWithPassengers(fish);
                fish.playAmbientSound();
            }
            level.gameEvent(user, GameEvent.ENTITY_PLACE, pos);
        }
    }
}
