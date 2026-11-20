package io.github.esclovrrivulet.creel.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.Species;

import java.util.EnumSet;
import java.util.List;

/** (0.23.0) Mating as something you see (docs/SPAWN.md; the owner asked for it after 0.22.0, where the second feeding laid the spawn
 *  at once). Feeding only puts a fish in love (FishBreeding). Then:
 *  - the search: a fish in love looks, every SEARCH_EVERY ticks (staggered by its id), for the nearest fish of its species in love within
 *    FishBreeding.PAIR_RANGE blocks that is not paired already and not on a line; found, the two are paired (a Pairing, held in a plain
 *    field on each fish: not saved, not synced);
 *  - stage 1, APPROACH: both swim to each other. When their noses are near (centres within the two half-widths and NOSE more), hearts
 *    on both, and the place for the spawn is found from their midpoint (FishSpawnBlock.find, the search 0.22.0 used to lay). No place:
 *    a puff of smoke, both out of love and on the cooldown, as 0.22.0 did;
 *  - stage 2, RISE: both swim up to the water just under that place, side by side;
 *  - stage 3, COURT: COURT_TICKS there, circling each other slowly, nose to tail, hearts on both every 10 to 15 ticks;
 *  - stage 4, the lay: the spawn placed (FishSpawnBlock.place: the laying sound and game event), the experience, both out of love and
 *    on the 6000-tick cooldown.
 *  It cannot fail from path-finding: DEADLINE ticks after the pairing the spawn is laid anyway, wherever the fish are, at the place
 *  found (or, when they never met, at the place found from their midpoint then). Love does not run out during a pairing. If one of the
 *  pair dies, is bucketed, is hooked or unloads (it is removed, or no longer holds this pairing, or a hook holds it), the pairing ends
 *  and the other is left with its own love stamp: in love again if its love time is left, no cooldown.
 *
 *  Steering: far off, the fish's own navigation (vanilla's WaterBoundPathNavigation, as its random swimming uses), a new path every
 *  10 ticks; within NEAR blocks, where a path is too coarse, a small push toward the point each tick and the fish turned to face its
 *  way (as vanilla's FishMoveControl turns it), so it does not shove. Out of water a fish only flops (vanilla's), and the deadline
 *  still holds.
 *
 *  What it costs: every fish that breeds has the goal; for a fish neither in love nor paired its check is one comparison of a stamp
 *  with the game time (FishEntity.mateGate). A fish in love and unpaired does one entity query every 10 ticks; a paired fish ticks
 *  the goal (steering, the stage's checks) and asks about hooks every 10 ticks. Nothing else, and nothing for any other fish. */
public final class FishMating {
    private FishMating() {}

    /** a fish in love looks for a mate this often (ticks), staggered by its id */
    public static final int SEARCH_EVERY = 10;
    /** a pairing lays by this many ticks after it began, whatever happened */
    public static final int DEADLINE = 300;
    /** how long the pair court under the place before they lay */
    public static final int COURT_TICKS = 60;
    /** how near the noses must be to meet: the two half-widths and this */
    public static final double NOSE = 0.35;
    /** within this distance a fish is steered by a push, not a path */
    public static final double NEAR = 2.0;
    /** the swimming speed (navigation's modifier) of a fish in a pairing */
    public static final double SPEED = 1.15;

    public enum Stage { APPROACH, RISE, COURT, DONE }

    public enum Result { IN_PROGRESS, LAID, NO_PLACE, BROKEN }

    /** Looks for a mate for a fish in love (the goal's check, every SEARCH_EVERY ticks): the nearest of its species in love within
     *  range, not paired, not on a line (nor this fish); found, both are paired. Returns the pairing or null. */
    static Pairing findMate(ServerLevel level, FishEntity fish, long now) {
        double r = FishBreeding.PAIR_RANGE;
        List<FishEntity> near = level.getEntitiesOfClass(FishEntity.class, fish.getBoundingBox().inflate(r),
            f -> f != fish && f.getType() == fish.getType() && f.isAlive() && f.pairing() == null && f.inLove(now) && f.distanceToSqr(fish) <= r * r);
        if (near.isEmpty() || FishBreeding.onLine(level, fish)) {
            return null;
        }
        near.sort((x, y) -> Double.compare(x.distanceToSqr(fish), y.distanceToSqr(fish)));
        for (FishEntity f : near) {
            if (!FishBreeding.onLine(level, f)) {
                return new Pairing(fish, f, now);
            }
        }
        return null;
    }

    /** A pair of fish on their way to lay, shared by the two (each holds it in FishEntity.pairing). The stage moves on once a game
     *  tick, from whichever fish's goal ticks first; each fish steers itself. The times and distances it records (when they met, how
     *  near, where each was when the court began, where and when it laid, whether by the deadline) are what the scene tests read. */
    public static final class Pairing {
        public final FishEntity a, b;
        public final Species species;
        public final long pairedAt, deadline;
        private Stage stage = Stage.APPROACH;
        private Result result = Result.IN_PROGRESS;
        private long lastAdvance = Long.MIN_VALUE, nextHeart;
        /** the place found when they met (null before, or when there was none), the midpoint it was found from */
        private BlockPos place;
        private Vec3 mid;
        private long metAt = -1, courtAt = -1, endedAt = -1;
        private double metDistance = -1, courtGap = -1;
        private boolean byDeadline;
        private BlockPos laidAt;
        /** the court's turn: its start angle, and the side of the line between them each fish keeps while rising */
        private double angle0;
        private Vec3 side = Vec3.ZERO;

        Pairing(FishEntity a, FishEntity b, long now) {
            this.a = a;
            this.b = b;
            this.species = a.species();
            this.pairedAt = now;
            this.deadline = now + DEADLINE;
            a.setPairing(this);
            b.setPairing(this);
        }

        public FishEntity other(FishEntity f) {
            return f == this.a ? this.b : this.a;
        }

        public Stage stage() { return this.stage; }
        public Result result() { return this.result; }
        public BlockPos place() { return this.place; }
        public Vec3 mid() { return this.mid; }
        public long metAt() { return this.metAt; }
        public double metDistance() { return this.metDistance; }
        public long courtAt() { return this.courtAt; }
        public double courtGap() { return this.courtGap; }
        public long endedAt() { return this.endedAt; }
        public boolean byDeadline() { return this.byDeadline; }
        public BlockPos laidAt() { return this.laidAt; }

        /** whether a fish of the pair is still in it: alive, in the world, holding this pairing */
        boolean holds(FishEntity f) {
            return f.isAlive() && !f.isRemoved() && f.pairing() == this;
        }

        /** the pairing ends without a lay (one of the pair gone or hooked): each fish keeps its own love stamp, no cooldown */
        void breakUp() {
            if (this.result != Result.IN_PROGRESS) {
                return;
            }
            this.result = Result.BROKEN;
            this.stage = Stage.DONE;
            this.endedAt = this.a.level().getGameTime();
            for (FishEntity f : new FishEntity[] {this.a, this.b}) {
                if (f.pairing() == this) {
                    f.setPairing(null);
                    f.getNavigation().stop();
                }
            }
        }

        /** the stage moves on (once a game tick, whichever fish comes first) */
        void advance(ServerLevel level, long now) {
            if (this.lastAdvance == now || this.result != Result.IN_PROGRESS) {
                return;
            }
            this.lastAdvance = now;
            if (now >= this.deadline) {
                this.byDeadline = true;
                this.lay(level, now);
                return;
            }
            switch (this.stage) {
                case APPROACH -> {
                    double d = centre(this.a).distanceTo(centre(this.b));
                    if (d <= (this.a.getBbWidth() + this.b.getBbWidth()) / 2.0 + NOSE) {
                        this.met(level, now, d);
                    }
                }
                case RISE -> {
                    if (this.arrived(this.a) && this.arrived(this.b)) {
                        this.stage = Stage.COURT;
                        this.courtAt = now;
                        this.courtGap = Math.max(gap(this.a, this.spot(this.a)), gap(this.b, this.spot(this.b)));
                        this.nextHeart = now + 10 + this.a.getRandom().nextInt(6);
                        Vec3 c = this.centreOfPlace();
                        this.angle0 = Math.atan2(this.a.getZ() - c.z, this.a.getX() - c.x);
                    }
                }
                case COURT -> {
                    if (now >= this.nextHeart) {
                        level.broadcastEntityEvent(this.a, FishBreeding.HEARTS);
                        level.broadcastEntityEvent(this.b, FishBreeding.HEARTS);
                        this.nextHeart = now + 10 + this.a.getRandom().nextInt(6);
                    }
                    if (now - this.courtAt >= COURT_TICKS) {
                        this.lay(level, now);
                    }
                }
                default -> { }
            }
        }

        /** their noses met: hearts, and the place found from their midpoint; none, the pairing ends there with the smoke */
        private void met(ServerLevel level, long now, double d) {
            this.metAt = now;
            this.metDistance = d;
            this.mid = centre(this.a).add(centre(this.b)).scale(0.5);
            level.broadcastEntityEvent(this.a, FishBreeding.HEARTS);
            level.broadcastEntityEvent(this.b, FishBreeding.HEARTS);
            this.place = CreelMod.FISH_SPAWN.find(level, this.mid);
            if (this.place == null) {
                this.finish(level, now, null);
                return;
            }
            Vec3 ab = this.b.position().subtract(this.a.position()).multiply(1, 0, 1);
            this.side = ab.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : ab.normalize();
            this.stage = Stage.RISE;
        }

        /** the lay (after the court, or at the deadline): at the place found (found again near it if something came there since; when
         *  they never met, found now from their midpoint) */
        private void lay(ServerLevel level, long now) {
            BlockPos at = this.place;
            if (at == null) {
                this.mid = centre(this.a).add(centre(this.b)).scale(0.5);
                at = CreelMod.FISH_SPAWN.find(level, this.mid);
            } else if (!CreelMod.FISH_SPAWN.placeable(level, at)) {
                at = CreelMod.FISH_SPAWN.find(level, Vec3.atCenterOf(at.below()));
            }
            this.finish(level, now, at != null && CreelMod.FISH_SPAWN.place(level, at, this.species, this.a) ? at : null);
        }

        /** the end of a pairing that laid, or found no place: both out of love and on the cooldown, vanilla's breeding experience (1 to
         *  7, when mobs drop loot, as Animal.finalizeSpawnChildFromBreeding), the smoke when nothing was laid */
        private void finish(ServerLevel level, long now, BlockPos laid) {
            this.result = laid != null ? Result.LAID : Result.NO_PLACE;
            this.stage = Stage.DONE;
            this.laidAt = laid;
            this.endedAt = now;
            for (FishEntity f : new FishEntity[] {this.a, this.b}) {
                if (f.pairing() == this) {
                    f.setPairing(null);
                }
                f.setLoveUntil(0);
                f.setCooldownUntil(now + FishBreeding.COOLDOWN_TICKS);
                f.getNavigation().stop();
            }
            if (level.getGameRules().get(GameRules.MOB_DROPS)) {
                level.addFreshEntity(new ExperienceOrb(level, this.a.getX(), this.a.getY(), this.a.getZ(), this.a.getRandom().nextInt(7) + 1));
            }
            if (laid == null) {
                Vec3 m = this.mid != null ? this.mid : centre(this.a).add(centre(this.b)).scale(0.5);
                level.sendParticles(ParticleTypes.SMOKE, m.x, m.y + 0.3, m.z, 7, 0.3, 0.2, 0.3, 0.02);
            }
        }

        /** the middle of the water block under the place, at the height a fish of this pair keeps there */
        private Vec3 centreOfPlace() {
            return new Vec3(this.place.getX() + 0.5, this.place.getY() - 1, this.place.getZ() + 0.5);
        }

        /** where a fish of the pair goes while rising: under the place, its side of the pair, its back just under the surface */
        private Vec3 spot(FishEntity f) {
            Vec3 c = this.centreOfPlace();
            double half = (this.a.getBbWidth() + this.b.getBbWidth()) / 4.0 + 0.05;
            Vec3 off = this.side.scale(f == this.a ? -half : half);
            return new Vec3(c.x + off.x, c.y + depth(f), c.z + off.z);
        }

        private boolean arrived(FishEntity f) {
            Vec3 t = this.spot(f);
            double dx = f.getX() - t.x, dz = f.getZ() - t.z;
            return dx * dx + dz * dz < 0.75 * 0.75 && Math.abs(f.getY() - t.y) < 0.6;
        }

        /** a fish's steering for its stage (from its own goal, every tick) */
        void steer(FishEntity f, long now) {
            switch (this.stage) {
                case APPROACH -> FishMating.steer(f, this.other(f).position(), true);
                case RISE -> FishMating.steer(f, this.spot(f), true);
                case COURT -> {
                    // circling the place's middle, opposite each other, slowly: each faces the way it goes round
                    Vec3 c = this.centreOfPlace();
                    double r = (this.a.getBbWidth() + this.b.getBbWidth()) / 4.0 + 0.05;
                    double t = this.angle0 + (now - this.courtAt) * 0.07 + (f == this.a ? 0.0 : Math.PI);
                    Vec3 to = new Vec3(c.x + Math.cos(t) * r, c.y + depth(f), c.z + Math.sin(t) * r);
                    FishMating.steer(f, to, false);
                    face(f, -Math.sin(t), Math.cos(t), 8.0F);
                }
                default -> { }
            }
        }
    }

    /** a fish's height in the water block under the place: its back at three quarters of the block, under the surface (8/9) */
    static double depth(FishEntity f) {
        return Math.max(0.02, 0.75 - f.getBbHeight());
    }

    static Vec3 centre(FishEntity f) {
        return f.getBoundingBox().getCenter();
    }

    static double gap(FishEntity f, Vec3 spot) {
        return f.position().distanceTo(spot);
    }

    /** steers a fish toward a point: by its navigation when farther than NEAR (a new path every 10 ticks, staggered by its id), else
     *  by a small push each tick in water, turned to face its way (face: whether to turn it here) */
    static void steer(FishEntity f, Vec3 target, boolean faceWay) {
        Vec3 to = target.subtract(f.position());
        double d = to.length();
        if (d > NEAR) {
            if ((f.tickCount + f.getId()) % 10 == 0 || f.getNavigation().isDone()) {
                f.getNavigation().moveTo(target.x, target.y, target.z, SPEED);
            }
            return;
        }
        if (!f.getNavigation().isDone()) {
            f.getNavigation().stop();
        }
        if (!f.isInWater() || d < 1.0E-3) {
            return;
        }
        double push = Math.min(0.009, d * 0.02 + 0.002);
        f.setDeltaMovement(f.getDeltaMovement().scale(0.92).add(to.scale(push / d)));
        if (faceWay && to.x * to.x + to.z * to.z > 0.0025) {
            face(f, to.x, to.z, 10.0F);
        }
    }

    /** turns a fish (body and head) toward a horizontal direction, at most step degrees a tick, as vanilla's FishMoveControl does */
    static void face(FishEntity f, double dx, double dz, float step) {
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float y = Mth.approachDegrees(f.getYRot(), yaw, step);
        f.setYRot(y);
        f.yBodyRot = y;
        f.yHeadRot = y;
    }

    /** The mating goal (FishEntity.registerGoals, on every fish that breeds, at priority 1: under panic, over vanilla's avoiding of
     *  players, the schooling and the random swimming; it holds movement and looking while it runs). */
    public static final class MatingGoal extends Goal {
        private final FishEntity fish;

        public MatingGoal(FishEntity fish) {
            this.fish = fish;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            long now = this.fish.level().getGameTime();
            if (this.fish.mateGate() <= now) {
                return false;                       // not in love, not paired: the one comparison every other fish makes
            }
            if (this.fish.pairing() != null) {
                return true;
            }
            if ((this.fish.tickCount + this.fish.getId()) % SEARCH_EVERY != 0 || !(this.fish.level() instanceof ServerLevel level)) {
                return false;
            }
            return findMate(level, this.fish, now) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.fish.pairing() != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            this.fish.getNavigation().stop();
        }

        @Override
        public void tick() {
            Pairing p = this.fish.pairing();
            if (p == null || !(this.fish.level() instanceof ServerLevel level)) {
                return;
            }
            FishEntity other = p.other(this.fish);
            if (!p.holds(other) || (this.fish.tickCount + this.fish.getId()) % 10 == 0
                && (FishBreeding.onLine(level, this.fish) || FishBreeding.onLine(level, other))) {
                p.breakUp();
                return;
            }
            long now = level.getGameTime();
            p.advance(level, now);
            if (this.fish.pairing() == p) {
                p.steer(this.fish, now);
            }
        }
    }
}
