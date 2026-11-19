package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A fish on one of our rods' lines is kept (it does not despawn and is not counted in the spawn budgets) by vanilla's own persistence
 *  flag, and given back the flag it had when it comes off the line; vanilla has no way to clear the flag, hence this. */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("persistenceRequired")
    void creel$setPersistenceRequired(boolean required);
}
