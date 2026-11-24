package io.github.esclovrrivulet.creel.compat;

import net.minecraft.world.level.LevelAccessor;

/** (1.21.1 build only) 26.x passes a block's updateShape the level's tick access apart from the level; 1.21.1 has the level alone
 *  (a LevelAccessor, which schedules ticks itself). This stands in for 26.x's type (versions/1.21.1/java_renames.txt), holding the
 *  level, for FishSpawnBlockBase. */
public record ScheduledTickAccess(LevelAccessor level) {}
