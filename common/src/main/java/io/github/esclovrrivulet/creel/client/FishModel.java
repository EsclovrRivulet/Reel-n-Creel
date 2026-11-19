package io.github.esclovrrivulet.creel.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.SwimRig;

/** Any fish: the geometry is the species' generated layer (FishLayers), the swim its body shape's rig (FishTable). The parts a rig
 *  moves are looked up once, here, so a rig that names a part the model lacks fails when the model is built (BakeCheck does that
 *  offline), never in the middle of a frame. */
public class FishModel extends EntityModel<LivingEntityRenderState> {
    private final SwimRig rig;
    private final ModelPart[] limbs;

    public FishModel(ModelPart root, SwimRig rig) {
        super(root);
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

    public static ModelLayerLocation layer(Species species) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, species.id()), "main");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);                                   // back to the rest pose the layer gives
        float effort = state.isInWater ? 1.0F : 1.5F;           // vanilla's fish thrash harder out of water
        float beat = this.rig.beat() * state.ageInTicks;
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
}
