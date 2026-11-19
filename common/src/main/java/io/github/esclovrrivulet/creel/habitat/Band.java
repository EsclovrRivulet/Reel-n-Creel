package io.github.esclovrrivulet.creel.habitat;

/** How warm the water is (docs/HABITAT.md section 3). The bands follow the world generator's own temperature parameter, whose
 *  vanilla slices are -1 to -0.45 (frozen biomes), -0.45 to -0.15 (taiga), -0.15 to 0.2 (plains and forest), above 0.2 (jungle,
 *  savanna, desert), so a band is one of vanilla's temperature slices, the two hottest joined. */
public enum Band {
    COLD, COOL, TEMPERATE, WARM;

    public String key() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Band byKey(String key) {
        for (Band b : values()) {
            if (b.key().equals(key)) {
                return b;
            }
        }
        return null;
    }

    /** one band colder (highland water); cold stays cold */
    public Band colder() {
        return this == COLD ? COLD : values()[this.ordinal() - 1];
    }

    /** the band of a climate temperature value (-1 to 1) */
    public static Band ofClimate(double t) {
        return t < -0.45 ? COLD : t < -0.15 ? COOL : t < 0.2 ? TEMPERATE : WARM;
    }

    /** the band of a biome's own temperature, used where the generator has no climate sampler; vanilla's values: snowy biomes
     *  below 0.15, taiga and windswept 0.2 to 0.3, river, ocean, forest and plains 0.5 to 0.8, jungle 0.95, desert 2 */
    public static Band ofBiomeTemperature(float t) {
        return t < 0.15F ? COLD : t < 0.45F ? COOL : t < 0.9F ? TEMPERATE : WARM;
    }

    /** the band of the lower end of a quantized climate span (Climate.Parameter min, times 10000) */
    public static Band ofSpanMin(long min) {
        return min < -4500 ? COLD : min < -1500 ? COOL : min < 2000 ? TEMPERATE : WARM;
    }

    /** the band of the upper end of a quantized climate span (its max is the next slice's min, so the comparison is inclusive) */
    public static Band ofSpanMax(long max) {
        return max <= -4500 ? COLD : max <= -1500 ? COOL : max <= 2000 ? TEMPERATE : WARM;
    }
}
