package io.github.esclovrrivulet.creel.fish;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** The size roll (docs/SIZES.md), read from data/creel/sizes.json whenever data packs load (server start and /reload); a datapack may
 *  override the file. Each size class has a factor and a weight; "tiers" may give a size tier weights of its own (the giant tier has
 *  no trophy, its share goes to large); "wobble" is the random spread round each factor (0.04: plus or minus 4%). A bad file keeps
 *  the defaults below. A fish keeps the factor it rolled, so a change here touches only fish that appear afterwards. */
public final class Sizes {
    private static final Logger LOG = LoggerFactory.getLogger("creel");

    /** the roll as loaded: factor and weight per size, weights per tier where a tier has its own, the wobble */
    public record Table(Map<Size, Float> scale, Map<Size, Integer> weight, Map<Tier, Map<Size, Integer>> tierWeight, float wobble) {
        Map<Size, Integer> weights(Tier t) {
            return this.tierWeight.getOrDefault(t, this.weight);
        }
    }

    private static final Table DEFAULTS = new Table(
        Map.of(Size.SMALL, 0.8F, Size.ORDINARY, 1.0F, Size.LARGE, 1.25F, Size.TROPHY, 1.5F),
        Map.of(Size.SMALL, 15, Size.ORDINARY, 60, Size.LARGE, 20, Size.TROPHY, 5),
        Map.of(Tier.GIANT, Map.of(Size.SMALL, 15, Size.ORDINARY, 60, Size.LARGE, 25, Size.TROPHY, 0)),
        0.04F);

    private static volatile Table current = DEFAULTS;

    private Sizes() {}

    /** the factor a size class gives, without wobble */
    public static float scale(Size s) {
        return current.scale().get(s);
    }

    /** one roll for a fish of that tier: the size class by weight, the factor with its wobble */
    public static Rolled roll(Tier tier, RandomSource r) {
        Table t = current;
        Map<Size, Integer> w = t.weights(tier);
        int total = 0;
        for (Size s : Size.values()) {
            total += w.get(s);
        }
        Size pick = Size.ORDINARY;
        if (total > 0) {
            int n = r.nextInt(total);
            for (Size s : Size.values()) {
                n -= w.get(s);
                if (n < 0) {
                    pick = s;
                    break;
                }
            }
        }
        float wob = t.wobble() <= 0 ? 0 : Mth.nextFloat(r, -t.wobble(), t.wobble());
        return new Rolled(pick, t.scale().get(pick) * (1.0F + wob));
    }

    public record Rolled(Size size, float factor) {}

    /** a fish's full health at a size: its tier's health times the factor to the power 1.5, rounded, at least 1 */
    public static float health(float tierHealth, float factor) {
        return Math.max(1, Math.round(tierHealth * Math.pow(factor, 1.5)));
    }

    /** for /creel census and the README: the roll in words */
    public static String text() {
        Table t = current;
        StringBuilder sb = new StringBuilder();
        for (Size s : Size.values()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(s.key).append(" x").append(t.scale().get(s)).append(' ').append(t.weight().get(s));
        }
        sb.append(", wobble ").append(t.wobble());
        for (Map.Entry<Tier, Map<Size, Integer>> e : t.tierWeight().entrySet()) {
            sb.append("; ").append(e.getKey().name().toLowerCase()).append(' ').append(e.getValue());
        }
        return sb.toString();
    }

    static Table parse(JsonObject json) {
        Map<Size, Float> scale = new EnumMap<>(DEFAULTS.scale());
        Map<Size, Integer> weight = new EnumMap<>(DEFAULTS.weight());
        JsonObject sizes = json.getAsJsonObject("sizes");
        for (Size s : Size.values()) {
            if (sizes != null && sizes.has(s.key)) {
                JsonObject o = sizes.getAsJsonObject(s.key);
                if (o.has("scale")) {
                    scale.put(s, o.get("scale").getAsFloat());
                }
                if (o.has("weight")) {
                    weight.put(s, o.get("weight").getAsInt());
                }
            }
            if (scale.get(s) <= 0 || weight.get(s) < 0) {
                throw new IllegalArgumentException(s.key + ": scale must be above 0 and weight 0 or more");
            }
        }
        Map<Tier, Map<Size, Integer>> tiers = new EnumMap<>(Tier.class);
        JsonObject tj = json.getAsJsonObject("tiers");
        if (tj != null) {
            for (String k : tj.keySet()) {
                Tier tier = Tier.valueOf(k.toUpperCase());
                Map<Size, Integer> w = new EnumMap<>(weight);
                JsonObject o = tj.getAsJsonObject(k);
                for (Size s : Size.values()) {
                    if (o.has(s.key)) {
                        int v = o.get(s.key).getAsInt();
                        if (v < 0) {
                            throw new IllegalArgumentException(k + "." + s.key + ": weight must be 0 or more");
                        }
                        w.put(s, v);
                    }
                }
                tiers.put(tier, w);
            }
        }
        float wobble = json.has("wobble") ? json.get("wobble").getAsFloat() : DEFAULTS.wobble();
        if (wobble < 0 || wobble >= 1) {
            throw new IllegalArgumentException("wobble must be 0 or more and below 1");
        }
        return new Table(scale, weight, tiers, wobble);
    }

    public static void register() {
        Identifier file = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "sizes.json");
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "sizes"),
            new SimpleReloadListener<Table>() {
                @Override
                protected Table prepare(PreparableReloadListener.SharedState state) {
                    Optional<Resource> res = state.resourceManager().getResource(file);
                    if (res.isEmpty()) {
                        LOG.warn("creel: no data/creel/sizes.json, the default sizes apply");
                        return DEFAULTS;
                    }
                    try (Reader r = res.get().openAsReader()) {
                        return parse(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception ex) {
                        LOG.error("creel: bad data/creel/sizes.json, the default sizes apply: {}", ex.getMessage());
                        return DEFAULTS;
                    }
                }

                @Override
                protected void apply(Table data, PreparableReloadListener.SharedState state) {
                    current = data;
                    LOG.info("creel: fish sizes {}", text());
                }
            });
    }
}
