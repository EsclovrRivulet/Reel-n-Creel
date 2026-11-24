package io.github.esclovrrivulet.creel.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.CreelModClient;
import io.github.esclovrrivulet.creel.client.HeldRodModels;
import io.github.esclovrrivulet.creel.client.RodDebug;
import io.github.esclovrrivulet.creel.client.SpawnItemProperty;

/** CreelFabricClient for MINECRAFT 1.21.1 (replaces fabric/.../CreelFabricClient.java in the 1.21.1 build): the fish's model layers and
 *  renderers and the debug read-outs (/creelline, /creelstage), as on 26.x, through 1.21.1's Fabric API. Where 26.x registers the rods'
 *  bend property for its item definitions, 1.21.1 registers the held root models and the two item properties their overrides choose by
 *  (client.HeldRodModels): the models through Fabric's model loading plugin, found again baked through Fabric's model manager helper.
 *  Two things 26.x needs no code for: the Fish Spawn's blended layer (26.x follows the texture's transparency; 1.21.1 draws a block
 *  solid unless told, as it is told for frogspawn), and the Fish Spawn item's species property (client.SpawnItemProperty). */
public class CreelFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // /creelline and /creelstage: the debug read-outs (client.RodDebug); Fabric's own client command source shows the feedback
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            dispatcher.register(ClientCommandManager.literal(RodDebug.LINE_COMMAND).executes(c -> {
                c.getSource().sendFeedback(RodDebug.toggleLine());
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal(RodDebug.STAGE_COMMAND).executes(c -> {
                c.getSource().sendFeedback(RodDebug.toggleStage());
                return 1;
            }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(RodDebug::onEndClientTick);
        // the rods held in 3D: their root models loaded, the properties their overrides read
        HeldRodModels.registerProperties(FabricModelPredicateProviderRegistry::register);
        ModelLoadingPlugin.register(context -> context.addModels(HeldRodModels.rootModels()));
        HeldRodModels.lookup(id -> ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(id));
        CreelModClient.registerModelLayers((layer, definition) -> EntityModelLayerRegistry.registerModelLayer(layer, definition::get));
        CreelModClient.registerRenderers(EntityRendererRegistry::register);
        // the Fish Spawn's layer: 1.21.1's tripwire layer, which blends as the translucent one does but drops texels under 10 percent
        // alpha and is drawn after it. Its fringe reaches over the water of the next block, and in the translucent layer its clear
        // texels wrote depth, so the water drawn after them (nearer the eye) was cut away there: a dark band along the near edge.
        // (26.x draws translucent blocks order-independent: no such band.) Only the faintest fringe texels (7 and 9 percent) are lost.
        BlockRenderLayerMap.INSTANCE.putBlock(CreelMod.FISH_SPAWN, RenderType.tripwire());
        FabricModelPredicateProviderRegistry.register(CreelMod.FISH_SPAWN_ITEM, SpawnItemProperty.ID,
            (stack, level, entity, seed) -> SpawnItemProperty.value(stack));
    }
}
