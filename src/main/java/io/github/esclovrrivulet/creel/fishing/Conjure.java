package io.github.esclovrrivulet.creel.fishing;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.Tier;
import io.github.esclovrrivulet.creel.habitat.FishHabitat;
import io.github.esclovrrivulet.creel.habitat.FishSpawner;
import io.github.esclovrrivulet.creel.habitat.Habitat;
import io.github.esclovrrivulet.creel.habitat.HabitatData;
import io.github.esclovrrivulet.creel.habitat.SizeClass;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What bites a rod (docs/FISHING.md): from one reading of the water at the bobber, first one of the rod's two size tiers by its chance
 *  (FishingData: the lower 65, the upper 35, Luck of the Sea tilting toward the upper), counting only a tier with a species that suits
 *  the water; then a species of that tier by its habitat weight, as the spawner weighs them. Vanilla's salmon are one more kind of the
 *  salmon run, as in the spawner: wherever a salmon of ours suits the water they join the medium tier's draw with the spawner's weight
 *  for them. Nothing is looked up in the world but the reading: the fish is made, not found. */
public final class Conjure {
    private Conjure() {}

    /** one fish to make: our species (null for vanilla's salmon), its type, and the tier it was drawn in */
    public record Catch(Species species, EntityType<? extends AbstractFish> type, Tier tier) {
        public String id() {
            return this.species == null ? "minecraft:salmon" : this.species.id();
        }
    }

    /** the tier vanilla's salmon bite in */
    public static final Tier VANILLA_SALMON_TIER = Tier.MEDIUM;

    private static final Map<Tier, List<Species>> BY_TIER = new EnumMap<>(Tier.class);

    static {
        for (Tier t : Tier.values()) {
            BY_TIER.put(t, new ArrayList<>());
        }
        for (Species s : FishTable.ALL) {
            BY_TIER.get(s.tier()).add(s);
        }
    }

    private record Option(Species species, double weight) {}

    /** the kinds of one tier that suit the water, with their weights (vanilla's salmon as a null species) */
    private static List<Option> options(Habitat.Reading r, Tier t, boolean salmonRun) {
        List<Option> out = new ArrayList<>();
        for (Species s : BY_TIER.get(t)) {
            FishHabitat h = HabitatData.get(s.id());
            if (h != null && h.enabled() && r.suits(h)) {
                out.add(new Option(s, FishSpawner.weight(s, h)));
            }
        }
        if (t == VANILLA_SALMON_TIER && salmonRun) {
            out.add(new Option(null, FishSpawner.VANILLA_SALMON_WEIGHT));
        }
        return out;
    }

    /** whether any salmon of ours suits the water (then vanilla's salmon may bite too) */
    private static boolean salmonRun(Habitat.Reading r) {
        for (Species s : FishSpawner.species(SizeClass.SALMON)) {
            FishHabitat h = HabitatData.get(s.id());
            if (h != null && h.enabled() && r.suits(h)) {
                return true;
            }
        }
        return false;
    }

    /** the rod's tiers that something in this water belongs to (lower first); empty when nothing would bite */
    public static List<Tier> tiers(Habitat.Reading r, FishingData.RodData rod) {
        List<Tier> out = new ArrayList<>(2);
        if (r.place() == null || rod == null) {
            return out;
        }
        boolean run = salmonRun(r);
        for (Tier t : new Tier[] {rod.lower(), rod.upper()}) {
            if (!out.contains(t) && !options(r, t, run).isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean anyBites(Habitat.Reading r, FishingData.RodData rod) {
        return !tiers(r, rod).isEmpty();
    }

    /** the fish that bites the rod, or null when nothing of its tiers suits the water */
    public static Catch pick(Habitat.Reading r, FishingData.RodData rod, int luck, RandomSource random) {
        List<Tier> tiers = tiers(r, rod);
        double total = 0;
        for (Tier t : tiers) {
            total += FishingData.biteWeight(rod, t, luck);
        }
        if (tiers.isEmpty()) {
            return null;
        }
        Tier chosen = tiers.getLast();
        if (total > 0) {
            double roll = random.nextDouble() * total;
            for (Tier t : tiers) {
                roll -= FishingData.biteWeight(rod, t, luck);
                if (roll < 0) {
                    chosen = t;
                    break;
                }
            }
        }
        List<Option> o = options(r, chosen, salmonRun(r));
        double sum = 0;
        for (Option x : o) {
            sum += x.weight();
        }
        double pick = random.nextDouble() * sum;
        Option got = o.getLast();
        for (Option x : o) {
            pick -= x.weight();
            if (pick < 0) {
                got = x;
                break;
            }
        }
        return got.species() == null ? new Catch(null, EntityTypes.SALMON, chosen)
            : new Catch(got.species(), CreelMod.FISH.get(got.species().id()), chosen);
    }
}
