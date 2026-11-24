package io.github.esclovrrivulet.creel.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.mixin.SpawnPlacementsAccessor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** CreelFabric for MINECRAFT 1.21.1 (replaces fabric/.../CreelFabric.java in the 1.21.1 build): the same steps in the same order,
 *  through 1.21.1's Fabric API. Two differ: the data reload listeners go through Fabric's ResourceManagerHelper, each under its id (the
 *  26.x build's ResourceLoader is newer), and the spawn placements through an invoker of vanilla's private SpawnPlacements.register
 *  (mixin.SpawnPlacementsAccessor), since Fabric runs 1.21.1 with its own names and the 26.x build's reflection by Mojang's name would
 *  find nothing. */
public class CreelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CreelMod.registerBlocks((id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block));      // 0.22.0: Fish Spawn
        CreelMod.registerEntityTypes((id, type) -> Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type));
        for (CreelMod.SpawnPlacement p : CreelMod.spawnPlacements()) {
            SpawnPlacementsAccessor.creel$register(p.type(), p.placementType(), p.heightmap(), p.predicate());
        }
        CreelMod.attributes().forEach(FabricDefaultAttributeRegistry::register);
        CreelMod.registerItems((id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));

        ResourceManagerHelper data = ResourceManagerHelper.get(PackType.SERVER_DATA);
        for (Map.Entry<ResourceLocation, PreparableReloadListener> e : CreelMod.dataReloadListeners().entrySet()) {
            data.registerReloadListener(withId(e.getKey(), e.getValue()));
        }

        ServerLifecycleEvents.SERVER_STARTING.register(CreelMod::onServerStarting);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            CreelMod.onUseEntity(player, level, entity);
            return InteractionResult.PASS;          // never takes the interaction: vanilla's own runs on
        });
        ServerTickEvents.END_SERVER_TICK.register(CreelMod::onEndServerTick);
        ServerEntityEvents.ENTITY_LOAD.register(CreelMod::onEntityLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(CreelMod::onEntityUnload);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> CreelMod.onDataPackReloaded(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(CreelMod::onServerStopped);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CreelMod.registerCommands(dispatcher));

        // Fabric's builder places a mod's tab among the pages; the tab is filled through Fabric's entries, since vanilla's
        // CreativeModeTab.Output is not open to a mod's own displayItems
        CreelMod.registerCreativeTab(FabricItemGroup.builder(), (id, tab) -> Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab));
        ItemGroupEvents.modifyEntriesEvent(CreelMod.CREATIVE_TAB).register(entries -> {
            for (ItemStack stack : CreelMod.creativeTabContents()) {
                entries.accept(stack);
            }
        });
    }

    /** one of our listeners as Fabric's ResourceManagerHelper takes it: under its id, the work its own */
    private static IdentifiableResourceReloadListener withId(ResourceLocation id, PreparableReloadListener listener) {
        return new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepareProfiler,
                                                  ProfilerFiller applyProfiler, Executor prepareExecutor, Executor applyExecutor) {
                return listener.reload(barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
            }

            @Override
            public String getName() {
                return listener.getName();
            }
        };
    }
}
