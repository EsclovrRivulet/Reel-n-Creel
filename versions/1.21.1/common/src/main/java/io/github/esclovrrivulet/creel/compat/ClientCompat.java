package io.github.esclovrrivulet.creel.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** ClientCompat for MINECRAFT 1.21.1: replaces common/.../compat/ClientCompat.java (26.3's) in the 1.21.1 build. The same members, each
 *  one line of 1.21.1's own API, but gameRenderState: 1.21.1 draws without render states, and the files that read them (the rods in the
 *  hand and their line, client.RodPose and client.RodLine) are not in the 1.21.1 build (versions/1.21.1/left_out.txt). */
public final class ClientCompat {
    private ClientCompat() {}

    /** 1.21.1's pose stack has no rotate helpers: the rotation is multiplied in (what 26.3's helpers do) */
    public static void rotateDegrees(PoseStack pose, Axis axis, float degrees) {
        pose.mulPose(axis.rotationDegrees(degrees));
    }

    /** 1.21.1's mulPose takes a Quaternionf, not the read-only view: a copy */
    public static void rotate(PoseStack pose, Quaternionfc rotation) {
        pose.mulPose(new Quaternionf(rotation));
    }

    /** 1.21.1 names the game renderer's camera accessor getMainCamera (26.2 and 26.3: mainCamera) */
    public static Camera mainCamera(GameRenderer renderer) {
        return renderer.getMainCamera();
    }
}
