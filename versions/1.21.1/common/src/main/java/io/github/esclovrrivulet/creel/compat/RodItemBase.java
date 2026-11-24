package io.github.esclovrrivulet.creel.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** (1.21.1 build only) What fishing.CastingRodItem extends in the 1.21.1 build (versions/1.21.1/java_renames.txt puts it in place of
 *  Item, and names its use and releaseUsing useRod and releaseRod there, since 1.21.1's have the same arguments and another result):
 *  the game's calls are passed on to the rod's own, the results put in 1.21.1's form. And the enchantability, which 26.x gives in the
 *  item's properties (Properties.enchantable(1), left out of this build): vanilla's fishing rod's, 1. */
public abstract class RodItemBase extends Item {
    protected RodItemBase(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = this.useRod(level, player, hand);
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    /** CastingRodItem's use (26.x's form) */
    public InteractionResult useRod(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        this.releaseRod(stack, level, entity, remaining);
    }

    /** CastingRodItem's releaseUsing (26.x's form) */
    public boolean releaseRod(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
