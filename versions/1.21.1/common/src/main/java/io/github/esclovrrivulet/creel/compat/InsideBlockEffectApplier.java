package io.github.esclovrrivulet.creel.compat;

/** (1.21.1 build only) 26.x passes a block's entityInside a collector of effects; 1.21.1 has none. This stands in for 26.x's type
 *  (versions/1.21.1/java_renames.txt), empty, for FishSpawnBlockBase. */
public final class InsideBlockEffectApplier {
    public static final InsideBlockEffectApplier NONE = new InsideBlockEffectApplier();

    private InsideBlockEffectApplier() {}
}
