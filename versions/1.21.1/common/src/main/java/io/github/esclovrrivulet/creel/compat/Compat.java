package io.github.esclovrrivulet.creel.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.esclovrrivulet.creel.mixin.MultiNoiseBiomeSourceAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Compat for MINECRAFT 1.21.1: replaces common/.../compat/Compat.java (26.3's) in the 1.21.1 build. The same members, each one line of
 *  1.21.1's own API (or, where 1.21.1 has no single call for it, the few lines of the game's own code that do the same); see the base
 *  copy for what each one is. The names the main tree uses for 26.x's classes are 1.21.1's in this build (EntitySpawnReason is
 *  MobSpawnType, versions/1.21.1/java_renames.txt). */
public final class Compat {
    private Compat() {}

    /** 1.21.1 keeps the vanilla entity types on EntityType itself (26.2 and 26.3: EntityTypes) */
    public static final EntityType<Salmon> SALMON = EntityType.SALMON;
    public static final EntityType<FallingBlockEntity> FALLING_BLOCK = EntityType.FALLING_BLOCK;

    /** 1.21.1 names frogspawn's reaction DESTROY (26.3: POPPED) */
    public static final PushReaction POPPED = PushReaction.DESTROY;

    /** 1.21.1's random state holds one sampler (26.3: createClimateSampler(SamplerContext.EMPTY_UNCACHED)) */
    public static Climate.Sampler climateSampler(RandomState state) {
        return state.sampler();
    }

    /** 1.21.1's density functions are computed at a point context (26.3: sampleValue(x, y, z)) */
    public static double temperature(Climate.Sampler sampler, BlockPos pos) {
        return sampler.temperature().compute(new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** 1.21.1 has one swing animation, no argument for it */
    public static void swing(Player player, InteractionHand hand) {
        player.swing(hand, true);
    }

    /** 1.21.1 makes the changeable contents with a constructor (26.3: asMutable()) */
    public static BundleContents.Mutable mutable(BundleContents contents) {
        return new BundleContents.Mutable(contents);
    }

    /** 1.21.1's Bucketable is in world.entity.animal (26.2 and 26.3: world.entity) */
    public static <T extends LivingEntity & Bucketable> boolean bucketMobPickup(Player player, InteractionHand hand, T mob) {
        return Bucketable.bucketMobPickup(player, hand, mob).isPresent();
    }

    /** 1.21.1 calls it the attack animation (26.3: getSwingAnimation) */
    public static float swingProgress(LivingEntity entity, float partialTick) {
        return entity.getAttackAnim(partialTick);
    }

    // ---- (1.21.1) the members below were added for 1.21.1, which differs from the 26.x line in more of the game's API ----

    /** 1.21.1 makes an entity from the level alone (the reason is given later, to finalizeSpawn) */
    public static <T extends Entity> T create(EntityType<T> type, Level level, MobSpawnType reason) {
        return type.create(level);
    }

    /** 1.21.1 compares the type (26.x: Entity.is(type)) */
    public static boolean isType(Entity entity, EntityType<?> type) {
        return entity.getType() == type;
    }

    /** 1.21.1 has no Block.column: the same box, from Block.box's corners */
    public static VoxelShape column(double width, double y0, double y1) {
        return Block.box(8.0 - width / 2.0, y0, 8.0 - width / 2.0, 8.0 + width / 2.0, y1, 8.0 + width / 2.0);
    }

    /** 1.21.1's frogspawn has no tags for it: it lies on water only (FrogspawnBlock.mayPlaceOn: the fluid under it is water's source) */
    public static boolean supportsFrogspawn(BlockGetter level, BlockPos below) {
        return level.getFluidState(below).getType() == Fluids.WATER;
    }

    /** 1.21.1's spawn eggs take their type and two tint colours in the constructor: white for both, which leaves the egg's own picture
     *  as it is drawn (every egg of ours has its own picture, not vanilla's tinted one) */
    public static SpawnEggItem spawnEgg(EntityType<? extends Mob> type, Item.Properties properties) {
        return new SpawnEggItem(type, 0xFFFFFF, 0xFFFFFF, properties);
    }

    /** 1.21.1 keeps the eating time in the food itself (FoodProperties.Builder.fast(): 0.8 s, dried kelp's) */
    public static Item.Properties quickFood(Item.Properties properties, FoodProperties food) {
        return properties.food(new FoodProperties(food.nutrition(), food.saturation(), food.canAlwaysEat(), 0.8F, food.usingConvertsTo(),
            food.effects()));
    }

    /** 1.21.1's block items take the block's name by themselves, and the block holds no feature flags of its own */
    public static Item.Properties blockItem(Item.Properties properties, Block block) {
        return properties;
    }

    /** 1.21.1 has no such call: 26.x's own rule (LivingEntity.canInteractWithLevel) */
    public static boolean canInteractWithLevel(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }

    /** 1.21.1 takes the hand's equipment slot */
    public static void hurtAndBreak(ItemStack stack, int damage, LivingEntity owner, InteractionHand hand) {
        stack.hurtAndBreak(damage, owner, LivingEntity.getSlotForHand(hand));
    }

    /** 1.21.1's rod puts its hook into the level as any entity (FishingRodItem.use: level.addFreshEntity) */
    public static void spawnProjectile(Projectile projectile, ServerLevel level, ItemStack stack) {
        level.addFreshEntity(projectile);
    }

    /** 1.21.1's rod makes the event from the player itself (FishingRodItem.use) */
    public static void useVibration(ItemStack stack, Entity user, Holder.Reference<GameEvent> event) {
        user.gameEvent(event);
    }

    /** 1.21.1 has one bundle and no tag of bundles */
    public static boolean isBundle(ItemStack stack) {
        return stack.is(Items.BUNDLE);
    }

    /** 1.21.1 has no item tag of shulker boxes: the item of a shulker box block */
    public static boolean isShulkerBox(ItemStack stack) {
        return Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    /** 1.21.1's dropFromLootTable is protected and drops into the world: its own steps, from its loot table to out (the player
     *  set as the last to hurt the entity first, as a player's kill sets it) */
    public static void killDrops(ServerLevel level, Player player, LivingEntity entity, Consumer<ItemStack> out) {
        ResourceKey<LootTable> key = entity.getLootTable();
        if (key == null) {
            return;
        }
        entity.setLastHurtByPlayer(player);
        DamageSource kill = player.damageSources().playerAttack(player);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ORIGIN, entity.position()).withParameter(LootContextParams.DAMAGE_SOURCE, kill)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, kill.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, kill.getDirectEntity())
            .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck())
            .create(LootContextParamSets.ENTITY);
        table.getRandomItems(params, entity.getLootTableSeed(), out);
    }

    /** 1.21.1's villager data holds the profession itself */
    public static boolean isFisherman(Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.FISHERMAN;
    }

    /** 1.21.1 asks the source for a permission level */
    public static Predicate<CommandSourceStack> gameMasters() {
        return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    /** 1.21.1's build heights: the top one is the first height above the level, hence the one less */
    public static int maxY(LevelHeightAccessor level) {
        return level.getMaxBuildHeight() - 1;
    }

    public static int minY(LevelHeightAccessor level) {
        return level.getMinBuildHeight();
    }

    /** 1.21.1's custom model data is one number, which item models choose their override by */
    public static CustomModelData modelData(String key, int number) {
        return new CustomModelData(number);
    }

    /** The fishing hook's private state field, found by its type: Fabric runs 1.21.1 with its own names (intermediary), NeoForge with
     *  Mojang's, so no one name serves; the field is the one of the hook's own enum type whose constants are the states (an enum's
     *  constant names are kept under every naming). */
    public static Field hookStateField() throws ReflectiveOperationException {
        for (Field f : FishingHook.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType().isEnum() && f.getType().getEnclosingClass() == FishingHook.class) {
                for (Object o : f.getType().getEnumConstants()) {
                    if (((Enum<?>) o).name().equals("BOBBING")) {
                        return f;
                    }
                }
            }
        }
        throw new NoSuchFieldException("FishingHook's state");
    }

    /** the parameter list through an invoker (mixin.MultiNoiseBiomeSourceAccessor, 1.21.1's build only): Fabric runs 1.21.1 with its
     *  own names, so it cannot be read by Mojang's */
    public static Climate.ParameterList<Holder<Biome>> biomeParameters(MultiNoiseBiomeSource source) throws ReflectiveOperationException {
        return ((MultiNoiseBiomeSourceAccessor) source).creel$parameters();
    }
}
