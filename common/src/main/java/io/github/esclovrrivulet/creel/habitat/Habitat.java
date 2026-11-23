package io.github.esclovrrivulet.creel.habitat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import io.github.esclovrrivulet.creel.compat.Compat;

/** Reads a spot of water: its class (section 2), temperature band (section 3) and grade (section 1), and what a biome can offer before
 *  its water is seen. No biome is ever named: classes come from shared biome tags and the water itself, bands from the
 *  world's climate, and the creel tags overrule either. */
public final class Habitat {
    private Habitat() {}

    // the mod's own override tags (data/creel/tags/worldgen/biome/...), empty as shipped
    public static final TagKey<Biome> NO_FISH = tag(CreelMod.MOD_ID, "no_fish");
    public static final TagKey<Biome> CLASS_SWAMP = tag(CreelMod.MOD_ID, "class/swamp");
    public static final TagKey<Biome> CLASS_COAST = tag(CreelMod.MOD_ID, "class/coast");
    public static final TagKey<Biome> CLASS_HIGHLAND = tag(CreelMod.MOD_ID, "class/highland");
    private static final List<TagKey<Biome>> WATER_TAGS = List.of(tag(CreelMod.MOD_ID, "water/cold"), tag(CreelMod.MOD_ID, "water/cool"),
        tag(CreelMod.MOD_ID, "water/temperate"), tag(CreelMod.MOD_ID, "water/warm"));
    // the shared convention biome tags (namespace "c") that mods and both loaders fill; the same ids as Fabric's ConventionalBiomeTags
    private static final TagKey<Biome> C_IS_CAVE = tag("c", "is_cave"), C_IS_UNDERGROUND = tag("c", "is_underground"),
        C_IS_DEEP_OCEAN = tag("c", "is_deep_ocean"), C_IS_OCEAN = tag("c", "is_ocean"), C_IS_BEACH = tag("c", "is_beach"),
        C_IS_STONY_SHORES = tag("c", "is_stony_shores"), C_IS_SWAMP = tag("c", "is_swamp"), C_IS_MOUNTAIN = tag("c", "is_mountain");

    /** sea water shallower than this is coast, deeper than DEEP_SEA is deep sea (section 2) */
    public static final int COAST_DEPTH = 8, DEEP_SEA_DEPTH = 20;
    /** fresh water whose surface stands this far above sea level is highland */
    public static final int HIGHLAND_RISE = 40;

    private static TagKey<Biome> tag(String ns, String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(ns, path));
    }

    /** What a biome's tags make of its water, before the water itself is looked at. */
    public enum Kind {
        /** no fish (creel:no_fish, or a cave biome) */
        NONE(List.of()),
        SWAMP(List.of(Place.SWAMP)),
        COAST(List.of(Place.COAST)),
        HIGHLAND(List.of(Place.HIGHLAND)),
        DEEP_SEA(List.of(Place.DEEP_SEA)),
        /** an ocean biome: coast, ocean or deep sea by the depth of the water */
        SEA(List.of(Place.COAST, Place.OCEAN, Place.DEEP_SEA)),
        /** any other land or river biome: fresh, or highland when the surface stands high */
        LAND(List.of(Place.FRESH, Place.HIGHLAND));

        public final List<Place> places;

        Kind(List<Place> places) {
            this.places = places;
        }

        /** the class of water with this column (surface and depth) in a biome of this kind */
        public Place place(Water.Column col, int seaLevel) {
            return switch (this) {
                case NONE -> null;
                case SWAMP -> Place.SWAMP;
                case COAST -> Place.COAST;
                case HIGHLAND -> Place.HIGHLAND;
                case DEEP_SEA -> Place.DEEP_SEA;
                case SEA -> col.depth() < COAST_DEPTH ? Place.COAST : col.depth() > DEEP_SEA_DEPTH ? Place.DEEP_SEA : Place.OCEAN;
                case LAND -> col.surface() >= seaLevel + HIGHLAND_RISE ? Place.HIGHLAND : Place.FRESH;
            };
        }
    }

    /** the biome's kind from its tags, overrides first: creel:no_fish, creel:class/*, then caves (no fish: their water is vanilla's
     *  axolotls' and glow squid's), deep ocean, ocean, beach and stony shore (coast), swamp, mountain; anything else is land */
    public static Kind kind(Predicate<TagKey<Biome>> is) {
        if (is.test(NO_FISH)) {
            return Kind.NONE;
        }
        if (is.test(CLASS_SWAMP)) {
            return Kind.SWAMP;
        }
        if (is.test(CLASS_COAST)) {
            return Kind.COAST;
        }
        if (is.test(CLASS_HIGHLAND)) {
            return Kind.HIGHLAND;
        }
        if (is.test(C_IS_CAVE) || is.test(C_IS_UNDERGROUND)) {
            return Kind.NONE;
        }
        if (is.test(BiomeTags.IS_DEEP_OCEAN) || is.test(C_IS_DEEP_OCEAN)) {
            return Kind.DEEP_SEA;
        }
        if (is.test(BiomeTags.IS_OCEAN) || is.test(C_IS_OCEAN)) {
            return Kind.SEA;
        }
        if (is.test(BiomeTags.IS_BEACH) || is.test(C_IS_BEACH) || is.test(C_IS_STONY_SHORES)) {
            return Kind.COAST;
        }
        if (is.test(C_IS_SWAMP)) {
            return Kind.SWAMP;
        }
        if (is.test(BiomeTags.IS_MOUNTAIN) || is.test(C_IS_MOUNTAIN)) {
            return Kind.HIGHLAND;
        }
        return Kind.LAND;
    }

    public static Kind kind(Holder<Biome> biome) {
        return kind(biome::is);
    }

    /** the band a creel:water/* tag sets, or null */
    public static Band tagBand(Predicate<TagKey<Biome>> is) {
        for (int i = 0; i < WATER_TAGS.size(); i++) {
            if (is.test(WATER_TAGS.get(i))) {
                return Band.values()[i];
            }
        }
        return null;
    }

    // climate samplers, one per level, made the first time a level is asked (empty when its generator has no multi-noise climate)
    private static final Map<ResourceKey<Level>, Optional<Climate.Sampler>> SAMPLERS = new HashMap<>();

    public static void clearCaches() {
        SAMPLERS.clear();
    }

    /** the level's climate sampler: any noise generator has one (terrain mods often wrap the multi-noise biome source, so the biome
     *  source is not what is asked); flat and debug worlds have none and fall back to the biome's own temperature */
    public static Climate.Sampler sampler(ServerLevel level) {
        return SAMPLERS.computeIfAbsent(level.dimension(), k -> level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator
            ? Optional.of(Compat.climateSampler(level.getChunkSource().randomState())) : Optional.empty()).orElse(null);
    }

    /** the band at a spot, and where it came from */
    public record BandReading(Band band, String source, double climate) {}

    /** the world's climate temperature at the spot (the multi-noise value, -1 to 1), falling back to the biome's own temperature where
     *  the generator has none; highland water reads one band colder; a creel:water/* tag overrules all of it */
    public static BandReading band(ServerLevel level, BlockPos pos, Holder<Biome> biome, Place place) {
        Band tagged = tagBand(biome::is);
        if (tagged != null) {
            return new BandReading(tagged, "tag", Double.NaN);
        }
        Climate.Sampler s = sampler(level);
        Band band;
        String source;
        double t;
        if (s != null) {
            t = Compat.temperature(s, pos);
            band = Band.ofClimate(t);
            source = "climate";
        } else {
            t = biome.value().getBaseTemperature();
            band = Band.ofBiomeTemperature((float) t);
            source = "biome temperature";
        }
        if (place == Place.HIGHLAND) {
            band = band.colder();
            source += ", one colder for highland";
        }
        return new BandReading(band, source, t);
    }

    /** everything the mod reads at a spot */
    public record Reading(Holder<Biome> biome, Kind kind, Water.Column column, Place place, BandReading band, Grade grade) {
        public boolean suits(FishHabitat h) {
            return h != null && this.place != null && h.accepts(this.place, this.band.band(), this.grade, this.column.depth());
        }
    }

    public static Reading read(ServerLevel level, BlockPos pos) {
        return read(level, pos, Water.column(level, pos));
    }

    /** read with the water column through pos already read (Water.column, null when pos holds no water) */
    public static Reading read(ServerLevel level, BlockPos pos, Water.Column col) {
        Holder<Biome> biome = level.getBiome(pos);
        Kind kind = kind(biome);
        if (col == null || kind == Kind.NONE) {
            return new Reading(biome, kind, col, null, new BandReading(null, "-", Double.NaN), Grade.NONE);
        }
        Place place = kind.place(col, level.getSeaLevel());
        return new Reading(biome, kind, col, place, band(level, pos, biome, place), Water.grade(level, pos, col));
    }

    /** What a biome can offer before any water is seen: its kind, the bands it can hold (a creel:water tag, else its climate span in the
     *  overworld, else for a sea every band, else its own temperature's band and the ones either side) and where they came from. Used
     *  by /creel biomes. */
    public record Offer(Kind kind, EnumSet<Band> bands, String source, boolean tagged) {
        /** whether any class and band of this biome suits the fish (highland one band colder, unless a tag set the band) */
        public boolean takes(FishHabitat h) {
            for (Place p : this.kind.places) {
                for (Band b : this.bands) {
                    if (h.takes(p, p == Place.HIGHLAND && !this.tagged ? b.colder() : b)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public String bandsText() {
            if (this.bands.isEmpty()) {
                return "-";
            }
            Band lo = this.bands.iterator().next();
            Band hi = lo;
            for (Band b : this.bands) {
                hi = b;
            }
            return lo == hi ? lo.key() : lo.key() + ".." + hi.key();
        }
    }

    public static Offer offer(Predicate<TagKey<Biome>> is, ResourceKey<Biome> key, float baseTemperature, ClimateTable climate) {
        Kind kind = kind(is);
        Band tagged = tagBand(is);
        if (tagged != null) {
            return new Offer(kind, EnumSet.of(tagged), "tag", true);
        }
        EnumSet<Band> span = climate.bands(key);
        if (span != null) {
            return new Offer(kind, span, "climate", false);
        }
        if (kind == Kind.SEA || kind == Kind.DEEP_SEA) {
            return new Offer(kind, EnumSet.allOf(Band.class), "every band (sea, no climate entry)", false);
        }
        Band b = Band.ofBiomeTemperature(baseTemperature);
        Band lo = b.colder();
        Band hi = b == Band.WARM ? Band.WARM : Band.values()[b.ordinal() + 1];
        return new Offer(kind, EnumSet.range(lo, hi), "biome temperature " + baseTemperature + ", one band either side", false);
    }

    /** The spawn test for our fish (SpawnPlacements). Vanilla's spawner never places them (their category is MISC and they are in no
     *  spawn list); this only matters if another mod or a pack asks for a natural spawn of one: then the spot must suit the species,
     *  cheapest checks first (the species' data, the biome's tags against its classes, the water column and its depth, the climate,
     *  last the grade).
     *  Spawns that are not natural (spawners, commands, eggs) only need water, which vanilla's IN_WATER placement checks. */
    public static boolean canSpawn(EntityType<FishEntity> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }
        FishHabitat h = HabitatData.get(EntityType.getKey(type).getPath());
        if (h == null || !h.enabled()) {
            return false;
        }
        Holder<Biome> biome = accessor.getBiome(pos);
        Kind kind = kind(biome);
        if (!h.takesAny(kind.places)) {
            return false;
        }
        Water.Column col = Water.column(accessor, pos);
        if (col == null || col.depth() < h.minDepth()) {
            return false;
        }
        Place place = kind.place(col, accessor.getSeaLevel());
        EnumSet<Band> bands = h.places().get(place);
        if (bands == null) {
            return false;
        }
        if (!bands.contains(band(accessor.getLevel(), pos, biome, place).band())) {
            return false;
        }
        return Water.grade(accessor, pos, col).suits(h.grade());
    }
}
