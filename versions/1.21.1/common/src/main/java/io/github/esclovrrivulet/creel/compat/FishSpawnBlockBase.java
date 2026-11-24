package io.github.esclovrrivulet.creel.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** (1.21.1 build only) What block.FishSpawnBlock extends in the 1.21.1 build (versions/1.21.1/java_renames.txt puts it in place of
 *  Block): three of the block's methods the game calls have other arguments in 1.21.1, so the game's calls are passed on to
 *  FishSpawnBlock's own (26.x's form), and FishSpawnBlock's calls of super reach the game's. Nothing else. */
public abstract class FishSpawnBlockBase extends Block {
    protected FishSpawnBlockBase(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return this.updateShape(state, level, new ScheduledTickAccess(level), pos, dir, neighborPos, neighbor, level.getRandom());
    }

    /** FishSpawnBlock's (26.x's form); this one is the game's */
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return super.updateShape(state, dir, neighbor, ticks.level(), pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        this.entityInside(state, level, pos, entity, InsideBlockEffectApplier.NONE, true);
    }

    /** FishSpawnBlock's (26.x's form); this one is the game's */
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean first) {
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return this.getCloneItemStack(level, pos, state, false);
    }

    /** FishSpawnBlock's (26.x's form); this one is the game's */
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return super.getCloneItemStack(level, pos, state);
    }
}
