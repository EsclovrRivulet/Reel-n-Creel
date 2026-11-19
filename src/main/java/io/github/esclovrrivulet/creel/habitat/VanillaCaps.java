package io.github.esclovrrivulet.creel.habitat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.NaturalSpawner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;

import java.io.Reader;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The one place the mod touches vanilla's fish, at the owner's request (docs/HABITAT.md section 4): a cap on how many of a vanilla type
 *  may exist before vanilla's spawner stops placing more of it naturally. The caps are data (data/creel/vanilla_caps.json, entity
 *  type id -> cap, reloaded with /reload; an empty object switches it off) and scale as vanilla's own mob caps do: cap x the level's
 *  spawnable chunks / 289, so one player gets the cap itself.
 *
 *  The check (allows) is called from SpawnPlacementsMixin at the head of vanilla's spawn rules check, before the entity is made, for
 *  natural spawns only: eggs, spawners, buckets, commands and breeding never reach it with that reason. It costs two map lookups: the
 *  count of each capped type is kept per level by the entity load and unload events (every loaded entity of the type counts), and
 *  recounted once after a /reload in case the capped types changed. Nothing runs per tick. */
public final class VanillaCaps {
    private static final Logger LOG = LoggerFactory.getLogger("creel");
    /** vanilla's own mob-cap divisor: the chunks round one player (17 x 17) */
    private static final int CHUNKS_PER_PLAYER = 289;

    private static volatile Map<EntityType<?>, Integer> caps = Map.of();
    /** types counted toward another's cap: our salmon run toward minecraft:salmon (the file's "also") */
    private static volatile Map<EntityType<?>, EntityType<?>> also = Map.of();

    /** what the file holds: each capped type's cap, and the types counted toward a cap besides its own */
    record Data(Map<EntityType<?>, Integer> caps, Map<EntityType<?>, EntityType<?>> also) {}
    private static final Map<ServerLevel, Reference2IntOpenHashMap<EntityType<?>>> COUNTS = new IdentityHashMap<>();
    /** for the test hook: the spawnable chunk count to assume (a headless server has no player, so none); -1 when not testing */
    static int testChunks = -1;

    private VanillaCaps() {}

    public static Map<EntityType<?>, Integer> caps() {
        return caps;
    }

    /** loaded entities of the type in the level */
    public static int count(ServerLevel level, EntityType<?> type) {
        Reference2IntOpenHashMap<EntityType<?>> m = COUNTS.get(level);
        return m == null ? 0 : m.getInt(type);
    }

    /** the type's cap in this level now, scaled by its spawnable chunks; -1 when the type has none */
    public static int scaledCap(ServerLevel level, EntityType<?> type) {
        Integer cap = caps.get(type);
        if (cap == null) {
            return -1;
        }
        int chunks;
        if (testChunks >= 0) {
            chunks = testChunks;
        } else {
            NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
            chunks = state == null ? 0 : state.getSpawnableChunkCount();
        }
        return cap * chunks / CHUNKS_PER_PLAYER;
    }

    /** whether a natural spawn of the type may go ahead: true unless the type is capped and its count has reached the cap */
    public static boolean allows(EntityType<?> type, ServerLevel level) {
        if (!caps.containsKey(type)) {
            return true;
        }
        return count(level, type) < scaledCap(level, type);
    }

    private static void add(Entity e, ServerLevel level, int d) {
        if (caps.isEmpty()) {
            return;         // no caps (also is then empty too: it only holds types counted toward a cap)
        }
        EntityType<?> t = caps.containsKey(e.getType()) ? e.getType() : also.get(e.getType());
        if (t != null) {
            Reference2IntOpenHashMap<EntityType<?>> m = COUNTS.computeIfAbsent(level, k -> new Reference2IntOpenHashMap<>());
            m.put(t, Math.max(0, m.getInt(t) + d));
        }
    }

    /** counts every loaded entity of the capped types again (after a /reload, which may have changed which types are capped) */
    private static void recount(MinecraftServer server) {
        COUNTS.clear();
        if (caps.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                add(e, level, 1);
            }
        }
    }

    private static Optional<EntityType<?>> type(String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> (EntityType<?>) t);
    }

    /** each entry: type id -> cap, or type id -> {"cap": n, "also": [type ids counted toward it]} */
    static Data parse(JsonObject json) {
        Map<EntityType<?>, Integer> out = new LinkedHashMap<>();
        Map<EntityType<?>, EntityType<?>> alsoOut = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            Optional<EntityType<?>> type = type(e.getKey());
            JsonObject o = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
            int cap = o == null ? e.getValue().getAsInt() : o.get("cap").getAsInt();
            if (type.isEmpty() || cap < 0) {
                LOG.warn("creel: vanilla_caps.json: skipped {} = {} (no such entity type, or a cap below 0)", e.getKey(), e.getValue());
                continue;
            }
            out.put(type.get(), cap);
            if (o != null && o.has("also")) {
                for (JsonElement m : o.getAsJsonArray("also")) {
                    Optional<EntityType<?>> member = type(m.getAsString());
                    if (member.isEmpty()) {
                        LOG.warn("creel: vanilla_caps.json: {} also counts {}, which is no entity type; skipped", e.getKey(), m);
                    } else {
                        alsoOut.put(member.get(), type.get());
                    }
                }
            }
        }
        return new Data(out, alsoOut);
    }

    public static void register() {
        Identifier file = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "vanilla_caps.json");
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "vanilla_caps"),
            new SimpleReloadListener<Data>() {
                @Override
                protected Data prepare(PreparableReloadListener.SharedState state) {
                    Optional<Resource> res = state.resourceManager().getResource(file);
                    if (res.isEmpty()) {
                        return new Data(Map.of(), Map.of());
                    }
                    try (Reader r = res.get().openAsReader()) {
                        return parse(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception ex) {
                        LOG.error("creel: bad data/creel/vanilla_caps.json, no vanilla caps: {}", ex.getMessage());
                        return new Data(Map.of(), Map.of());
                    }
                }

                @Override
                protected void apply(Data parsed, PreparableReloadListener.SharedState state) {
                    Map<EntityType<?>, Integer> data = parsed.caps();
                    caps = Map.copyOf(data);
                    also = Map.copyOf(parsed.also());
                    LOG.info("creel: vanilla caps {}", data.isEmpty() ? "off" : data.entrySet().stream()
                        .map(e -> EntityType.getKey(e.getKey()) + " " + e.getValue()).toList());
                }
            });
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> add(e, level, 1));
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> add(e, level, -1));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, rm, ok) -> recount(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> COUNTS.clear());
    }
}
