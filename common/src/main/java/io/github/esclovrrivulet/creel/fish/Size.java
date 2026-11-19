package io.github.esclovrrivulet.creel.fish;

import net.minecraft.network.chat.Component;

/** A fish's size class (docs/SIZES.md), rolled once when the fish first appears and kept for life. The factor each class gives, its
 *  chance and the wobble are data (Sizes, data/creel/sizes.json); the four classes themselves are fixed, since the loot tables and
 *  the names read them. key is the word in the save data (CreelSize) and in the size word's translation key (creel.size.<key>). */
public enum Size {
    SMALL("small"), ORDINARY("ordinary"), LARGE("large"), TROPHY("trophy");

    public final String key;

    Size(String key) {
        this.key = key;
    }

    public static Size byKey(String key) {
        for (Size s : values()) {
            if (s.key.equals(key)) {
                return s;
            }
        }
        return null;
    }

    public static Size byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : ORDINARY;
    }

    /** the name with the size word in front ("Large King Salmon"); an ordinary fish's name is left as it is */
    public Component name(Component name) {
        return this == ORDINARY ? name : Component.translatable("creel.size." + this.key, name);
    }
}
