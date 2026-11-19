package io.github.esclovrrivulet.creel.habitat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** The five spawn budgets (docs/HABITAT.md section 4), read from data/creel/budgets.json whenever data packs load (server start and
 *  /reload); a datapack may override the file. Each size class: the most fish of it within FishSpawner.RANGE of a player, and the
 *  size of its schools (a species' own "school" in its data file overrides that). A class missing from the file, or a bad file, keeps
 *  the defaults below. */
public final class Budgets {
    private static final Logger LOG = LoggerFactory.getLogger("creel");

    /** one class's budget: at most max fish round a player, schools of schoolMin to schoolMax */
    public record Budget(int max, int schoolMin, int schoolMax) {
        public String text() {
            return this.max + " fish, schools of " + (this.schoolMin == this.schoolMax ? "" + this.schoolMin : this.schoolMin + " to " + this.schoolMax);
        }
    }

    private static final Map<SizeClass, Budget> DEFAULTS = Map.of(
        SizeClass.LITTLE, new Budget(15, 3, 6),
        SizeClass.MEDIUM, new Budget(10, 2, 5),
        SizeClass.LARGE, new Budget(10, 2, 3),
        SizeClass.VERY_LARGE, new Budget(3, 1, 1),
        SizeClass.GIANT, new Budget(3, 1, 1),
        SizeClass.SALMON, new Budget(15, 2, 4));

    private static volatile Map<SizeClass, Budget> current = new EnumMap<>(DEFAULTS);

    private Budgets() {}

    public static Budget get(SizeClass c) {
        return current.get(c);
    }

    static Map<SizeClass, Budget> parse(JsonObject json) {
        Map<SizeClass, Budget> out = new EnumMap<>(DEFAULTS);
        for (SizeClass c : SizeClass.values()) {
            if (!json.has(c.key)) {
                continue;
            }
            JsonObject o = json.getAsJsonObject(c.key);
            int max = o.get("max").getAsInt();
            JsonArray school = o.getAsJsonArray("school");
            int min = school.get(0).getAsInt(), top = school.get(1).getAsInt();
            if (max < 0 || min < 1 || top < min) {
                throw new IllegalArgumentException(c.key + ": max must be 0 or more and school [min, max] with 1 <= min <= max");
            }
            out.put(c, new Budget(max, min, top));
        }
        return out;
    }

    /** the id this data reload listener is registered under (CreelMod.dataReloadListeners) */
    public static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "budgets");
    private static final Identifier FILE = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "budgets.json");
    /** reads the data at every data (re)load (the loader part registers it under RELOAD_ID) */
    public static final PreparableReloadListener RELOADER = new SimplePreparableReloadListener<Map<SizeClass, Budget>>() {
        @Override
        protected Map<SizeClass, Budget> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> res = manager.getResource(FILE);
            if (res.isEmpty()) {
                LOG.warn("creel: no data/creel/budgets.json, the default budgets apply");
                return new EnumMap<>(DEFAULTS);
            }
            try (Reader r = res.get().openAsReader()) {
                return parse(JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception ex) {
                LOG.error("creel: bad data/creel/budgets.json, the default budgets apply: {}", ex.getMessage());
                return new EnumMap<>(DEFAULTS);
            }
        }

        @Override
        protected void apply(Map<SizeClass, Budget> data, ResourceManager manager, ProfilerFiller profiler) {
            current = data;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<SizeClass, Budget> e : data.entrySet()) {
                sb.append(sb.isEmpty() ? "" : "; ").append(e.getKey().label()).append(' ').append(e.getValue().text());
            }
            LOG.info("creel: spawn budgets {}", sb);
        }
    };
}
