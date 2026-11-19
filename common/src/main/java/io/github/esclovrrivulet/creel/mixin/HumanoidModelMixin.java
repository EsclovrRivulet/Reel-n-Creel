package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** Client only, cosmetic, optional. Third person: while a player charges a cast with one of our rods (mixin.AvatarRendererMixin marks
 *  that arm with the game's THROW_TRIDENT pose), the arm is not snapped to that pose (arm straight up, which turns the rod down behind
 *  the back) but raised with the charge: from the ordinary holding pose (the game's ITEM pose) by up to client.RodAnim.ARM_DRAW_DEGREES,
 *  the rod up and back over the shoulder, by the charge's smooth ease (RodAnim.armEase), from the synced ticks of use, so other players
 *  see it too. Reached at full charge and held; on release the arm returns to holding and the game's swing (sent with the cast) throws
 *  it forward. An @Inject just before the attack animation is set up, after both
 *  arms are posed; it only adjusts that arm's x rotation. require = 0. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"),
        require = 0, expect = 0)
    private void creel$chargeArm(HumanoidRenderState state, CallbackInfo ci) {
        creel$charge(this.rightArm, state.rightArmPose, state.rightHandItemStack, state);
        creel$charge(this.leftArm, state.leftArmPose, state.leftHandItemStack, state);
    }

    @Unique
    private static void creel$charge(ModelPart arm, HumanoidModel.ArmPose pose, ItemStack stack, HumanoidRenderState state) {
        if (pose != HumanoidModel.ArmPose.THROW_TRIDENT || stack == null || !(stack.getItem() instanceof CastingRodItem)) {
            return;
        }
        // THROW_TRIDENT left it at (arm * 0.5 - PI); the holding pose, ITEM, is (arm * 0.5 - PI / 10), 0.9 PI further down: from there
        // the arm is raised by up to ARM_DRAW_DEGREES as the charge builds
        float drawn = RodAnim.armEase(state.ticksUsingItem / RodAnim.CHARGE_TICKS) * RodAnim.ARM_DRAW_DEGREES * Mth.DEG_TO_RAD;
        arm.xRot += Mth.PI * 0.9F - drawn;
    }
}
