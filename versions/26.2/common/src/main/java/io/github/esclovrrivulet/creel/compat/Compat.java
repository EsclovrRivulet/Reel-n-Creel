package io.github.esclovrrivulet.creel.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Bucketable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Compat for MINECRAFT 26.2: replaces common/.../compat/Compat.java (26.3's) in the 26.2 build. The same members, each one line of
 *  26.2's own API; see the base copy for what each one is. */
public final class Compat {
    private Compat() {}

    public static final EntityType<Salmon> SALMON = EntityTypes.SALMON;
    public static final EntityType<FallingBlockEntity> FALLING_BLOCK = EntityTypes.FALLING_BLOCK;

    /** 26.2 names frogspawn's reaction DESTROY (26.3: POPPED) */
    public static final PushReaction POPPED = PushReaction.DESTROY;

    /** 26.2's random state holds one sampler (26.3: createClimateSampler(SamplerContext.EMPTY_UNCACHED)) */
    public static Climate.Sampler climateSampler(RandomState state) {
        return state.sampler();
    }

    /** 26.2's density functions are computed at a point context (26.3: sampleValue(x, y, z)) */
    public static double temperature(Climate.Sampler sampler, BlockPos pos) {
        return sampler.temperature().compute(new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** 26.2 has one swing animation, no argument for it */
    public static void swing(Player player, InteractionHand hand) {
        player.swing(hand, true);
    }

    /** 26.2 makes the changeable contents with a constructor (26.3: asMutable()) */
    public static BundleContents.Mutable mutable(BundleContents contents) {
        return new BundleContents.Mutable(contents);
    }

    public static <T extends LivingEntity & Bucketable> boolean bucketMobPickup(Player player, InteractionHand hand, T mob) {
        return Bucketable.bucketMobPickup(player, hand, mob).isPresent();
    }

    /** 26.2 calls it the attack animation (26.3: getSwingAnimation) */
    public static float swingProgress(LivingEntity entity, float partialTick) {
        return entity.getAttackAnim(partialTick);
    }

    // ---- (1.21.1) the members below were added for 1.21.1, which differs from the 26.x line in more of the game's API ----

    /** a new entity of that type in the level, for the reason given, not yet added to it (null when the type cannot be made) */
    public static <T extends Entity> T create(EntityType<T> type, Level level, EntitySpawnReason reason) {
        return type.create(level, reason);
    }

    /** whether the entity is of that type */
    public static boolean isType(Entity entity, EntityType<?> type) {
        return entity.is(type);
    }

    /** a box the full width and depth of a block, width wide and centred, from y0 to y1 (in pixels, as Block.box) */
    public static VoxelShape column(double width, double y0, double y1) {
        return Block.column(width, y0, y1);
    }

    /** whether the block under a spot supports frogspawn, by its fluid or by itself (the two tags of that name) */
    public static boolean supportsFrogspawn(BlockGetter level, BlockPos below) {
        return level.getFluidState(below).is(FluidTags.SUPPORTS_FROGSPAWN) || level.getBlockState(below).is(BlockTags.SUPPORTS_FROGSPAWN);
    }

    /** a spawn egg of that type */
    public static SpawnEggItem spawnEgg(EntityType<? extends Mob> type, Item.Properties properties) {
        return new SpawnEggItem(properties.spawnEgg(type));
    }

    /** a food eaten in 0.8 s, as dried kelp is */
    public static Item.Properties quickFood(Item.Properties properties, FoodProperties food) {
        return properties.food(food, Consumables.DRIED_KELP);
    }

    /** the properties of a block's own item: the block's name, and its feature flags */
    public static Item.Properties blockItem(Item.Properties properties, Block block) {
        return properties.useBlockDescriptionPrefix().requiredFeatures(block.requiredFeatures());
    }

    /** whether a player may act on the world at all (alive, in it, not a spectator) */
    public static boolean canInteractWithLevel(Player player) {
        return player.canInteractWithLevel();
    }

    /** wear on the item in that hand, as its use wears it (it breaks at the end, with the breaking sound) */
    public static void hurtAndBreak(ItemStack stack, int damage, LivingEntity owner, InteractionHand hand) {
        stack.hurtAndBreak(damage, owner, hand);
    }

    /** a projectile thrown from an item, put into the level (with the item's enchantment effects on a thrown projectile) */
    public static void spawnProjectile(Projectile projectile, ServerLevel level, ItemStack stack) {
        Projectile.spawnProjectile(projectile, level, stack);
    }

    /** the game event an item's use makes (the vibration a sculk sensor hears) */
    public static void useVibration(ItemStack stack, Entity user, Holder.Reference<GameEvent> event) {
        stack.causeUseVibration(user, event);
    }

    /** whether a stack is a bundle (any colour) */
    public static boolean isBundle(ItemStack stack) {
        return stack.is(ItemTags.BUNDLES);
    }

    /** whether a stack is a shulker box (any colour) */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack.is(ItemTags.SHULKER_BOXES);
    }

    /** What a player's kill of the entity drops, rolled from its own loot table as its death drop is (LivingEntity.dropFromLootTable:
     *  the entity, where it is, a player's attack, the player as the last to hurt it and their luck), given to out instead of the
     *  world; nothing when it has no loot table. */
    public static void killDrops(ServerLevel level, Player player, LivingEntity entity, Consumer<ItemStack> out) {
        Optional<ResourceKey<LootTable>> table = entity.getLootTable();
        if (table.isPresent()) {
            entity.setLastHurtByPlayer(player, LivingEntity.PLAYER_HURT_EXPERIENCE_TIME);
            DamageSource kill = player.damageSources().playerAttack(player);
            entity.dropFromLootTable(level, kill, true, table.get(), out);
        }
    }

    /** whether a villager is a fisherman */
    public static boolean isFisherman(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.FISHERMAN);
    }

    /** the permission a command needs for game masters (operators of level 2, command blocks) */
    public static Predicate<CommandSourceStack> gameMasters() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    /** the highest and the lowest block height of a level (both inside it) */
    public static int maxY(LevelHeightAccessor level) {
        return level.getMaxY();
    }

    public static int minY(LevelHeightAccessor level) {
        return level.getMinY();
    }

    /** an item's custom model data naming one picture of its item model definition: the string key (26.x reads the strings; number is
     *  for 1.21.1, whose item models choose by one number) */
    public static CustomModelData modelData(String key, int number) {
        return new CustomModelData(List.of(), List.of(), List.of(key), List.of());
    }

    /** vanilla's fishing hook's private state field (FishingHook.currentState, a private enum): 26.x runs with Mojang's names */
    public static Field hookStateField() throws ReflectiveOperationException {
        return FishingHook.class.getDeclaredField("currentState");
    }

    /** a multi-noise biome source's private parameter list (MultiNoiseBiomeSource.parameters()): 26.x runs with Mojang's names */
    @SuppressWarnings("unchecked")
    public static Climate.ParameterList<Holder<Biome>> biomeParameters(MultiNoiseBiomeSource source) throws ReflectiveOperationException {
        Method m = MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");
        m.setAccessible(true);
        return (Climate.ParameterList<Holder<Biome>>) m.invoke(source);
    }
}
