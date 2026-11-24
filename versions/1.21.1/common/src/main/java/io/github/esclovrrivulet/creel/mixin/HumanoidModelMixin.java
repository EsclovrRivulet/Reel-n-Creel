package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.compat.ClientBridge;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** HumanoidModelMixin for MINECRAFT 1.21.1 (replaces the base copy in the 1.21.1 build; what it does is described there). 1.21.1's
 *  model is set up from the entity itself, setupAnim(entity, limb swing, limb amount, age in ticks, head yaw, head pitch), with the arm
 *  poses on the model (26.x: on the render state). 26.x's state.ticksUsingItem is the entity's ticks of use plus the partial tick
 *  (LivingEntity.getTicksUsingItem(partialTick)); here the partial tick is the age's fraction (the renderer passes the entity's tick
 *  count plus the partial tick as the age). THROW_SPEAR is 26.x's THROW_TRIDENT, the same pose. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    public HumanoidModel.ArmPose rightArmPose;
    @Shadow
    public HumanoidModel.ArmPose leftArmPose;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/world/entity/LivingEntity;F)V"),
        require = 0, expect = 0)
    private void creel$chargeArm(LivingEntity entity, float limbSwing, float limbAmount, float ageInTicks, float headYaw, float headPitch,
                                 CallbackInfo ci) {
        float ticks = entity.isUsingItem() ? entity.getTicksUsingItem() + Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F) : 0.0F;
        creel$charge(this.rightArm, this.rightArmPose, ClientBridge.heldByArm(entity, HumanoidArm.RIGHT), ticks);
        creel$charge(this.leftArm, this.leftArmPose, ClientBridge.heldByArm(entity, HumanoidArm.LEFT), ticks);
    }

    @Unique
    private static void creel$charge(ModelPart arm, HumanoidModel.ArmPose pose, ItemStack stack, float ticksUsingItem) {
        if (pose != HumanoidModel.ArmPose.THROW_SPEAR || stack == null || !(stack.getItem() instanceof CastingRodItem)) {
            return;
        }
        // THROW_SPEAR left it at (arm * 0.5 - PI); the holding pose, ITEM, is (arm * 0.5 - PI / 10), 0.9 PI further down: from there
        // the arm is raised by up to ARM_DRAW_DEGREES as the charge builds
        float drawn = RodAnim.armEase(ticksUsingItem / RodAnim.CHARGE_TICKS) * RodAnim.ARM_DRAW_DEGREES * Mth.DEG_TO_RAD;
        arm.xRot += Mth.PI * 0.9F - drawn;
    }
}
