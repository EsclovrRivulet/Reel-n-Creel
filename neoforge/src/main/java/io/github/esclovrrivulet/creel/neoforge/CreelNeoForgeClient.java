package io.github.esclovrrivulet.creel.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.common.NeoForge;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.CreelModClient;
import io.github.esclovrrivulet.creel.client.RodBendProperty;
import io.github.esclovrrivulet.creel.client.RodDebug;

/** The NeoForge client entry point (loaded by NeoForge on the client only): NeoForge's client events wired to the loader-neutral client
 *  parts (CreelModClient, client.RodDebug, client.RodBendProperty), as fabric.CreelFabricClient wires Fabric's. The rods' bend property
 *  goes through NeoForge's own event for range-select item model properties, under RodBendProperty's id and codec (its reflective
 *  register() is Fabric's way in and is not called here). */
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
        // 0.13.0: "creel:rod_bend", which picks the rods' bent held models (client.RodFlex)
        modBus.addListener((RegisterRangeSelectItemModelPropertyEvent e) -> e.register(RodBendProperty.ID, RodBendProperty.MAP_CODEC));
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) ->
            CreelModClient.registerModelLayers((layer, definition) -> e.registerLayerDefinition(layer, definition)));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
            CreelModClient.registerRenderers((type, renderer) -> e.registerEntityRenderer(type, renderer)));
    }
}
