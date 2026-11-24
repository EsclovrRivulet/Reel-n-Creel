package io.github.esclovrrivulet.creel;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import io.github.esclovrrivulet.creel.habitat.CreelCommands;
import io.github.esclovrrivulet.creel.habitat.Habitat;
import io.github.esclovrrivulet.creel.habitat.HabitatData;
import io.github.esclovrrivulet.creel.habitat.Budgets;
import io.github.esclovrrivulet.creel.habitat.ClimateTable;
import io.github.esclovrrivulet.creel.habitat.FishSpawner;
import io.github.esclovrrivulet.creel.habitat.VanillaCaps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Sizes;
import io.github.esclovrrivulet.creel.item.FishBucketItem;
import io.github.esclovrrivulet.creel.item.FishSpawnItem;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import io.github.esclovrrivulet.creel.fishing.FishermanTrades;
import io.github.esclovrrivulet.creel.fishing.FishingData;
import io.github.esclovrrivulet.creel.fishing.RodTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import io.github.esclovrrivulet.creel.block.FishSpawnBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.esclovrrivulet.creel.compat.Compat;

/** Creel: fish built from the real animals in vanilla's own model language. Every fish comes from the generated FishTable
 *  (tools/gen_fish.py): one EntityType and one spawn egg each. They appear on their own in water that suits them (docs/HABITAT.md):
 *  all through the mod's own spawner (habitat.FishSpawner) under five budgets by size class (habitat.Budgets); vanilla's spawner and
 *  spawn lists are left as they are, apart from the owner's caps on natural cod and salmon (habitat.VanillaCaps).
 *
 *  This class knows no mod loader. A loader's own part (fabric/: fabric.CreelFabric) calls the steps below at the moments its loader
 *  allows: the registration steps each take a small Registrar for the actual "register this id"; the rest are plain accessors (the
 *  spawn placements, the attributes, the creative tab's contents, the data reload listeners) and plain event bodies (onServerStarting
 *  and the others) that the loader part wires to its own events. registerEntityTypes must run before registerItems (the spawn eggs
 *  need the types), and both before the accessors that read FISH or the items; registerBlocks (0.22.0) runs first of all, as the
 *  game's own registries are filled (blocks before entity types and items). */
public final class CreelMod {
    private CreelMod() {}

    public static final String MOD_ID = "creel";
    /** every fish's type, by id, in the table's order */
    public static final Map<String, EntityType<FishEntity>> FISH = new LinkedHashMap<>();
    public static Item FISH_MORSEL, COOKED_FISH_MORSEL, RAW_FISH, FISH_STEAK;
    /** one bucket for every fish of ours (item.FishBucketItem) */
    public static Item FISH_BUCKET;
    /** the rods (fishing.CastingRodItem; five, 0.21.0 six with the Ol' Mariner), by id, lowest tier first */
    public static final Map<String, Item> RODS = new LinkedHashMap<>();
    /** (0.22.0) the Fish Spawn block, creel:fish_spawn (docs/SPAWN.md) */
    public static FishSpawnBlock FISH_SPAWN;
    /** (0.23.0) its one item, for creative mode (item.FishSpawnItem): the species in its block_state component */
    public static Item FISH_SPAWN_ITEM;
    /** every fish's spawn egg, in the table's order */
    public static final List<Item> EGGS = new ArrayList<>();
    /** the mod's one creative tab, creel:creel */
    public static final ResourceKey<CreativeModeTab> CREATIVE_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID));

    /** registers one object under an id in the registry the step is for; the loader part supplies it */
    @FunctionalInterface
    public interface Registrar<T> {
        void register(Identifier id, T value);
    }

    /** where and how a fish type may be placed by a spawn (vanilla's SpawnPlacements entry for it) */
    public record SpawnPlacement(EntityType<FishEntity> type, SpawnPlacementType placementType, Heightmap.Types heightmap,
                                 SpawnPlacements.SpawnPredicate<FishEntity> predicate) {}

    /** the item step's registrar while registerItems runs (food(...) registers through it) */
    private static Registrar<Item> items;

    /** (0.22.0) The mod's one block, Fish Spawn (block.FishSpawnBlock, docs/SPAWN.md), with frogspawn's properties as vanilla's Blocks
     *  gives them (the 26.3 jar): the water's map colour, broken at once, no occlusion, no collision, frogspawn's sounds, popped by a
     *  piston. Its loot table (data/creel/loot_table/blocks/fish_spawn.json) drops nothing, as frogspawn's; its one item (0.23.0, for
     *  creative mode) is registered with the other items. */
    public static void registerBlocks(Registrar<Block> registrar) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "fish_spawn");
        FISH_SPAWN = new FishSpawnBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))
            .mapColor(MapColor.WATER).instabreak().noOcclusion().noCollision().sound(SoundType.FROGSPAWN).pushReaction(Compat.POPPED));
        registrar.register(id, FISH_SPAWN);
    }

    /** Every fish's EntityType, in the table's order (filling FISH). Every fish is MISC, outside vanilla's spawner and every vanilla
     *  cap; they come from FishSpawner under their own budgets (docs/HABITAT.md section 4) and despawn as vanilla's fish do
     *  (FishEntity.checkDespawn). Their spawn placements and attributes are given by spawnPlacements() and attributes(). */
    public static void registerEntityTypes(Registrar<EntityType<?>> registrar) {
        for (Species s : FishTable.ALL) {
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, s.id());
            EntityType<FishEntity> type = EntityType.Builder.of(FishEntity::new, MobCategory.MISC)
                .sized(s.width(), s.height()).eyeHeight(s.eyeHeight())
                .clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, id));
            registrar.register(id, type);
            FISH.put(s.id(), type);
        }
    }

    /** each fish type's spawn placement, in the table's order: in water, by Habitat.canSpawn */
    public static List<SpawnPlacement> spawnPlacements() {
        List<SpawnPlacement> out = new ArrayList<>();
        for (EntityType<FishEntity> type : FISH.values()) {
            out.add(new SpawnPlacement(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Habitat::canSpawn));
        }
        return out;
    }

    /** each fish type's default attributes (a new builder each call), in the table's order: vanilla's fish, with the species' health */
    public static Map<EntityType<FishEntity>, AttributeSupplier.Builder> attributes() {
        Map<EntityType<FishEntity>, AttributeSupplier.Builder> out = new LinkedHashMap<>();
        for (Species s : FishTable.ALL) {
            out.put(FISH.get(s.id()), AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, s.health()));
        }
        return out;
    }

    /** Every item of the mod, in this order: the spawn eggs (the table's order), the fish bucket, the rods (0.21.0: six), the four fish foods,
     *  the Fish Spawn's item (0.23.0). Needs registerBlocks and registerEntityTypes first. */
    public static void registerItems(Registrar<Item> registrar) {
        items = registrar;
        for (Species s : FishTable.ALL) {
            Identifier egg = Identifier.fromNamespaceAndPath(MOD_ID, s.egg());
            Item item = Compat.spawnEgg(FISH.get(s.id()), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, egg)));
            registrar.register(egg, item);
            EGGS.add(item);
        }

        // one bucket for every fish: the species and size are in its data; a dispenser empties it as vanilla's fish buckets (their own
        // behaviour, taken once vanilla's are in place: onServerStarting)
        Identifier bucket = Identifier.fromNamespaceAndPath(MOD_ID, "fish_bucket");
        FISH_BUCKET = new FishBucketItem(new Item.Properties().stacksTo(1)
            .component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).setId(ResourceKey.create(Registries.ITEM, bucket)));
        registrar.register(bucket, FISH_BUCKET);

        // the rods (docs/FISHING.md; fishing.RodTable, generated): durability 64 to 192 by tier (0.21.0: the Ol' Mariner, tier 6, 224),
        // vanilla's rod's enchantability; the
        // fishing enchantments come from the item tags data/minecraft/tags/item/enchantable/{fishing,durability}.json; what each bites
        // and how far it casts is data, data/creel/fishing.json
        for (RodTable.Rod r : RodTable.ALL) {
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, r.id());
            Item rod = new CastingRodItem(r, new Item.Properties()
                .durability(r.durability()).enchantable(1)
                // the names do not say what a rod is for, so a grey line under the name does
                .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.creel." + r.id() + ".catches")
                    .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))))
                .setId(ResourceKey.create(Registries.ITEM, id)));
            registrar.register(id, rod);
            RODS.put(r.id(), rod);
        }

        // fish meat, named as in Don't Starve Together: raw fish and fish steak are vanilla cod's values;
        // the cooked morsel is eaten in 0.8 s, as dried kelp is (Compat.quickFood)
        FISH_MORSEL = food("fish_morsel", new Item.Properties().food(food(1, 0.1F)));
        COOKED_FISH_MORSEL = food("cooked_fish_morsel", Compat.quickFood(new Item.Properties(), food(2, 0.6F)));
        RAW_FISH = food("raw_fish", new Item.Properties().food(food(2, 0.1F)));
        FISH_STEAK = food("fish_steak", new Item.Properties().food(food(5, 0.6F)));

        // (0.23.0) one item for Fish Spawn, made as vanilla's Items makes frogspawn's (Items.registerBlock with PlaceOnWaterBlockItem:
        // the block's name, its feature flags, and the block-to-item map vanilla's registerItem fills; a loader that fills that map
        // on registration has already done it, so it is put only when missing)
        Identifier spawn = Identifier.fromNamespaceAndPath(MOD_ID, "fish_spawn");
        FishSpawnItem spawnItem = new FishSpawnItem(FISH_SPAWN, Compat.blockItem(new Item.Properties(), FISH_SPAWN)
            .setId(ResourceKey.create(Registries.ITEM, spawn)));
        registrar.register(spawn, spawnItem);
        if (Item.BY_BLOCK.get(FISH_SPAWN) != spawnItem) {
            spawnItem.registerBlocks(Item.BY_BLOCK, spawnItem);
        }
        FISH_SPAWN_ITEM = spawnItem;
        items = null;
    }

    /** One creative tab of our own holding every item of the mod, and none of ours in vanilla's tabs (the search tab still finds them),
     *  registered under CREATIVE_TAB's id. The loader part gives its own builder (one that places a mod's tab); the contents come from
     *  creativeTabContents(), through the loader's own way of filling a tab (vanilla's CreativeModeTab.Output is not open to a mod). */
    public static void registerCreativeTab(CreativeModeTab.Builder builder, Registrar<CreativeModeTab> registrar) {
        registrar.register(CREATIVE_TAB.identifier(), builder
            .title(Component.translatable("itemGroup.creel"))
            .icon(() -> FishBucketItem.of(FishTable.BY_ID.get("perch"), Size.ORDINARY, 1.0F))
            .build());
    }

    /** the creative tab's contents in order (new stacks each call): the rods (0.21.0: six), the four fish foods, a bucket of each fish (ordinary
     *  size), (0.23.0) the Fish Spawn of each fish that breeds, the spawn eggs, each in the manifest's order */
    public static List<ItemStack> creativeTabContents() {
        List<ItemStack> out = new ArrayList<>();
        for (Item rod : RODS.values()) {
            out.add(new ItemStack(rod));
        }
        out.add(new ItemStack(FISH_MORSEL));
        out.add(new ItemStack(COOKED_FISH_MORSEL));
        out.add(new ItemStack(RAW_FISH));
        out.add(new ItemStack(FISH_STEAK));
        for (Species s : FishTable.ALL) {
            out.add(FishBucketItem.of(s, Size.ORDINARY, 1.0F));       // every fish has a bucket
        }
        for (Species s : FishTable.ALL) {
            if (s.breeds()) {
                out.add(FishSpawnItem.of(s.id()));                    // one item, a stack per species (not one item each)
            }
        }
        for (Item egg : EGGS) {
            out.add(new ItemStack(egg));
        }
        return out;
    }

    /** The mod's server data reload listeners, by the id each is registered under, in the order they are registered: sizes
     *  (docs/SIZES.md, data/creel/sizes.json), the rods' fishing numbers (data/creel/fishing.json), where fish live (docs/HABITAT.md:
     *  the habitat files and the budgets), the owner's caps on vanilla's cod and salmon (VanillaCaps, applied by
     *  mixin.SpawnPlacementsMixin). Vanilla's spawn lists are not touched. */
    public static Map<Identifier, PreparableReloadListener> dataReloadListeners() {
        Map<Identifier, PreparableReloadListener> out = new LinkedHashMap<>();
        out.put(Sizes.RELOAD_ID, Sizes.RELOADER);
        out.put(FishingData.RELOAD_ID, FishingData.RELOADER);
        out.put(HabitatData.RELOAD_ID, HabitatData.RELOADER);
        out.put(Budgets.RELOAD_ID, Budgets.RELOADER);
        out.put(VanillaCaps.RELOAD_ID, VanillaCaps.RELOADER);
        return out;
    }

    /** as the server starts, once vanilla's dispenser behaviours are in place: our bucket is emptied by a dispenser as vanilla's fish
     *  buckets are, with their own behaviour */
    public static void onServerStarting(MinecraftServer server) {
        DispenseItemBehavior fishBucket = DispenserBlock.DISPENSER_REGISTRY.get(Items.COD_BUCKET);
        if (fishBucket != null) {
            DispenserBlock.registerBehavior(FISH_BUCKET, fishBucket);
        }
    }

    /** at the end of every server tick: the mod's own fish spawner */
    public static void onEndServerTick(MinecraftServer server) {
        FishSpawner.tick(server);
    }

    /** an entity was loaded into a server level (the vanilla caps' counts) */
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        VanillaCaps.onEntityLoad(entity, level);
    }

    /** an entity was unloaded from a server level (the vanilla caps' counts) */
    public static void onEntityUnload(Entity entity, ServerLevel level) {
        VanillaCaps.onEntityUnload(entity, level);
    }

    /** after a data pack reload (/reload) has finished, successful or not: the vanilla caps' counts are made again */
    public static void onDataPackReloaded(MinecraftServer server) {
        VanillaCaps.recount(server);
    }

    /** when the server has stopped: the vanilla caps' counts, then the habitat's caches and the climate table, are cleared */
    public static void onServerStopped(MinecraftServer server) {
        VanillaCaps.clearCounts();
        Habitat.clearCaches();
        ClimateTable.clear();
    }

    /** the server commands (/creel ...) */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        CreelCommands.register(dispatcher);
    }

    /** (0.17.0) rewards: a player uses (right-clicks) an entity, on either side, before the entity's own interaction runs; never takes
     *  the interaction (the loader part lets it pass). Every fisherman buys our meat as two offers extra to vanilla's (added here); the
     *  treasure with a landed fish is rolled at the landing itself (fishing.CastingHook, fishing.Treasure) */
    public static void onUseEntity(Player player, Level level, Entity entity) {
        FishermanTrades.onUseEntity(player, level, entity);
    }

    private static FoodProperties food(int nutrition, float saturationModifier) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
    }

    private static Item food(String name, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        Item item = new Item(properties.setId(ResourceKey.create(Registries.ITEM, id)));
        items.register(id, item);
        return item;
    }
}
