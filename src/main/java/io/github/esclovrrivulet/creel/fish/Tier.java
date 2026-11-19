package io.github.esclovrrivulet.creel.fish;

/** A fish's size tier (docs/ROSTER.md). It sets the fish's health, the water grade it needs and its spawn budget: tiny and small
 *  share one budget, medium has one, large one, very large and giant one (habitat.SizeClass, docs/HABITAT.md section 4). */
public enum Tier {
    TINY, SMALL, MEDIUM, LARGE, VERY_LARGE, GIANT
}
