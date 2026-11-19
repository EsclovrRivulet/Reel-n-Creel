package io.github.esclovrrivulet.creel;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.ChatFormatting;
import io.github.esclovrrivulet.creel.fish.Tier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import io.github.esclovrrivulet.creel.habitat.CreelCommands;
import io.github.esclovrrivulet.creel.habitat.Habitat;
import io.github.esclovrrivulet.creel.habitat.HabitatData;
import io.github.esclovrrivulet.creel.habitat.Budgets;
import io.github.esclovrrivulet.creel.habitat.ClimateTable;
import io.github.esclovrrivulet.creel.habitat.FishSpawner;
import io.github.esclovrrivulet.creel.habitat.VanillaCaps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.food.FoodProperties;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Sizes;
import io.github.esclovrrivulet.creel.item.FishBucketItem;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import io.github.esclovrrivulet.creel.fishing.FishermanTrades;
import io.github.esclovrrivulet.creel.fishing.FishingData;
import io.github.esclovrrivulet.creel.fishing.RodTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.DispenserBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creel: fish built from the real animals in vanilla's own model language. Every fish comes from the generated FishTable
 *  (tools/gen_fish.py): one EntityType and one spawn egg each. They appear on their own in water that suits them (docs/HABITAT.md):
 *  all through the mod's own spawner (habitat.FishSpawner) under five budgets by size class (habitat.Budgets); vanilla's spawner and
 *  spawn lists are left as they are, apart from the owner's caps on natural cod and salmon (habitat.VanillaCaps). */
public class CreelMod implements ModInitializer {
    public static final String MOD_ID = "creel";
    /** every fish's type, by id, in the table's order */
    public static final Map<String, EntityType<FishEntity>> FISH = new LinkedHashMap<>();
    public static Item FISH_MORSEL, COOKED_FISH_MORSEL, RAW_FISH, FISH_STEAK;
    /** one bucket for every fish of ours (item.FishBucketItem) */
    public static Item FISH_BUCKET;
    /** the five rods (fishing.CastingRodItem), by id, lowest tier first */
    public static final Map<String, Item> RODS = new LinkedHashMap<>();

    @Override
    public void onInitialize() {
        List<Item> eggs = new ArrayList<>();
        for (Species s : FishTable.ALL) {
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, s.id());
            // every fish is MISC, outside vanilla's spawner and every vanilla cap; they come from FishSpawner under their own budgets
            // (docs/HABITAT.md section 4) and despawn as vanilla's fish do (FishEntity.checkDespawn)
            EntityType<FishEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                FabricEntityType.Builder.createMob(FishEntity::new, MobCategory.MISC,
                        b -> b.spawnPlacement(SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Habitat::canSpawn))
                    .sized(s.width(), s.height()).eyeHeight(s.eyeHeight())
                    .clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
            FabricDefaultAttributeRegistry.register(type, AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, s.health()));
            FISH.put(s.id(), type);
            Identifier egg = Identifier.fromNamespaceAndPath(MOD_ID, s.egg());
            eggs.add(Registry.register(BuiltInRegistries.ITEM, egg,
                new SpawnEggItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, egg)).spawnEgg(type))));
        }

        // sizes (docs/SIZES.md): the roll is data, data/creel/sizes.json
        Sizes.register();

        // one bucket for every fish: the species and size are in its data; a dispenser empties it as vanilla's fish buckets (their own behaviour, taken once vanilla's are in place)
        Identifier bucket = Identifier.fromNamespaceAndPath(MOD_ID, "fish_bucket");
        FISH_BUCKET = Registry.register(BuiltInRegistries.ITEM, bucket, new FishBucketItem(new Item.Properties().stacksTo(1)
            .component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).setId(ResourceKey.create(Registries.ITEM, bucket))));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            DispenseItemBehavior fishBucket = DispenserBlock.DISPENSER_REGISTRY.get(Items.COD_BUCKET);
            if (fishBucket != null) {
                DispenserBlock.registerBehavior(FISH_BUCKET, fishBucket);
            }
        });

        // the five rods (docs/FISHING.md; fishing.RodTable, generated): durability 64 to 192 by tier, vanilla's rod's enchantability; the
        // fishing enchantments come from the item tags data/minecraft/tags/item/enchantable/{fishing,durability}.json; what each bites
        // and how far it casts is data, data/creel/fishing.json
        for (RodTable.Rod r : RodTable.ALL) {
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, r.id());
            RODS.put(r.id(), Registry.register(BuiltInRegistries.ITEM, id, new CastingRodItem(r, new Item.Properties()
                .durability(r.durability()).enchantable(1)
                // the names do not say what a rod is for, so a grey line under the name does
                .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.creel." + r.id() + ".catches")
                    .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))))
                .setId(ResourceKey.create(Registries.ITEM, id)))));
        }
        FishingData.register();
        // (0.17.0) rewards: every fisherman buys our meat as two offers extra to vanilla's (added when a player interacts with him); the
        // treasure with a landed fish is rolled at the landing itself (fishing.CastingHook, fishing.Treasure)
        FishermanTrades.register();

        // where fish live (docs/HABITAT.md): habitat data, budgets, the spawner, the commands; vanilla's spawn lists are not touched,
        // and vanilla's fish only by the owner's caps on cod and salmon (VanillaCaps, applied by mixin.SpawnPlacementsMixin)
        HabitatData.register();
        Budgets.register();
        FishSpawner.register();
        VanillaCaps.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CreelCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Habitat.clearCaches();
            ClimateTable.clear();
        });

        // fish meat, named as in Don't Starve Together: raw fish and fish steak are vanilla cod's values;
        // the cooked morsel is eaten in 0.8 s, as dried kelp is (Consumables.DRIED_KELP)
        FISH_MORSEL = food("fish_morsel", new Item.Properties().food(food(1, 0.1F)));
        COOKED_FISH_MORSEL = food("cooked_fish_morsel", new Item.Properties().food(food(2, 0.6F), Consumables.DRIED_KELP));
        RAW_FISH = food("raw_fish", new Item.Properties().food(food(2, 0.1F)));
        FISH_STEAK = food("fish_steak", new Item.Properties().food(food(5, 0.6F)));

        // one creative tab of our own holding every item of the mod, and none of ours in vanilla's tabs (the search tab still finds
        // them): the four fish foods, a bucket of each fish (ordinary size), the spawn eggs, each in the manifest's order
        Identifier tab = Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.creel"))
            .icon(() -> FishBucketItem.of(FishTable.BY_ID.get("perch"), Size.ORDINARY, 1.0F))
            .build());
        // filled through Fabric's output, since vanilla's CreativeModeTab.Output is not open to a mod's own displayItems
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, tab))
            .register(output -> {
                for (Item rod : RODS.values()) {
                    output.accept(rod);
                }
                output.accept(FISH_MORSEL);
                output.accept(COOKED_FISH_MORSEL);
                output.accept(RAW_FISH);
                output.accept(FISH_STEAK);
                for (Species s : FishTable.ALL) {
                    output.accept(FishBucketItem.of(s, Size.ORDINARY, 1.0F));       // every fish has a bucket
                }
                for (Item egg : eggs) {
                    output.accept(egg);
                }
            });
    }

    private static FoodProperties food(int nutrition, float saturationModifier) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
    }

    private static Item food(String name, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        return Registry.register(BuiltInRegistries.ITEM, id, new Item(properties.setId(ResourceKey.create(Registries.ITEM, id))));
    }
}
