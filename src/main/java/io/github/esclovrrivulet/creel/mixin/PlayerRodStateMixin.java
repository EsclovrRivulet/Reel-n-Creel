package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import io.github.esclovrrivulet.creel.client.RodHolder;

/** (0.16.4) Client only: two fields on every player, the rods' animation state (client.RodHolder: RodFlex's and RodAnim's). No
 *  injection, so nothing here can clash with another mod. */
@Mixin(Player.class)
public abstract class PlayerRodStateMixin implements RodHolder {
    @Unique
    private Object creel$flexState;
    @Unique
    private Object creel$animState;

    @Override
    public Object creel$flexState() {
        return this.creel$flexState;
    }

    @Override
    public void creel$setFlexState(Object state) {
        this.creel$flexState = state;
    }

    @Override
    public Object creel$animState() {
        return this.creel$animState;
    }

    @Override
    public void creel$setAnimState(Object state) {
        this.creel$animState = state;
    }
}
