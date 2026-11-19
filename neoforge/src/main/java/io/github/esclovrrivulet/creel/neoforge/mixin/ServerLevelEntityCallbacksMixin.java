package io.github.esclovrrivulet.creel.neoforge.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.neoforge.CreelNeoForge;

/** An entity loaded into, or unloaded from, a server level (CreelMod.onEntityLoad / onEntityUnload: the vanilla caps' counts), at the
 *  same two points Fabric's ServerEntityEvents.ENTITY_LOAD and ENTITY_UNLOAD fire: the end of the level's onTrackingStart and the start
 *  of its onTrackingEnd. NeoForge's EntityJoinLevelEvent fires elsewhere (once, when the entity is added, not again when its chunk comes
 *  back into range and it is tracked again), so the counts would drift from the Fabric build's. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerLevelEntityCallbacksMixin {
    @Shadow
    @Final
    ServerLevel this$0;

    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void creel$entityLoad(Entity entity, CallbackInfo ci) {
        CreelNeoForge.onTrackingStart(entity, this$0);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void creel$entityUnload(Entity entity, CallbackInfo ci) {
        CreelNeoForge.onTrackingEnd(entity, this$0);
    }
}
