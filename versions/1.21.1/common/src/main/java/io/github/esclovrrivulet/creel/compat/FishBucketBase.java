package io.github.esclovrrivulet.creel.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/** (1.21.1 build only) What item.FishBucketItem extends in the 1.21.1 build (versions/1.21.1/java_renames.txt puts it in place of
 *  MobBucketItem): 1.21.1's checkExtraContent takes the player (null for a dispenser) where 26.x's takes any living user, so the game's
 *  call is passed on to the bucket's own. Nothing else. */
public abstract class FishBucketBase extends MobBucketItem {
    protected FishBucketBase(EntityType<? extends Mob> type, Fluid fluid, SoundEvent sound, Properties properties) {
        super(type, fluid, sound, properties);
    }

    @Override
    public void checkExtraContent(Player player, Level level, ItemStack stack, BlockPos pos) {
        this.checkExtraContent((LivingEntity) player, level, stack, pos);
    }

    /** FishBucketItem's (26.x's form); this one is the game's */
    public void checkExtraContent(LivingEntity user, Level level, ItemStack stack, BlockPos pos) {
        super.checkExtraContent(user instanceof Player player ? player : null, level, stack, pos);
    }
}
