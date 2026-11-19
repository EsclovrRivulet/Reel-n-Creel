package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodPose;

/** Client only, cosmetic, optional (0.12.3). The pose the first-person hands start from, whoever draws them: vanilla starts it from
 *  the inverse camera rotation with the view bobbing in it, Iris's hand pass from a bare pose stack with the bobbing in its projection.
 *  client.RodPose measures our rod's tip from this pose, so the line's start no longer depends on either convention. Read only, at the
 *  head and the return of the call; nothing is replaced. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsBaseMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), require = 0, expect = 0)
    private void creel$handBase(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
                                FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        RodPose.handBase(poseStack.last().pose());
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"), require = 0, expect = 0)
    private void creel$handBaseEnd(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
                                   FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        RodPose.handBase(null);
    }
}
