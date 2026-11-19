package io.github.esclovrrivulet.creel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import io.github.esclovrrivulet.creel.fishing.BendStage;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import io.github.esclovrrivulet.creel.fishing.HookLevel;
import io.github.esclovrrivulet.creel.fishing.RodTable;

import java.util.Locale;

/** The rods' flex (0.13.0, reworked 0.15.0, client only): how far the held rod is bent, as one of the pre-built steps of its held model
 *  (tools/rod_models.py FLEX; RodTable.FLEX_MIN to FLEX_MAX, 0 straight, + the tip forward toward the line, - back). Read where the held
 *  item's model is picked (RodBendProperty, each time the rod is drawn); nothing runs per tick.
 *
 *  The drivers give a TARGET, in steps (a real number):
 *  - The cast. While the cast is drawn back the rod stays straight (0.15.0: the owner, the rod must not bend while charging). On
 *    letting go the rod loads the other way through the whip: in RodAnim.WHIP_OUT_TICKS the target runs to -WHIP_BEND times the cast's
 *    power (at least a third, as the whip's angle), then comes back to 0 by RECOVER_TICKS in one smoothstep. One way back, no swing past
 *    straight: the rod never shakes (the owner's rule).
 *  - A fish on the line (0.16.0): the bend is the STAGE, 1 to 6 (fishing.HookLevel.stage): the fish's level the server syncs on the
 *    player's bobber (HookLevel.level: calm 1, tension 2, danger 3; their stages 1, 3 and 5) plus one while the holder reels, read from the holder's use state
 *    (isUsingItem with one of our rods, synced by the game for every player). Not the fish's size. Every player sees the same bend.
 *  During the cast the drawn bend moves toward the target eased (a share of the way each moment, frame-rate free: EASE_CAST_TICKS); the
 *  whip keeps that easing. Under a fish (0.16.2, the owner: the shapes between confused when to reel) a change of stage goes STRAIGHT to
 *  the new stage's shape, no shapes between; the line leaves from the tip of the shape drawn (RodPose, drawn). 0.16.0 to 0.16.1 glided
 *  there in a smoothstep over GLIDE_TICKS: GLIDE_BETWEEN_STAGES brings that back (and the line's sag glides with it, RodLine.taut). The
 *  step is picked with fishing.BendStage.HYSTERESIS: it only moves to a neighbour once the bend is past the half-way point by that
 *  much, so it never flips back and forth between two steps (a jump lands on the stage exactly). */
public final class RodFlex {
    private RodFlex() {}

    public static final float WHIP_BEND = 2.3F;
    public static final float RECOVER_TICKS = 9;
    public static final float EASE_CAST_TICKS = 0.4F;
    /** (0.16.2) OFF: under a fish the rod jumps to the new stage's shape. True brings back 0.16.0's glide through the shapes between,
     *  over GLIDE_TICKS, for the rod's bend and the line's sag alike */
    public static final boolean GLIDE_BETWEEN_STAGES = false;
    /** (0.16.0) ticks a change of stage under a fish glides, when GLIDE_BETWEEN_STAGES is on */
    public static final float GLIDE_TICKS = 7;

    /** the fish's level on a player's line (fishing.HookLevel NONE to DANGER): the synced level of the player's bobber */
    private static int level(Player player) {
        return HookLevel.level(player.fishing);
    }

    private static final class State {
        float bend;
        int step;
        double at = -1;
        boolean charging;
        double releasedAt = -1;
        /** the charge: kept up to date while charging (it is read only after the release, so it is then the charge at the release) */
        float releasedCharge;
        /** the change of stage under a fish: from, to, when it began (only a glide uses from and when) */
        float glideFrom, glideTo;
        double glideAt = -1;
    }

    /** that player's state (on the player itself, RodHolder), made on first use */
    private static State state(Player player) {
        RodHolder h = (RodHolder) player;
        State s = (State) h.creel$flexState();
        if (s == null) {
            s = new State();
            h.creel$setFlexState(s);
        }
        return s;
    }

    /** that player's state, or null when none was made yet */
    private static State peek(Player player) {
        return (State) ((RodHolder) player).creel$flexState();
    }

    /** whether this stack is the rod in the arm the line leaves from, or the item in use */
    private static boolean lineRodOrInUse(Player player, ItemStack stack) {
        return stack == player.getItemHeldByArm(RodLine.holdingArm(player)) || stack == player.getUseItem();
    }

    /** the step to draw for this stack in that holder's hand (RodBendProperty); 0 for anything but one of our rods held by a player in
     *  the hand the line leaves from or in use */
    public static float step(ItemStack stack, ItemOwner owner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || owner == null || !(owner.asLivingEntity() instanceof Player player) || !(stack.getItem() instanceof CastingRodItem)) {
            return 0;
        }
        if (!lineRodOrInUse(player, stack)) {
            return 0;
        }
        return update(mc, player);
    }

    /** the step drawn for this stack now (the model has only the cast's steps with the line in): for the line's start (RodPose) */
    public static int drawn(Player player, ItemStack stack) {
        State s = peek(player);
        if (s == null || !lineRodOrInUse(player, stack)) {
            return 0;
        }
        boolean cast = player.fishing != null && player.getItemHeldByArm(FishingHookRenderer.getHoldingArm(player)) == stack;
        return Math.min(s.step, cast ? RodTable.FLEX_MAX : RodTable.FLEX_LINE_IN_MAX);
    }

    private static int update(Minecraft mc, Player player) {
        State s = state(player);
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + pt;
        boolean charging = RodAnim.charging(player);

        float target = 0;
        boolean cast = false;
        if (charging) {
            float c = Mth.clamp((player.getTicksUsingItem() + pt) / RodAnim.CHARGE_TICKS, 0, 1);
            s.charging = true;
            s.releasedCharge = c;
            target = 0;             // 0.15.0: straight while charging
            cast = true;
        } else {
            if (s.charging) {
                s.charging = false;
                s.releasedAt = now;
            }
            double since = s.releasedAt < 0 ? Double.MAX_VALUE : now - s.releasedAt;
            if (since < 0) {
                since = Double.MAX_VALUE;       // a cast time from another clock (0.16.4: never with the state on the player): no cast
            }
            if (since < RECOVER_TICKS) {
                float back = -WHIP_BEND * Math.max(0.35F, s.releasedCharge);
                if (since < RodAnim.WHIP_OUT_TICKS) {
                    target = Mth.lerp((float) (since / RodAnim.WHIP_OUT_TICKS), 0, back);
                } else {
                    float t = (float) ((since - RodAnim.WHIP_OUT_TICKS) / (RECOVER_TICKS - RodAnim.WHIP_OUT_TICKS));
                    target = back * (1 - t * t * (3 - 2 * t));
                }
                cast = true;
            } else {
                target = fishTarget(player);
            }
        }

        double dt = s.at < 0 ? 0 : Mth.clamp(now - s.at, 0, 20);
        s.at = now;
        if (cast) {
            float k = 1 - (float) Math.exp(-dt / EASE_CAST_TICKS);
            s.bend += (target - s.bend) * k;
            s.glideAt = -1;
        } else {
            if (s.glideAt < 0 || target != s.glideTo) {
                s.glideFrom = s.bend;
                s.glideTo = target;
                s.glideAt = now;
            }
            s.bend = BendStage.bend(s.glideFrom, s.glideTo, now - s.glideAt, GLIDE_BETWEEN_STAGES ? GLIDE_TICKS : 0);
        }
        s.step = BendStage.step(s.step, s.bend);
        return s.step;
    }

    /** the bend under a fish on the line, in steps (0 with none): its stage, the synced level plus one while the holder reels; calm for a
     *  fish on a line whose level is not known (never for our hook, which always sends it) */
    private static float fishTarget(Player player) {
        if (player.fishing == null || !(player.fishing.getHookedIn() instanceof AbstractFish)) {
            return 0;
        }
        return BendStage.target(level(player), reeling(player));
    }

    /** whether that player is reeling: holding use with one of our rods (the game syncs the use state of every player) */
    public static boolean reeling(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof CastingRodItem;
    }

    /** (0.16.2) for /creelstage (RodDebug): the stage that player's rod is being drawn at and why, read from what was last drawn (the
     *  step picked when the rod was drawn, capped as RodFlex.drawn caps it), not from the server; it names a glide still under way, the
     *  line-in models being drawn with a fish on, and a settled stage that is not the level's plus reeling */
    public static String stageText(Player player) {
        State s = peek(player);
        ItemStack stack = player.getItemHeldByArm(RodLine.holdingArm(player));
        if (s == null || !(stack.getItem() instanceof CastingRodItem)) {
            return "no rod of ours drawn";
        }
        if (player.fishing == null || !(player.fishing.getHookedIn() instanceof AbstractFish)) {
            return "stage " + drawn(player, stack) + ": no fish on the line";
        }
        int level = Math.max(HookLevel.CALM, level(player));
        boolean reeling = reeling(player);
        String why = switch (level) {
            case HookLevel.TENSION -> "tension";
            case HookLevel.DANGER -> "danger";
            default -> "calm";
        };
        int drawn = drawn(player, stack);
        boolean gliding = Math.abs(s.bend - s.glideTo) > 0.02F;
        boolean cast = player.getItemHeldByArm(FishingHookRenderer.getHoldingArm(player)) == stack;
        int want = BendStage.target(level, reeling);
        return "stage " + drawn + " of " + RodTable.FLEX_MAX + ": " + why + ", " + (reeling ? "reeling" : "not reeling")
            + (gliding ? ", gliding to " + Math.round(s.glideTo) : "") + (cast ? "" : ", LINE-IN MODEL")
            + (!gliding && drawn != want ? ", NOT THE STAGE " + want : "");
    }

    /** for the debug read-out: the local player's bend now */
    public static String debugText(Player player) {
        State s = peek(player);
        return s == null ? "flex: none yet" : String.format(Locale.ROOT, "flex: bend %.2f, step %d, level %d, reeling %s",
            s.bend, s.step, level(player), reeling(player));
    }
}
