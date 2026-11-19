package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What our rods' hook (fishing.CastingHook, a FishingHook of vanilla's own) needs of vanilla's private parts: to hang a fish on
 *  the line, and the Luck of the Sea and Lure levels the rod gave it. nibble is set only by the fishtest hook, to make vanilla's own rod
 *  bite on the spot. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Invoker("setHookedEntity")
    void creel$setHookedEntity(Entity entity);

    @Accessor("luck")
    int creel$luck();

    @Accessor("lureSpeed")
    int creel$lureSpeed();

    @Accessor("nibble")
    void creel$setNibble(int ticks);
}
