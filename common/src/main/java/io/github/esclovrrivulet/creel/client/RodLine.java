package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import io.github.esclovrrivulet.creel.CreelMod;
import net.minecraft.world.entity.projectile.FishingHook;
import io.github.esclovrrivulet.creel.fishing.BendStage;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import io.github.esclovrrivulet.creel.fishing.HookLevel;
import io.github.esclovrrivulet.creel.fishing.RodTable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.esclovrrivulet.creel.compat.ClientCompat;

/** The fishing line of our rods (client only; mixin.FishingHookRendererMixin): where it leaves the rod, and how it is drawn. (0.24.0:
 *  and of vanilla's own Fishing Rod, see the end.)
 *
 *  WHERE IT STARTS. Where the rod's tip was actually drawn (RodPose: the pose our rod is drawn with, captured as it is submitted), so it
 *  follows the rod through our animation and the view bobbing. When no recent pose is known (another player's rod, a hand drawn some
 *  other way), vanilla's own start: never a guessed point off the rod.
 *
 *  HOW IT IS DRAWN (0.12.1, after Cast n Chill): as a one-pixel line in pixel art. The line's sag curve (vanilla's own) is projected to
 *  the screen and walked on a grid of square cells CELL_PX screen pixels across, fixed to the screen; the cells a one-cell-wide line
 *  passes through are filled, Bresenham's way between samples, with no doubled corners (a cell that only joins two diagonal
 *  neighbours is dropped), so neighbouring cells touch side by side or corner to corner. Then (0.12.2) it is broken as Cast n Chill's
 *  is: an irregular share of the cells (about a third) is left out, never more than MAX_GAP in a row, so it reads as runs of one to
 *  three pixels (longer where it is flat, single where it is steep); each kept cell has its own brightness, DIM_MIN to full, the
 *  dimmer ones grey-blue; pattern and shades are fixed to the cell's count along the line from the rod, so they hold still. The whole
 *  line is a little dimmer and cooler in the dark (the bobber's light, the night). Each kept cell is drawn as a square facing the
 *  camera, at the cell's place on screen and the curve's depth there, CELL_PX across on screen. The
 *  projection is rebuilt here from the camera (its position, rotation and field of view) and the window, which is what the world is
 *  drawn with (the view bobbing aside).
 *
 *  (0.24.0) Vanilla's own Fishing Rod, held in the hand as our model (tools/stick_rods.py), has this line and our bobber too, from its
 *  model's tip guide (StickRodTable.FISHING_ROD; lineRod); only the drawing: its casting, its sag (always vanilla's slack curve) and
 *  everything else stay vanilla's. Any other rod's line stays vanilla's (or its mod's) in every respect. */
public final class RodLine {
    private RodLine() {}

    /** the hand's field of view, fixed in the game */
    public static final double HAND_FOV = 70;

    // ---- the line's look (0.12.2: a broken pixel line, after the owner's close look at Cast n Chill's) ----
    /** the line's alpha: 90 percent */
    public static final int ALPHA = 0xE6;
    /** a cell of the line's pixel grid, in screen pixels */
    public static final float CELL_PX = 3.0F;
    /** a run of kept cells is at most RUN_FLAT long where the line is flat (at most half as steep as level), RUN_MID where it is
     *  steeper, up to twice as steep as level, RUN_STEEP where it is steeper still */
    public static final int RUN_FLAT = 3, RUN_MID = 2, RUN_STEEP = 1;
    /** after a kept cell (whose run may go on), the chance the next is left out */
    public static final float GAP_START = 0.18F;
    /** after a left-out cell, the chance the next is left out too; never more than MAX_GAP in a row */
    public static final float GAP_EXTEND = 0.25F;
    public static final int MAX_GAP = 2;
    /** 0.13.0: the first cells from the rod are always kept. In first person the first cell or two lie behind the tip-top (the hand
     *  is drawn over the world), so a gap among them showed as the visible line starting a cell beside the tip ring instead of on it */
    public static final int SOLID_START = 3;
    /** each kept cell's brightness, fixed along the line: from DIM_MIN to 1 */
    public static final float DIM_MIN = 0.55F;
    /** the dimmer a cell, the more it leans to this grey-blue (its red and green; blue stays) */
    public static final float GREY_BLUE_R = 0.78F, GREY_BLUE_G = 0.86F;
    /** in the dark (no block light, and no sky light or night) the whole line is this bright ... */
    public static final float DARK_DIM = 0.62F;
    /** ... and this much cooler (red and green taken down by these shares, blue kept) */
    public static final float DARK_COOL_R = 0.16F, DARK_COOL_G = 0.07F;
    /** the cells' texture: plain white, so the colour above is the cell's colour */
    public static final Identifier DOT_TEXTURE = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "textures/misc/line_dot.png");
    /** full brightness: the light is put into the colour (lightTint), so the thread stays clean */
    private static final int FULL_BRIGHT = 0xF000F0;
    /** at most this many cells, however long the line */
    private static final int MAX_CELLS = 4000;
    /** nothing nearer the eye than this is drawn */
    private static final double NEAR = 0.05;

    /** the arm the line leaves from: the main arm when it holds one of our rods, else the other (as vanilla picks it for its own rod) */
    public static HumanoidArm holdingArm(Player player) {
        return player.getMainHandItem().getItem() instanceof CastingRodItem ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    /** our rod in the holding arm, or null */
    public static RodTable.Rod rodHeld(Player player) {
        return player.getItemHeldByArm(holdingArm(player)).getItem() instanceof CastingRodItem rod ? rod.rod() : null;
    }

    /** (0.24.0) the arm vanilla's line leaves from when no rod of ours is in the main hand: vanilla's own rule
     *  (FishingHookRenderer.getHoldingArm: the main arm when the main hand holds a fishing rod item of any kind, else the other) */
    public static HumanoidArm vanillaArm(Player player) {
        return player.getMainHandItem().getItem() instanceof FishingRodItem ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    /** (0.24.0) whether the line is vanilla's own Fishing Rod's (exactly Items.FISHING_ROD in the arm vanilla's line leaves from): its
     *  line and bobber are drawn as ours, from our model's tip guide (tools/stick_rods.py). Another mod's rod, even one extending
     *  vanilla's rod class, is not */
    public static boolean vanillaRodHeld(Player player) {
        return player.getItemHeldByArm(vanillaArm(player)).is(Items.FISHING_ROD);
    }

    /** (0.24.0) the rod whose line is drawn as ours: one of our rods in the holding arm, else vanilla's Fishing Rod
     *  (StickRodTable.FISHING_ROD), else null (vanilla's line, or another mod's, untouched) */
    public static RodTable.Rod lineRod(Player player) {
        RodTable.Rod rod = rodHeld(player);
        if (rod != null) {
            return rod;
        }
        return vanillaRodHeld(player) ? StickRodTable.FISHING_ROD : null;
    }

    /** the line's start for our rod (0.24.0: or vanilla's Fishing Rod) in the holding arm: where its tip was drawn, else vanilla's (or
     *  another mod's) start, unchanged */
    public static Vec3 start(Player player, float swing, float partialTick, Vec3 vanilla) {
        RodTable.Rod rod = lineRod(player);
        if (rod == null) {
            return vanilla;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson() && player == mc.player;
        HumanoidArm arm = rod == StickRodTable.FISHING_ROD ? vanillaArm(player) : holdingArm(player);
        Vec3 drawn = RodPose.tip(player, rod, arm, firstPerson, partialTick);
        return drawn != null ? drawn : vanilla;
    }

    /** vanilla's sag curve (FishingHookRenderer.stringVertex): the point at t from the bobber (t 0) to the rod (t 1). (0.14.0) The sag,
     *  vanilla's curve less the straight line y * t, is scaled by one minus how taut the line is: slack, vanilla's curve; taut, a
     *  straight line */
    private static void at(float x, float y, float z, float t, float taut, Vector3f out) {
        out.set(x * t, y * t + (1.0F - taut) * y * (t * t - t) * 0.5F + 0.25F, z * t);
    }

    /** (0.16.2) the line's glide between tautnesses per hook, used only with RodFlex.GLIDE_BETWEEN_STAGES on: from, to, when it began
     *  (the hook's ticks); forgotten with the hook */
    private static final Map<FishingHook, float[]> TAUT = new WeakHashMap<>();

    /** (0.16.2) how taut a hook's line is drawn now, 0 to 1: the fish's synced level (HookLevel.taut: slack calm, straighter in tension,
     *  straight in danger), not the time reeled. It changes at once, as the rod's bend does; with RodFlex.GLIDE_BETWEEN_STAGES on it
     *  glides there over RodFlex.GLIDE_TICKS from where it is. Read when the hook's render state is extracted (only for our rods' lines). */
    public static float taut(FishingHook hook, float partialTick) {
        float target = HookLevel.taut(HookLevel.level(hook));
        if (!RodFlex.GLIDE_BETWEEN_STAGES) {
            return target;
        }
        float now = hook.tickCount + partialTick;
        float[] g = TAUT.computeIfAbsent(hook, h -> new float[] {target, target, now});
        float drawn = BendStage.bend(g[0], g[1], now - g[2], RodFlex.GLIDE_TICKS);
        if (target != g[1]) {
            g[0] = drawn;
            g[1] = target;
            g[2] = now;
        }
        return drawn;
    }

    // ---- per-frame work without new objects (0.16.4; render thread only) ----
    /** the cells of the line being drawn, in order from the rod: grid place and the view depth there; reused every frame, grown as
     *  needed */
    private static int[] cellX = new int[256], cellY = new int[256];
    private static double[] cellDepth = new double[256];
    private static int cellCount;
    private static final Quaternionf ROT = new Quaternionf(), INV = new Quaternionf();
    private static final Vector3f CURVE = new Vector3f(), PROJECTED = new Vector3f(), VIEW = new Vector3f(), LOCAL = new Vector3f();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), FACING = new Vector3f();
    private static final double[] SCREEN_A = new double[3], SCREEN_B = new double[3];
    private static final float[] TINT = new float[3];
    /** a cell square's corners: its offsets along the camera's right and up, and its texture's u and v */
    private static final float[][] CORNERS = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};

    // the last line drawn, for the debug read-out (lastDrawn): kept as numbers, the sentence made only when asked
    private static final int NONE_YET = 0, NO_CELL = 1, DRAWN = 2;
    private static volatile int lastState = NONE_YET;
    private static int lastCells, lastRodX, lastRodY, lastBobX, lastBobY;
    private static float lastPointX, lastPointY, lastPointZ;

    /** Our line, in place of vanilla's: (x, y, z) is the rod's point from the bobber, as vanilla's line gets it; (hx, hy, hz) the bobber's
     *  drawn position in the world (the pose's origin); light the bobber's packed light (block and sky). */
    public static void draw(float x, float y, float z, double hx, double hy, double hz, int light, float tautNow, PoseStack.Pose pose, VertexConsumer out) {
        float taut = Mth.clamp(tautNow, 0.0F, 1.0F);
        Minecraft mc = Minecraft.getInstance();
        Camera camera = ClientCompat.mainCamera(mc.gameRenderer);
        Vec3 cam = camera.position();
        Quaternionf rot = ROT.set(camera.rotation());
        Quaternionf inv = INV.set(rot).conjugate();
        int w = Math.max(1, mc.getWindow().getWidth()), h = Math.max(1, mc.getWindow().getHeight());
        double tanHalf = Math.tan(Math.toRadians(camera.getFov()) / 2), aspect = (double) w / h;

        // sample the curve from the rod (t 1) to the bobber (t 0), finely enough for the grid: a coarse pass sizes the fine one
        Vector3f p = CURVE;
        double screenLen = 0;
        double[] s = SCREEN_A, prev = SCREEN_B;
        boolean prevOn = false;
        for (int i = 0; i <= 16; i++) {
            at(x, y, z, 1 - i / 16.0F, taut, p);
            boolean on = project(p, hx, hy, hz, cam, inv, tanHalf, aspect, w, h, s);
            if (on && prevOn && (onScreen(s, w, h) || onScreen(prev, w, h))) {
                screenLen += Math.min(w + h, Math.hypot(s[0] - prev[0], s[1] - prev[1]));
            }
            double[] swap = prev;
            prev = s;
            s = swap;
            prevOn = on;
        }
        int samples = (int) Math.max(16, Math.min(4000, screenLen / (CELL_PX * 0.5)));
        cellCount = 0;
        int gw = (int) Math.ceil(w / CELL_PX), gh = (int) Math.ceil(h / CELL_PX);       // the grid's size: cells off it are not kept
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= samples && cellCount < MAX_CELLS; i++) {
            at(x, y, z, 1 - (float) i / samples, taut, p);
            if (!project(p, hx, hy, hz, cam, inv, tanHalf, aspect, w, h, s)) {
                continue;
            }
            if (Math.abs(s[0]) > 1e6 || Math.abs(s[1]) > 1e6) {
                lastX = Integer.MIN_VALUE;         // far off the screen, just in front of the eye: nothing of it to draw
                continue;
            }
            int cx = (int) Math.floor(s[0] / CELL_PX), cy = (int) Math.floor(s[1] / CELL_PX);
            if (lastX == Integer.MIN_VALUE) {
                add(cx, cy, s[2], gw, gh);
            } else if ((cx != lastX || cy != lastY) && (inGrid(cx, cy, gw, gh) || inGrid(lastX, lastY, gw, gh))
                && Math.max(Math.abs(cx - lastX), Math.abs(cy - lastY)) <= 2 * (gw + gh)) {
                // Bresenham from the last cell to this one, so the run is one cell wide whatever the sampling
                int dx = Math.abs(cx - lastX), dy = -Math.abs(cy - lastY), sx = lastX < cx ? 1 : -1, sy = lastY < cy ? 1 : -1, err = dx + dy;
                int ax = lastX, ay = lastY;
                while (ax != cx || ay != cy) {
                    int e2 = 2 * err;
                    if (e2 >= dy) {
                        err += dy;
                        ax += sx;
                    }
                    if (e2 <= dx) {
                        err += dx;
                        ay += sy;
                    }
                    add(ax, ay, s[2], gw, gh);
                }
            }
            lastX = cx;
            lastY = cy;
        }

        // each kept cell a square facing the camera, at its place on screen and its depth, CELL_PX across, in its own shade
        float[] tint = lightTint(light);
        Vector3f right = RIGHT.set(1, 0, 0).rotate(rot), up = UP.set(0, 1, 0).rotate(rot), facing = FACING.set(0, 0, 1).rotate(rot);
        int n = cellCount;
        int run = 0, gap = 0;
        for (int k = 0; k < n; k++) {
            // the pattern is fixed to the cell's count along the line from the rod, so it holds still from frame to frame
            int a = Math.max(0, k - 1), b = Math.min(n - 1, k + 1);
            int dx = Math.abs(cellX[b] - cellX[a]), dy = Math.abs(cellY[b] - cellY[a]);
            int maxRun = dy > 2 * dx ? RUN_STEEP : dy * 2 > dx ? RUN_MID : RUN_FLAT;
            float r = unit(k, 0x5bd1e995);
            boolean leave = gap > 0 ? gap < MAX_GAP && r < GAP_EXTEND : k >= SOLID_START && (run >= maxRun || r < GAP_START);
            if (leave) {
                gap++;
                run = 0;
                continue;
            }
            gap = 0;
            run++;
            float bright = DIM_MIN + (1 - DIM_MIN) * unit(k, 0x27d4eb2d);
            float m = (1 - bright) / (1 - DIM_MIN);                              // 0 for the brightest, 1 for the dimmest
            int colour = argb(bright * Mth.lerp(m, 1, GREY_BLUE_R) * tint[0], bright * Mth.lerp(m, 1, GREY_BLUE_G) * tint[1], bright * tint[2]);
            double depth = cellDepth[k];
            double sx = (cellX[k] + 0.5) * CELL_PX, sy = (cellY[k] + 0.5) * CELL_PX;
            double ndcX = sx / w * 2 - 1, ndcY = 1 - sy / h * 2;
            Vector3f view = VIEW.set((float) (ndcX * tanHalf * aspect * depth), (float) (ndcY * tanHalf * depth), (float) -depth);
            view.rotate(rot);
            Vector3f local = LOCAL.set((float) (cam.x + view.x - hx), (float) (cam.y + view.y - hy), (float) (cam.z + view.z - hz));
            float half = (float) (CELL_PX * tanHalf * depth / h);         // half of CELL_PX screen pixels at that depth
            quad(out, pose, local, right, up, facing, half, colour);
        }
        if (n > 0) {
            lastCells = n;
            lastRodX = cellX[0];
            lastRodY = cellY[0];
            lastBobX = cellX[n - 1];
            lastBobY = cellY[n - 1];
            lastPointX = x;
            lastPointY = y;
            lastPointZ = z;
            lastState = DRAWN;
        } else {
            lastState = NO_CELL;
        }
    }

    /** for the debug read-out (client.RodDebug, and the visual test's driver): the last line drawn, in one sentence */
    public static String lastDrawn() {
        return switch (lastState) {
            case DRAWN -> String.format(Locale.ROOT, "line %d cells, rod end at (%.0f, %.0f) px, bobber end at (%.0f, %.0f) px, rod point from the bobber (%.2f, %.2f, %.2f)",
                lastCells, lastRodX * CELL_PX, lastRodY * CELL_PX, lastBobX * CELL_PX, lastBobY * CELL_PX, lastPointX, lastPointY, lastPointZ);
            case NO_CELL -> "line: no cell on screen";
            default -> "no line drawn yet";
        };
    }

    /** a fixed pseudo-random number in [0, 1) for the k-th cell from the rod */
    private static float unit(int k, int salt) {
        int x = k * 0x9E3779B1 + salt;
        x ^= x >>> 16;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        x *= 0xC2B2AE35;
        x ^= x >>> 16;
        return (x >>> 8) / (float) (1 << 24);
    }

    /** the light's tint at the bobber: 1 in full light; DARK_DIM and cooler in the dark (its block light, or its sky light less the
     *  night's darkening) */
    private static float[] lightTint(int packed) {
        Minecraft mc = Minecraft.getInstance();
        int block = (packed >>> 4) & 15, sky = (packed >>> 20) & 15;
        int darken = mc.level != null ? mc.level.getSkyDarken() : 0;
        float dark = 1 - Math.max(block, Math.max(0, sky - darken)) / 15.0F;
        float dim = Mth.lerp(dark, 1, DARK_DIM);
        TINT[0] = dim * (1 - DARK_COOL_R * dark);
        TINT[1] = dim * (1 - DARK_COOL_G * dark);
        TINT[2] = dim;
        return TINT;
    }

    private static int argb(float r, float g, float b) {
        return ALPHA << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float v) {
        return Mth.clamp(Math.round(v * 255), 0, 255);
    }

    /** add a cell, dropping the one before it when that one only made a doubled corner (the new cell touches the one before it) */
    private static void add(int cx, int cy, double depth, int gw, int gh) {
        if (!inGrid(cx, cy, gw, gh)) {
            return;
        }
        int n = cellCount;
        if (n >= 2 && Math.abs(cellX[n - 2] - cx) <= 1 && Math.abs(cellY[n - 2] - cy) <= 1) {
            n--;                    // the one before only made a doubled corner: dropped
            cellCount = n;
        }
        if (n > 0 && cellX[n - 1] == cx && cellY[n - 1] == cy) {
            return;
        }
        if (n == cellX.length) {
            cellX = Arrays.copyOf(cellX, n * 2);
            cellY = Arrays.copyOf(cellY, n * 2);
            cellDepth = Arrays.copyOf(cellDepth, n * 2);
        }
        cellX[n] = cx;
        cellY[n] = cy;
        cellDepth[n] = depth;
        cellCount = n + 1;
    }

    private static boolean inGrid(int cx, int cy, int gw, int gh) {
        return cx >= -1 && cy >= -1 && cx <= gw && cy <= gh;
    }

    private static boolean onScreen(double[] s, int w, int h) {
        return s[0] >= -CELL_PX && s[1] >= -CELL_PX && s[0] <= w + CELL_PX && s[1] <= h + CELL_PX;
    }

    /** a point of the curve (relative to the bobber's drawn place) on screen, written into out: pixels from the top left, and its view
     *  depth; false (out untouched) when behind or too near the eye */
    private static boolean project(Vector3f p, double hx, double hy, double hz, Vec3 cam, Quaternionf inv, double tanHalf, double aspect,
                                   int w, int h, double[] out) {
        Vector3f v = PROJECTED.set((float) (hx + p.x - cam.x), (float) (hy + p.y - cam.y), (float) (hz + p.z - cam.z)).rotate(inv);
        double depth = -v.z;
        if (depth < NEAR) {
            return false;
        }
        double ndcX = v.x / depth / (tanHalf * aspect), ndcY = v.y / depth / tanHalf;
        out[0] = (ndcX + 1) / 2 * w;
        out[1] = (1 - ndcY) / 2 * h;
        out[2] = depth;
        return true;
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, Vector3f c, Vector3f right, Vector3f up, Vector3f facing, float h, int colour) {
        for (float[] k : CORNERS) {
            float px = c.x + (right.x * k[0] + up.x * k[1]) * h, py = c.y + (right.y * k[0] + up.y * k[1]) * h, pz = c.z + (right.z * k[0] + up.z * k[1]) * h;
            out.addVertex(pose, px, py, pz).setColor(colour).setUv(k[2], k[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose, facing.x, facing.y, facing.z);
        }
    }
}
