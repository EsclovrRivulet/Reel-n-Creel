package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.HumanoidArm;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
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
import io.github.esclovrrivulet.creel.client.RodLine;
import io.github.esclovrrivulet.creel.client.RodLineState;

/** Client only, and purely cosmetic: the fishing line of a player holding one of our rods (client.RodLine). Its start is moved to the
 *  rod's tip, and the line is drawn white, translucent and dashed. Any other rod's line is left exactly as vanilla (or another mod)
 *  draws it.
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
        boolean ours = owner != null && RodLine.rodHeld(owner) != null && state.lineOriginOffset != null;
        mark.creel$setOurLine(ours);
        mark.creel$setTaut(ours ? RodLine.taut(hook, partialTick) : 0.0F);
        if (!ours) {
            return;
        }
        // the start as vanilla (or another mod) left it, then moved to our rod's tip: the same point vanilla measures it from
        Vec3 hookPoint = hook.getPosition(partialTick).add(0.0, 0.25, 0.0);
        Vec3 hand = hookPoint.add(state.lineOriginOffset);
        float swing = Mth.sin(Mth.sqrt(owner.getSwingAnimation(partialTick)) * Mth.PI);
        state.lineOriginOffset = RodLine.start(owner, swing, partialTick, hand).subtract(hookPoint);
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
