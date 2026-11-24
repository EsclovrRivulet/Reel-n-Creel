package io.github.esclovrrivulet.creel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import io.github.esclovrrivulet.creel.fishing.RodTable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Where the local player's rod tip was actually drawn (client only), so the line can leave from it while the rod is animated (by
 *  Punchy, by a swing, by anything). mixin.ItemLayerRenderStateMixin hands over the pose each of our held rods is drawn with, the very
 *  matrix of its geometry, after every mod's transforms; the rod's tip (RodTable.Rod.tipX, tipY, tipZ, in model pixels) is put
 *  through it.
 *
 *  0.12.3: NO CONVENTION IS ASSUMED about the pose the hand or the player starts from. Each capture is measured against the pose the
 *  drawing started from in that same pass, taken where the game starts it (mixin.FirstPersonHandsBaseMixin: the head of
 *  submitHandsWithItems; mixin.EntitySubmitBaseMixin: the head of EntityRenderDispatcher.submit for the local player), so whatever a
 *  mod puts before that (vanilla's inverse camera rotation, Iris's bare hand pose, a shadow pass's view from the sun) cancels out.
 *  Until 0.12.2 the hand was assumed to start from the inverse camera rotation, as vanilla's does; Iris's hand pass (shaders on)
 *  starts from a bare pose with the bob in its projection, so the tip was turned by the camera rotation a second time and the line
 *  came in from a corner of the screen. And in third person Iris draws the player again for the shadow map with the sun's view in the
 *  pose stack; that capture was read as camera-relative and sent the line off to one side, low over the water.
 *
 *  First person. B, the pose submitHandsWithItems starts from; M, the pose the rod is drawn with: B^-1 M is the hand's own transform
 *  (the arm, the swing, our animation), bob excluded; the tip through it is kept. When the line is placed, the view bobbing and hurt
 *  tilt of that frame are put back on (rebuilt by calling the game's own bobHurt and bobView, which vanilla puts in the hand's pose and
 *  Iris in the hand's projection: the same place in the chain; the frame's own, so a walk's bob does not lag a frame behind). The tip
 *  so placed is a point in the hand's camera space, projected with the hand's own field of view (hudFov, 70 normally). The world is
 *  projected with the player's field of view, with the same bobbing in its projection. To land on the same screen point in the world:
 *  its sideways parts are scaled by tan(fov / 2) / tan(hudFov / 2), the world's bob is taken off again, and it is turned into the
 *  world by the camera rotation and placed at the camera. The bob is thereby counted once: the world adds it back when it draws the
 *  line.
 *
 *  Third person. B, the pose the local player's submit starts from, and (x, y, z), the player's drawn offset from that pass's camera:
 *  B^-1 M tip - (x, y, z) is the tip relative to the player's drawn position, in the world's axes, whichever pass drew it (the world's,
 *  a shadow pass, a moved camera). It is kept relative to the player so it moves with the player.
 *
 *  A pose is used for a quarter second (the line is extracted before the hand is drawn, so it is one frame old); after that, or for
 *  another player, client.RodLine falls back to vanilla's own start of the line. */
public final class RodPose {
    private RodPose() {}

    /** how long a captured pose stays good, in nanoseconds */
    public static final long FRESH_NANOS = 250_000_000L;

    /** The last capture for one hand context, rewritten in place by each capture (render thread; 0.16.4: no objects made per frame).
     *  First person: view, the tip in the hand's camera space, bob excluded, and hand, the hand's own transform (B^-1 M), which the
     *  tip is put through (and the debug read-out reads). Third person: (fromX, fromY, fromZ), the tip from the player's drawn
     *  position. A context's slot is made at its first capture, and is only ever of its own kind (first or third person). */
    private static final class Captured {
        final boolean firstPerson;
        RodTable.Rod rod;
        final Vector3f view = new Vector3f();
        final Matrix4f hand = new Matrix4f();
        double fromX, fromY, fromZ;
        long nanos;

        Captured(boolean firstPerson) {
            this.firstPerson = firstPerson;
        }
    }

    private static final Map<ItemDisplayContext, Captured> LOCAL = new EnumMap<>(ItemDisplayContext.class);

    // scratch objects (render thread only)
    private static final Vector3f TIP = new Vector3f(), TIP_VIEW = new Vector3f();
    private static final Matrix4f THIRD = new Matrix4f(), BOB_INV = new Matrix4f(), VIEW_INV = new Matrix4f();

    // the poses the drawing started from (render thread only): the inverse of each, while that drawing is under way
    private static final Matrix4f HAND_BASE_INV = new Matrix4f(), ENTITY_BASE_INV = new Matrix4f();
    private static boolean handBaseSet, entityBaseSet;
    private static double entityX, entityY, entityZ;

    /** the bent step of the last capture, for the debug read-out */
    private static volatile int lastStep;

    /** the hand's field of view this frame (the game's fixed one when it has not set it); the camera is read through client.RodView,
     *  which each Minecraft version has its own copy of (1.0.0) */
    private static double handFov(Minecraft mc) {
        float hud = RodView.hudFov(mc);
        return hud > 0 ? hud : RodLine.HAND_FOV;
    }

    /** the first-person hands start being submitted from this pose (null: they are done) */
    public static void handBase(Matrix4f base) {
        handBaseSet = base != null && invertInto(base, HAND_BASE_INV);
    }

    /** a player is submitted from this pose at this offset from the pass's camera; kept only for the local player */
    public static void entityBase(int entityId, Matrix4f base, double x, double y, double z) {
        Player player = Minecraft.getInstance().player;
        if (player == null || entityId != player.getId()) {
            return;
        }
        entityBaseSet = invertInto(base, ENTITY_BASE_INV);
        entityX = x;
        entityY = y;
        entityZ = z;
    }

    /** that player's submit is done */
    public static void entityBaseEnd(int entityId) {
        Player player = Minecraft.getInstance().player;
        if (player != null && entityId == player.getId()) {
            entityBaseSet = false;
        }
    }

    private static boolean invertInto(Matrix4f m, Matrix4f out) {
        float det = m.determinant();
        if (!Float.isFinite(det) || Math.abs(det) < 1e-12F) {
            return false;
        }
        m.invert(out);
        return true;
    }

    /** a rod of ours was just drawn with this pose (render thread), bent to that step of its held model (0.13.0, RodFlex): the tip is
     *  that step's tip */
    public static void capture(int playerId, RodTable.Rod rod, ItemDisplayContext context, Matrix4f pose, int step) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || playerId != player.getId() || context == null) {
            return;
        }
        lastStep = step;
        Vector3f tip = TIP.set(rod.flexTip(step, 0) / 16.0F, rod.flexTip(step, 1) / 16.0F, rod.flexTip(step, 2) / 16.0F);
        if (context.firstPerson()) {
            if (!handBaseSet) {
                return;                     // drawn some other way: no base to measure from, so nothing captured
            }
            Captured c = slot(context, true);
            c.hand.set(HAND_BASE_INV).mul(pose);
            c.hand.transformPosition(c.view.set(tip));
            c.rod = rod;
            c.nanos = System.nanoTime();
        } else {
            if (!entityBaseSet) {
                return;
            }
            Vector3f p = THIRD.set(ENTITY_BASE_INV).mul(pose).transformPosition(tip);
            Captured c = slot(context, false);
            c.fromX = p.x() - entityX;
            c.fromY = p.y() - entityY;
            c.fromZ = p.z() - entityZ;
            c.rod = rod;
            c.nanos = System.nanoTime();
        }
    }

    /** the context's capture slot, made at its first capture */
    private static Captured slot(ItemDisplayContext context, boolean firstPerson) {
        Captured c = LOCAL.get(context);
        if (c == null) {
            c = new Captured(firstPerson);
            LOCAL.put(context, c);
        }
        return c;
    }

    /** for the debug line (client.RodDebug): for each hand context, how long ago a pose of ours was captured, and for first person where
     *  the tip it gives is on screen, as fractions of the half-width (right +) and half-height (up +) */
    public static String debugText() {
        Minecraft mc = Minecraft.getInstance();
        boolean cam = RodView.known(mc);
        StringBuilder sb = new StringBuilder();
        if (mc.player != null) {
            sb.append(RodFlex.debugText(mc.player)).append(", last drawn step ").append(lastStep).append("; ");
        }
        long now = System.nanoTime();
        for (ItemDisplayContext ctx : new ItemDisplayContext[] {ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND}) {
            Captured c = LOCAL.get(ctx);
            sb.append(sb.isEmpty() ? "" : "; ").append(ctx.getSerializedName()).append(": ");
            if (c == null) {
                sb.append("never captured");
                continue;
            }
            long ms = (now - c.nanos) / 1_000_000L;
            sb.append(c.rod.id()).append(" captured ").append(ms).append(" ms ago").append(ms * 1_000_000L > FRESH_NANOS ? " (stale, not used)" : " (used)");
            if (!c.firstPerson) {
                sb.append(String.format(Locale.ROOT, ", tip from the player's feet x %.2f y %.2f z %.2f", c.fromX, c.fromY, c.fromZ));
            }
            if (c.firstPerson && cam && c.view.z() < 0) {
                double t = Math.tan(Math.toRadians(handFov(mc)) / 2);
                double aspect = mc.getWindow().getHeight() > 0 ? (double) mc.getWindow().getWidth() / mc.getWindow().getHeight() : 1;
                Vector3f v = new Vector3f(c.view);
                Matrix4f bob = RodView.bob(mc);
                if (bob != null) {
                    bob.transformPosition(v);
                }
                sb.append(String.format(Locale.ROOT, ", tip on screen x %.3f y %.3f", v.x() / -v.z() / t / aspect, v.y() / -v.z() / t));
            }
            if (c.firstPerson && cam) {
                // without the bob: where the tip is at 16:9, and the extra forward tilt about the grip that would bring it to y 0.92
                double t = Math.tan(Math.toRadians(handFov(mc)) / 2);
                Vector3f v = c.hand.transformPosition(new Vector3f(c.rod.tipX() / 16.0F, c.rod.tipY() / 16.0F, c.rod.tipZ() / 16.0F));
                sb.append(String.format(Locale.ROOT, ", unbobbed tip at 16:9 x %.3f y %.3f", v.x() / -v.z() / t / (16.0 / 9.0), v.y() / -v.z() / t));
                float gx = c.rod.gripX() / 16.0F, gy = c.rod.gripY() / 16.0F, gz = c.rod.gripZ() / 16.0F;
                for (float deg = 0; deg >= -45; deg -= 0.1F) {
                    Matrix4f m = new Matrix4f(c.hand).translate(gx, gy, gz).rotateZ(deg * (float) Math.PI / 180).translate(-gx, -gy, -gz);
                    Vector3f w = m.transformPosition(new Vector3f(c.rod.tipX() / 16.0F, c.rod.tipY() / 16.0F, c.rod.tipZ() / 16.0F));
                    if (w.y() / -w.z() / t <= 0.92) {
                        sb.append(String.format(Locale.ROOT, ", more tilt for y 0.92: %.1f deg", deg));
                        break;
                    }
                }
            }
        }
        return sb.toString();
    }

    /** where the local player's rod tip is drawn now, in the world; null when no recent pose is known for that view and arm */
    public static Vec3 tip(Player player, RodTable.Rod rod, HumanoidArm arm, boolean firstPerson, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player) {
            return null;
        }
        boolean right = arm == HumanoidArm.RIGHT;
        ItemDisplayContext context = firstPerson ? (right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
            : (right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        Captured c = LOCAL.get(context);
        if (c == null || c.rod != rod || System.nanoTime() - c.nanos > FRESH_NANOS) {
            return null;
        }
        if (!firstPerson) {
            return player.getPosition(partialTick).add(c.fromX, c.fromY, c.fromZ);
        }
        Matrix4fc viewRotation = RodView.viewRotation(mc);
        Vec3 camPos = RodView.position(mc);
        if (viewRotation == null || camPos == null) {
            return null;
        }
        double k = Math.tan(Math.toRadians(RodView.fov(mc)) / 2) / Math.tan(Math.toRadians(handFov(mc)) / 2);
        // this frame's bob on the hand's own pose (so a walk's bob does not lag a frame behind), scaled to the world's field of view,
        // and the world's bob taken off again
        Vector3f v = TIP_VIEW.set(c.view);
        Matrix4f bob = RodView.bob(mc);
        if (bob != null) {
            bob.transformPosition(v);
        }
        v.set((float) (v.x() * k), (float) (v.y() * k), v.z());
        if (bob != null) {
            bob.invert(BOB_INV).transformPosition(v);
        }
        VIEW_INV.set(viewRotation).invert().transformPosition(v);
        return camPos.add(v.x(), v.y(), v.z());
    }
}
