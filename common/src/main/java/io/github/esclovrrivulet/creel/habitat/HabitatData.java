package io.github.esclovrrivulet.creel.habitat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Every species' habitat, read from data/creel/fish/<id>.json whenever data packs load (server start and /reload). The spawner and
 *  the commands read it live, so a /reload changes where and how often fish appear at once (docs/HABITAT.md). */
public final class HabitatData {
    private static final Logger LOG = LoggerFactory.getLogger("creel");
    private static volatile Map<String, FishHabitat> byId = Map.of();

    private HabitatData() {}

    /** the species' habitat, or null when it has no data file (it then never appears on its own) */
    public static FishHabitat get(String id) {
        return byId.get(id);
    }

    public static int count() {
        return byId.size();
    }

    /** (0.21.0) a test hook: the habitats in force replaced, returning the ones that were (to put back). No natural water holds fish of
     *  all six classes, so the scene's check of the Ol' Mariner's bite shares gives one species of each class the water at its spot,
     *  for the length of one command (test/creeltest, habitat.HabitatSwap). Nothing in the mod calls it */
    static Map<String, FishHabitat> swap(Map<String, FishHabitat> m) {
        Map<String, FishHabitat> was = byId;
        byId = Map.copyOf(m);
        return was;
    }

    /** the id this data reload listener is registered under (CreelMod.dataReloadListeners) */
    public static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "fish_habitat");
    /** reads the data at every data (re)load (the loader part registers it under RELOAD_ID) */
    public static final PreparableReloadListener RELOADER = new SimplePreparableReloadListener<Map<String, FishHabitat>>() {
        @Override
        protected Map<String, FishHabitat> prepare(ResourceManager manager, ProfilerFiller profiler) {
            FileToIdConverter files = FileToIdConverter.json("fish");
            Map<String, FishHabitat> out = new HashMap<>();
            for (Map.Entry<Identifier, Resource> e : files.listMatchingResources(manager).entrySet()) {
                Identifier id = files.fileToId(e.getKey());
                if (!id.getNamespace().equals(CreelMod.MOD_ID)) {
                    continue;
                }
                try (Reader r = e.getValue().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                    out.put(id.getPath(), FishHabitat.parse(json));
                } catch (Exception ex) {
                    LOG.error("creel: bad fish habitat file {}: {}", e.getKey(), ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<String, FishHabitat> data, ResourceManager manager, ProfilerFiller profiler) {
            byId = Map.copyOf(data);
            LOG.info("creel: habitat data for {} fish", data.size());
        }
    };
}
