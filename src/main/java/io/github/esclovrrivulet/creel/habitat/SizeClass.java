package io.github.esclovrrivulet.creel.habitat;

import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.Tier;

/** The six spawn budgets (docs/HABITAT.md section 4): each size class has its own cap round a player and its own school size, set in
 *  data/creel/budgets.json under the key named here. */
public enum SizeClass {
    /** A: tiny and small fish */
    LITTLE("A", "tiny_and_small"),
    /** B: medium fish */
    MEDIUM("B", "medium"),
    /** C: large fish */
    LARGE("C", "large"),
    /** D: very large fish */
    VERY_LARGE("D", "very_large"),
    /** E: giant fish */
    GIANT("E", "giant"),
    /** S: the salmon run: our salmon and the steelhead, whatever their size, counted together with vanilla's salmon */
    SALMON("S", "salmon");

    public final String letter;
    public final String key;

    SizeClass(String letter, String key) {
        this.letter = letter;
        this.key = key;
    }

    /** a fish's class: the salmon budget for the fish of a salmon run, else by its size */
    public static SizeClass of(Species s) {
        return "salmon".equals(s.group()) ? SALMON : of(s.tier());
    }

    public static SizeClass of(Tier tier) {
        return switch (tier) {
            case TINY, SMALL -> LITTLE;
            case MEDIUM -> MEDIUM;
            case LARGE -> LARGE;
            case VERY_LARGE -> VERY_LARGE;
            case GIANT -> GIANT;
        };
    }

    /** "A tiny_and_small" */
    public String label() {
        return this.letter + " " + this.key;
    }
}
