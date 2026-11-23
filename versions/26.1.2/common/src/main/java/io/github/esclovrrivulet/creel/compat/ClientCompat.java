package io.github.esclovrrivulet.creel.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.joml.Quaternionfc;

/** ClientCompat for MINECRAFT 26.1.2: replaces common/.../compat/ClientCompat.java (26.3's) in the 26.1.2 build. The same members, each
 *  one line of 26.1.2's own API. */
public final class ClientCompat {
    private ClientCompat() {}

    /** 26.1.2's pose stack has no rotate helpers: the rotation is multiplied in (what 26.3's helpers do) */
    public static void rotateDegrees(PoseStack pose, Axis axis, float degrees) {
        pose.mulPose(axis.rotationDegrees(degrees));
    }

    public static void rotate(PoseStack pose, Quaternionfc rotation) {
        pose.mulPose(rotation);
    }

    /** 26.1.2 names the game renderer's accessors getMainCamera and getGameRenderState (26.2 and 26.3: mainCamera, gameRenderState) */
    public static Camera mainCamera(GameRenderer renderer) {
        return renderer.getMainCamera();
    }

    public static GameRenderState gameRenderState(GameRenderer renderer) {
        return renderer.getGameRenderState();
    }
}
