package io.github.esclovrrivulet.creel;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import io.github.esclovrrivulet.creel.client.FishLayers;
import io.github.esclovrrivulet.creel.client.FishModel;
import io.github.esclovrrivulet.creel.client.FishRenderer;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;

import java.util.function.Supplier;

/** The client's part of the mod, knowing no mod loader: what a loader's client part (fabric/: fabric.CreelFabricClient) registers
 *  with its own registries and events. Besides these: client.RodBendProperty.register() once at client start, before any resource is
 *  read (the rods' bent held models), and client.RodDebug for the debug read-outs (its client tick and its two client commands). */
public final class CreelModClient {
    private CreelModClient() {}

    /** gives a model layer and its definition */
    @FunctionalInterface
    public interface LayerSink {
        void register(ModelLayerLocation layer, Supplier<LayerDefinition> definition);
    }

    /** gives an entity type and the factory of its renderer */
    @FunctionalInterface
    public interface RendererSink {
        void register(EntityType<FishEntity> type, EntityRendererProvider<FishEntity> renderer);
    }

    /** every fish's model layer (its generated geometry, FishLayers), in the table's order */
    public static void registerModelLayers(LayerSink sink) {
        for (Species s : FishTable.ALL) {
            sink.register(FishModel.layer(s), () -> FishLayers.create(s.id()));
        }
    }

    /** every fish's renderer, in the table's order; needs CreelMod.registerEntityTypes first */
    public static void registerRenderers(RendererSink sink) {
        for (Species s : FishTable.ALL) {
            sink.register(CreelMod.FISH.get(s.id()), context -> new FishRenderer(context, s));
        }
    }
}
