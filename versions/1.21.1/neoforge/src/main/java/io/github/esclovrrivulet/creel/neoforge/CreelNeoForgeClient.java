package io.github.esclovrrivulet.creel.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.CreelModClient;
import io.github.esclovrrivulet.creel.client.HeldRodModels;
import io.github.esclovrrivulet.creel.client.RodDebug;
import io.github.esclovrrivulet.creel.client.SpawnItemProperty;

/** CreelNeoForgeClient for MINECRAFT 1.21.1 (replaces neoforge/.../CreelNeoForgeClient.java in the 1.21.1 build): the fish's model
 *  layers and renderers and the debug read-outs (/creelline, /creelstage), as on 26.x. Where 26.x registers the rods' bend property
 *  for its item definitions, 1.21.1 registers the held root models (NeoForge's additional models, found again baked under their
 *  "standalone" name) and the two item properties their overrides choose by (client.HeldRodModels). Set once at client setup
 *  (NeoForge's way, on the main thread), as 26.x needs no code for: the Fish Spawn's blended layer (1.21.1 draws a block solid
 *  unless told, as it is told for frogspawn), and the item properties (the Fish Spawn item's species, client.SpawnItemProperty, and
 *  the rods' cast and bend). */
@Mod(value = CreelMod.MOD_ID, dist = Dist.CLIENT)
public class CreelNeoForgeClient {
    public CreelNeoForgeClient(IEventBus modBus) {
        // /creelline and /creelstage: the debug read-outs (client.RodDebug). The toggle runs first, whatever the source does with feedback.
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> {
            e.getDispatcher().register(Commands.literal(RodDebug.LINE_COMMAND).executes(c -> {
                Component msg = RodDebug.toggleLine();
                c.getSource().sendSuccess(() -> msg, false);
                return 1;
            }));
            e.getDispatcher().register(Commands.literal(RodDebug.STAGE_COMMAND).executes(c -> {
                Component msg = RodDebug.toggleStage();
                c.getSource().sendSuccess(() -> msg, false);
                return 1;
            }));
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> RodDebug.onEndClientTick(Minecraft.getInstance()));
        // the rods held in 3D: their root models loaded and found again baked
        modBus.addListener((ModelEvent.RegisterAdditional e) ->
            HeldRodModels.rootModels().forEach(id -> e.register(ModelResourceLocation.standalone(id))));
        HeldRodModels.lookup(id -> Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id)));
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) ->
            CreelModClient.registerModelLayers((layer, definition) -> e.registerLayerDefinition(layer, definition)));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
            CreelModClient.registerRenderers((type, renderer) -> e.registerEntityRenderer(type, renderer)));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            // the Fish Spawn's layer: 1.21.1's tripwire layer, which blends as the translucent one does but drops texels under 10 percent
            // alpha and is drawn after it. Its fringe reaches over the water of the next block, and in the translucent layer its clear
            // texels wrote depth, so the water drawn after them (nearer the eye) was cut away there: a dark band along the near edge.
            // (26.x draws translucent blocks order-independent: no such band.) Only the faintest fringe texels (7 and 9 percent) are lost.
            ItemBlockRenderTypes.setRenderLayer(CreelMod.FISH_SPAWN, RenderType.tripwire());
            ItemProperties.register(CreelMod.FISH_SPAWN_ITEM, SpawnItemProperty.ID, (stack, level, entity, seed) -> SpawnItemProperty.value(stack));
            HeldRodModels.registerProperties(ItemProperties::register);
        }));
    }
}
