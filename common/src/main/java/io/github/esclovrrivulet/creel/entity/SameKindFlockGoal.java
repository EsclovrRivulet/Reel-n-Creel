package io.github.esclovrrivulet.creel.entity;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import org.slf4j.Logger;

/** Vanilla's FollowFlockLeaderGoal with one change: it gathers fish of the mob's own EntityType, where vanilla's gathers every
 *  entity of the mob's Java class, and all the mod's fish share FishEntity, so a perch would school with a trout. Same intervals:
 *  a look round every 200-219 ticks (halved by reducedTickDelay, as vanilla's), a new path to the leader every 10, nothing else
 *  per tick. A fish carrying the scoreboard tag "creel_school_log" logs each school it forms, for the server scene test. */
public class SameKindFlockGoal extends Goal {
    public static final String LOG_TAG = "creel_school_log";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final FishEntity mob;
    private int timeToRecalcPath;
    private int nextStartTick;

    public SameKindFlockGoal(FishEntity mob) {
        this.mob = mob;
        this.nextStartTick = this.nextStartTick(mob);
    }

    protected int nextStartTick(FishEntity mob) {
        return reducedTickDelay(200 + mob.getRandom().nextInt(200) % 20);
    }

    @Override
    public boolean canUse() {
        if (this.mob.hasFollowers()) {
            return false;
        }
        if (this.mob.isFollower()) {
            return true;
        }
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        this.nextStartTick = this.nextStartTick(this.mob);
        List<FishEntity> near = this.mob.level().getEntitiesOfClass(FishEntity.class, this.mob.getBoundingBox().inflate(8.0, 8.0, 8.0),
            f -> f.getType() == this.mob.getType() && (f.canBeFollowed() || !f.isFollower()));
        FishEntity leader = near.stream().filter(FishEntity::canBeFollowed).findAny().orElse(this.mob);
        boolean log = this.mob.entityTags().contains(LOG_TAG);
        List<FishEntity> free = log ? near.stream().filter(f -> !f.isFollower() && f != leader).toList() : List.of();
        leader.addFollowers(near.stream().filter(f -> !f.isFollower()));
        if (log) {
            List<FishEntity> joined = free.stream().filter(FishEntity::isFollower).toList();
            if (!joined.isEmpty()) {
                // the check: every fish that just joined is of its leader's kind, and every fish of another kind nearby stayed out
                List<FishEntity> all = this.mob.level().getEntitiesOfClass(FishEntity.class, this.mob.getBoundingBox().inflate(8.0, 8.0, 8.0));
                long mixed = joined.stream().filter(f -> f.getType() != leader.getType()).count();
                long othersNear = all.stream().filter(f -> f.getType() != leader.getType()).count();
                LOGGER.info("creel school: {} took {} followers, {} of another kind ({} fish of another kind within reach)",
                    EntityType.getKey(leader.getType()), joined.size(), mixed, othersNear);
            }
        }
        return this.mob.isFollower();
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isFollower() && this.mob.inRangeOfLeader();
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.mob.stopFollowing();
    }

    @Override
    public void tick() {
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            this.mob.pathToLeader();
        }
    }
}
