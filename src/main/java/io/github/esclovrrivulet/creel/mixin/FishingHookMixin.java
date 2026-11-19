package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import io.github.esclovrrivulet.creel.fishing.HookLevel;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.github.esclovrrivulet.creel.fishing.CastingHook;

/** Vanilla's fishing hook, changed only for our rods' own hook (fishing.CastingHook) and left exactly as it is for vanilla's rod:
 *  - catchingFish (the wait, the bite and vanilla's loot) is switched off for our hook, which runs its own bite instead;
 *  - shouldStopFishing asks our hook (it checks for the rod the line was cast with in hand where vanilla checks for its own rod);
 *  - when a hook takes or loses an entity while bobbing (only ours ever does: vanilla's takes one only in flight), it goes back to
 *    flying, so vanilla's own tick moves it on to riding the fish, or lets it fall back to the water. This runs on the client too, where
 *    our hook is an ordinary vanilla hook, so the bobber is seen riding the fish;
 *  - (0.14.0) one byte of synced data, since 0.16.2 the fish's level alone (fishing.HookLevel), written only by our hook; on
 *    vanilla's it stays 0 and is never sent. */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin implements HookLevel {
    @Shadow
    @Final
    private static EntityDataAccessor<Integer> DATA_HOOKED_ENTITY;

    /** (0.14.0) the fight's byte (since 0.16.2 the fish's level, HookLevel): defined after vanilla's own two (this initialiser runs at the end of the
     *  class's static initialiser), so its id is the same on the server and on every client */
    @Unique
    private static final EntityDataAccessor<Byte> CREEL$LEVEL = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.BYTE);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void creel$defineLevel(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(CREEL$LEVEL, (byte) 0);
    }

    @Override
    public int creel$fishLevel() {
        return ((Entity) (Object) this).getEntityData().get(CREEL$LEVEL);
    }

    @Override
    public void creel$setFishLevel(int value) {
        ((Entity) (Object) this).getEntityData().set(CREEL$LEVEL, (byte) value);
    }

    @Shadow
    private Entity hookedIn;

    @Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true)
    private void creel$ownBite(BlockPos pos, CallbackInfo ci) {
        if ((Object) this instanceof CastingHook hook) {
            hook.waitForBite(pos);
            ci.cancel();
        }
    }

    @Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
    private void creel$ownRod(Player player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CastingHook hook) {
            cir.setReturnValue(hook.stopFishing(player));
        }
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void creel$rideTheFish(EntityDataAccessor<?> key, CallbackInfo ci) {
        if (DATA_HOOKED_ENTITY.equals(key)) {
            CastingHook.State.afterHookedChanged((FishingHook) (Object) this, this.hookedIn != null);
        }
    }
}
