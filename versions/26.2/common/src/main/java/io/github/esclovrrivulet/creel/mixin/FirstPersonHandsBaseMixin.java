package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodPose;

/** FirstPersonHandsBaseMixin for MINECRAFT 26.2 (replaces the base copy in the 26.2 build; what it does is described there). 26.2 draws
 *  the first-person hands in ItemInHandRenderer.submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int), where
 *  26.3 has FirstPersonHandsAndItemsRenderer with render states. The same two reads, at the head and the return. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonHandsBaseMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), require = 0, expect = 0)
    private void creel$handBase(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
                                int lightCoords, CallbackInfo ci) {
        RodPose.handBase(poseStack.last().pose());
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"), require = 0, expect = 0)
    private void creel$handBaseEnd(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
                                   int lightCoords, CallbackInfo ci) {
        RodPose.handBase(null);
    }
}
