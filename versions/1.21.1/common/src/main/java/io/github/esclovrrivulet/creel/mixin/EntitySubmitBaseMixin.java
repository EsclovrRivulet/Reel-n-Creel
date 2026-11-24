package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodPose;

/** EntitySubmitBaseMixin for MINECRAFT 1.21.1 (replaces the base copy in the 1.21.1 build; what it does is described there). 1.21.1
 *  draws an entity at once, with EntityRenderDispatcher.render(entity, x, y, z, yaw, partialTick, pose, buffers, light), where (x, y, z)
 *  is its drawn place from the pass's camera, as 26.x's submit is given it; the entity itself, not a render state, says who it is.
 *  Read only, at the head and the return of the call. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntitySubmitBaseMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), require = 0, expect = 0)
    private void creel$entityBase(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack poseStack,
                                  MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof Player) {
            RodPose.entityBase(entity.getId(), poseStack.last().pose(), x, y, z);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN"), require = 0, expect = 0)
    private void creel$entityBaseEnd(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof Player) {
            RodPose.entityBaseEnd(entity.getId());
        }
    }
}
