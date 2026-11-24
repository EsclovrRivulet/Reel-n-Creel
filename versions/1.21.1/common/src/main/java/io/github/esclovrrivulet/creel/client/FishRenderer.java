package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.Species;

/** FishRenderer for MINECRAFT 1.21.1 (replaces common/.../client/FishRenderer.java in the 1.21.1 build): the same renderer in 1.21.1's
 *  renderer class, which draws from the entity itself (26.x: from a render state). The turn is FishSwim's, as on 26.x; ageInTicks is
 *  1.21.1's "bob" (the entity's ticks and the partial tick, what 26.x's render state holds). */
public class FishRenderer extends MobRenderer<FishEntity, FishModel> {
    private final Species species;
    private final ResourceLocation texture;

    public FishRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new FishModel(context.bakeLayer(FishModel.layer(species)), species.rig()), species.shadow());
        this.species = species;
        this.texture = ResourceLocation.fromNamespaceAndPath(CreelMod.MOD_ID, species.texture());
    }

    @Override
    public ResourceLocation getTextureLocation(FishEntity fish) {
        return this.texture;
    }

    @Override
    protected void setupRotations(FishEntity fish, PoseStack pose, float ageInTicks, float bodyRot, float partialTick, float scale) {
        super.setupRotations(fish, pose, ageInTicks, bodyRot, partialTick, scale);
        FishSwim.turn(pose, this.species.rig(), ageInTicks, fish.isInWater());
    }

    @Override
    protected void scale(FishEntity fish, PoseStack pose, float partialTick) {
        float s = this.species.scale();
        pose.scale(s, s, s);
    }
}
