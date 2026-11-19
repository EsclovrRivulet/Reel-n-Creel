package io.github.esclovrrivulet.creel.habitat;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The water at a spot (docs/HABITAT.md section 1): its column (surface and floor) and its grade. Player-made water counts like any,
 *  and flowing water counts as water. */
public final class Water {
    /** the deepest a column is followed up or down from the spot; deeper water still reads as deep */
    private static final int MAX_SCAN = 96;
    /** deep water (shown by the commands): open, and at least this many water blocks from surface to floor at the spot. How deep a
     *  fish needs is its own min_depth (FishHabitat). */
    public static final int DEEP = 6;
    /** narrow water: at least this many water blocks in the 5 x 5 x 3 box round the spot */
    public static final int NARROW = 12;
    /** open water: at least this many of the 25 surface cells round the spot count (a river three wide passes, a one-wide channel or a
     *  farm trench does not) */
    public static final int OPEN_CELLS = 15;

    private Water() {}

    /** the water column through a spot: the top water block (the surface) and the lowest, both inclusive */
    public record Column(int surface, int floor) {
        public int depth() {
            return this.surface - this.floor + 1;
        }
    }

    public static boolean isWater(BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** the column through the spot, or null when the spot holds no water */
    public static Column column(BlockGetter level, BlockPos spot) {
        if (!isWater(level, spot)) {
            return null;
        }
        BlockPos.MutableBlockPos m = spot.mutable();
        int top = spot.getY();
        int maxY = level.getMaxY();
        for (int i = 0; i < MAX_SCAN && top < maxY && isWater(level, m.setY(top + 1)); i++) {
            top++;
        }
        int bottom = spot.getY();
        int minY = level.getMinY();
        for (int i = 0; i < MAX_SCAN && bottom > minY && isWater(level, m.setY(bottom - 1)); i++) {
            bottom--;
        }
        return new Column(top, bottom);
    }

    /** what column(level, spot) gives for the spot's surface (the top water block), NO_SURFACE where column gives null (the spot holds
     *  no water); the same upward scan, without the downward one */
    public static int surface(BlockGetter level, BlockPos spot) {
        if (!isWater(level, spot)) {
            return NO_SURFACE;
        }
        BlockPos.MutableBlockPos m = spot.mutable();
        int top = spot.getY();
        int maxY = level.getMaxY();
        for (int i = 0; i < MAX_SCAN && top < maxY && isWater(level, m.setY(top + 1)); i++) {
            top++;
        }
        return top;
    }

    /** surface: the spot holds no water */
    public static final int NO_SURFACE = Integer.MIN_VALUE;

    /** The spot's grade, its column already read: open when at least OPEN_CELLS of the 25 cells of the 5 x 5 round the spot are open
     *  surface water (surfaceCells), deep when open and at least DEEP blocks of water stand at the spot, narrow when not open but NARROW
     *  water blocks lie in the 5 x 5 x 3 box round the spot, else none. Vanilla's treasure-fishing rule (all 25 cells still water under
     *  open air) passing always means open, since it is a stricter form of the same test. */
    public static Grade grade(BlockGetter level, BlockPos spot, Column col) {
        if (col == null) {
            return Grade.NONE;
        }
        if (surfaceCells(level, spot.getX(), col.surface(), spot.getZ()) >= OPEN_CELLS) {
            return col.depth() >= DEEP ? Grade.DEEP : Grade.OPEN;
        }
        return waterAround(level, spot) >= NARROW ? Grade.NARROW : Grade.NONE;
    }

    /** Cells of the 5 x 5 round (x, z) whose water lies open at the spot's surface level or one below it: water (source or flowing)
     *  there, with air, a lily pad or ice directly above it. A sloped bank, or a flowing river whose edges sit one lower, still counts.
     *  At most three blocks are read per cell; stops once OPEN_CELLS are found or can no longer be reached. */
    public static int surfaceCells(BlockGetter level, int x, int surface, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = 0, left = 25;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                left--;
                boolean counts;
                if (isWater(level, m.set(x + dx, surface, z + dz))) {
                    counts = openAbove(level, m.set(x + dx, surface + 1, z + dz));
                } else {
                    counts = openAbove(level, m) && isWater(level, m.set(x + dx, surface - 1, z + dz));
                }
                if (counts && ++n >= OPEN_CELLS) {
                    return n;
                }
                if (n + left < OPEN_CELLS) {
                    return n;
                }
            }
        }
        return n;
    }

    /** air, a lily pad or ice (ice so that a frozen lake or sea is judged as the open water it is in summer) */
    private static boolean openAbove(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.LILY_PAD) || state.is(BlockTags.ICE);
    }

    /** water blocks (source or flowing) in the 5 x 5 x 3 box centred on the spot, stopping once NARROW are found */
    private static int waterAround(BlockGetter level, BlockPos spot) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (isWater(level, m.set(spot.getX() + dx, spot.getY() + dy, spot.getZ() + dz)) && ++n >= NARROW) {
                        return n;
                    }
                }
            }
        }
        return n;
    }
}
