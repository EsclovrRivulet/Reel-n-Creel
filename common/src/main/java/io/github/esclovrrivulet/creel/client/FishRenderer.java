package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.compat.ClientCompat;

/** Any fish, as vanilla's cod renderer: the whole fish yaws against the tail's beat, and lies on its side out of water. The species
 *  gives the texture, the scale (the model is drawn as built and scaled about its feet) and the shadow. */
public class FishRenderer extends MobRenderer<FishEntity, LivingEntityRenderState, FishModel> {
    private final Species species;
    private final Identifier texture;

    public FishRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new FishModel(context.bakeLayer(FishModel.layer(species)), species.rig()), species.shadow());
        this.species = species;
        this.texture = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, species.texture());
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return this.texture;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void setupRotations(LivingEntityRenderState state, PoseStack pose, float bodyRot, float scale) {
        super.setupRotations(state, pose, bodyRot, scale);
        ClientCompat.rotateDegrees(pose, Axis.YP, this.species.rig().bodyYaw() * Mth.sin(this.species.rig().beat() * state.ageInTicks));
        if (!state.isInWater) {
            pose.translate(0.1F, 0.1F, -0.1F);
            ClientCompat.rotateDegrees(pose, Axis.ZP, 90.0F);
        }
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack pose) {
        float s = this.species.scale();
        pose.scale(s, s, s);
    }
}
