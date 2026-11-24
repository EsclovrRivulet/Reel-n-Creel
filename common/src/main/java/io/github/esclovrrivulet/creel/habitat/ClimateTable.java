package io.github.esclovrrivulet.creel.habitat;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.esclovrrivulet.creel.compat.Compat;

/** Which temperature bands each biome can have in the overworld: the span of the climate temperature over all the places the overworld
 *  generator puts it (its multi-noise parameter list). Used by /creel biomes, to show what each biome can offer. A biome the list does not hold (or a generator without one) has no entry. */
public final class ClimateTable {
    private static final Logger LOG = LoggerFactory.getLogger("creel");
    private static RegistryAccess cachedFor;
    private static ClimateTable cached;

    private final Map<ResourceKey<Biome>, long[]> spans = new HashMap<>();

    private ClimateTable() {}

    /** forgets the cached table, so a stopped server's registries are not kept (CreelMod, on SERVER_STOPPED) */
    public static synchronized void clear() {
        cached = null;
        cachedFor = null;
    }

    public static synchronized ClimateTable of(RegistryAccess access) {
        if (cached == null || cachedFor != access) {
            cached = build(access);
            cachedFor = access;
        }
        return cached;
    }

    /** the bands the biome's climate spans, or null when the overworld's parameter list does not place it */
    public EnumSet<Band> bands(ResourceKey<Biome> key) {
        long[] s = this.spans.get(key);
        return s == null ? null : EnumSet.range(Band.ofSpanMin(s[0]), Band.ofSpanMax(s[1]));
    }

    public int size() {
        return this.spans.size();
    }

    /** The multi-noise biome source behind a source some mod has wrapped round it (a biome-injecting mod may replace the overworld's
     *  source with its own that delegates to the original): the wrapper's fields are searched, a few levels deep. */
    private static BiomeSource unwrap(BiomeSource source, int depth) {
        if (source instanceof MultiNoiseBiomeSource || depth > 3) {
            return source;
        }
        for (Class<?> c = source.getClass(); c != null && c != BiomeSource.class && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !BiomeSource.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    if (f.get(source) instanceof BiomeSource inner && inner != source) {
                        BiomeSource found = unwrap(inner, depth + 1);
                        if (found instanceof MultiNoiseBiomeSource) {
                            return found;
                        }
                    }
                } catch (Exception | LinkageError e) {
                    // not readable: try the next field
                }
            }
        }
        return source;
    }

    @SuppressWarnings("unchecked")
    private static ClimateTable build(RegistryAccess access) {
        ClimateTable t = new ClimateTable();
        try {
            Registry<LevelStem> stems = access.lookupOrThrow(Registries.LEVEL_STEM);
            LevelStem overworld = stems.getValue(LevelStem.OVERWORLD);
            if (overworld == null) {
                return t;
            }
            BiomeSource source = unwrap(overworld.generator().getBiomeSource(), 0);
            if (!(source instanceof MultiNoiseBiomeSource)) {
                return t;
            }
            // the parameter list is private to the biome source; 26.3 runs with Mojang's names, so it is read by name
            Climate.ParameterList<Holder<Biome>> list = Compat.biomeParameters((MultiNoiseBiomeSource) source);
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> values = list.values();
            for (Pair<Climate.ParameterPoint, Holder<Biome>> p : values) {
                p.getSecond().unwrapKey().ifPresent(key -> {
                    Climate.Parameter temp = p.getFirst().temperature();
                    t.spans.merge(key, new long[] {temp.min(), temp.max()}, (a, b) -> new long[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
                });
            }
        } catch (Exception e) {
            LOG.warn("creel: could not read the overworld's climate list ({}); biomes fall back to their own temperature", e.toString());
        }
        return t;
    }
}
