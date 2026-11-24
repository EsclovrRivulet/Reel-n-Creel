package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodPose;

/** FirstPersonHandsBaseMixin for MINECRAFT 1.21.1 (replaces the base copy in the 1.21.1 build; what it does is described there). 1.21.1
 *  draws the hands with ItemInHandRenderer.renderHandsWithItems(float, PoseStack, BufferSource, LocalPlayer, int), as 26.1.2 does but
 *  into a buffer source rather than a submit collector. Read only, at the head and the return of the call. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonHandsBaseMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0, expect = 0)
    private void creel$handBase(float frameInterp, PoseStack poseStack, MultiBufferSource.BufferSource buffers, LocalPlayer player,
                                int lightCoords, CallbackInfo ci) {
        RodPose.handBase(poseStack.last().pose());
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 0, expect = 0)
    private void creel$handBaseEnd(float frameInterp, PoseStack poseStack, MultiBufferSource.BufferSource buffers, LocalPlayer player,
                                   int lightCoords, CallbackInfo ci) {
        RodPose.handBase(null);
    }
}
