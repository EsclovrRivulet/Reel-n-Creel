package io.github.esclovrrivulet.creel.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.SwimRig;

/** FishModel for MINECRAFT 1.21.1 (replaces common/.../client/FishModel.java in the 1.21.1 build): the same model in 1.21.1's model
 *  class, which is posed from the entity itself (26.x: from a render state). The swim is FishSwim's, as on 26.x. */
public class FishModel extends HierarchicalModel<FishEntity> {
    private final ModelPart root;
    private final FishSwim swim;

    public FishModel(ModelPart root, SwimRig rig) {
        this.root = root;
        this.swim = new FishSwim(root, rig);
    }

    public static ModelLayerLocation layer(Species species) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CreelMod.MOD_ID, species.id()), "main");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(FishEntity fish, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);    // back to the rest pose the layer gives (26.x's setupAnim does it)
        this.swim.swim(fish.isInWater(), ageInTicks);
    }
}
