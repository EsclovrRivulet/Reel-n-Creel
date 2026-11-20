package io.github.esclovrrivulet.creel.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import io.github.esclovrrivulet.creel.CreelMod;

/** The NeoForge entry point: NeoForge's registry events and game events wired to the loader-neutral steps and event bodies in CreelMod
 *  (common/), as fabric.CreelFabric wires Fabric's, so ids, registration order and behaviour are those of the Fabric build.
 *
 *  Registration: NeoForge fires one RegisterEvent per registry, blocks first (0.22.0: Fish Spawn), entity types before items (vanilla's own order of its registries), so
 *  the spawn eggs find their types; the creative tab's registry comes later still. Nothing here refers to a client class: the client's
 *  part is CreelNeoForgeClient, which NeoForge loads only on the client.
 *
 *  Entities loaded into and unloaded from a server level (the vanilla caps' counts) are not taken from NeoForge's join and leave
 *  events, which fire at different moments than Fabric's (an entity joins once when added, but is re-tracked without a new join when its
 *  chunk comes back into range): mixin.ServerLevelEntityCallbacksMixin calls them where Fabric's own events fire. */
@Mod(CreelMod.MOD_ID)
public class CreelNeoForge {
    public CreelNeoForge(IEventBus modBus) {
        modBus.addListener(CreelNeoForge::register);
        modBus.addListener(CreelNeoForge::spawnPlacements);
        modBus.addListener(CreelNeoForge::attributes);
        modBus.addListener(CreelNeoForge::creativeTabContents);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((AddServerReloadListenersEvent e) -> CreelMod.dataReloadListeners().forEach(e::addListener));
        game.addListener((ServerStartingEvent e) -> CreelMod.onServerStarting(e.getServer()));
        // a player uses an entity, before the entity's own interaction; never cancelled here, so vanilla's own runs on
        game.addListener((PlayerInteractEvent.EntityInteract e) -> CreelMod.onUseEntity(e.getEntity(), e.getLevel(), e.getTarget()));
        game.addListener((ServerTickEvent.Post e) -> CreelMod.onEndServerTick(e.getServer()));
        // a /reload has applied (the new data is in place and is being sent to the players): NeoForge fires this with no player only then;
        // with a player, it is that player joining, which is not a reload
        game.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) {
                CreelMod.onDataPackReloaded(e.getPlayerList().getServer());
            }
        });
        game.addListener((ServerStoppedEvent e) -> CreelMod.onServerStopped(e.getServer()));
        game.addListener((RegisterCommandsEvent e) -> CreelMod.registerCommands(e.getDispatcher()));
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> CreelMod.registerBlocks(helper::register));      // 0.22.0: Fish Spawn (blocks come first)
        event.register(Registries.ENTITY_TYPE, helper -> CreelMod.registerEntityTypes(helper::register));
        event.register(Registries.ITEM, helper -> {
            if (CreelMod.FISH.isEmpty()) {
                throw new IllegalStateException("creel: the items were to be registered before the entity types (the spawn eggs need them)");
            }
            CreelMod.registerItems(helper::register);
        });
        event.register(Registries.CREATIVE_MODE_TAB, helper -> CreelMod.registerCreativeTab(CreativeModeTab.builder(), helper::register));
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (CreelMod.SpawnPlacement p : CreelMod.spawnPlacements()) {
            event.register(p.type(), p.placementType(), p.heightmap(), p.predicate(), RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        CreelMod.attributes().forEach((type, builder) -> event.put(type, builder.build()));
    }

    private static void creativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreelMod.CREATIVE_TAB) {
            for (ItemStack stack : CreelMod.creativeTabContents()) {
                event.accept(stack);
            }
        }
    }

    /** for the mixin: an entity began to be tracked in a server level */
    public static void onTrackingStart(net.minecraft.world.entity.Entity entity, ServerLevel level) {
        CreelMod.onEntityLoad(entity, level);
    }

    /** for the mixin: an entity stops being tracked in a server level */
    public static void onTrackingEnd(net.minecraft.world.entity.Entity entity, ServerLevel level) {
        CreelMod.onEntityUnload(entity, level);
    }
}
