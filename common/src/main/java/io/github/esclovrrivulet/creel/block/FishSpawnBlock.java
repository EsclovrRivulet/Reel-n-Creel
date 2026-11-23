package io.github.esclovrrivulet.creel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import io.github.esclovrrivulet.creel.item.FishSpawnItem;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Sizes;
import io.github.esclovrrivulet.creel.fish.Species;

import java.util.ArrayList;
import java.util.List;
import io.github.esclovrrivulet.creel.compat.Compat;

/** (0.22.0) Fish Spawn, creel:fish_spawn (docs/SPAWN.md): the eggs a pair of our fish lays on the water's surface when bred
 *  (entity.FishBreeding). It is vanilla's frogspawn in all it does (FrogspawnBlock, read from the 26.3 jar): it lies in the air block
 *  over water (the fluid there in minecraft:supports_frogspawn, or the block in the block tag of that name, and no fluid in its own
 *  block) and goes when that support goes; a falling block breaks it; no collision, broken at once, frogspawn's sounds, popped by a
 *  piston, washed away by flowing water (the block tag minecraft:washed_away_by_fluids, as frogspawn); it drops nothing. 0.23.0: one
 *  item for creative mode (item.FishSpawnItem, the species in its block_state component), placed on water as frogspawn's, and given
 *  by pick-block; it cannot be had in survival. It hatches on one scheduled tick, 3600 to 11999 ticks after it is placed
 *  (frogspawn's own numbers), into 1 to 3 fish of its species in the water under it. Nothing ticks in between: no block entity, no
 *  random ticks.
 *
 *  Its state: the species (SPECIES, every fish that breeds) and, for the picture's bleed-out, whether each side touches another Fish
 *  Spawn of any species (NORTH, EAST, SOUTH, WEST), kept up to date as a fence keeps its connections: set on placement from the
 *  neighbours (Block.updateFromNeighbourShapes) and changed in updateShape when a neighbour changes; nothing polls. The picture
 *  (tools/spawn_art.py) is a multipart blockstate: the jelly and eggs always, a fringe on each open side, a corner where two sides beside
 *  each other are open. */
public class FishSpawnBlock extends Block {
    public static final SpeciesProperty SPECIES = new SpeciesProperty("species");
    public static final BooleanProperty NORTH = PipeBlock.NORTH, EAST = PipeBlock.EAST, SOUTH = PipeBlock.SOUTH, WEST = PipeBlock.WEST;
    /** frogspawn's: a column 16 wide, 1.5 high */
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 1.5);
    /** frogspawn's hatch delay: RandomSource.nextInt(3600, 12000) */
    public static final int MIN_HATCH_DELAY = 3600, MAX_HATCH_DELAY = 12000;
    /** how many fish hatch: 1 to 3 (the owner's numbers; frogspawn gives 2 to 5 tadpoles) */
    public static final int MIN_HATCH = 1, MAX_HATCH = 3;
    /** Where a bred pair's spawn may go (lay): the columns up to SEARCH_RADIUS blocks each way from the pair's midpoint (7 x 7), each
     *  read upward from the midpoint's height through water, at most SEARCH_RISE blocks, to its surface. */
    public static final int SEARCH_RADIUS = 3, SEARCH_RISE = 16;

    public FishSpawnBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SPECIES, SPECIES.getPossibleValues().getFirst())
            .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPECIES, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** frogspawn's: on water (or a block that supports frogspawn), with no fluid in its own block */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level, pos.below());
    }

    private static boolean mayPlaceOn(BlockGetter level, BlockPos below) {
        FluidState under = level.getFluidState(below);
        FluidState here = level.getFluidState(below.above());
        return (under.is(FluidTags.SUPPORTS_FROGSPAWN) || level.getBlockState(below).is(BlockTags.SUPPORTS_FROGSPAWN)) && here.is(Fluids.EMPTY);
    }

    /** the hatch is scheduled once, when the block first appears: not again when only its sides change (a neighbour's spawn came or
     *  went), so a patch growing round it does not put its hatch off */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, level.getRandom().nextInt(MIN_HATCH_DELAY, MAX_HATCH_DELAY));
        }
    }

    /** frogspawn's (gone, without drops, when its support goes), and the sides: a side is true while another Fish Spawn of any species
     *  is beside it */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (!this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BooleanProperty side = side(dir);
        if (side != null) {
            state = state.setValue(side, neighbor.is(this));
        }
        return super.updateShape(state, level, ticks, pos, dir, neighborPos, neighbor, random);
    }

    /** the property of a horizontal side, null up and down */
    public static BooleanProperty side(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };
    }

    /** frogspawn's: the scheduled tick breaks it if it lost its support, else it hatches */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.canSurvive(state, level, pos)) {
            level.destroyBlock(pos, false);
        } else {
            this.hatch(level, pos, state, random);
        }
    }

    /** frogspawn's: a falling block breaks it */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean first) {
        if (entity.is(Compat.FALLING_BLOCK)) {
            level.destroyBlock(pos, false);
        }
    }

    /** The hatch, as frogspawn's: the block is broken (without drops) with frogspawn's hatching sound, and 1 to 3 fish of its species
     *  come in the water just under it, of the smallest size class at the low end of its wobble (Sizes.hatchling), which they keep, as
     *  every fish keeps its size. They are kept as a fish released from a bucket is (AbstractFish.setFromBucket): they never despawn and
     *  are outside the spawner's budgets, as a bucketed fish is (habitat.FishSpawner.present counts only fish neither named nor kept);
     *  otherwise ordinary fish, caught, bucketed and bred as any. Public for the test hook (a forced hatch); returns the fish. */
    public List<FishEntity> hatch(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        level.destroyBlock(pos, false);
        level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);
        Species s = FishTable.BY_ID.get(state.getValue(SPECIES));
        EntityType<FishEntity> type = s == null ? null : CreelMod.FISH.get(s.id());
        List<FishEntity> out = new ArrayList<>(MAX_HATCH);
        if (type == null) {
            return out;
        }
        float factor = Sizes.hatchling();
        int n = random.nextInt(MIN_HATCH, MAX_HATCH + 1);
        for (int i = 0; i < n; i++) {
            FishEntity fish = type.create(level, EntitySpawnReason.BREEDING);
            if (fish == null) {
                continue;
            }
            fish.setSize(Size.SMALL, factor, true);
            fish.setFromBucket(true);
            // in the water block under the spawn, wholly under its surface where it is deep enough (a source's surface is 8/9 up)
            double y = pos.getY() - 1 + Math.max(0.05, 0.85 - fish.getBbHeight());
            fish.snapTo(pos.getX() + offset(random), y, pos.getZ() + offset(random), random.nextInt(1, 361), 0.0F);
            level.addFreshEntity(fish);
            out.add(fish);
        }
        return out;
    }

    /** frogspawn's tadpole offset: somewhere in the middle of the block */
    private static double offset(RandomSource random) {
        return Mth.clamp(random.nextDouble(), 0.2, 0.8);
    }

    /** Where a bred pair's spawn goes (0.23.0: found when the pair meet, entity.FishMating; placed later by place): the nearest
     *  place to their midpoint where it may lie, in the columns up to SEARCH_RADIUS blocks each way, each read upward from the
     *  midpoint's block through water (or a waterlogged block) for at most SEARCH_RISE blocks: the first block that is not water ends
     *  the column; it is the place if it may hold the spawn (placeable), else the column has none (ice, a roof, a lily pad, spawn
     *  already there). At most 7 x 7 x 17 block reads, once per pairing. Null when there is no place. */
    public BlockPos find(ServerLevel level, Vec3 mid) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int x0 = Mth.floor(mid.x), y0 = Mth.floor(mid.y), z0 = Mth.floor(mid.z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int y = y0; y <= y0 + SEARCH_RISE; y++) {
                    p.set(x0 + dx, y, z0 + dz);
                    if (!level.isInWorldBounds(p) || !level.isLoaded(p)) {
                        break;
                    }
                    BlockState here = level.getBlockState(p);
                    if (here.getFluidState().is(FluidTags.WATER)) {
                        continue;
                    }
                    if (this.placeable(level, p)) {
                        double d = Vec3.atBottomCenterOf(p).distanceToSqr(mid);
                        if (d < bestD) {
                            bestD = d;
                            best = p.immutable();
                        }
                    }
                    break;
                }
            }
        }
        return best;
    }

    /** whether the spawn may be laid at a block: air over a water source, where it survives (canSurvive) */
    public boolean placeable(LevelReader level, BlockPos at) {
        return level.getBlockState(at).isAir() && level.getFluidState(at.below()).isSourceOfType(Fluids.WATER)
            && this.defaultBlockState().canSurvive(level, at);
    }

    /** Lays the spawn at a place find gave: with its sides set from its neighbours, a block-place game event and the frog's laying
     *  sound. False (nothing laid) when the place is no longer placeable (something came there since it was found). */
    public boolean place(ServerLevel level, BlockPos at, Species species, Entity layer) {
        if (!this.placeable(level, at)) {
            return false;
        }
        BlockState state = Block.updateFromNeighbourShapes(this.defaultBlockState().setValue(SPECIES, species.id()), level, at);
        level.setBlock(at, state, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, at, GameEvent.Context.of(layer, state));
        level.playSound(null, at, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** find and place at once (the test hooks); returns where, or null */
    public BlockPos lay(ServerLevel level, Vec3 mid, Species species, Entity layer) {
        BlockPos at = this.find(level, mid);
        return at != null && this.place(level, at, species, layer) ? at : null;
    }

    /** (0.23.0) placed by hand (the item, item.FishSpawnItem, as frogspawn's): its sides set from its neighbours at once; the species
     *  comes after from the stack's block_state component, as BlockItem applies it */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Block.updateFromNeighbourShapes(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    /** (0.23.0) pick-block (creative only, as vanilla's) gives the item of this block's species */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return FishSpawnItem.of(state.getValue(SPECIES));
    }
}
