package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.HumanoidArm;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodBobber;
import io.github.esclovrrivulet.creel.client.RodLine;
import io.github.esclovrrivulet.creel.client.RodLineState;

/** Client only, and purely cosmetic: the fishing line of a player holding one of our rods (client.RodLine). Its start is moved to the
 *  rod's tip, and the line is drawn white, translucent and dashed; (0.20.0) and its bobber is drawn solid (client.RodBobber). Any
 *  other rod's line and bobber are left exactly as vanilla (or another mod) draws them, except (0.24.0) vanilla's own Fishing Rod
 *  (exactly Items.FISHING_ROD), held in the hand as our model: its line leaves our model's tip guide and is drawn as ours, its bobber
 *  is ours; its sag is vanilla's (RodLine.lineRod, vanillaRodHeld).
 *
 *  Rules, after 0.11.2 failed to load beside another mod that redirects the same call:
 *  - nothing here replaces a vanilla call outright (no @Redirect, no @Overwrite). The start is set by an @Inject at the end of
 *    extractRenderState, after vanilla and any other mod have computed it. The line is changed by MixinExtras' @WrapOperation on the
 *    submit of the line, which chains: other mods' @WrapOperations nest with it, and a @Redirect of the same call by another mod is
 *    wrapped rather than fought; whenever the holder is not using one of our rods the original operation (whatever it has become) is
 *    called unchanged;
 *  - every injection is optional (require = 0, expect = 0): if one cannot apply, the game runs and shows the line as it would without us.
 *  A client never sees our hook class (the bobber is vanilla's own hook there), so the line is ours when the rod in the holding arm is. */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    /** Vanilla picks the arm a line leaves from (and the arm whose rod shows the cast picture, FishingRodCast) by testing the main hand
     *  for vanilla's rod class, which ours no longer extend (0.12.0): with one of ours in the main hand, the main arm. A
     *  @ModifyReturnValue (it chains with other mods); optional, like everything here. */
    @ModifyReturnValue(method = "getHoldingArm(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/entity/HumanoidArm;", at = @At("RETURN"),
        require = 0, expect = 0)
    private static HumanoidArm creel$holdingArm(HumanoidArm arm, Player player) {
        return player.getMainHandItem().getItem() instanceof CastingRodItem ? player.getMainArm() : arm;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V",
        at = @At("TAIL"), require = 0, expect = 0)
    private void creel$ourLineStart(FishingHook hook, FishingHookRenderState state, float partialTick, CallbackInfo ci) {
        if (!((Object) state instanceof RodLineState mark)) {
            return;
        }
        Player owner = hook.getPlayerOwner();
        // (0.24.0) ours: one of our rods, or vanilla's own Fishing Rod (its line and bobber drawn as ours; RodLine.lineRod)
        boolean rodOfOurs = owner != null && RodLine.rodHeld(owner) != null;
        boolean ours = owner != null && (rodOfOurs || RodLine.vanillaRodHeld(owner)) && state.lineOriginOffset != null;
        mark.creel$setOurLine(ours);
        mark.creel$setTaut(ours && rodOfOurs ? RodLine.taut(hook, partialTick) : 0.0F);        // vanilla's rod: vanilla's slack curve
        if (!ours) {
            return;
        }
        // the start as vanilla (or another mod) left it, then moved to our rod's tip: the same point vanilla measures it from
        Vec3 hookPoint = hook.getPosition(partialTick).add(0.0, 0.25, 0.0);
        Vec3 hand = hookPoint.add(state.lineOriginOffset);
        float swing = Mth.sin(Mth.sqrt(owner.getSwingAnimation(partialTick)) * Mth.PI);
        state.lineOriginOffset = RodLine.start(owner, swing, partialTick, hand).subtract(hookPoint);
    }

    /** (0.20.0) The first geometry vanilla submits is the bobber, one flat picture turned to the camera: for one of our rods' lines it
     *  is client.RodBobber's four boxes instead, standing in the world, in vanilla's own render type (so with the game's own bobber
     *  texture). The pose here is already scaled and turned to the camera by vanilla; the turn is taken back (its inverse) and the
     *  model turned about the vertical so its front faces along the line toward the one fishing, who then sees the bobber as
     *  vanilla's picture shows it, while anyone else sees it from where they stand. Any other rod's bobber: the original, untouched. */
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
            ordinal = 0), require = 0, expect = 0)
    private void creel$ourBobber(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer,
                                 Operation<Void> original, @Local(argsOnly = true) FishingHookRenderState state,
                                 @Local(argsOnly = true) CameraRenderState camera) {
        if (!((Object) state instanceof RodLineState mark) || !mark.creel$ourLine() || state.lineOriginOffset == null || camera == null
            || camera.orientation == null) {
            original.call(collector, poseStack, type, renderer);
            return;
        }
        int light = state.lightCoords;
        float toOwner = (float) Math.atan2(state.lineOriginOffset.x, state.lineOriginOffset.z);
        poseStack.pushPose();
        poseStack.rotate(new Quaternionf(camera.orientation).conjugate());
        poseStack.rotate(Axis.YP.rotation(toOwner));
        collector.submitCustomGeometry(poseStack, type, (pose, out) -> RodBobber.draw(pose, out, light));
        poseStack.popPose();
    }

    /** the second geometry vanilla submits is the line: ours when the line is one of our rods', else the original, untouched */
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
            ordinal = 1), require = 0, expect = 0)
    private void creel$ourLine(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer,
                               Operation<Void> original, @Local(argsOnly = true) FishingHookRenderState state) {
        if (!((Object) state instanceof RodLineState mark) || !mark.creel$ourLine() || state.lineOriginOffset == null) {
            original.call(collector, poseStack, type, renderer);
            return;
        }
        float x = (float) state.lineOriginOffset.x, y = (float) state.lineOriginOffset.y, z = (float) state.lineOriginOffset.z;
        double hx = state.x, hy = state.y, hz = state.z;
        int light = state.lightCoords;
        float taut = mark.creel$taut();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RodLine.DOT_TEXTURE),
            (pose, out) -> RodLine.draw(x, y, z, hx, hy, hz, light, taut, pose, out));
    }
}
