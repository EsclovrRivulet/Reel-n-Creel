package io.github.esclovrrivulet.creel.fishing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Tier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The rods' fishing numbers (docs/FISHING.md), read from data/creel/fishing.json whenever data packs load (server start and /reload);
 *  a datapack may override the file. Per rod: the two size tiers it hooks ("classes", lower then upper), their chances ("chances"),
 *  and its throw against vanilla's ("cast"). For all rods: what each level of Luck of the Sea multiplies the upper tier's chance by
 *  ("luck_of_the_sea"), and how fast a reeled fish of each tier comes in, in blocks per tick ("reel_speed"). (0.17.0) Per rod the chance
 *  of a treasure with a landed fish ("treasure"); the share of it each Luck of the Sea level adds ("treasure": "luck_of_the_sea"); what
 *  fishermen buy ("trades"). A rod or key missing from
 *  the file, or a bad file, keeps the defaults (RodTable, generated from tools/rod_proto.py, and the numbers below). */
public final class FishingData {
    private static final Logger LOG = LoggerFactory.getLogger("creel");

    /** one rod's numbers */
    public record RodData(Tier lower, Tier upper, double lowerChance, double upperChance, double cast, double treasure) {}

    /** (0.17.0) one offer a fisherman makes on top of vanilla's: count of the item for 1 emerald */
    public record Buy(Identifier item, int count) {}

    /** (0.17.0) the "trades" block: what every fisherman buys (fishing.FishermanTrades) and the offers' shared numbers, vanilla's for a
     *  novice sell trade: uses before a restock, the villager's trade xp, the price multiplier (reputation and demand) */
    public record Trades(List<Buy> buys, int maxUses, int xp, float priceMultiplier) {}

    /** the hold to cast and the hold to wind: ticks to full charge, the power of a tap and of a full charge, the wind speed */
    public record Handling(int chargeTicks, double minPower, double fullPower, double windSpeed) {
        /** the cast's power after holding for that many ticks: minPower at once, rising to fullPower at chargeTicks, then held */
        public double power(int heldTicks) {
            double c = Math.min(1.0, Math.max(0, heldTicks) / (double) Math.max(1, this.chargeTicks));
            return this.minPower + (this.fullPower - this.minPower) * c;
        }

        /** the charge from 0 to 1 */
        public double charge(int heldTicks) {
            return Math.min(1.0, Math.max(0, heldTicks) / (double) Math.max(1, this.chargeTicks));
        }
    }

    /** The fight (0.14.0, reworked 0.15.0, rolled 0.16.0; docs/FISHING.md "The fight"): every number of it, the "fight" block of the file.
     *  Ticks are game ticks (20 a second). At the end of each stage the fish ROLLS what it does next
     *  and for how long (FightCore): REST (rest on a little, or PULL), PULL (RUN or settle back to REST), RUN (always eases off through a
     *  PULL-level easing before a rest). A run is held to a credit: the fish's running time may never pass run_cap of the fight so far.
     *  Its stamina drains in pulls and runs (faster reeled against); spent, it finishes its stage and is beaten.
     *  runningTicks  a fish's stamina by tier; 0: no fight, it comes straight in. sizeScale: times this by size variant
     *  restMin/Max, pullMin/Max, runMin/Max  each stage's length is rolled between the two, skewed by lengthSkew (mostly middling, now and
     *                then long, never under the first)
     *  restMoreMin/Max, restAgain  at a rest's end, the chance it rests on, and for how long
     *  easeTicks     the PULL-level easing after every run, before the rest
     *  warnTicks     the shortest pull a run may come out of
     *  runTired/Fresh the chance a pull ends in a run, spent and fresh (between by the stamina left) ...
     *  provoke       ... plus this when the player reeled through more than provokeAbove of the pull, minus it when less than settleBelow
     *  runCap        by tier: the most of the fight so far the fish may spend running (a run the credit cannot cover settles, or is cut)
     *  restRecover   stamina a resting fish gains a tick while it is not reeled (up to what it started with)
     *  reeledDrain   stamina a tick of a pull or run reeled against uses (1 when not reeled; a rest uses none)
     *  runSpeed      a fresh running fish's speed, times its tier's reel speed; tiredSpeed: the share of it left when spent
     *  restDrag      a resting fish keeps this share of its own movement each tick
     *  runReelGain, pullReelGain, beatenReelGain  reeling draws the fish in at this share of its tier's reel speed (rest: 1)
     *  endOfLine     blocks from the player where the line runs out (under vanilla's 32): a run stops there (the fish is held)
     *  snapDamage    uses the rod loses when the line snaps
     *  clickRest/Pull/Run (0.16.2) ticks between the reel's clicks by the fish's state (bitten or beaten: rest); never by time
     *  pitchRest/Pull/Run (0.16.2) the click's pitch by the fish's state
     *  clickVolume   the click's volume
     *  landBelow     (0.16.1) a resting fish reeled to the landing distance with at most this share of its stamina left is landed there;
     *                a fresher one is spooked by the bank into a pull
     *  reelPace, paceTailTicks (0.16.1) a fighting fish is reeled in no faster than the distance still to cover over
     *                max(paceTailTicks, its stamina left times reelPace) ticks, a tick: it arrives about as it tires
     *  snapReelTicks (0.16.2) the line snaps when, in one run, the reeled ticks less the ticks let go (never below 0) reach this */
    public record Fight(Map<Tier, Integer> runningTicks, Map<Size, Double> sizeScale, int restMin, int restMax,
                        int restMoreMin, int restMoreMax, double restAgain, int pullMin, int pullMax, int runMin, int runMax, double lengthSkew,
                        int easeTicks, int warnTicks, double runTired, double runFresh, double provoke, double settleBelow, double provokeAbove,
                        Map<Tier, Double> runCap, double restRecover, double reeledDrain, double runSpeed, double tiredSpeed, double restDrag,
                        double runReelGain, double pullReelGain, double beatenReelGain, double endOfLine, int snapDamage, int clickRest,
                        int clickPull, int clickRun, double pitchRest, double pitchPull, double pitchRun, double clickVolume,
                        double landBelow, double reelPace, int paceTailTicks, int snapReelTicks) {
        /** a fish's stamina: its tier's running ticks times its size's scale */
        public double stamina(Tier t, Size size) {
            return this.runningTicks.getOrDefault(t, 0) * this.sizeScale.getOrDefault(size, 1.0);
        }

        /** the most of the fight so far a fish of that tier may spend running */
        public double cap(Tier t) {
            return this.runCap.getOrDefault(t, 0.3);
        }

        /** a fresh running fish's speed, blocks a tick: run_speed times the tier's reel speed */
        public double runSpeed(Tier t) {
            return this.runSpeed * reelSpeed(t);
        }

        /** (0.16.2) ticks between the reel's clicks for the fish's state: RUN, PULL (and the easing), anything else (rest, bitten,
         *  beaten) the rest's */
        public int clickGap(CastingHook.Line state) {
            return state == CastingHook.Line.RUN ? this.clickRun : state == CastingHook.Line.PULL ? this.clickPull : this.clickRest;
        }

        /** (0.16.2) the click's pitch for the fish's state, as clickGap */
        public double clickPitch(CastingHook.Line state) {
            return state == CastingHook.Line.RUN ? this.pitchRun : state == CastingHook.Line.PULL ? this.pitchPull : this.pitchRest;
        }
    }

    /** treasureLuck (0.17.0): the share of a rod's own treasure chance each level of Luck of the Sea adds */
    public record Table(Map<String, RodData> rods, double luckUpper, Map<Tier, Double> reelSpeed, Handling handling, Fight fight,
                        double treasureLuck, Trades trades) {}

    private static Fight defaultFight() {
        Map<Tier, Integer> run = new EnumMap<>(Tier.class);
        run.put(Tier.TINY, 0);
        run.put(Tier.SMALL, 165);
        run.put(Tier.MEDIUM, 260);
        run.put(Tier.LARGE, 365);
        run.put(Tier.VERY_LARGE, 470);
        run.put(Tier.GIANT, 575);
        Map<Size, Double> scale = new EnumMap<>(Size.class);
        scale.put(Size.SMALL, 0.85);
        scale.put(Size.ORDINARY, 1.0);
        scale.put(Size.LARGE, 1.1);
        scale.put(Size.TROPHY, 1.2);
        Map<Tier, Double> cap = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            cap.put(t, t == Tier.VERY_LARGE || t == Tier.GIANT ? 0.25 : 0.3);
        }
        return new Fight(run, scale, 30, 90, 15, 30, 0.25, 20, 60, 30, 70, 2.0, 20, 15, 0.35, 0.75, 0.2, 0.3, 0.6, cap, 0.25, 2.0, 1.2,
            0.6, 0.6, 0.1, 0.5, 1.5, 30.0, 3, 17, 9, 4, 1.8, 1.9, 2.0, 0.35, 0.5, 0.45, 20, 25);
    }

    private static final Map<Tier, Double> REEL = Map.of(Tier.TINY, 0.12, Tier.SMALL, 0.12, Tier.MEDIUM, 0.105, Tier.LARGE, 0.09,
        Tier.VERY_LARGE, 0.075, Tier.GIANT, 0.06);

    /** (0.17.0) the treasure chance with a landed fish, by rod (the owner's) */
    private static final Map<String, Double> TREASURE = Map.of("panfish_rod", 0.015, "trout_rod", 0.025, "salmon_rod", 0.035,
        "surf_rod", 0.05, "big_game_rod", 0.05);

    /** (0.17.0) the owner's prices: 1 raw fish or 4 fish morsels for an emerald, 16 uses, vanilla's novice sell trade's xp and multiplier */
    private static Trades defaultTrades() {
        return new Trades(List.of(new Buy(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "raw_fish"), 1),
            new Buy(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "fish_morsel"), 4)), 16, 2, 0.05F);
    }

    private static Table defaults() {
        Map<String, RodData> rods = new LinkedHashMap<>();
        for (RodTable.Rod r : RodTable.ALL) {
            rods.put(r.id(), new RodData(r.lower(), r.upper(), 65, 35, r.cast(), TREASURE.getOrDefault(r.id(), 0.0)));
        }
        return new Table(rods, 1.3, new EnumMap<>(REEL), new Handling(20, 0.35, 1.0, 0.15), defaultFight(), 0.4, defaultTrades());
    }

    private static final Table DEFAULTS = defaults();
    private static volatile Table current = DEFAULTS;

    private FishingData() {}

    public static Handling handling() {
        return current.handling();
    }

    public static Fight fight() {
        return current.fight();
    }

    /** for the tests (FightTest): the fight's numbers in force replaced; returns the ones that were (to put back) */
    static Fight swapFight(Fight f) {
        Table t = current;
        current = new Table(t.rods(), t.luckUpper(), t.reelSpeed(), t.handling(), f, t.treasureLuck(), t.trades());
        return t.fight();
    }

    public static RodData rod(String id) {
        RodData d = current.rods().get(id);
        return d != null ? d : DEFAULTS.rods().get(id);
    }

    /** the chance of a tier biting on that rod with that much Luck of the Sea; 0 for a tier the rod does not hook */
    public static double biteWeight(RodData rod, Tier t, int luck) {
        if (t == rod.lower()) {
            return rod.lowerChance();
        }
        if (t == rod.upper()) {
            return rod.upperChance() * Math.pow(current.luckUpper(), Math.max(0, luck));
        }
        return 0;
    }

    /** (0.17.0) the chance of a treasure with a fish landed on that rod with that much Luck of the Sea: the rod's own chance times
     *  (1 + treasure luck x the level) */
    public static double treasureChance(String rod, int luck) {
        RodData d = rod(rod);
        return d == null ? 0 : d.treasure() * (1 + current.treasureLuck() * Math.max(0, luck));
    }

    /** (0.17.0) what fishermen buy */
    public static Trades trades() {
        return current.trades();
    }

    public static double reelSpeed(Tier t) {
        return current.reelSpeed().getOrDefault(t, 0.1);
    }

    public static String key(Tier t) {
        return t.name().toLowerCase(Locale.ROOT);
    }

    private static Tier tier(String key) {
        return Tier.valueOf(key.toUpperCase(Locale.ROOT));
    }

    public static String text() {
        Table t = current;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, RodData> e : t.rods().entrySet()) {
            RodData r = e.getValue();
            sb.append(sb.isEmpty() ? "" : "; ").append(e.getKey()).append(' ').append(key(r.lower())).append(' ').append(r.lowerChance())
                .append(" / ").append(key(r.upper())).append(' ').append(r.upperChance()).append(", cast x").append(r.cast());
        }
        sb.append("; treasure");
        for (Map.Entry<String, RodData> e : t.rods().entrySet()) {
            sb.append(' ').append(e.getKey()).append(' ').append(e.getValue().treasure());
        }
        sb.append(", +").append(t.treasureLuck()).append(" of it per luck level; fishermen buy ").append(t.trades());
        return sb.append("; luck x").append(t.luckUpper()).append(" upper per level; ").append(t.handling()).append("; ").append(t.fight()).toString();
    }

    static Table parse(JsonObject json) {
        Map<String, RodData> rods = new LinkedHashMap<>(DEFAULTS.rods());
        JsonObject rj = json.getAsJsonObject("rods");
        if (rj != null) {
            for (String id : rj.keySet()) {
                RodData base = rods.get(id);
                if (base == null) {
                    throw new IllegalArgumentException("rods." + id + ": no such rod");
                }
                JsonObject o = rj.getAsJsonObject(id);
                Tier lo = base.lower(), hi = base.upper();
                double cl = base.lowerChance(), cu = base.upperChance(), cast = base.cast(), treasure = base.treasure();
                if (o.has("classes")) {
                    JsonArray a = o.getAsJsonArray("classes");
                    lo = tier(a.get(0).getAsString());
                    hi = tier(a.get(1).getAsString());
                }
                if (o.has("chances")) {
                    JsonArray a = o.getAsJsonArray("chances");
                    cl = a.get(0).getAsDouble();
                    cu = a.get(1).getAsDouble();
                }
                if (o.has("cast")) {
                    cast = o.get("cast").getAsDouble();
                }
                if (o.has("treasure")) {
                    treasure = o.get("treasure").getAsDouble();
                }
                if (cl < 0 || cu < 0 || cast <= 0 || !unit(treasure)) {
                    throw new IllegalArgumentException("rods." + id + ": chances must be 0 or more, cast above 0, treasure 0..1");
                }
                rods.put(id, new RodData(lo, hi, cl, cu, cast, treasure));
            }
        }
        double luck = json.has("luck_of_the_sea") ? json.get("luck_of_the_sea").getAsDouble() : DEFAULTS.luckUpper();
        if (luck <= 0) {
            throw new IllegalArgumentException("luck_of_the_sea must be above 0");
        }
        Map<Tier, Double> reel = new EnumMap<>(DEFAULTS.reelSpeed());
        JsonObject sj = json.getAsJsonObject("reel_speed");
        if (sj != null) {
            for (String k : sj.keySet()) {
                double v = sj.get(k).getAsDouble();
                if (v <= 0) {
                    throw new IllegalArgumentException("reel_speed." + k + " must be above 0");
                }
                reel.put(tier(k), v);
            }
        }
        Handling h = DEFAULTS.handling();
        JsonObject cj = json.getAsJsonObject("cast");
        int chargeTicks = h.chargeTicks();
        double minPower = h.minPower(), fullPower = h.fullPower();
        if (cj != null) {
            chargeTicks = cj.has("charge_ticks") ? cj.get("charge_ticks").getAsInt() : chargeTicks;
            minPower = cj.has("min_power") ? cj.get("min_power").getAsDouble() : minPower;
            fullPower = cj.has("full_power") ? cj.get("full_power").getAsDouble() : fullPower;
        }
        double wind = json.has("wind_speed") ? json.get("wind_speed").getAsDouble() : h.windSpeed();
        if (chargeTicks < 1 || minPower <= 0 || fullPower < minPower || wind <= 0) {
            throw new IllegalArgumentException("cast.charge_ticks must be 1 or more, 0 < min_power <= full_power, wind_speed above 0");
        }
        JsonObject tj = json.getAsJsonObject("treasure");
        double treasureLuck = tj != null && tj.has("luck_of_the_sea") ? tj.get("luck_of_the_sea").getAsDouble() : DEFAULTS.treasureLuck();
        if (treasureLuck < 0) {
            throw new IllegalArgumentException("treasure.luck_of_the_sea must be 0 or more");
        }
        return new Table(rods, luck, reel, new Handling(chargeTicks, minPower, fullPower, wind), parseFight(json.getAsJsonObject("fight")),
            treasureLuck, parseTrades(json.getAsJsonObject("trades")));
    }

    /** (0.17.0) the "trades" block; a key missing keeps its default. An item that is not registered is refused (the whole file then
     *  falls back to the defaults, as any bad number does) */
    static Trades parseTrades(JsonObject o) {
        Trades d = DEFAULTS == null ? defaultTrades() : DEFAULTS.trades();
        if (o == null) {
            return d;
        }
        List<Buy> buys = d.buys();
        if (o.has("buys")) {
            List<Buy> read = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("buys")) {
                JsonObject b = e.getAsJsonObject();
                Identifier id = Identifier.parse(b.get("item").getAsString());
                int count = b.has("count") ? b.get("count").getAsInt() : 1;
                if (!BuiltInRegistries.ITEM.containsKey(id) || count < 1 || count > 64) {
                    throw new IllegalArgumentException("trades.buys: " + id + " is not an item, or its count is not 1..64");
                }
                read.add(new Buy(id, count));
            }
            buys = List.copyOf(read);
        }
        int maxUses = (int) num(o, "max_uses", d.maxUses()), xp = (int) num(o, "xp", d.xp());
        float mult = (float) num(o, "price_multiplier", d.priceMultiplier());
        if (maxUses < 1 || xp < 0 || mult < 0) {
            throw new IllegalArgumentException("trades: max_uses 1 or more, xp and price_multiplier 0 or more");
        }
        return new Trades(buys, maxUses, xp, mult);
    }

    private static double num(JsonObject o, String key, double def) {
        return o.has(key) ? o.get(key).getAsDouble() : def;
    }

    /** the "fight" block; a key missing keeps its default */
    static Fight parseFight(JsonObject o) {
        Fight d = DEFAULTS == null ? defaultFight() : DEFAULTS.fight();
        if (o == null) {
            return d;
        }
        Map<Tier, Integer> run = new EnumMap<>(d.runningTicks());
        JsonObject rj = o.getAsJsonObject("running_ticks");
        if (rj != null) {
            for (String k : rj.keySet()) {
                run.put(tier(k), Math.max(0, rj.get(k).getAsInt()));
            }
        }
        Map<Size, Double> scale = new EnumMap<>(d.sizeScale());
        JsonObject sj = o.getAsJsonObject("size_scale");
        if (sj != null) {
            for (String k : sj.keySet()) {
                Size s = Size.byKey(k);
                if (s == null) {
                    throw new IllegalArgumentException("fight.size_scale." + k + ": no such size");
                }
                scale.put(s, Math.max(0, sj.get(k).getAsDouble()));
            }
        }
        Map<Tier, Double> cap = new EnumMap<>(d.runCap());
        JsonObject cj = o.getAsJsonObject("run_cap");
        if (cj != null) {
            for (String k : cj.keySet()) {
                cap.put(tier(k), cj.get(k).getAsDouble());
            }
        }
        double[] rest = pair(o, "rest_ticks", d.restMin(), d.restMax());
        double[] more = pair(o, "rest_more_ticks", d.restMoreMin(), d.restMoreMax());
        double[] pull = pair(o, "pull_ticks", d.pullMin(), d.pullMax());
        double[] runs = pair(o, "run_ticks", d.runMin(), d.runMax());
        double[] chance = pair(o, "run_chance", d.runTired(), d.runFresh());
        double[] share = pair(o, "provoke_share", d.settleBelow(), d.provokeAbove());
        double[] click = byState(o, "click_ticks", d.clickRest(), d.clickPull(), d.clickRun());
        double[] pitch = byState(o, "click_pitch", d.pitchRest(), d.pitchPull(), d.pitchRun());
        Fight f = new Fight(run, scale, (int) rest[0], (int) rest[1], (int) more[0], (int) more[1], num(o, "rest_again", d.restAgain()),
            (int) pull[0], (int) pull[1], (int) runs[0], (int) runs[1], num(o, "length_skew", d.lengthSkew()),
            (int) num(o, "ease_ticks", d.easeTicks()), (int) num(o, "warn_ticks", d.warnTicks()), chance[0], chance[1],
            num(o, "provoke", d.provoke()), share[0], share[1], cap, num(o, "rest_recover", d.restRecover()),
            num(o, "reeled_drain", d.reeledDrain()), num(o, "run_speed", d.runSpeed()), num(o, "tired_speed", d.tiredSpeed()),
            num(o, "rest_drag", d.restDrag()), num(o, "run_reel_gain", d.runReelGain()), num(o, "pull_reel_gain", d.pullReelGain()),
            num(o, "beaten_reel_gain", d.beatenReelGain()), Math.min(31.0, num(o, "end_of_line", d.endOfLine())),
            (int) num(o, "snap_damage", d.snapDamage()), (int) click[0], (int) click[1], (int) click[2], pitch[0], pitch[1], pitch[2],
            num(o, "click_volume", d.clickVolume()), num(o, "land_below", d.landBelow()), num(o, "reel_pace", d.reelPace()),
            (int) num(o, "pace_tail_ticks", d.paceTailTicks()), (int) num(o, "snap_reel_ticks", d.snapReelTicks()));
        boolean capsOk = cap.values().stream().allMatch(c -> c > 0 && c < 1);
        if (f.restMin() < 1 || f.restMax() < f.restMin() || f.restMoreMin() < 1 || f.restMoreMax() < f.restMoreMin() || f.pullMin() < 1
            || f.pullMax() < f.pullMin() || f.runMin() < 1 || f.runMax() < f.runMin() || f.lengthSkew() <= 0 || f.easeTicks() < 1
            || f.warnTicks() < 0 || !unit(f.restAgain()) || !unit(f.runTired()) || !unit(f.runFresh()) || !unit(f.provoke())
            || !unit(f.settleBelow()) || !unit(f.provokeAbove()) || !capsOk || f.restRecover() < 0 || f.reeledDrain() < 0 || f.runSpeed() < 0
            || !unit(f.tiredSpeed()) || !unit(f.restDrag()) || f.runReelGain() < 0 || f.pullReelGain() < 0 || f.beatenReelGain() < 0
            || f.endOfLine() < 4 || f.snapDamage() < 0 || f.clickRest() < 1 || f.clickPull() < 1 || f.clickRun() < 1
            || f.pitchRest() <= 0 || f.pitchPull() <= 0 || f.pitchRun() <= 0 || f.clickVolume() < 0 || !unit(f.landBelow())
            || f.reelPace() < 0 || f.paceTailTicks() < 1 || f.snapReelTicks() < 1) {
            throw new IllegalArgumentException("fight: a number out of range (the stage ticks, ease_ticks, the click ticks and "
                + "snap_reel_ticks 1 or more, chances, shares, rest_drag, tired_speed and land_below 0..1, run_cap above 0 and below 1, "
                + "length_skew above 0, end_of_line 4..31, pitches above 0, pace_tail_ticks 1 or more, the rest 0 or more)");
        }
        return f;
    }

    private static boolean unit(double v) {
        return v >= 0 && v <= 1;
    }

    /** (0.16.2) a {"rest", "pull", "run"} object of the block as three numbers; a key missing keeps its default */
    private static double[] byState(JsonObject o, String key, double rest, double pull, double run) {
        JsonObject j = o.has(key) ? o.getAsJsonObject(key) : null;
        if (j == null) {
            return new double[] {rest, pull, run};
        }
        return new double[] {num(j, "rest", rest), num(j, "pull", pull), num(j, "run", run)};
    }

    /** a two-number array of the block, or the two defaults */
    private static double[] pair(JsonObject o, String key, double a, double b) {
        if (!o.has(key)) {
            return new double[] {a, b};
        }
        JsonArray arr = o.getAsJsonArray(key);
        return new double[] {arr.get(0).getAsDouble(), arr.get(1).getAsDouble()};
    }

    public static void register() {
        Identifier file = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "fishing.json");
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "fishing"),
            new SimpleReloadListener<Table>() {
                @Override
                protected Table prepare(PreparableReloadListener.SharedState state) {
                    Optional<Resource> res = state.resourceManager().getResource(file);
                    if (res.isEmpty()) {
                        LOG.warn("creel: no data/creel/fishing.json, the default fishing numbers apply");
                        return DEFAULTS;
                    }
                    try (Reader r = res.get().openAsReader()) {
                        return parse(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception ex) {
                        LOG.error("creel: bad data/creel/fishing.json, the default fishing numbers apply: {}", ex.getMessage());
                        return DEFAULTS;
                    }
                }

                @Override
                protected void apply(Table data, PreparableReloadListener.SharedState state) {
                    current = data;
                    LOG.info("creel: rods {}", text());
                }
            });
    }
}
