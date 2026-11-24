package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import io.github.esclovrrivulet.creel.compat.ClientCompat;
import io.github.esclovrrivulet.creel.fish.SwimRig;

/** How a fish swims as it is drawn, for FishModel and FishRenderer: the parts its rig moves (looked up once, when the model is built,
 *  so a rig that names a part the model lacks fails then, as BakeCheck checks offline, never in the middle of a frame), their angles
 *  each frame, and the whole fish's yaw against the beat. (1.21.1) Apart from FishModel and FishRenderer because the 1.21.1 build has
 *  its own two (the game's older model and renderer classes, versions/1.21.1/), which swim by this same code. */
public final class FishSwim {
    private final SwimRig rig;
    private final ModelPart[] limbs;

    public FishSwim(ModelPart root, SwimRig rig) {
        this.rig = rig;
        this.limbs = new ModelPart[rig.limbs().size()];
        for (int i = 0; i < this.limbs.length; i++) {
            ModelPart part = root;
            for (String name : rig.limbs().get(i).part().split("/")) {
                part = part.getChild(name);
            }
            this.limbs[i] = part;
        }
    }

    /** each limb's swing for this frame, added to the rest pose the layer gives (the caller has put the parts back to it) */
    public void swim(boolean inWater, float ageInTicks) {
        float effort = inWater ? 1.0F : 1.5F;                     // vanilla's fish thrash harder out of water
        float beat = this.rig.beat() * ageInTicks;
        for (int i = 0; i < this.limbs.length; i++) {
            SwimRig.Limb limb = this.rig.limbs().get(i);
            float angle = (limb.effort() ? effort : 1.0F) * limb.amp() * Mth.sin(limb.freq() * beat + limb.phase());
            ModelPart part = this.limbs[i];
            switch (limb.axis()) {
                case 'x' -> part.xRot += angle;
                case 'y' -> part.yRot += angle;
                case 'z' -> part.zRot += angle;
                case 'X' -> part.x += angle;              // a shift of the pivot in px (SwimRig.Limb)
                case 'Y' -> part.y += angle;
                case 'Z' -> part.z += angle;
                default -> { }
            }
        }
    }

    /** As vanilla's cod renderer turns its fish, after the game's own body rotation: the whole fish yaws against the tail's beat, and
     *  lies on its side out of water. */
    public static void turn(PoseStack pose, SwimRig rig, float ageInTicks, boolean inWater) {
        ClientCompat.rotateDegrees(pose, Axis.YP, rig.bodyYaw() * Mth.sin(rig.beat() * ageInTicks));
        if (!inWater) {
            pose.translate(0.1F, 0.1F, -0.1F);
            ClientCompat.rotateDegrees(pose, Axis.ZP, 90.0F);
        }
    }
}
