package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.HeldRodModels;

/** (1.21.1 build only) Client only, cosmetic, optional. ItemRenderer.render(stack, context, left hand, pose, buffers, light, overlay,
 *  model) draws every item, with the model already picked for it; for our rods and vanilla's three sticks:
 *  - at its head, the model argument becomes the held model in any context but the inventory, the ground and a frame
 *    (client.HeldRodModels.held: what 26.x's item definitions do). A @ModifyVariable on the argument: it chains with other mods and
 *    leaves any other item's model as it was given;
 *  - just after the display transform (the call's first PoseStack.translate, the -0.5 that puts the model's corner at the origin, which
 *    26.x's ItemTransform.apply ends with), our rod's own animation turns it (client.RodAnim; 26.x: ItemLayerRenderStateMixin);
 *  - just before the pose is popped, the pose it was drawn with is captured for the line's start (client.RodPose).
 *  The same three places in NeoForge's patched copy of the method. Nothing is replaced; every injection is optional. */
@Mixin(ItemRenderer.class)
public abstract class HeldItemModelMixin {
    @Unique
    private static final String RENDER = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V";

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, require = 0, expect = 0)
    private BakedModel creel$heldModel(BakedModel model, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context) {
        return HeldRodModels.held(model, stack, context);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0, shift = At.Shift.AFTER),
        require = 0, expect = 0)
    private void creel$animateRod(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack, MultiBufferSource buffers,
                                  int light, int overlay, BakedModel model, CallbackInfo ci) {
        HeldRodModels.animate(poseStack.last());
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), require = 0, expect = 0)
    private void creel$captureRodPose(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack, MultiBufferSource buffers,
                                      int light, int overlay, BakedModel model, CallbackInfo ci) {
        HeldRodModels.capture(poseStack.last().pose());
    }
}
