package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import io.github.esclovrrivulet.creel.client.RodLineState;

/** Client only: on vanilla's fishing hook render state, whether its line is one of our rods' and how taut it is drawn (client.RodLineState). */
@Mixin(FishingHookRenderState.class)
public abstract class FishingHookRenderStateMixin implements RodLineState {
    @Unique
    private boolean creel$ours;

    @Unique
    private float creel$tautNow;

    @Override
    public boolean creel$ourLine() {
        return this.creel$ours;
    }

    @Override
    public void creel$setOurLine(boolean ours) {
        this.creel$ours = ours;
    }

    @Override
    public float creel$taut() {
        return this.creel$tautNow;
    }

    @Override
    public void creel$setTaut(float taut) {
        this.creel$tautNow = taut;
    }
}
