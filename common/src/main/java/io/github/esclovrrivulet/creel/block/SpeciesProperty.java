package io.github.esclovrrivulet.creel.block;

import net.minecraft.world.level.block.state.properties.Property;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** (0.22.0) The species a Fish Spawn block was laid by, as a block state property: its values are the ids of every fish that breeds
 *  (Species.breeds: every fish with a spawn family, so all but the giants), in the species table's order. A property of plain strings
 *  rather than an enum, so the list follows the generated FishTable and a fish added later needs only its "spawn" in tools/fish.json.
 *  The saved world keeps the id by name (as any block state property), so the order may change without harm. */
public final class SpeciesProperty extends Property<String> {
    private final List<String> values;
    private final Map<String, Integer> index = new HashMap<>();

    public SpeciesProperty(String name) {
        super(name, String.class);
        this.values = FishTable.ALL.stream().filter(Species::breeds).map(Species::id).toList();
        for (int i = 0; i < this.values.size(); i++) {
            this.index.put(this.values.get(i), i);
        }
    }

    @Override
    public List<String> getPossibleValues() {
        return this.values;
    }

    @Override
    public String getName(String value) {
        return value;
    }

    @Override
    public Optional<String> getValue(String name) {
        return this.index.containsKey(name) ? Optional.of(name) : Optional.empty();
    }

    @Override
    public int getInternalIndex(String value) {
        Integer i = this.index.get(value);
        return i == null ? -1 : i;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SpeciesProperty p && super.equals(o) && this.values.equals(p.values));
    }

    @Override
    public int generateHashCode() {
        return 31 * super.generateHashCode() + this.values.hashCode();
    }
}
