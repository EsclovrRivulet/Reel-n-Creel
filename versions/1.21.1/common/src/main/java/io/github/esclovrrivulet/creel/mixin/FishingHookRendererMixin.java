package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.RodBobber;
import io.github.esclovrrivulet.creel.client.RodLine;
import io.github.esclovrrivulet.creel.compat.Compat;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** FishingHookRendererMixin for MINECRAFT 1.21.1 (replaces the base copy in the 1.21.1 build; what it does and its rules are described
 *  there: our rods' line from the rod's tip, drawn as client.RodLine draws it, and their bobber solid, client.RodBobber; vanilla's own
 *  Fishing Rod's too; any other rod's line and bobber untouched). 1.21.1 draws the hook at once, in render(hook, yaw, partialTick,
 *  pose, buffers, light), with no render state to carry the decision, so the decision is made at the head of each call and kept in
 *  this class for that call (render thread):
 *  - at the head: whether the line is ours (the owner holds one of our rods in the holding arm, or vanilla's Fishing Rod) and how taut;
 *  - vanilla's four bobber vertices and its seventeen line segments are left out for our lines (@WrapWithCondition on each call; any
 *    other line calls them as always);
 *  - the line's start: vanilla's hand position (getPlayerHandPos, or whatever another mod made of it) moved to our rod's tip, as 26.x
 *    moves it at the end of extractRenderState (@ModifyExpressionValue on that call);
 *  - just before the call's last pop, our line and our bobber are drawn: the line in the hook's pose, the bobber in the pose vanilla
 *    draws its picture in (scaled by a half) without its turn to the camera, turned about the vertical to face along the line toward
 *    the one fishing; 26.x takes the camera's turn back out of vanilla's pose, which comes to the same pose.
 *  And 26.x's change to vanilla's choice of arm (FishingHookRenderer.getHoldingArm there): 1.21.1 decides the arm inside
 *  getPlayerHandPos (the main arm only for vanilla's rod; NeoForge: any item that can cast), so the side it stores is the main arm's
 *  for one of our rods in the main hand. This is the start used when our rod's tip is not known (another player's rod).
 *  Every injection optional (require = 0); nothing replaced outright. */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @Unique
    private static final String RENDER = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** the call under way (render thread): whether its line is ours, how taut, and where it starts (null until known) */
    @Unique
    private static boolean creel$ours;
    @Unique
    private static float creel$taut;
    @Unique
    private static Vec3 creel$start;

    @ModifyVariable(method = "getPlayerHandPos", at = @At("STORE"), ordinal = 0, require = 0, expect = 0)
    private int creel$handSide(int side, @Local(argsOnly = true) Player player) {
        if (player.getMainHandItem().getItem() instanceof CastingRodItem) {
            return player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        }
        return side;
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 0, expect = 0)
    private void creel$decide(FishingHook hook, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
                              CallbackInfo ci) {
        Player owner = hook.getPlayerOwner();
        // (0.24.0) ours: one of our rods, or vanilla's own Fishing Rod (its line and bobber drawn as ours; RodLine.lineRod)
        boolean rodOfOurs = owner != null && RodLine.rodHeld(owner) != null;
        creel$ours = owner != null && (rodOfOurs || RodLine.vanillaRodHeld(owner));
        creel$taut = creel$ours && rodOfOurs ? RodLine.taut(hook, partialTick) : 0.0F;        // vanilla's rod: vanilla's slack curve
        creel$start = null;
    }

    @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;IFIII)V"),
        require = 0, expect = 0)
    private boolean creel$vanillaBobber(VertexConsumer out, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        return !creel$ours;
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;getPlayerHandPos(Lnet/minecraft/world/entity/player/Player;FF)Lnet/minecraft/world/phys/Vec3;"),
        require = 0, expect = 0)
    private Vec3 creel$ourLineStart(Vec3 hand, @Local(argsOnly = true) FishingHook hook, @Local(argsOnly = true, ordinal = 1) float partialTick) {
        Player owner = hook.getPlayerOwner();
        if (!creel$ours || owner == null) {
            return hand;
        }
        // the start as vanilla (or another mod) left it, then moved to our rod's tip
        float swing = Mth.sin(Mth.sqrt(Compat.swingProgress(owner, partialTick)) * Mth.PI);
        creel$start = RodLine.start(owner, swing, partialTick, hand);
        return creel$start;
    }

    @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;stringVertex(FFFLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FF)V"),
        require = 0, expect = 0)
    private boolean creel$vanillaLine(float x, float y, float z, VertexConsumer out, PoseStack.Pose pose, float t0, float t1) {
        return !(creel$ours && creel$start != null);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 1),
        require = 0, expect = 0)
    private void creel$ourLineAndBobber(FishingHook hook, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                       int light, CallbackInfo ci) {
        if (!creel$ours || creel$start == null) {
            return;
        }
        Vec3 offset = creel$start.subtract(hook.getPosition(partialTick).add(0.0, 0.25, 0.0));
        // the hook's drawn place in the world, the pose's origin (as the entity is placed: between its old and new position)
        double hx = Mth.lerp(partialTick, hook.xOld, hook.getX()), hy = Mth.lerp(partialTick, hook.yOld, hook.getY());
        double hz = Mth.lerp(partialTick, hook.zOld, hook.getZ());
        RodLine.draw((float) offset.x, (float) offset.y, (float) offset.z, hx, hy, hz, light, creel$taut, poseStack.last(),
            buffers.getBuffer(RenderType.entityTranslucent(RodLine.DOT_TEXTURE)));
        float toOwner = (float) Math.atan2(offset.x, offset.z);
        VertexConsumer bobber = buffers.getBuffer(RenderType.entityCutout(((FishingHookRenderer) (Object) this).getTextureLocation(hook)));
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotation(toOwner));
        RodBobber.draw(poseStack.last(), bobber, light);
        poseStack.popPose();
        creel$ours = false;
        creel$start = null;
    }
}
