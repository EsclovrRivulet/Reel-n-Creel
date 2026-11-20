package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** (0.20.0) The bobber of our rods, in three dimensions (client only; mixin.FishingHookRendererMixin). The owner's choice
 *  (2026-11-20, over three floats of our own, tools/bobber_proto.py): vanilla's bobber, made solid. Vanilla draws its bobber as one
 *  flat picture turned to the camera; ours is four boxes standing in the world, drawn in the place of that picture with vanilla's own
 *  render type, so with the game's own bobber texture: nothing of the game's is copied into the mod, and a resource pack that
 *  repaints the bobber repaints ours (as long as it keeps the picture's layout: the faces are cut from it by fractions of the
 *  picture, so a pack of any resolution fits).
 *
 *  The picture is 8 by 8 (entity/fishing/fishing_hook.png), drawn by vanilla across a square of one unit in a pose scaled by a half:
 *  one texel is an eighth of a unit, a sixteenth of a block. Its parts: the float, 3 by 3 texels (columns 3 to 5, rows 1 to 3: white,
 *  red, white, the right column darker); under it the hook's shank (column 4, rows 4 to 7), its bend (column 3, row 7) and its point
 *  (column 2, row 6). Here the float is a cube of 3 texels, the hook's three parts one texel thick, each in the place the picture has
 *  it, shifted half a texel so the shank stands on the middle (the line's end) and the model can turn about it.
 *
 *  Every face is cut from the picture by where it is: the two broad faces (front, back) show their part of the picture as vanilla's
 *  does; a side shows the picture's column at that side, drawn across the depth (so the float's right side is the darker grey and
 *  red vanilla shades it with); the top and the bottom show the row at that end. The faces carry their true normals, so the game
 *  lights them as it lights any model.
 *
 *  Only our rods' bobbers: vanilla's rod, and any other mod's, keep vanilla's picture (the mixin calls the original). */
public final class RodBobber {
    /** one texel of the picture, in the units vanilla draws it in */
    private static final float T = 1.0F / 8.0F;
    /** half a texel: the picture's column 4 (the shank) is centred on the middle */
    private static final float SHIFT = -0.5F - T / 2.0F;
    /** the parts as the picture's texels: first column, first row, columns, rows, and the depth in texels */
    private static final int[][] PARTS = {
        {3, 1, 3, 3, 3},        // the float
        {4, 4, 1, 4, 1},        // the hook's shank
        {3, 7, 1, 1, 1},        // its bend
        {2, 6, 1, 1, 1},        // its point
    };

    private RodBobber() {}

    /** the four boxes, into vanilla's bobber render type, in the pose vanilla draws its picture in (scaled by a half) but not turned to
     *  the camera: x across the picture, y up, z toward the one fishing */
    public static void draw(PoseStack.Pose pose, VertexConsumer out, int light) {
        for (int[] p : PARTS) {
            float x0 = p[0] * T + SHIFT, x1 = x0 + p[2] * T;
            float y1 = 0.5F - p[1] * T, y0 = y1 - p[3] * T;
            float z1 = p[4] * T / 2.0F, z0 = -z1;
            float u0 = p[0] * T, u1 = u0 + p[2] * T, v0 = p[1] * T, v1 = v0 + p[3] * T;
            // front and back: the part of the picture itself, placed by x and y
            quad(pose, out, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v1, u1, v1, u1, v0, u0, v0, 0, 0, 1);
            quad(pose, out, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u1, v1, u0, v1, u0, v0, u1, v0, 0, 0, -1);
            // the sides: the column at that side, across the depth
            quad(pose, out, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u1 - T, v1, u1, v1, u1, v0, u1 - T, v0, 1, 0, 0);
            quad(pose, out, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v1, u0 + T, v1, u0 + T, v0, u0, v0, -1, 0, 0);
            // the top and the bottom: the row at that end, across the depth
            quad(pose, out, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0 + T, u1, v0 + T, u1, v0, u0, v0, 0, 1, 0);
            quad(pose, out, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v1 - T, u1, v1 - T, u1, v1, u0, v1, 0, -1, 0);
        }
    }

    /** one face, its corners counter-clockwise seen from outside, each with its place on the picture */
    private static void quad(PoseStack.Pose pose, VertexConsumer out, int light,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float au, float av, float bu, float bv, float cu, float cv, float du, float dv, float nx, float ny, float nz) {
        vertex(pose, out, light, ax, ay, az, au, av, nx, ny, nz);
        vertex(pose, out, light, bx, by, bz, bu, bv, nx, ny, nz);
        vertex(pose, out, light, cx, cy, cz, cu, cv, nx, ny, nz);
        vertex(pose, out, light, dx, dy, dz, du, dv, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, int light, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
