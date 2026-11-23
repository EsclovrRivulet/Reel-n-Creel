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
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.material.PushReaction;

/** Every call of the game's own code that is not the same in each Minecraft version the mod is built for (26.3, 26.2, 26.1.2), behind
 *  one API, so the rest of the source is one copy for all of them. THIS FILE IS 26.3's, the base version's; each older version has its
 *  own copy of this class, with the same names, in versions/<version>/common/src/main/java/.../compat/Compat.java, which replaces this
 *  one in that version's build (see versions/versions.properties). Each member is one line of the game's own API, with no logic of ours: a change of
 *  behaviour belongs in the calling code, for every version at once. Loaded on both sides: no client class here (ClientCompat has those).
 *  When a member is added here, add it to every version's copy, or that version's build fails to compile (which is the point). */
public final class Compat {
    private Compat() {}

    /** vanilla's salmon and falling block types (26.3 keeps the vanilla entity types in EntityTypes) */
    public static final EntityType<Salmon> SALMON = EntityTypes.SALMON;
    public static final EntityType<FallingBlockEntity> FALLING_BLOCK = EntityTypes.FALLING_BLOCK;

    /** what a piston does to the Fish Spawn: what it does to frogspawn, which breaks and drops nothing (26.3 names this POPPED) */
    public static final PushReaction POPPED = PushReaction.POPPED;

    /** the climate sampler of a level's noise generator, uncached, as the game's own locate-biome reads it */
    public static Climate.Sampler climateSampler(RandomState state) {
        return state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
    }

    /** the multi-noise temperature at a spot (-1 to 1) */
    public static double temperature(Climate.Sampler sampler, BlockPos pos) {
        return sampler.temperature().sampleValue(pos.getX(), pos.getY(), pos.getZ());
    }

    /** the swing the server sends with a cast, which other players see; the game's default swing animation */
    public static void swing(Player player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, true);
    }

    /** a bundle's contents, made changeable (BundleContents.Mutable) */
    public static BundleContents.Mutable mutable(BundleContents contents) {
        return contents.asMutable();
    }

    /** vanilla's own pickup of a bucketable mob into the water bucket in that hand (what using the bucket on it does) */
    public static <T extends LivingEntity & Bucketable> boolean bucketMobPickup(Player player, InteractionHand hand, T mob) {
        return Bucketable.bucketMobPickup(player, hand, mob).isPresent();
    }

    /** how far through its swing a living entity's arm is, 0 to 1 */
    public static float swingProgress(LivingEntity entity, float partialTick) {
        return entity.getSwingAnimation(partialTick);
    }
}
