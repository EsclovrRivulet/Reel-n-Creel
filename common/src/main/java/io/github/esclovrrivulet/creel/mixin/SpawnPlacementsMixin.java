package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.github.esclovrrivulet.creel.habitat.VanillaCaps;

/** The vanilla caps (habitat.VanillaCaps): at the head of vanilla's spawn rules check, which its natural spawner asks before it makes
 *  the entity, a natural spawn of a capped type that has reached its cap is refused. Every other reason, and every uncapped type,
 *  passes straight through to vanilla's own rules. */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsMixin {
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private static <T extends Entity> void creel$vanillaCap(EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
                                                            RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (reason == EntitySpawnReason.NATURAL && !VanillaCaps.allows(type, level.getLevel())) {
            cir.setReturnValue(false);
        }
    }
}
