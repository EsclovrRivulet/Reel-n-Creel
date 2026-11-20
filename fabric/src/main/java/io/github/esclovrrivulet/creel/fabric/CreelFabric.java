package io.github.esclovrrivulet.creel.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import io.github.esclovrrivulet.creel.CreelMod;

import java.lang.reflect.Method;

/** The Fabric entry point: Fabric's registries and events wired to the loader-neutral steps and event bodies in CreelMod (common/),
 *  in the order 0.17.1 ran them in, so ids, registration order and behaviour are those of the Fabric-only mod. */
public class CreelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CreelMod.registerBlocks((id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block));      // 0.22.0: Fish Spawn
        CreelMod.registerEntityTypes((id, type) -> Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type));
        for (CreelMod.SpawnPlacement p : CreelMod.spawnPlacements()) {
            registerSpawnPlacement(p);
        }
        CreelMod.attributes().forEach(FabricDefaultAttributeRegistry::register);
        CreelMod.registerItems((id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));

        ResourceLoader data = ResourceLoader.get(PackType.SERVER_DATA);
        CreelMod.dataReloadListeners().forEach(data::registerReloadListener);

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

        // Fabric's builder places a mod's tab among the pages; the tab is filled through Fabric's output, since vanilla's
        // CreativeModeTab.Output is not open to a mod's own displayItems
        CreelMod.registerCreativeTab(FabricCreativeModeTab.builder(), (id, tab) -> Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab));
        CreativeModeTabEvents.modifyOutputEvent(CreelMod.CREATIVE_TAB).register(output -> {
            for (ItemStack stack : CreelMod.creativeTabContents()) {
                output.accept(stack);
            }
        });
    }

    /** Vanilla's SpawnPlacements.register is private; Fabric widens it at run time only (its access widener), so javac, compiling
     *  against the plain game jar, cannot see it: called by reflection, as Fabric's own mob builder calls it. A failure stops the
     *  game's start, since a fish type without its placement would not spawn as it should. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerSpawnPlacement(CreelMod.SpawnPlacement p) {
        try {
            Method register = SpawnPlacements.class.getDeclaredMethod("register", EntityType.class, SpawnPlacementType.class,
                Heightmap.Types.class, SpawnPlacements.SpawnPredicate.class);
            register.setAccessible(true);
            register.invoke(null, p.type(), p.placementType(), p.heightmap(), p.predicate());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("creel: cannot register the spawn placement of " + EntityType.getKey(p.type()), e);
        }
    }
}
