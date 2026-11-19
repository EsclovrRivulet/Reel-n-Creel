package io.github.esclovrrivulet.creel.fishing;

/** (0.16.2) How the rod's drawn bend follows its target, apart from the client (client.RodFlex draws with it) so the scene tests can
 *  check it on a server. A bend is in steps (tools/rod_models.py FLEX; RodTable.FLEX_MIN to FLEX_MAX), the step drawn is a whole one. */
public final class BendStage {
    private BendStage() {}

    /** a step moves to a neighbour only once the bend is past the half-way point by this much, so it never flips between two */
    public static final float HYSTERESIS = 0.15F;

    /** the bend drawn under a fish, in steps, since a change of stage from `from` to `to` began `since` ticks ago: with glideTicks 0 or
     *  less (0.16.2, as drawn) the new stage at once, no shapes between; else a smoothstep over glideTicks (0.16.0 to 0.16.1) */
    public static float bend(float from, float to, double since, float glideTicks) {
        if (glideTicks <= 0) {
            return to;
        }
        float t = (float) Math.max(0, Math.min(1, since / glideTicks));
        return from + (to - from) * (t * t * (3 - 2 * t));
    }

    /** the whole step drawn for a bend, from the step drawn before (HYSTERESIS), within FLEX_MIN to FLEX_MAX */
    public static int step(int before, float bend) {
        int step = before;
        while (step < RodTable.FLEX_MAX && bend > step + 0.5F + HYSTERESIS) {
            step++;
        }
        while (step > RodTable.FLEX_MIN && bend < step - 0.5F - HYSTERESIS) {
            step--;
        }
        return step;
    }

    /** the stage a fish on the line asks the rod to show: the level's (calm if unknown) plus one while reeling, at most FLEX_MAX */
    public static int target(int level, boolean reeling) {
        return Math.min(RodTable.FLEX_MAX, HookLevel.stage(Math.max(HookLevel.CALM, level), reeling));
    }

    /** the step drawn the moment a fish's stage changes, from the step drawn before, with no glide: the stage itself (for the tests) */
    public static int drawnAtOnce(int before, int level, boolean reeling) {
        int to = target(level, reeling);
        return step(before, bend(before, to, 0, 0));
    }
}
