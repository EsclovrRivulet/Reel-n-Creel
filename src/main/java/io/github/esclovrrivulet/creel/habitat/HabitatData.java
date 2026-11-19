package io.github.esclovrrivulet.creel.habitat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
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

    public static void register() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "fish_habitat"),
            new SimpleReloadListener<Map<String, FishHabitat>>() {
                @Override
                protected Map<String, FishHabitat> prepare(PreparableReloadListener.SharedState state) {
                    FileToIdConverter files = FileToIdConverter.json("fish");
                    Map<String, FishHabitat> out = new HashMap<>();
                    for (Map.Entry<Identifier, Resource> e : files.listMatchingResources(state.resourceManager()).entrySet()) {
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
                protected void apply(Map<String, FishHabitat> data, PreparableReloadListener.SharedState state) {
                    byId = Map.copyOf(data);
                    LOG.info("creel: habitat data for {} fish", data.size());
                }
            });
    }
}
