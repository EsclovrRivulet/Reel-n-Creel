package io.github.esclovrrivulet.creel.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/** (0.23.0) A fish that breeds follows a player near it who holds seagrass in either hand, as vanilla's animals follow their food
 *  (docs/SPAWN.md). Vanilla's TemptGoal looks for a player at every check (every other tick, for every mob that has it); this one
 *  looks once a second per fish (LOOK_EVERY ticks, staggered by its id; one comparison on the other checks), within RANGE blocks. It
 *  swims toward the player by its own navigation, a new path every 10 ticks, and stops STOP blocks short (and its own width), so it
 *  never shoves into them; it gives up when the player puts the seagrass away, goes out of range or becomes a spectator. A fish on a
 *  line or in a pairing (FishMating) ignores seagrass: checked when it starts, and every 10 ticks while it follows (the line; the pairing
 *  at every check, a field). Only fish that breed have it (FishEntity.registerGoals): never a giant, never vanilla's fish. Priority 1,
 *  as the mating goal: over vanilla's avoiding of players (2), the schooling and the random swimming, under panic. */
public class SeagrassFollowGoal extends Goal {
    public static final int LOOK_EVERY = 20;
    public static final double RANGE = 8.0, STOP = 1.5, SPEED = 1.2;
    private final FishEntity fish;
    private Player player;
    private int repath;

    public SeagrassFollowGoal(FishEntity fish) {
        this.fish = fish;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** whether a player holds seagrass in either hand */
    public static boolean holds(Player p) {
        return p.getMainHandItem().is(Items.SEAGRASS) || p.getOffhandItem().is(Items.SEAGRASS);
    }

    /** the player this fish follows, or null (the scene tests ask) */
    public Player following() {
        return this.player;
    }

    @Override
    public boolean canUse() {
        if ((this.fish.tickCount + this.fish.getId()) % LOOK_EVERY != 0 || this.fish.pairing() != null
            || !(this.fish.level() instanceof ServerLevel level)) {
            return false;
        }
        Player p = level.getNearestPlayer(this.fish.getX(), this.fish.getY(), this.fish.getZ(), RANGE,
            e -> !e.isSpectator() && e.isAlive() && e instanceof Player q && holds(q));
        if (p == null || FishBreeding.onLine(level, this.fish)) {
            return false;
        }
        this.player = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        Player p = this.player;
        if (p == null || !p.isAlive() || p.isSpectator() || !holds(p) || this.fish.pairing() != null
            || this.fish.distanceToSqr(p) > RANGE * RANGE) {
            return false;
        }
        return (this.fish.tickCount + this.fish.getId()) % 10 != 0 || !(this.fish.level() instanceof ServerLevel level)
            || !FishBreeding.onLine(level, this.fish);
    }

    @Override
    public void start() {
        this.repath = 0;
    }

    @Override
    public void stop() {
        this.player = null;
        this.fish.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player p = this.player;
        if (p == null) {
            return;
        }
        this.fish.getLookControl().setLookAt(p, this.fish.getMaxHeadYRot() + 20, this.fish.getMaxHeadXRot());
        double stop = STOP + this.fish.getBbWidth();
        if (this.fish.distanceToSqr(p) < stop * stop) {
            this.fish.getNavigation().stop();
        } else if (--this.repath <= 0) {
            this.repath = this.adjustedTickDelay(10);
            this.fish.getNavigation().moveTo(p, SPEED);
        }
    }
}
