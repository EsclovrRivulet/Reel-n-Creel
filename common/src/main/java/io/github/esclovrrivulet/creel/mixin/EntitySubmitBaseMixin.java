package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodPose;

/** Client only, cosmetic, optional (0.12.3). The pose and offset the local player is submitted with, in whatever pass draws it: the
 *  world's (pose bare, offset from the camera) or a shader's shadow pass (pose turned to the sun's view). client.RodPose measures our
 *  rod's third-person tip from them, so the tip is a point relative to the player in every pass and no pass can spoil another's.
 *  Read only, at the head and the return of the call; nothing is replaced. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntitySubmitBaseMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD"), require = 0, expect = 0)
    private void creel$entityBase(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
                                  SubmitNodeCollector collector, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            RodPose.entityBase(avatar.id, poseStack.last().pose(), x, y, z);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("RETURN"), require = 0, expect = 0)
    private void creel$entityBaseEnd(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
                                     SubmitNodeCollector collector, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            RodPose.entityBaseEnd(avatar.id);
        }
    }
}
