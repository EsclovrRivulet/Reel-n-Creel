package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.compat.ClientCompat;

import java.lang.reflect.Method;

/** (1.0.0, for the 1.21.1 port) What client.RodPose reads of the frame's camera: where it is, how it is turned, the world's and the
 *  hand's fields of view, and the frame's view bobbing. Moved here out of RodPose unchanged, so that RodPose's arithmetic is one file
 *  for every Minecraft version: 1.21.1 has no render states and keeps its own copy of this class
 *  (versions/1.21.1/common/src/main/java/.../client/RodView.java), with the same members. Render thread only. */
final class RodView {
    private RodView() {}

    private static final Logger LOG = LoggerFactory.getLogger("creel");
    private static final Matrix4f BOB = new Matrix4f();
    private static final PoseStack BOB_STACK = new PoseStack();
    private static final Object[] BOB_ARGS = new Object[2];
    private static Method bobHurt, bobView;
    private static boolean reflected;

    private static CameraRenderState camera(Minecraft mc) {
        return ClientCompat.gameRenderState(mc.gameRenderer).levelRenderState.cameraRenderState;
    }

    /** whether the frame's camera is known (none before the first frame) */
    static boolean known(Minecraft mc) {
        return camera(mc) != null;
    }

    /** the hand's field of view this frame, in degrees (0 when the game has not set it) */
    static float hudFov(Minecraft mc) {
        return camera(mc).hudFov;
    }

    /** the world's field of view, in degrees */
    static double fov(Minecraft mc) {
        return ClientCompat.mainCamera(mc.gameRenderer).getFov();
    }

    /** the camera's turn, as the world is drawn with it (null when unknown) */
    static Matrix4fc viewRotation(Minecraft mc) {
        CameraRenderState cam = camera(mc);
        return cam == null ? null : cam.viewRotationMatrix;
    }

    /** the camera's place in the world (null when unknown) */
    static Vec3 position(Minecraft mc) {
        CameraRenderState cam = camera(mc);
        return cam == null ? null : cam.pos;
    }

    /** the world's bob and hurt tilt this frame, by the game's own methods; null when they cannot be called. The matrix returned is a
     *  scratch one (render thread), good until the next call. The methods are reached by reflection, not an @Invoker, so that a
     *  missing or changed method only drops the bob from the line's start (the fallback) instead of failing the mod. */
    static Matrix4f bob(Minecraft mc) {
        if (!reflected) {
            reflected = true;
            try {
                bobHurt = GameRenderer.class.getDeclaredMethod("bobHurt", CameraRenderState.class, PoseStack.class);
                bobView = GameRenderer.class.getDeclaredMethod("bobView", CameraRenderState.class, PoseStack.class);
                bobHurt.setAccessible(true);
                bobView.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.warn("creel: cannot reach the view bobbing, the line's first-person start ignores it: {}", e.toString());
                bobHurt = bobView = null;
            }
        }
        if (bobHurt == null) {
            return null;
        }
        try {
            PoseStack stack = BOB_STACK;
            while (!stack.isEmpty()) {
                stack.popPose();            // nothing the game's methods do pushes, but a pose left pushed must not carry over
            }
            stack.setIdentity();
            BOB_ARGS[0] = camera(mc);
            BOB_ARGS[1] = stack;
            bobHurt.invoke(mc.gameRenderer, BOB_ARGS);
            if (ClientCompat.gameRenderState(mc.gameRenderer).optionsRenderState.bobView) {
                bobView.invoke(mc.gameRenderer, BOB_ARGS);
            }
            return BOB.set(stack.last().pose());
        } catch (ReflectiveOperationException | RuntimeException e) {
            bobHurt = null;
            return null;
        }
    }
}
