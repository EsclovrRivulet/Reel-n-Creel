package io.github.esclovrrivulet.creel.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Sizes;
import io.github.esclovrrivulet.creel.fish.Tier;
import io.github.esclovrrivulet.creel.item.FishBucketItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.habitat.FishSpawner;
import io.github.esclovrrivulet.creel.habitat.HabitatData;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Any of the mod's fish; the species is its EntityType. Behaves as vanilla's schooling fish do (swims, schools with its own species
 *  only, see SameKindFlockGoal, flops on land); its own per-tick work is only the despawn check (vanilla's, with a nearer distance)
 *  and a landed fish's flop (at each flop only); the sounds are the cod's. Where it appears on its own is
 *  the habitat package's business (docs/HABITAT.md); here only the school size and the despawn. */
public class FishEntity extends AbstractSchoolingFish {
    /** the size class, synced so the client shows the size word in the name */
    private static final EntityDataAccessor<Byte> DATA_SIZE = SynchedEntityData.defineId(FishEntity.class, EntityDataSerializers.BYTE);
    private float sizeFactor = 1.0F;
    private boolean sized;

    public FishEntity(EntityType<? extends FishEntity> type, Level level) {
        super(type, level);
    }

    /** this fish's row, cached on first use (see species()) */
    private Species species;

    /** this fish's row of the species table (looked up by its type's id). It is cached lazily, not set in the constructor:
     *  registerGoals runs inside the super constructor and asks for it before any field of this class is initialised (a field
     *  initialiser would then reset it to null, so the field has none). */
    public Species species() {
        Species s = this.species;
        if (s == null) {
            s = FishTable.BY_ID.get(EntityType.getKey(this.getType()).getPath());
            this.species = s;
        }
        return s;
    }

    /** Vanilla's goals, with its flock goal (which gathers every FishEntity, so every species together) swapped for one that
     *  schools with this fish's own species only, at the same priority; a solitary species gets no flock goal at all. */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> goal instanceof FollowFlockLeaderGoal);
        if (this.species().schools()) {
            this.goalSelector.addGoal(5, new SameKindFlockGoal(this));
        }
    }

    /** The most fish one school holds: the top of the species' school size (its own in its habitat data, else its size class's,
     *  FishSpawner.schoolMax). A solitary fish is a school of one: no fish of its kind joins it, and it spawns alone. */
    @Override
    public int getMaxSchoolSize() {
        Species s = this.species();
        return FishSpawner.schoolMax(s, HabitatData.get(s.id()));
    }

    /** A solitary fish spawned with a group does not follow the group's first fish. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (!this.sized) {
            this.rollSize();        // a natural spawn, a spawn egg, the mod's own summon; a fish from a bucket takes its size after
        }
        if (!this.species().schools() && this.isFollower()) {
            this.stopFollowing();
        }
        return result;
    }

    /** Every fish of ours is in category MISC (outside vanilla's fish pool), whose despawn distance is 128; they leave beyond 64 blocks
     *  as vanilla's fish do, unless named or from a bucket. This is vanilla's Mob.checkDespawn copied call for call (one search for the
     *  nearest player, where calling super after a search of our own made two), with WATER_AMBIENT's despawn distance (64) in place of
     *  the entity's own category's; everything else, the no-despawn distance included, is vanilla's own. RE-CHECK AGAINST VANILLA'S
     *  Mob.checkDespawn ON EACH GAME UPDATE (javap -c -p net.minecraft.world.entity.Mob). */
    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && !this.getType().isAllowedInPeaceful()) {
            this.discard();
            return;
        }
        boolean persistent = this.isPersistenceRequired() || this.requiresCustomPersistence();
        Player player = this.level().getNearestPlayer(this, -1.0);
        if (player != null) {
            double d = player.distanceToSqr(this);
            int far = MobCategory.WATER_AMBIENT.getDespawnDistance();
            int farSq = far * far;
            if (!persistent && d > farSq && this.removeWhenFarAway(d)) {
                this.discard();
            }
            int near = this.getType().getCategory().getNoDespawnDistance();
            int nearSq = near * near;
            if (!persistent && this.noActionTime > 600 && this.random.nextInt(800) == 0 && d > nearSq && this.removeWhenFarAway(d)) {
                this.discard();
            } else if (d < nearSq) {
                this.noActionTime = 0;
            }
        }
    }

    // ---- size (docs/SIZES.md): rolled once when the fish first appears, kept for life in CreelSize and CreelScale ----

    /** this fish's size class and its exact factor, ORDINARY 1.0 until set */
    public Size size() {
        return Size.byOrdinal(this.entityData.get(DATA_SIZE));
    }

    public float sizeFactor() {
        return this.sizeFactor;
    }

    /** Sets the size: the scale attribute (the game scales the hitbox and the model by it, over the species' own scale) and the full
     *  health, Sizes.health; full is whether the fish is healed to it (a fish that already has health of its own keeps it). */
    public void setSize(Size size, float factor, boolean full) {
        this.sized = true;
        this.sizeFactor = factor;
        this.entityData.set(DATA_SIZE, (byte) size.ordinal());
        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(factor);
            this.refreshDimensions();       // the game would do it on the next tick (dirty attributes); now, so the hitbox is right at once
        }
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Sizes.health(this.species().health(), factor));
        }
        if (full) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void rollSize() {
        Sizes.Rolled r = Sizes.roll(this.species().tier(), this.getRandom());
        this.setSize(r.size(), r.factor(), true);
    }

    /** the size from saved data: CreelSize names the class, CreelScale the factor (missing: the class's own factor, no wobble, as
     *  /summon creel:king_salmon ~ ~ ~ {CreelSize:"trophy"} gives); full health only when the factor was not saved (a fresh fish) */
    private boolean readSize(String key, float saved) {
        Size s = Size.byKey(key);
        if (s == null) {
            return false;
        }
        this.setSize(s, saved > 0 ? saved : Sizes.scale(s), saved <= 0);
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SIZE, (byte) Size.ORDINARY.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (this.sized) {
            out.putString("CreelSize", this.size().key);
            out.putFloat("CreelScale", this.sizeFactor);
        }
        if (this.landedAt != NO_MARK) {
            out.putLong("CreelLanded", this.landedAt);          // 0.12.4: a landed fish keeps its block over a reload
        } else if (this.landing) {
            out.putBoolean("CreelLanding", true);
        }
    }

    /** a saved fish keeps its size; a fish loaded without one (a /summon, which always loads, or a fish saved before 0.8) rolls it now */
    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        if (!this.readSize(in.getStringOr("CreelSize", ""), in.getFloatOr("CreelScale", 0.0F)) && !this.sized && !this.level().isClientSide()) {
            this.rollSize();
        }
        this.landedAt = in.getLongOr("CreelLanded", NO_MARK);
        this.landing = in.getBooleanOr("CreelLanding", false);
    }

    /** the name carries the size word: "Large King Salmon" (a custom name stays as given) */
    @Override
    protected Component getTypeName() {
        return this.size().name(super.getTypeName());
    }

    // ---- the bucket (item.FishBucketItem): every fish, whatever its size (the owner's choice, 2026-11-15); a water bucket picks up
    //      any fish of ours as vanilla's does a cod ----

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(CreelMod.FISH_BUCKET);
    }

    /** vanilla's bucket data (name, health and the rest), then the species and size, and the bucket's name and look */
    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        FishBucketItem.fill(stack, this.species(), this.size(), this.sizeFactor);
    }

    /** the size first, so the saved health fits under the full health it gives; then vanilla's bucket data */
    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        this.readSize(tag.getStringOr("CreelSize", ""), tag.getFloatOr("CreelScale", 0.0F));
        super.loadFromBucketTag(tag);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COD_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    // ---- a fish landed by a rod (0.12.4): it flops where it was landed, not straight back into the water ----

    /** the block a fish landed by a rod flops in, as BlockPos.asLong; NO_MARK when none */
    private static final long NO_MARK = Long.MIN_VALUE;
    /** half vanilla's flop height: vanilla adds 0.4 up, about a block; this about half that */
    private static final double LANDED_HOP = 0.28;
    /** how far each way from the block's centre the next flop may carry it, and how far a sideways push carries over one flop (the
     *  flight's ticks with the air's drag, and the slide after) */
    private static final double LANDED_REACH = 0.3, LANDED_CARRY = 5.0;
    private long landedAt = NO_MARK;
    private boolean landing;

    /** fishing.CastingHook: this fish was just lifted out by a rod; the block it first flops in becomes its mark */
    public void markLanded() {
        this.landing = true;
        this.landedAt = NO_MARK;
    }

    /** the block a landed fish keeps to, or null (a fish never landed, back in water, or still in the air from the rod) */
    public BlockPos landedBlock() {
        return this.landedAt == NO_MARK ? null : BlockPos.of(this.landedAt);
    }

    /** back in water (the game calls this once, on entering it): the mark is forgotten */
    @Override
    protected void doWaterSplashEffect() {
        this.landing = false;
        this.landedAt = NO_MARK;
        super.doWaterSplashEffect();
    }

    /** The flop: vanilla's AbstractFish.aiStep asks for the flop's sound at each flop and only then (out of water, on the ground, just
     *  after it set the hop), so a landed fish's hop is changed here and nothing runs on any other tick or for a fish in water. A fish
     *  landed by a rod hops about half as high, and its sideways push is held to what keeps it inside the block it was landed on (it is
     *  steered back toward the middle when near an edge). Any other fish flops as vanilla's. */
    @Override
    protected SoundEvent getFlopSound() {
        if ((this.landing || this.landedAt != NO_MARK) && !this.level().isClientSide()) {
            if (this.landedAt == NO_MARK) {
                this.landedAt = this.blockPosition().asLong();
                this.landing = false;
            }
            BlockPos at = BlockPos.of(this.landedAt);
            double vx = this.sideways(this.getX() - (at.getX() + 0.5));
            double vz = this.sideways(this.getZ() - (at.getZ() + 0.5));
            // vanilla has just added 0.4 up to what the fish had (about nothing, on the ground): the hop, halved
            double vy = this.getDeltaMovement().y - 0.4 + LANDED_HOP;
            this.setDeltaMovement(vx, vy, vz);
            // its swimming goals keep a path to water they cannot follow on land, and the move control would still drive it along that
            // path through the air between flops (what carried vanilla's landed fish back into the water): dropped at each flop
            this.getNavigation().stop();
            this.setSpeed(0.0F);
            this.setZza(0.0F);
        }
        return SoundEvents.COD_FLOP;
    }

    /** one axis of a landed fish's sideways push: a small random one (half vanilla's), held to what lands it within LANDED_REACH of the
     *  block's middle; off is how far from the middle it is now (beyond the reach, the push takes it back toward the middle) */
    private double sideways(double off) {
        double v = (this.random.nextFloat() * 2.0F - 1.0F) * 0.025;
        return Mth.clamp(v, (-LANDED_REACH - off) / LANDED_CARRY, (LANDED_REACH - off) / LANDED_CARRY);
    }
}
