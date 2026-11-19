package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.HeldRodMark;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.client.RodPose;

/** Client only, cosmetic, optional. Where each item layer is submitted, for our rods held in a hand:
 *  - just after the layer's display transform is applied, our rods' own animation turns the rod (client.RodAnim): there, every other
 *    transform (the arm, any mod's first-person animation) is already in the pose, so ours is added on top and nothing can replace it;
 *  - just before the pose is popped, the pose the rod was drawn with is captured (client.RodPose), so the line's start follows the rod
 *    as drawn, our animation included.
 *  Both @Inject; neither replaces anything. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateMixin {
    @Shadow
    @Final
    ItemStackRenderState this$0;

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
        shift = At.Shift.AFTER), require = 0, expect = 0)
    private void creel$animateRod(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        if ((Object) this.this$0 instanceof HeldRodMark mark && mark.creel$rod() != null) {
            RodAnim.apply(mark.creel$playerId(), mark.creel$rod(), mark.creel$context(), poseStack.last());
        }
    }

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), require = 0, expect = 0)
    private void creel$captureRodPose(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        if ((Object) this.this$0 instanceof HeldRodMark mark && mark.creel$rod() != null) {
            RodPose.capture(mark.creel$playerId(), mark.creel$rod(), mark.creel$context(), poseStack.last().pose(), mark.creel$step());
        }
    }
}
