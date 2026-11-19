package io.github.esclovrrivulet.creel.fish;

import java.util.List;

/** How a body shape swims, as data, so a new shape needs a new rig and no new code. Every limb turns about one axis by
 *  amp * sin(freq * beat * age + phase), added to the angle the model gives the part at rest; limbs marked effort swing half as far
 *  again out of water, as vanilla's fish thrash. The whole fish yaws bodyYaw degrees against the beat, as vanilla's cod and salmon do.
 *  Plain data with no client classes, so the server can load the species table that holds it. */
public record SwimRig(float beat, float bodyYaw, List<Limb> limbs) {
    /** part: a path from the root, children after a slash (body_back/tail_fin); axis: 'x', 'y' or 'z' turns the part about that axis
     *  (amp in radians), 'X', 'Y' or 'Z' moves its pivot along that axis (amp in model px), for a segment that is not the child of
     *  the one before it but must follow its end (the alligator gar's back body). */
    public record Limb(String part, char axis, float amp, float freq, float phase, boolean effort) {}
}
