package io.github.esclovrrivulet.creel.habitat;

/** The kind of place a spot of water is (docs/HABITAT.md section 2). */
public enum Place {
    FRESH, HIGHLAND, SWAMP, COAST, OCEAN, DEEP_SEA;

    /** the name used in data files and commands: fresh, highland, swamp, coast, ocean, deep_sea */
    public String key() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Place byKey(String key) {
        for (Place p : values()) {
            if (p.key().equals(key)) {
                return p;
            }
        }
        return null;
    }
}
