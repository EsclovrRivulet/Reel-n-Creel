package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.mixin.GameRendererInvoker;

/** RodView for MINECRAFT 1.21.1 (replaces common/.../client/RodView.java in the 1.21.1 build): the same members, read from 1.21.1's
 *  camera and game renderer, which has no render states. The world's and the hand's fields of view and the frame's view bobbing are
 *  the game's own private methods (getFov, bobHurt, bobView), reached through mixin.GameRendererInvoker: Fabric runs 1.21.1 under
 *  other names, so reflection by name, as 26.x's copy does, would not find them. If the invoker is missing, the bob is dropped from
 *  the line's start (as 26.x's copy does when its reflection fails) and the fields of view fall back to the game's defaults.
 *  Each is computed with the frame's partial tick, as GameRenderer.renderLevel computes them. Render thread only. */
final class RodView {
    private RodView() {}

    private static final Logger LOG = LoggerFactory.getLogger("creel");
    private static final Matrix4f BOB = new Matrix4f(), VIEW = new Matrix4f();
    private static final Quaternionf TURN = new Quaternionf();
    private static final PoseStack BOB_STACK = new PoseStack();
    /** false once the invoker failed: the game's defaults from then on, and one warning */
    private static boolean invoker = true;

    private static float partialTick(Minecraft mc) {
        return mc.getTimer().getGameTimeDeltaPartialTick(true);
    }

    private static Camera camera(Minecraft mc) {
        return mc.gameRenderer.getMainCamera();
    }

    /** whether the frame's camera is known (none before the first frame) */
    static boolean known(Minecraft mc) {
        return camera(mc).isInitialized();
    }

    /** the hand's field of view this frame, in degrees (the game's getFov without the player's setting; 0 when it cannot be had,
     *  which RodPose reads as the game's fixed 70) */
    static float hudFov(Minecraft mc) {
        if (invoker) {
            try {
                return (float) ((GameRendererInvoker) mc.gameRenderer).creel$getFov(camera(mc), partialTick(mc), false);
            } catch (LinkageError | RuntimeException e) {
                failed(e);
            }
        }
        return 0.0F;
    }

    /** the world's field of view, in degrees (the player's setting alone when the game's own cannot be had) */
    static double fov(Minecraft mc) {
        if (invoker) {
            try {
                return ((GameRendererInvoker) mc.gameRenderer).creel$getFov(camera(mc), partialTick(mc), true);
            } catch (LinkageError | RuntimeException e) {
                failed(e);
            }
        }
        return mc.options.fov().get();
    }

    /** the camera's turn, as the world is drawn with it (GameRenderer.renderLevel: the camera's rotation, inverted); null when unknown.
     *  A scratch matrix (render thread), good until the next call */
    static Matrix4fc viewRotation(Minecraft mc) {
        Camera camera = camera(mc);
        return camera.isInitialized() ? VIEW.rotation(camera.rotation().conjugate(TURN)) : null;
    }

    /** the camera's place in the world (null when unknown) */
    static Vec3 position(Minecraft mc) {
        Camera camera = camera(mc);
        return camera.isInitialized() ? camera.getPosition() : null;
    }

    /** the world's bob and hurt tilt this frame, by the game's own methods (as GameRenderer.renderLevel puts them in the projection);
     *  null when they cannot be called. A scratch matrix (render thread), good until the next call */
    static Matrix4f bob(Minecraft mc) {
        if (!invoker) {
            return null;
        }
        try {
            PoseStack stack = BOB_STACK;
            while (!stack.clear()) {           // 1.21.1: clear() says whether only the first pose is left
                stack.popPose();            // nothing the game's methods do pushes, but a pose left pushed must not carry over
            }
            stack.setIdentity();
            float pt = partialTick(mc);
            GameRendererInvoker game = (GameRendererInvoker) mc.gameRenderer;
            game.creel$bobHurt(stack, pt);
            if (mc.options.bobView().get()) {
                game.creel$bobView(stack, pt);
            }
            return BOB.set(stack.last().pose());
        } catch (LinkageError | RuntimeException e) {
            failed(e);
            return null;
        }
    }

    private static void failed(Throwable e) {
        invoker = false;
        LOG.warn("creel: cannot reach the game's field of view and view bobbing, the line's first-person start uses the defaults: {}", e.toString());
    }
}
