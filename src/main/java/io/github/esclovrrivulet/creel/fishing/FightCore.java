package io.github.esclovrrivulet.creel.fishing;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import io.github.esclovrrivulet.creel.fish.Tier;

/** (0.16.0) The fight's mind, apart from the world: the fish's stage, its rolls, its stamina, the running credit and the snap. The hook
 *  (CastingHook.fight) moves the fish, plays the cues and acts on what tick returns; the tests (FightTest.simulate) run this same class
 *  alone, hundreds of fights a tier, with a modelled line. Nothing here runs except inside the hook's tick (or a test).
 *
 *  Stages: REST, PULL (easing marks the PULL-level easing after a run), RUN; ON_LINE once beaten. At each stage's end the fish rolls:
 *  - REST: rest on a little (rest_again, rest_more_ticks), or start a PULL;
 *  - PULL: RUN with a chance from its freshness (run_chance, tired to fresh) moved by provocation (the share of the pull the player
 *    reeled through: above provoke_share[1] plus provoke, below provoke_share[0] minus it), only out of a pull of warn_ticks or more;
 *    otherwise settle back to REST (the feint). Spent (no stamina left), its last pull runs if the credit allows, else it is beaten;
 *  - RUN: always the easing (ease_ticks, PULL-level), then REST, or beaten when spent.
 *  Lengths are rolled skewed: min + (max - min) * x ^ length_skew, x the mean of two even rolls (mostly middling, now and then long).
 *  The credit: the fish's running ticks may never pass run_cap of the fight's ticks so far, at any moment; a run longer than the credit
 *  covers is cut to it, and one the credit cannot cover for run_ticks[0] is not run (the pull settles).
 *  (0.16.1) The fight begins in a REST.
 *  (0.16.2) No hidden strain: what the player perceives follows the fish's state. The snap is time spent reeling a running fish: in a
 *  RUN each reeled tick adds one to a counter and each tick let go takes one off (never below 0); it starts at 0 with every run, and at
 *  snap_reel_ticks the line parts. Nothing else can snap it (not a rest, a pull, the easing or a beaten fish). */
public final class FightCore {
    public static final int NONE = 0, CHANGED = 1, SNAP = 2, BEATEN = 3;

    private final RandomSource random;
    private final Tier tier;
    CastingHook.Line line = CastingHook.Line.BITE;
    boolean easing;
    /** whether the present rest has already rested on once (it may only once) */
    boolean restedOn;
    int phaseTicks;
    double stamina, fullStamina;
    /** fight ticks so far, running ticks so far, ticks into the present pull and how many of them were reeled */
    int elapsed, runTicks, pullTicks, pullReeled;
    int runs;
    /** (0.16.2) the present run's snap counter: reeled ticks less ticks let go, never below 0; 0 at every run's start */
    int runReeled;

    public FightCore(RandomSource random, Tier tier) {
        this.random = random;
        this.tier = tier;
    }

    /** the hook set: a fish with stamina begins calm, in a rest (0.16.1; it pulled at once before); one with none (tiny) is on the line,
     *  never fighting */
    public void begin(FishingData.Fight cfg, double stamina) {
        this.stamina = this.fullStamina = stamina;
        if (stamina <= 0) {
            this.line = CastingHook.Line.ON_LINE;
            return;
        }
        this.startRest(cfg);
    }

    /** (0.16.1) a fresh resting fish reeled up to the bank bolts: it starts a pull there and then (the rest is cut short) */
    public void spook(FishingData.Fight cfg) {
        if (this.line == CastingHook.Line.REST) {
            this.startPull(cfg);
            this.spooks++;
        }
    }

    /** for the tests: the rests cut short at the bank so far */
    int spooks;

    /** the share of its stamina the fish has left, 0 to 1 */
    public double staminaShare() {
        return this.fullStamina <= 0 ? 0 : Mth.clamp(this.stamina / this.fullStamina, 0, 1);
    }

    /** (0.16.1) the fastest a fighting fish may be reeled in now, blocks a tick, before its state's gain: the tier's reel speed, or the
     *  distance still to cover over max(pace_tail_ticks, stamina left times reel_pace) ticks, whichever is less. So a fish hooked far
     *  out comes in about as it tires, and one that ran back out is brought in faster again. */
    public static double pacedReel(FishingData.Fight cfg, double reelSpeed, double toCover, double stamina) {
        if (toCover <= 0) {
            return 0;
        }
        double pace = toCover / Math.max(cfg.paceTailTicks(), stamina * cfg.reelPace());
        // never slower than PACE_CRAWL of the reel speed (a rest uses no stamina, so the pace alone would only ever close in on the bank)
        return Math.min(Math.min(reelSpeed, toCover), Math.max(pace, reelSpeed * PACE_CRAWL));
    }

    /** (0.16.1) the slowest a reeled fighting fish comes in, as a share of its tier's reel speed */
    public static final double PACE_CRAWL = 0.1;

    public boolean fighting() {
        return this.line == CastingHook.Line.REST || this.line == CastingHook.Line.PULL || this.line == CastingHook.Line.RUN;
    }

    /** the running credit in running ticks: run_cap of the fight so far less the running done */
    public double credit(FishingData.Fight cfg) {
        return cfg.cap(this.tier) * this.elapsed - this.runTicks;
    }

    /** the longest run the credit covers now (the cap holding at every tick of it) */
    public int coveredRun(FishingData.Fight cfg) {
        double c = cfg.cap(this.tier);
        return (int) Math.floor(this.credit(cfg) / (1 - c));
    }

    /** a running fish's speed share: 1 fresh, down to tired_speed spent */
    public double runShare(FishingData.Fight cfg) {
        double left = this.fullStamina <= 0 ? 0 : Mth.clamp(this.stamina / this.fullStamina, 0, 1);
        return Mth.lerp(left, cfg.tiredSpeed(), 1.0);
    }

    private int roll(int min, int max, double skew) {
        double x = (this.random.nextDouble() + this.random.nextDouble()) / 2;
        return min + (int) Math.round((max - min) * Math.pow(x, skew));
    }

    private void startPull(FishingData.Fight cfg) {
        this.line = CastingHook.Line.PULL;
        this.easing = false;
        this.pullTicks = 0;
        this.pullReeled = 0;
        this.phaseTicks = this.roll(cfg.pullMin(), cfg.pullMax(), cfg.lengthSkew());
    }

    private void startEase(FishingData.Fight cfg) {
        this.line = CastingHook.Line.PULL;
        this.easing = true;
        this.pullTicks = 0;
        this.pullReeled = 0;
        this.phaseTicks = Math.max(1, cfg.easeTicks());
    }

    private void startRest(FishingData.Fight cfg) {
        this.line = CastingHook.Line.REST;
        this.easing = false;
        this.restedOn = false;
        this.phaseTicks = this.roll(cfg.restMin(), cfg.restMax(), cfg.lengthSkew());
    }

    private void startRun(int len) {
        this.line = CastingHook.Line.RUN;
        this.easing = false;
        this.phaseTicks = len;
        this.runReeled = 0;
        this.runs++;
    }

    /** the chance a pull that has just ended runs: freshness, moved by how much of it the player reeled through */
    public double runChance(FishingData.Fight cfg) {
        double left = this.fullStamina <= 0 ? 0 : Mth.clamp(this.stamina / this.fullStamina, 0, 1);
        double p = Mth.lerp(left, cfg.runTired(), cfg.runFresh());
        double reeled = this.pullTicks == 0 ? 0 : this.pullReeled / (double) this.pullTicks;
        if (reeled > cfg.provokeAbove()) {
            p += cfg.provoke();
        } else if (reeled < cfg.settleBelow()) {
            p -= cfg.provoke();
        }
        return Mth.clamp(p, 0, 1);
    }

    /** a (real) pull's end: run, if it rolls one, the pull was long enough and the credit covers a whole shortest run; else settle */
    private int endPull(FishingData.Fight cfg) {
        boolean spent = this.stamina <= 0;
        boolean wants = spent || this.random.nextDouble() < this.runChance(cfg);
        int covered = this.coveredRun(cfg);
        if (wants && this.pullTicks >= cfg.warnTicks() && covered >= cfg.runMin()) {
            this.startRun(Math.min(covered, this.roll(cfg.runMin(), cfg.runMax(), cfg.lengthSkew())));
            return CHANGED;
        }
        if (spent) {
            this.line = CastingHook.Line.ON_LINE;
            this.easing = false;
            return BEATEN;
        }
        this.startRest(cfg);
        return CHANGED;
    }

    /** one tick of the fight (REST, PULL or RUN only): stamina, the snap counter, the stage's end and its roll. Returns NONE, CHANGED
     *  (a new stage), BEATEN or SNAP (the line parts: the caller lets go). */
    public int tick(FishingData.Fight cfg, boolean reeling) {
        int ev = NONE;
        double drain = reeling ? cfg.reeledDrain() : 1.0;
        this.elapsed++;
        switch (this.line) {
            case RUN -> {
                this.runTicks++;
                this.stamina = Math.max(0, this.stamina - drain);
                // (0.16.2) the snap lives only here: reeling a running fish adds to the counter, letting go takes it back
                this.runReeled = reeling ? this.runReeled + 1 : Math.max(0, this.runReeled - 1);
                if (this.runReeled >= cfg.snapReelTicks()) {
                    return SNAP;
                }
                if (--this.phaseTicks <= 0) {
                    this.startEase(cfg);            // never straight to a rest: it eases off through tension first
                    ev = CHANGED;
                }
            }
            case PULL -> {
                this.pullTicks++;
                if (reeling) {
                    this.pullReeled++;
                }
                this.stamina = Math.max(0, this.stamina - drain);
                if (--this.phaseTicks <= 0) {
                    if (this.easing) {
                        if (this.stamina <= 0) {
                            this.line = CastingHook.Line.ON_LINE;
                            this.easing = false;
                            ev = BEATEN;
                        } else {
                            this.startRest(cfg);
                            ev = CHANGED;
                        }
                    } else {
                        ev = this.endPull(cfg);
                    }
                }
            }
            case REST -> {
                if (!reeling) {
                    this.stamina = Math.min(this.fullStamina, this.stamina + cfg.restRecover());       // waiting is not free
                }
                if (--this.phaseTicks <= 0) {
                    if (!this.restedOn && this.random.nextDouble() < cfg.restAgain()) {
                        this.restedOn = true;
                        this.phaseTicks = this.roll(cfg.restMoreMin(), cfg.restMoreMax(), 1.0);      // rests on a little
                    } else {
                        this.startPull(cfg);
                        ev = CHANGED;
                    }
                }
            }
            default -> {
                return NONE;
            }
        }
        return ev;
    }

    /** (0.16.2) the present run's snap counter (0 outside a run's reeling; it is reset at every run's start) */
    public int runReeled() {
        return this.line == CastingHook.Line.RUN ? this.runReeled : 0;
    }

    /** for the visual test: held in that state with endless stamina and time */
    void hold(CastingHook.Line state) {
        this.stamina = this.fullStamina = 1.0E6;
        this.phaseTicks = 1_000_000;
        this.easing = false;
        this.line = state;
        if (state == CastingHook.Line.RUN) {
            this.runs++;
            this.runReeled = 0;
        }
    }

    public CastingHook.Line line() {
        return this.line;
    }

    public boolean easing() {
        return this.line == CastingHook.Line.PULL && this.easing;
    }

    public double stamina() {
        return this.stamina;
    }

    public int runs() {
        return this.runs;
    }

    public int elapsed() {
        return this.elapsed;
    }

    public int runTicks() {
        return this.runTicks;
    }

    public int phaseTicks() {
        return this.phaseTicks;
    }
}
