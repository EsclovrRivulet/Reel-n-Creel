package io.github.esclovrrivulet.creel.habitat;

/** How good a spot of water is for fish (docs/HABITAT.md section 1), worst first: a fish needing a grade takes that grade or better. */
public enum Grade {
    NONE, NARROW, OPEN, DEEP;

    public String key() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Grade byKey(String key) {
        for (Grade g : values()) {
            if (g.key().equals(key)) {
                return g;
            }
        }
        return null;
    }

    /** whether water of this grade is good enough for a fish that needs the given grade */
    public boolean suits(Grade needed) {
        return this != NONE && this.ordinal() >= needed.ordinal();
    }
}
