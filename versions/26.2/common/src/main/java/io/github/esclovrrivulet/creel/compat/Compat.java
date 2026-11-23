package io.github.esclovrrivulet.creel.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Bucketable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.PushReaction;

/** Compat for MINECRAFT 26.2: replaces common/.../compat/Compat.java (26.3's) in the 26.2 build. The same members, each one line of
 *  26.2's own API; see the base copy for what each one is. */
public final class Compat {
    private Compat() {}

    public static final EntityType<Salmon> SALMON = EntityTypes.SALMON;
    public static final EntityType<FallingBlockEntity> FALLING_BLOCK = EntityTypes.FALLING_BLOCK;

    /** 26.2 names frogspawn's reaction DESTROY (26.3: POPPED) */
    public static final PushReaction POPPED = PushReaction.DESTROY;

    /** 26.2's random state holds one sampler (26.3: createClimateSampler(SamplerContext.EMPTY_UNCACHED)) */
    public static Climate.Sampler climateSampler(RandomState state) {
        return state.sampler();
    }

    /** 26.2's density functions are computed at a point context (26.3: sampleValue(x, y, z)) */
    public static double temperature(Climate.Sampler sampler, BlockPos pos) {
        return sampler.temperature().compute(new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** 26.2 has one swing animation, no argument for it */
    public static void swing(Player player, InteractionHand hand) {
        player.swing(hand, true);
    }

    /** 26.2 makes the changeable contents with a constructor (26.3: asMutable()) */
    public static BundleContents.Mutable mutable(BundleContents contents) {
        return new BundleContents.Mutable(contents);
    }

    public static <T extends LivingEntity & Bucketable> boolean bucketMobPickup(Player player, InteractionHand hand, T mob) {
        return Bucketable.bucketMobPickup(player, hand, mob).isPresent();
    }

    /** 26.2 calls it the attack animation (26.3: getSwingAnimation) */
    public static float swingProgress(LivingEntity entity, float partialTick) {
        return entity.getAttackAnim(partialTick);
    }
}
