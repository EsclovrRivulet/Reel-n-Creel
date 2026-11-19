package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import io.github.esclovrrivulet.creel.fishing.RodTable;

/** Our rods' own animation (client only; 0.12.0, tuned by eye in 0.12.2). Applied to the held rod itself where its layer is submitted,
 *  just after its display transform (mixin.ItemLayerRenderStateMixin), so it shows whoever drew the arm (vanilla or an animation mod)
 *  and the line's start, captured later in the same submit, follows it. It turns the rod in its own plane about the grip
 *  (RodTable.Rod.gripX, gripY, gripZ), which in the first-person hand tilts the tip up and back toward the player (a positive angle) or
 *  forward (negative).
 *
 *  Driven only by what every client knows about every player: whether they are using our rod, for how many ticks, and whether their
 *  line is out (the bobber entity). Using with no line out is a charge; using with the line out is winding or reeling; a charge that
 *  ends is a cast. The rod never shakes (the owner's rule, 0.12.2): no tremble, no rock, no noise.
 *  - Charging: back by up to CHARGE_DEGREES, the hand dropping by up to DROP_PX, in one smooth ease-out over the charge time, then held
 *    perfectly still at full until use is let go.
 *  - The cast: from where the charge had it, a whip forward to WHIP_DEGREES past rest (times the charge, at least a third of it) in
 *    WHIP_OUT_TICKS, then eased back to rest by SETTLE_TICKS, exactly at rest after. In first person the game's own swing, which the
 *    server sends with the cast, is held off our rod for CAST_SWING_TICKS (mixin.FirstPersonSwingMixin): it would lay the rod flat.
 *  - Winding (0.16.2: not reeling a fish, where the rod stays at rest and only its bend, RodFlex, changes): the rod rises
 *    RAISE_DEGREES (the hand RAISE_PX) to a raised pose and stays there, steady, while use is held,
 *    and sinks back to rest when it is let go. This is one continuous value per player that moves toward its target (raised while use
 *    is held with the line out, else rest) at a rate of a full rise per RAISE_TICKS, frame by frame with the partial tick, and is drawn
 *    through a smooth ease: it never restarts with a new click, so tap, tap, tap looks as calm as one hold.
 *  First person only for the tilt and whip (third person has the arm's own charge, mixin.HumanoidModelMixin, and the game's swing);
 *  the raised pose shows in both. The charge time is the default (20 ticks); a datapack changing it on a server changes the cast but not
 *  this. */
public final class RodAnim {
    private RodAnim() {}

    public static final float CHARGE_TICKS = 20;
    public static final float CHARGE_DEGREES = 28;
    public static final float DROP_PX = 1.5F;
    public static final float WHIP_DEGREES = 22;
    public static final float WHIP_OUT_TICKS = 1.5F;
    public static final float SETTLE_TICKS = 6;
    public static final float CAST_SWING_TICKS = 10;
    public static final float RAISE_DEGREES = 6;
    public static final float RAISE_PX = 0.5F;
    public static final float RAISE_TICKS = 6;
    /** third person: how far the arm holding the rod is raised from the holding pose at full charge (mixin.HumanoidModelMixin) */
    public static final float ARM_DRAW_DEGREES = 110;

    /** First person (0.12.3): each rod's whole pose leaned forward about the grip by this many degrees (negative: forward), the least
     *  that keeps its tip a small margin (8 percent of the half-height) inside the top of a 16:9 screen at rest, with the line out and
     *  while winding (the raised pose, the highest of the three). The hand's own field of view (70) draws the rod, so the player's
     *  field of view does not change this. Measured in the visual test (RodPose's read-out, test/visual/rods_fp_tip.txt): unleaned,
     *  the winding tip sat at y 0.65 (Pebble Spinner), 1.53 (Kingfisher Drift, Amberleaf Caster), 1.61 (Mistral Pier) and 1.10
     *  (Tidebreaker) of the half-height. The charge goes back from here; the longest rods' tips may leave the view during it. */
    public static float restTilt(RodTable.Rod rod) {
        return switch (rod.id()) {
            case "trout_rod", "salmon_rod" -> -15.0F;
            case "surf_rod" -> -16.5F;
            case "big_game_rod" -> -6.0F;
            default -> 0.0F;            // the Pebble Spinner fits as it is
        };
    }

    private static final class State {
        boolean charging;
        double releasedAt = -1;
        /** the charge: kept up to date while charging (it is read only after the release, so it is then the charge at the release) */
        float releasedCharge;
        /** the winding pose's progress, 0 (rest) to 1 (raised), and the time it was last advanced to */
        float raise;
        double raisedAt = -1;
        /** 0.12.4: the use last seen, its ticks, and whether it began with the line out (a wind or a reel): such a use is never a charge,
         *  even once the line is in and use is still held (the server holds it, spent, until it is let go: CastingRodItem) */
        boolean inUse;
        int useTicks;
        boolean windUse;
    }

    /** that player's state (on the player itself, RodHolder), made on first use */
    private static State state(Player player) {
        RodHolder h = (RodHolder) player;
        State s = (State) h.creel$animState();
        if (s == null) {
            s = new State();
            h.creel$setAnimState(s);
        }
        return s;
    }

    /** the hand a context draws, for that player */
    private static InteractionHand hand(Player player, ItemDisplayContext context) {
        HumanoidArm arm = context.leftHand() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        return arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    /** whether that player is using one of our rods in that hand now */
    private static boolean using(Player player, InteractionHand hand) {
        return player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItem().getItem() instanceof CastingRodItem;
    }

    /** Whether that player's use of one of our rods is a charge: in use with no line out, and the use did not begin with the line out.
     *  A use that began as a wind or a reel stays one until it is let go (0.12.4). Read from what every client knows (the use, its
     *  ticks, the bobber), each time the rod or the arm is drawn: a new use is one that was not seen before, or whose ticks went back. */
    public static boolean charging(Player player) {
        State s = state(player);
        boolean use = player.isUsingItem() && player.getUseItem().getItem() instanceof CastingRodItem;
        int ticks = player.getTicksUsingItem();
        if (use && (!s.inUse || ticks < s.useTicks)) {           // a use not seen before: what it began as is what it stays
            s.windUse = player.fishing != null;
        }
        s.inUse = use;
        s.useTicks = ticks;
        return use && player.fishing == null && !s.windUse;
    }

    /** the charge's ease: an ease-out, fast at first, settling into full */
    public static float ease(float c) {
        c = Mth.clamp(c, 0, 1);
        return 1 - (1 - c) * (1 - c);
    }

    /** the third-person arm's ease: slow to start, slow into full, so the arm is seen drawing back */
    public static float armEase(float c) {
        c = Mth.clamp(c, 0, 1);
        return c * c * (3 - 2 * c);
    }

    private static double now(Minecraft mc) {
        return mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** whether the local player cast with one of our rods within the last CAST_SWING_TICKS (the swing sent with the cast is held off) */
    public static boolean justCast() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        State s = (State) ((RodHolder) mc.player).creel$animState();
        if (s == null || s.releasedAt < 0) {
            return false;
        }
        double since = now(mc) - s.releasedAt;
        return since >= 0 && since < CAST_SWING_TICKS;           // a negative time since: a cast time from another clock, no cast
    }

    /** turn the rod being submitted, if it is ours and in a hand (render thread) */
    public static void apply(int playerId, RodTable.Rod rod, ItemDisplayContext context, PoseStack.Pose pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || context == null || rod == null) {
            return;
        }
        Entity e = mc.level.getEntity(playerId);
        if (!(e instanceof Player player)) {
            return;
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = now(mc);
        InteractionHand hand = hand(player, context);
        State s = state(player);
        boolean use = using(player, hand);
        boolean lineOut = player.fishing != null;
        boolean charge = charging(player) && use;   // read every draw (in use or not), so a use's start is never missed

        // the winding pose: one value moving toward its target, so clicks never restart it
        double dt = s.raisedAt < 0 ? 0 : Mth.clamp(now - s.raisedAt, 0, RAISE_TICKS);
        s.raisedAt = now;
        // 0.16.2: with a fish on the line there is no reel pose (first and third person): the rod stays at rest and only the bend changes
        // (the raise sank the rod as the player let go, which read as the bend growing; the owner, playing 0.16.1). Winding an empty line
        // keeps it.
        boolean fishOn = lineOut && player.fishing.getHookedIn() instanceof AbstractFish;
        float target = use && lineOut && !fishOn ? 1 : 0;
        float step = (float) (dt / RAISE_TICKS);
        s.raise = s.raise < target ? Math.min(target, s.raise + step) : Math.max(target, s.raise - step);
        float raised = armEase(s.raise);

        float degrees = RAISE_DEGREES * raised, rise = RAISE_PX * raised;
        boolean tilt = false;                       // the charge or the whip (first person only)
        if (charge) {
            float c = Mth.clamp((player.getTicksUsingItem() + pt) / CHARGE_TICKS, 0, 1);
            s.charging = true;
            s.releasedCharge = c;
            float eased = ease(c);
            degrees = CHARGE_DEGREES * eased;
            rise = -DROP_PX * eased;
            tilt = true;
        } else {
            if (s.charging) {
                s.charging = false;
                s.releasedAt = now;
            }
            double since = s.releasedAt < 0 ? Double.MAX_VALUE : now - s.releasedAt;
            if (since < 0) {
                since = Double.MAX_VALUE;       // a cast time from another clock (0.16.4: never with the state on the player): no cast
            }
            if (since < SETTLE_TICKS) {
                float fromEase = ease(s.releasedCharge);
                float past = -WHIP_DEGREES * Math.max(0.35F, s.releasedCharge);
                if (since < WHIP_OUT_TICKS) {
                    float t = (float) (since / WHIP_OUT_TICKS);
                    degrees += Mth.lerp(t, CHARGE_DEGREES * fromEase, past);
                    rise += -DROP_PX * fromEase * (1 - t);
                } else {
                    float t = (float) ((since - WHIP_OUT_TICKS) / (SETTLE_TICKS - WHIP_OUT_TICKS));
                    degrees += past * (1 - t * t * (3 - 2 * t));       // smooth to exactly zero at SETTLE_TICKS
                }
                tilt = true;
            }
        }
        if (!context.firstPerson()) {
            if (tilt) {
                return;                     // third person: the arm's charge and the game's swing show the charge and the cast
            }
        } else {
            degrees += restTilt(rod);       // first person: the longer rods leaned forward so their tips stay on screen
        }
        if (degrees == 0 && rise == 0) {
            return;
        }
        // the turn about the grip, applied in place (pose * translate * rotate * translate back; the normal turned by the same angle)
        float gx = rod.gripX() / 16.0F, gy = rod.gripY() / 16.0F, gz = rod.gripZ() / 16.0F;
        pose.pose().translate(gx, gy + rise / 16.0F, gz).rotateZ(degrees * Mth.DEG_TO_RAD).translate(-gx, -gy, -gz);
        pose.normal().rotateZ(degrees * Mth.DEG_TO_RAD);
    }
}
