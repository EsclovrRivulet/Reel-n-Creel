package io.github.esclovrrivulet.creel;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import io.github.esclovrrivulet.creel.client.FishLayers;
import io.github.esclovrrivulet.creel.client.FishModel;
import io.github.esclovrrivulet.creel.client.FishRenderer;
import io.github.esclovrrivulet.creel.client.RodBendProperty;
import io.github.esclovrrivulet.creel.client.RodDebug;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;

public class CreelModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RodDebug.register();          // /creelline: the fishing line's debug read-out
        RodBendProperty.register();   // 0.13.0: "creel:rod_bend", which picks the rods' bent held models (client.RodFlex)
        for (Species s : FishTable.ALL) {
            ModelLayerRegistry.registerModelLayer(FishModel.layer(s), () -> FishLayers.create(s.id()));
            EntityRendererRegistry.register(CreelMod.FISH.get(s.id()), context -> new FishRenderer(context, s));
        }
    }
}
