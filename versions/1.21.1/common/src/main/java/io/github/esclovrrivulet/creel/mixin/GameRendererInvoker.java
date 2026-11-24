package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (1.21.1 build only, client) The game renderer's own field of view and view bobbing, private in 1.21.1, for client.RodView (where
 *  the line leaves the rod in first person). 26.x reaches its versions of these by reflection under Mojang's names; Fabric runs
 *  1.21.1 with its own names, so here invokers, which Loom's remapping and NeoForge's Mojang names both resolve. Invokers only: they
 *  call the game's methods, nothing of the game is changed. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("getFov")
    double creel$getFov(Camera camera, float partialTick, boolean useFovSetting);

    @Invoker("bobHurt")
    void creel$bobHurt(PoseStack poseStack, float partialTick);

    @Invoker("bobView")
    void creel$bobView(PoseStack poseStack, float partialTick);
}
