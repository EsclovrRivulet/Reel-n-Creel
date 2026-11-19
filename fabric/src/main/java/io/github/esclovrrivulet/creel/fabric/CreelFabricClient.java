package io.github.esclovrrivulet.creel.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import io.github.esclovrrivulet.creel.CreelModClient;
import io.github.esclovrrivulet.creel.client.RodBendProperty;
import io.github.esclovrrivulet.creel.client.RodDebug;

/** The Fabric client entry point: Fabric's client registries and events wired to the loader-neutral client parts (CreelModClient,
 *  client.RodDebug, client.RodBendProperty), in the order 0.17.1 ran them in. */
public class CreelFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // /creelline and /creelstage: the debug read-outs (client.RodDebug); Fabric's own client command source shows the feedback
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            dispatcher.register(ClientCommands.literal(RodDebug.LINE_COMMAND).executes(c -> {
                c.getSource().sendFeedback(RodDebug.toggleLine());
                return 1;
            }));
            dispatcher.register(ClientCommands.literal(RodDebug.STAGE_COMMAND).executes(c -> {
                c.getSource().sendFeedback(RodDebug.toggleStage());
                return 1;
            }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(RodDebug::onEndClientTick);
        RodBendProperty.register();   // 0.13.0: "creel:rod_bend", which picks the rods' bent held models (client.RodFlex)
        CreelModClient.registerModelLayers((layer, definition) -> ModelLayerRegistry.registerModelLayer(layer, definition::get));
        CreelModClient.registerRenderers(EntityRendererRegistry::register);
    }
}
