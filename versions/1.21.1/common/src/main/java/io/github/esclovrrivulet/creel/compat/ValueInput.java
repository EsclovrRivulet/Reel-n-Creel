package io.github.esclovrrivulet.creel.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** (1.21.1 build only) 26.x reads an entity's data through a ValueInput; 1.21.1 reads a CompoundTag. This stands in for 26.x's class
 *  (versions/1.21.1/java_renames.txt points the main tree's import here) with the calls the main tree makes: each gives the default
 *  when the key is missing or of another type, as 26.x's do. */
public final class ValueInput {
    private final CompoundTag tag;

    public ValueInput(CompoundTag tag) {
        this.tag = tag;
    }

    public CompoundTag tag() {
        return this.tag;
    }

    public String getStringOr(String key, String fallback) {
        return this.tag.contains(key, Tag.TAG_STRING) ? this.tag.getString(key) : fallback;
    }

    public float getFloatOr(String key, float fallback) {
        return this.tag.contains(key, Tag.TAG_ANY_NUMERIC) ? this.tag.getFloat(key) : fallback;
    }

    public long getLongOr(String key, long fallback) {
        return this.tag.contains(key, Tag.TAG_ANY_NUMERIC) ? this.tag.getLong(key) : fallback;
    }

    public boolean getBooleanOr(String key, boolean fallback) {
        return this.tag.contains(key, Tag.TAG_ANY_NUMERIC) ? this.tag.getBoolean(key) : fallback;
    }
}
