package io.github.esclovrrivulet.creel.compat;

import net.minecraft.nbt.CompoundTag;

/** (1.21.1 build only) 26.x saves an entity's data through a ValueOutput; 1.21.1 writes a CompoundTag. This stands in for 26.x's class
 *  (versions/1.21.1/java_renames.txt points the main tree's import here) with the calls the main tree makes, each the tag's own. */
public final class ValueOutput {
    private final CompoundTag tag;

    public ValueOutput(CompoundTag tag) {
        this.tag = tag;
    }

    public CompoundTag tag() {
        return this.tag;
    }

    public void putString(String key, String value) {
        this.tag.putString(key, value);
    }

    public void putFloat(String key, float value) {
        this.tag.putFloat(key, value);
    }

    public void putLong(String key, long value) {
        this.tag.putLong(key, value);
    }

    public void putBoolean(String key, boolean value) {
        this.tag.putBoolean(key, value);
    }
}
