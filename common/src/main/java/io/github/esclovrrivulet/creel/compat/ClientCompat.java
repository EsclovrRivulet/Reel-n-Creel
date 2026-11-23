package io.github.esclovrrivulet.creel.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.joml.Quaternionfc;

/** The client half of Compat (client only: never loaded on a dedicated server): the game's client calls that are not the same in each
 *  Minecraft version the mod is built for. THIS FILE IS 26.3's; each older version's copy is in
 *  versions/<version>/common/src/main/java/.../compat/ClientCompat.java (see versions/versions.properties). One line of the game's API per member. */
public final class ClientCompat {
    private ClientCompat() {}

    /** turns the pose by an angle in degrees about an axis */
    public static void rotateDegrees(PoseStack pose, Axis axis, float degrees) {
        pose.rotateDegrees(axis, degrees);
    }

    /** turns the pose by a rotation */
    public static void rotate(PoseStack pose, Quaternionfc rotation) {
        pose.rotate(rotation);
    }

    /** the camera the world is drawn from */
    public static Camera mainCamera(GameRenderer renderer) {
        return renderer.mainCamera();
    }

    /** the game renderer's state of the frame (camera, options) */
    public static GameRenderState gameRenderState(GameRenderer renderer) {
        return renderer.gameRenderState();
    }
}
