package io.github.esclovrrivulet.creel.fish;

/** One fish as the game needs it, a row of the generated FishTable. width and height are the hitbox in blocks, already scaled;
 *  eyeHeight and shadow follow from them; parts is the model's part count (the root left out), which BakeCheck holds the model to;
 *  schools is false for a solitary fish (the manifest's "solitary"), which neither follows nor leads a school; health is the
 *  fish's full health in points, set by its size tier; tier is that size tier. Where it lives is data (habitat.FishHabitat). */
/* group: "salmon" for the fish of a salmon run, which share one spawn budget with vanilla's salmon (habitat.SizeClass);
 * "" for every other fish. */
public record Species(String id, String egg, float scale, float width, float height, float eyeHeight, float shadow, SwimRig rig, int parts,
                      boolean schools, float health, Tier tier, String group) {
    /** the texture, under assets/creel/ */
    public String texture() {
        return "textures/entity/fish/" + this.id + ".png";
    }
}
