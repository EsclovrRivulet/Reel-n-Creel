package io.github.esclovrrivulet.creel.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.SwimRig;

/** Any fish: the geometry is the species' generated layer (FishLayers), the swim its body shape's rig (FishTable), moved by FishSwim,
 *  which looks the rig's parts up once, here, when the model is built. */
public class FishModel extends EntityModel<LivingEntityRenderState> {
    private final FishSwim swim;

    public FishModel(ModelPart root, SwimRig rig) {
        super(root);
        this.swim = new FishSwim(root, rig);
    }

    public static ModelLayerLocation layer(Species species) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, species.id()), "main");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);                                   // back to the rest pose the layer gives
        this.swim.swim(state.isInWater, state.ageInTicks);
    }
}
