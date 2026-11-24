package io.github.esclovrrivulet.creel.habitat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import io.github.esclovrrivulet.creel.compat.Compat;

/** The mod's own fish spawner, the only way our fish appear on their own (docs/HABITAT.md section 4). Vanilla's spawner and spawn lists
 *  never see them: every fish of ours is in category MISC, which vanilla's spawner and caps ignore, and despawns as vanilla's fish do
 *  (FishEntity.checkDespawn).
 *
 *  Every INTERVAL ticks, for each player (not spectators, spawn_mobs on): one entity query over the RANGE box, bucketed by size class;
 *  then, for each class under its budget (Budgets), one attempt: up to SAMPLES random columns MIN_DISTANCE to MAX_DISTANCE away, kept
 *  until one's water suits at least one species of the class; there one species is chosen by weight and one whole school of it
 *  placed. As vanilla's spawner does, a school may land beside another and a species may come again (the owner's choice,
 *  2026-11-13: the earlier spacing and variety rules made the water look like a mix of everything).
 *  Between the marks only the tick counter runs. */
public final class FishSpawner {
    public static final int RANGE = 64;
    public static final int MIN_DISTANCE = 24;
    public static final int MAX_DISTANCE = 62;
    public static final int INTERVAL = 100;
    public static final int SAMPLES = 6;
    private static int ticks;

    /** the species of each size class, in table order */
    private static final Map<SizeClass, List<Species>> BY_CLASS = new EnumMap<>(SizeClass.class);

    static {
        for (SizeClass c : SizeClass.values()) {
            BY_CLASS.put(c, new ArrayList<>());
        }
        for (Species s : FishTable.ALL) {
            BY_CLASS.get(SizeClass.of(s)).add(s);
        }
    }

    private FishSpawner() {}

    public static List<Species> species(SizeClass c) {
        return BY_CLASS.get(c);
    }

    /** at the end of every server tick (CreelMod.onEndServerTick) */
    public static void tick(MinecraftServer server) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            if (!player.isSpectator() && level.getGameRules().get(GameRules.SPAWN_MOBS)) {
                step(level, player.position(), level.getRandom());
            }
        }
    }

    /** the school size a species is drawn from: alone when solitary, else its own school from its data, else its class's */
    public static int[] schoolRange(Species s, FishHabitat h) {
        if (!s.schools()) {
            return new int[] {1, 1};
        }
        if (h != null && h.ownSchool()) {
            return new int[] {h.schoolMin(), h.schoolMax()};
        }
        Budgets.Budget b = Budgets.get(SizeClass.of(s));
        return new int[] {b.schoolMin(), b.schoolMax()};
    }

    /** the top of schoolRange, with no array (FishEntity.getMaxSchoolSize asks it often) */
    public static int schoolMax(Species s, FishHabitat h) {
        if (!s.schools()) {
            return 1;
        }
        if (h != null && h.ownSchool()) {
            return h.schoolMax();
        }
        return Budgets.get(SizeClass.of(s)).schoolMax();
    }

    /** Our fish round a point, from one entity query: every fish of ours within RANGE by size class (for the test hooks' census),
     *  and how many of them count against the budget (those not named or kept, as vanilla's caps count). */
    public record Present(Map<SizeClass, List<FishEntity>> fish, Map<SizeClass, Integer> counted) {
        public List<FishEntity> of(SizeClass c) {
            return this.fish.get(c);
        }

        public int count(SizeClass c) {
            return this.counted.get(c);
        }
    }

    public static Present present(ServerLevel level, Vec3 c) {
        Map<SizeClass, List<FishEntity>> fish = new EnumMap<>(SizeClass.class);
        Map<SizeClass, Integer> counted = new EnumMap<>(SizeClass.class);
        for (SizeClass k : SizeClass.values()) {
            fish.put(k, new ArrayList<>());
            counted.put(k, 0);
        }
        for (AbstractFish a : level.getEntitiesOfClass(AbstractFish.class, new AABB(c, c).inflate(RANGE), f -> f.distanceToSqr(c) <= RANGE * RANGE)) {
            boolean free = !a.isPersistenceRequired() && !a.requiresCustomPersistence();
            if (a instanceof FishEntity f) {
                SizeClass k = SizeClass.of(f.species());
                fish.get(k).add(f);
                if (free) {
                    counted.merge(k, 1, Integer::sum);
                }
            } else if (a.getType() == Compat.SALMON && free) {
                counted.merge(SizeClass.SALMON, 1, Integer::sum);     // vanilla's salmon share the salmon run's budget
            }
        }
        return new Present(fish, counted);
    }

    /** one school placed, for the test hook; species is null for a school of vanilla's salmon */
    public record Placed(SizeClass sizeClass, Species species, BlockPos spot, int size) {}

    /** One interval's work round a point (a player): the query, then one attempt for each class under budget. Returns the schools placed. */
    public static List<Placed> step(ServerLevel level, Vec3 c, RandomSource r) {
        Present present = present(level, c);
        List<Placed> out = new ArrayList<>(2);
        for (SizeClass k : SizeClass.values()) {
            int room = Budgets.get(k).max() - present.count(k);
            if (room > 0) {
                Placed p = attempt(level, c, r, k, room);
                if (p != null) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static Placed attempt(ServerLevel level, Vec3 c, RandomSource r, SizeClass k, int room) {
        List<Species> candidates = BY_CLASS.get(k);
        BlockPos best = null;
        Habitat.Reading bestReading = null;
        for (int i = 0; i < SAMPLES; i++) {
            Spot spot = sampleSpot(level, c, r);
            if (spot == null) {
                continue;
            }
            Habitat.Reading reading = Habitat.read(level, spot.pos(), spot.col());
            if (reading.place() == null || !anySuits(reading, candidates)) {
                continue;
            }
            best = spot.pos();
            bestReading = reading;
            break;
        }
        if (best == null) {
            return null;
        }
        if (k == SizeClass.SALMON && vanillaSalmonTurn(bestReading, candidates, r)) {
            Budgets.Budget b = Budgets.get(k);
            int n = Math.min(b.schoolMin() + r.nextInt(b.schoolMax() - b.schoolMin() + 1), room);
            if (n < b.schoolMin()) {
                return null;
            }
            int placed = placeSchool(level, Compat.SALMON, best, n, r);
            return placed > 0 ? new Placed(k, null, best, placed) : null;
        }
        Species chosen = choose(bestReading, candidates, r);
        if (chosen == null) {
            return null;
        }
        int[] range = schoolRange(chosen, HabitatData.get(chosen.id()));
        int n = Math.min(range[0] + r.nextInt(range[1] - range[0] + 1), room);
        if (n < range[0]) {
            return null;
        }
        int placed = placeSchool(level, CreelMod.FISH.get(chosen.id()), best, n, r);
        return placed > 0 ? new Placed(k, chosen, best, placed) : null;
    }

    private static boolean anySuits(Habitat.Reading reading, List<Species> candidates) {
        for (Species s : candidates) {
            if (reading.suits(HabitatData.get(s.id()))) {
                return true;
            }
        }
        return false;
    }

    /** a sampled spot and the water column read through it (Habitat.read takes it, so the column is not scanned twice) */
    private record Spot(BlockPos pos, Water.Column col) {}

    /** A random spot of loaded water MIN_DISTANCE to MAX_DISTANCE from the point: a column at a random angle and distance, its surface
     *  (the highest block that is not air, or the water under up to four blocks of ice), a random height in its water. Null if none.
     *  The column is the one read from the surface: from the spot it would read the same surface and depth (a column deeper than
     *  Water's scan reads deeper from a lower spot, but both are far past every depth the habitat rules compare). */
    private static Spot sampleSpot(ServerLevel level, Vec3 c, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2;
        double d = MIN_DISTANCE + r.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = Mth.floor(c.x + Math.cos(a) * d);
        int z = Mth.floor(c.z + Math.sin(a) * d);
        if (!level.isPositionEntityTicking(BlockPos.containing(x, c.y, z))) {
            return null;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
        for (int i = 0; i < 4 && !Water.isWater(level, m); i++) {
            m.setY(--top);
        }
        Water.Column col = Water.column(level, m);
        if (col == null) {
            return null;
        }
        BlockPos spot = new BlockPos(x, col.floor() + r.nextInt(col.depth()), z);
        double dist = spot.distToCenterSqr(c);
        return dist < MIN_DISTANCE * MIN_DISTANCE || dist > MAX_DISTANCE * MAX_DISTANCE ? null : new Spot(spot, col);
    }

    /** The species for a spot: by weight among the class's species that suit it (the fish already in range do not change it). */
    public static Species choose(Habitat.Reading reading, List<Species> candidates, RandomSource r) {
        List<Species> fits = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Species s : candidates) {
            FishHabitat h = HabitatData.get(s.id());
            if (reading.suits(h)) {
                double w = weight(s, h);
                fits.add(s);
                weights.add(w);
                total += w;
            }
        }
        if (fits.isEmpty()) {
            return null;
        }
        double pick = r.nextDouble() * total;
        for (int i = 0; i < fits.size(); i++) {
            pick -= weights.get(i);
            if (pick < 0) {
                return fits.get(i);
            }
        }
        return fits.getLast();
    }

    /** Vanilla's salmon in the salmon run (the owner's choice, 2026-11-13: an equal chance among the salmon, not the common one).
     *  Vanilla's spawner no longer places salmon (data/creel/vanilla_caps.json gives it a cap of 0); ours does, at the spots our
     *  salmon suit, as one more kind with the same weight as each of ours. */
    public static final double VANILLA_SALMON_WEIGHT = 10;

    /** whether vanilla's salmon win this spot: its weight against the summed weights of our salmon that suit it */
    public static boolean vanillaSalmonTurn(Habitat.Reading reading, List<Species> candidates, RandomSource r) {
        double ours = 0;
        for (Species s : candidates) {
            FishHabitat h = HabitatData.get(s.id());
            if (reading.suits(h)) {
                ours += h.weight();
            }
        }
        return ours > 0 && r.nextDouble() * (ours + VANILLA_SALMON_WEIGHT) < VANILLA_SALMON_WEIGHT;
    }

    /** a species' weight (its habitat data's) */
    public static double weight(Species s, FishHabitat h) {
        return h.weight();
    }

    /** Places a whole school of n round a spot of water, the first fish at the spot itself leading the rest (as vanilla's school
     *  spawning does), each in water with room for it; if room is not found for all n, nothing is placed. Returns n or 0. */
    public static <T extends Mob> int placeSchool(ServerLevel level, EntityType<T> type, BlockPos at, int n, RandomSource r) {
        List<BlockPos> spots = new ArrayList<>(n);
        if (!fits(level, type, at)) {
            return 0;
        }
        spots.add(at);
        for (int i = 0; i < n * 4 && spots.size() < n; i++) {
            BlockPos p = at.offset(r.nextInt(5) - 2, r.nextInt(3) - 1, r.nextInt(5) - 2);
            if (fits(level, type, p)) {
                spots.add(p);
            }
        }
        if (spots.size() < n) {
            return 0;
        }
        SpawnGroupData data = null;
        int done = 0;
        for (BlockPos p : spots) {
            T fish = Compat.create(type, level, EntitySpawnReason.NATURAL);
            if (fish == null) {
                break;
            }
            fish.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
            data = fish.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.NATURAL, data);
            level.addFreshEntityWithPassengers(fish);
            done++;
        }
        return done;
    }

    private static boolean fits(ServerLevel level, EntityType<?> type, BlockPos p) {
        return Water.isWater(level, p) && level.noCollision(type.getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
    }
}
