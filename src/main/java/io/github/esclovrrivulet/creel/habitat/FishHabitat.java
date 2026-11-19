package io.github.esclovrrivulet.creel.habitat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Where one species lives, as its data file says (data/creel/fish/<id>.json, generated from tools/habitat.json; a datapack may
 *  override it): whether it appears naturally at all, the water grade it needs and the least depth of water (blocks from surface to
 *  floor at the spot; large fish 4, the biggest 6, others 1), its weight against the other fish of its size class (10, or less for a
 *  rare find), its own school size if it has one (schoolMin 0 when not: its size class's school size applies, see Budgets), and its
 *  places, each class with the temperature bands it takes there. */
public record FishHabitat(boolean enabled, Grade grade, int minDepth, int weight, int schoolMin, int schoolMax, Map<Place, EnumSet<Band>> places) {

    /** whether the fish may appear in water of this class, band, grade and depth */
    public boolean accepts(Place place, Band band, Grade water, int depth) {
        if (!this.enabled || place == null || band == null || !water.suits(this.grade) || depth < this.minDepth) {
            return false;
        }
        EnumSet<Band> bands = this.places.get(place);
        return bands != null && bands.contains(band);
    }

    /** whether the fish takes any of these classes at all (the cheap first test, before the water is looked at) */
    public boolean takesAny(Collection<Place> classes) {
        if (!this.enabled) {
            return false;
        }
        for (Place p : classes) {
            if (this.places.containsKey(p)) {
                return true;
            }
        }
        return false;
    }

    /** whether the data gives the species a school size of its own (else its size class's applies) */
    public boolean ownSchool() {
        return this.schoolMin > 0;
    }

    /** whether the fish takes this class and band, whatever the water (what a biome can offer) */
    public boolean takes(Place place, Band band) {
        EnumSet<Band> bands = this.places.get(place);
        return this.enabled && bands != null && bands.contains(band);
    }

    /** reads a data file; unknown classes, bands or grades are an error, so a typo in a datapack shows in the log */
    public static FishHabitat parse(JsonObject json) {
        boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
        Grade grade = Grade.byKey(json.get("grade").getAsString());
        if (grade == null || grade == Grade.NONE) {
            throw new IllegalArgumentException("grade must be narrow, open or deep, not " + json.get("grade"));
        }
        int weight = json.get("weight").getAsInt();
        int minDepth = json.has("min_depth") ? json.get("min_depth").getAsInt() : 1;
        if (minDepth < 1) {
            throw new IllegalArgumentException("min_depth must be 1 or more");
        }
        int min = 0, max = 0;
        if (json.has("school")) {
            JsonArray school = json.getAsJsonArray("school");
            min = school.get(0).getAsInt();
            max = school.get(1).getAsInt();
            if (min < 1 || max < min) {
                throw new IllegalArgumentException("school must be [min, max] with 1 <= min <= max");
            }
        }
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be 1 or more");
        }
        Map<Place, EnumSet<Band>> places = new EnumMap<>(Place.class);
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("places").entrySet()) {
            Place p = Place.byKey(e.getKey());
            if (p == null) {
                throw new IllegalArgumentException("no class " + e.getKey());
            }
            EnumSet<Band> bands = EnumSet.noneOf(Band.class);
            for (JsonElement b : e.getValue().getAsJsonArray()) {
                Band band = Band.byKey(b.getAsString());
                if (band == null) {
                    throw new IllegalArgumentException("no temperature band " + b);
                }
                bands.add(band);
            }
            places.put(p, bands);
        }
        return new FishHabitat(enabled, grade, minDepth, weight, min, max, places);
    }

    /** "fresh cool+temperate, swamp warm" */
    public String placesText() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Place, EnumSet<Band>> e : this.places.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(e.getKey().key()).append(' ');
            sb.append(String.join("+", e.getValue().stream().map(Band::key).toList()));
        }
        return sb.toString();
    }
}
