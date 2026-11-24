package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** (1.21.1 build only) The bobber of one of our rods follows the server on the client. Our hook is moved by the server when the line is
 *  wound or a fish is reeled (fishing.CastingHook.wind, reel). 26.x's client takes a hook's position from the server like any entity's;
 *  1.21.1's fishing hook throws the server's position away (its lerpTo is empty: the client only simulates the bobber itself), so a
 *  wound bobber stood still on the screen while the server drew it in (the owner, 2026-11-24). Here the server's position is kept as a
 *  target and the bobber glides to it over the ticks between two updates, once it has come down (in flight the client's own
 *  simulation is ahead of the server's last word, and is left alone). Vanilla's rod's hook is not touched. */
@Mixin(FishingHook.class)
public abstract class FishingHookFollowMixin {
    /** the ticks a position from the server is glided over: 3, as the game gives every other entity (5 lagged behind 26.3's bobber) */
    @Unique
    private static final int CREEL$GLIDE = 3;
    @Unique
    private double creel$toX, creel$toY, creel$toZ;
    @Unique
    private int creel$steps;

    @Shadow
    public abstract Player getPlayerOwner();

    @Inject(method = "lerpTo", at = @At("HEAD"), require = 0, expect = 0)
    private void creel$serverPosition(double x, double y, double z, float yRot, float xRot, int steps, CallbackInfo ci) {
        Player player = this.getPlayerOwner();
        if (player != null && (player.getMainHandItem().getItem() instanceof CastingRodItem
            || player.getOffhandItem().getItem() instanceof CastingRodItem)) {
            this.creel$toX = x;
            this.creel$toY = y;
            this.creel$toZ = z;
            this.creel$steps = CREEL$GLIDE;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0, expect = 0)
    private void creel$glide(CallbackInfo ci) {
        if (this.creel$steps <= 0) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (self.getDeltaMovement().lengthSqr() > 0.0625) {
            return;                                     // in flight (faster than a quarter block a tick): the target waits until it is down
        }
        double f = 1.0 / this.creel$steps--;
        self.setPos(self.getX() + (this.creel$toX - self.getX()) * f, self.getY() + (this.creel$toY - self.getY()) * f,
            self.getZ() + (this.creel$toZ - self.getZ()) * f);
    }
}
