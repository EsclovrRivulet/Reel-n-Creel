package io.github.esclovrrivulet.creel.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.habitat.Habitat;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Sizes;
import io.github.esclovrrivulet.creel.fish.Species;
import io.github.esclovrrivulet.creel.fish.Tier;
import io.github.esclovrrivulet.creel.habitat.Water;
import io.github.esclovrrivulet.creel.mixin.FishingHookAccessor;
import io.github.esclovrrivulet.creel.mixin.MobAccessor;

import java.lang.reflect.Field;

/** The bobber of the mod's rods (docs/FISHING.md): vanilla's own fishing hook, of vanilla's own entity type, so the throw, the flight, the
 *  bobbing, the line drawn to the rod and the bobber riding a hooked entity are all vanilla's, on the server and on every client (a client
 *  sees an ordinary vanilla hook). What differs, all on the server, all inside this hook's own tick (mixin.FishingHookMixin):
 *  - vanilla's catch (its wait, bite, loot, junk and treasure) never runs; waitForBite runs in its place: vanilla's wait and Lure
 *    shortening, vanilla's wake of a fish coming, then a fish made from one reading of the water (Conjure), hung on the line;
 *  - the line holds while the rod it was cast with is in hand and the bobber within vanilla's 32 blocks (stopFishing);
 *  - the bobber passes through entities in flight: these rods catch fish only;
 *  - its launch speed is vanilla's times the rod's cast.
 *
 *  Everything about the fish on the line is here, in Line and in FightCore. The fight (0.14.0, reworked 0.15.0, rolled 0.16.0,
 *  docs/FISHING.md "The fight"): REST, PULL (and the easing after a run) and RUN between the first reel and a beaten fish; FightCore rolls
 *  the stages and keeps the stamina, the running credit and the snap counter; this hook moves the fish, plays the cues, snaps the line
 *  and marks the beaten fish, all in this hook's tick (fight) and the held-use tick (reel); its numbers are FishingData.Fight; the level
 *  is synced as HookLevel. (0.16.2) Every cue follows the fish's state; there is no hidden strain. */
public class CastingHook extends FishingHook {
    private static final Logger LOG = LoggerFactory.getLogger("creel");

    /** a fish not reeled within this many ticks of the bite shakes free */
    public static final int SHAKE_FREE_TICKS = 60;
    /** a reeled beaten fish this near the player (horizontally) is lifted out; or this far, when it is pressed against the bank (a fish
     *  pulling or running is held off there; 0.16.1 a resting one with land_below of its stamina or less is landed, a fresher one spooked) */
    public static final double LAND_DISTANCE = 2.5, LAND_AT_BANK = 4.0;
    /** vanilla's line: the bobber stays within 32 blocks of the player */
    public static final double LINE_LENGTH_SQR = 32 * 32;

    /** what is on the line. The fight is REST, PULL and RUN, between the first reel and a beaten fish (ON_LINE); the rod's bend shows
     *  which (bendLevel: calm, tension, danger), one stage more while the player reels (stage). */
    public enum Line {
        /** nothing on the line: the bobber waits for a bite */
        WAITING,
        /** a fish has just taken the bobber; it shakes free unless reeled within SHAKE_FREE_TICKS (calm) */
        BITE,
        /** the fight: the fish is calm. Reeled, it comes in at the normal speed; not reeled, it holds or
         *  drifts, never swims away (calm: safe to reel) */
        REST,
        /** the fight: the fish resists, facing away and tugging in place without taking line. Reeled, it comes in slowly (safe).
         *  Every run comes out of one; it may settle back to a rest; after every run it eases off at this level (tension) */
        PULL,
        /** the fight: the fish runs away from the player (or along the bank), taking line; reeling against it gains almost nothing and
         *  counts toward the snap (danger: do not reel) */
        RUN,
        /** the fish is beaten (or never fought: a tiny one): it stays on until it is landed or the line goes (calm) */
        ON_LINE
    }

    private Line line = Line.WAITING;
    // ---- the fight (0.14.0, 0.15.0, 0.16.0; numbers: FishingData.Fight) ----
    /** (0.16.0) the fight's mind (stage, rolls, stamina, running credit, snap counter); made at the bite */
    private FightCore core;
    /** for the tests: the last fight's mind, kept after the line goes (its runs, its time) */
    private FightCore lastCore;
    /** whether the fish fought (had stamina when the hook was set): a beaten fish comes in faster than a tiny one */
    private boolean fought;
    /** for the tests: beaten fish marked with a splash (since the server started) */
    public static int beatenSplashes;
    /** a resting fish not reeled is held at this distance from the player (it never swims away) */
    private double holdDistance;
    /** the reel's click: the hook's tick it last played, the gap before it, how many have played (for the tests) */
    private int lastClick = -1000, lastClickGap, clicks;
    /** the hook's tick when the line was last reeled */
    private int lastReel = -100;
    /** the present run's way (horizontal, unit) and the side it turns to along a bank (1 or -1) */
    private Vec3 heading = Vec3.ZERO;
    private int bankSide = 1;
    /** whether the fish is held at the end of the line now */
    private boolean atEnd;
    /** for the tests: the fish the line last snapped on */
    private AbstractFish snappedOff;
    /** for the tests: how many times a snapped line's sound has been played (since the server started) */
    public static int snapSounds;
    private AbstractFish fish;
    private Tier fishClass;
    /** the rod it was cast with (RodTable id) */
    private final String rod;
    private boolean fishWasKept;
    private int lineTicks, reelTicks;
    // the wait, as vanilla's: until lured, then a fish coming at an angle
    private int timeUntilLured, timeUntilHooked;
    private float fishAngle;
    /** the water at the bobber, read once when a wait starts; null while nothing would bite here */
    private Habitat.Reading reading;
    /** the last fish this hook landed (for the test hook) */
    private AbstractFish landed;

    /** the hook's tick when the line was last wound in (the wait pauses while it is) */
    private int lastWound = -100;
    /** whether the last wind was stopped by something (the bank) */
    private boolean windBlocked;

    /** vanilla's throw (its constructor), its speed then multiplied by the rod's cast and the cast's power (FishingData.Handling) */
    public CastingHook(Player player, Level level, int luck, int lureSpeed, String rod, double power) {
        super(player, level, luck, lureSpeed);
        this.rod = rod;
        FishingData.RodData d = FishingData.rod(rod);
        this.setDeltaMovement(this.getDeltaMovement().scale((d != null ? d.cast() : 1.0) * power));
    }

    /** whether the line is being wound in now (wound this tick or the last) */
    public boolean winding() {
        return this.tickCount - this.lastWound <= 1;
    }

    /** One tick of held use with nothing on the line (CastingRodItem.onUseTick, server): the bobber is drawn in along the water toward
     *  the player at the wind speed, with the reel's ticking; within LAND_DISTANCE the line is in and the bobber gone. Returns whether the
     *  line is still out. */
    public boolean wind(Player player) {
        if (this.fish != null || this.isRemoved()) {
            return this.fish != null;
        }
        Vec3 d = player.position().subtract(this.position()).multiply(1, 0, 1);
        double h = d.length();
        if (h <= LAND_DISTANCE || (h <= LAND_AT_BANK && this.windBlocked)) {     // in reach, or held against the bank below the player
            this.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                1.0F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
            this.discard();
            return false;
        }
        this.lastWound = this.tickCount;
        double step = Math.min(FishingData.handling().windSpeed(), h - LAND_DISTANCE + 0.01);
        Vec3 before = this.position();
        this.move(MoverType.SELF, d.scale(step / h));
        this.windBlocked = this.position().subtract(before).horizontalDistance() < step * 0.5;      // the bank stopped it
        if (this.reelTicks++ % 5 == 0) {
            this.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS,
                0.35F, 1.5F + this.random.nextFloat() * 0.2F);
        }
        return true;
    }

    /** for the test hook: the wait about to end, so a bite would come within a few ticks if the wait ran */
    public void primeBite(BlockPos pos) {
        if (this.level() instanceof ServerLevel level) {
            Habitat.Reading r = Habitat.read(level, pos);
            this.reading = Conjure.anyBites(r, this.rodData()) ? r : null;
            this.timeUntilLured = 0;
            this.timeUntilHooked = 2;
            this.fishAngle = 0;
        }
    }

    public String rod() {
        return this.rod;
    }

    private FishingData.RodData rodData() {
        return FishingData.rod(this.rod);
    }

    /** the hand holding the rod this line was cast with, or null */
    public InteractionHand rodHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof CastingRodItem r && r.rod().id().equals(this.rod)) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof CastingRodItem r && r.rod().id().equals(this.rod)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public Line line() {
        return this.line;
    }

    public AbstractFish fish() {
        return this.fish;
    }

    public Tier fishClass() {
        return this.fishClass;
    }

    public AbstractFish landed() {
        return this.landed;
    }

    public double stamina() {
        return this.core == null ? 0 : this.core.stamina();
    }

    public int runs() {
        FightCore c = this.core != null ? this.core : this.lastCore;
        return c == null ? 0 : c.runs();
    }

    /** for the tests: the fight's mind (the last one once the line is gone), or null before any bite */
    public FightCore core() {
        return this.core != null ? this.core : this.lastCore;
    }

    public boolean atEnd() {
        return this.atEnd;
    }

    public AbstractFish snappedOff() {
        return this.snappedOff;
    }

    /** for the tests: the reel's clicks played so far, the gap in ticks before the last one */
    public int clicks() {
        return this.clicks;
    }

    public int lastClickGap() {
        return this.lastClickGap;
    }

    /** (0.16.2) the present run's snap counter (0 outside a run) */
    public int runReeled() {
        return this.core == null ? 0 : this.core.runReeled();
    }

    /** for the visual test (/creel fightstate): the fish on the line held in that state of the fight (REST, PULL or RUN) until the line
     *  goes; returns whether there was a fish to hold */
    public boolean holdState(Line state) {
        if (this.fish == null || this.core == null || (state != Line.REST && state != Line.PULL && state != Line.RUN)) {
            return false;
        }
        this.fought = true;
        this.core.hold(state);
        this.line = state;
        if (state == Line.RUN) {
            Player owner = this.getPlayerOwner();
            Vec3 away = owner == null ? new Vec3(1, 0, 0) : this.fish.position().subtract(owner.position()).multiply(1, 0, 1);
            this.heading = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        }
        if (state == Line.REST) {
            Player owner = this.getPlayerOwner();
            this.holdDistance = owner == null ? 0 : flatDistance(this.fish, owner);
        }
        this.syncFight();
        return true;
    }

    /** (0.15.0, 0.16.0) the fish's level, the signal of when to reel, from its state alone (not the fish's size): none with no fish, calm
     *  while it is bitten, resting or beaten, tension while it pulls or eases off after a run, danger while it runs. The bend's stage is
     *  this plus whether the player reels (stage, HookLevel.stage). */
    public int bendLevel() {
        if (this.fish == null) {
            return HookLevel.NONE;
        }
        return switch (this.line) {
            case WAITING -> HookLevel.NONE;
            case PULL -> HookLevel.TENSION;
            case RUN -> HookLevel.DANGER;
            default -> HookLevel.CALM;
        };
    }

    /** (0.16.0) the bend stage now, 0 to 6 (HookLevel.stage): the level plus one while the line is reeled */
    public int stage() {
        return HookLevel.stage(this.bendLevel(), this.reeling());
    }

    /** for the tests: whether the present pull is the easing after a run */
    public boolean easing() {
        return this.core != null && this.core.easing();
    }

    /** whether the line is being reeled now (reeled this tick or the last) */
    public boolean reeling() {
        return this.tickCount - this.lastReel <= 1;
    }

    /** the rod's Luck of the Sea level, as the bite reads it (vanilla's hook keeps it from the cast) */
    int luck() {
        return this.vanilla().creel$luck();
    }

    private FishingHookAccessor vanilla() {
        return (FishingHookAccessor) this;
    }

    /** these rods catch fish only: the bobber hooks no mob or item in flight */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    /** vanilla's check with our rod in place of vanilla's: alive, the rod it was cast with in either hand, the bobber within 32 blocks */
    public boolean stopFishing(Player player) {
        if (player.canInteractWithLevel() && this.rodHand(player) != null && this.distanceToSqr(player) <= LINE_LENGTH_SQR) {
            return false;
        }
        this.discard();
        return true;
    }

    // ---- the wait and the bite (each tick the bobber rests in water, in place of vanilla's catchingFish) ----

    public void waitForBite(BlockPos pos) {
        if (this.line != Line.WAITING || !(this.level() instanceof ServerLevel level) || this.winding()) {
            return;                     // nothing bites while the line is being wound in; the wait resumes when the bobber rests
        }
        int rate = 1;           // vanilla's: faster in rain, slower under cover
        BlockPos above = pos.above();
        if (this.random.nextFloat() < 0.25F && level.isRainingAt(above)) {
            rate++;
        }
        if (this.random.nextFloat() < 0.5F && !level.canSeeSky(above)) {
            rate--;
        }
        if (this.timeUntilHooked > 0) {
            this.timeUntilHooked -= rate;
            if (this.timeUntilHooked > 0) {
                this.wake(level);
            } else {
                this.bite(level, this.reading);
            }
        } else if (this.timeUntilLured > 0) {
            this.timeUntilLured -= rate;
            if (this.reading != null) {
                this.stir(level);
            }
            if (this.timeUntilLured <= 0) {
                if (this.reading == null) {
                    this.timeUntilLured = 0;            // nothing would bite here: wait again, read again
                } else {
                    this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                    this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
                }
            }
        } else {
            this.timeUntilLured = Mth.nextInt(this.random, 100, 600) - this.vanilla().creel$lureSpeed();
            Habitat.Reading r = Habitat.read(level, pos);
            this.reading = Conjure.anyBites(r, this.rodData()) ? r : null;
        }
    }

    /** vanilla's splashes round the bobber as a fish is lured near */
    private void stir(ServerLevel level) {
        float chance = 0.15F;
        if (this.timeUntilLured < 20) {
            chance += (20 - this.timeUntilLured) * 0.05F;
        } else if (this.timeUntilLured < 40) {
            chance += (40 - this.timeUntilLured) * 0.02F;
        } else if (this.timeUntilLured < 60) {
            chance += (60 - this.timeUntilLured) * 0.01F;
        }
        if (this.random.nextFloat() < chance) {
            float a = Mth.nextFloat(this.random, 0.0F, 360.0F) * Mth.DEG_TO_RAD;
            float d = Mth.nextFloat(this.random, 25.0F, 60.0F);
            double x = this.getX() + Mth.sin(a) * d * 0.1, y = Mth.floor(this.getY()) + 1.0, z = this.getZ() + Mth.cos(a) * d * 0.1;
            if (level.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + this.random.nextInt(2), 0.1, 0.0, 0.1, 0.0);
            }
        }
    }

    /** vanilla's wake of the fish coming to the bobber */
    private void wake(ServerLevel level) {
        this.fishAngle += (float) this.random.triangle(0.0, 9.188);
        float a = this.fishAngle * Mth.DEG_TO_RAD, sin = Mth.sin(a), cos = Mth.cos(a);
        double x = this.getX() + sin * this.timeUntilHooked * 0.1F, y = Mth.floor(this.getY()) + 1.0, z = this.getZ() + cos * this.timeUntilHooked * 0.1F;
        if (level.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
            if (this.random.nextFloat() < 0.15F) {
                level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1, z, 1, sin, 0.1, cos, 0.0);
            }
            float sx = sin * 0.04F, cz = cos * 0.04F;
            level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, cz, 0.01, -sx, 1.0);
            level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -cz, 0.01, sx, 1.0);
        }
    }

    /** for the test hook: the bite now, from a reading of the water at the bobber now; returns whether a fish is on the line */
    public boolean forceBite() {
        if (!(this.level() instanceof ServerLevel level) || this.line != Line.WAITING) {
            return false;
        }
        Habitat.Reading r = Habitat.read(level, this.blockPosition());
        return Conjure.anyBites(r, this.rodData()) && this.bite(level, r);
    }

    /** for tests (0.13.0: /creel bite, FishTest pick, the visual test of the rods' flex): the bite now with that species at that size,
     *  whatever the water holds (it must be deep enough for the fish); returns whether it is on the line */
    public boolean forceBite(Species species, Size size) {
        if (!(this.level() instanceof ServerLevel level) || this.line != Line.WAITING) {
            return false;
        }
        Conjure.Catch c = new Conjure.Catch(species, CreelMod.FISH.get(species.id()), species.tier());
        if (!this.bite(level, c)) {
            return false;
        }
        if (this.fish instanceof FishEntity f) {
            f.setSize(size, Sizes.scale(size), true);
        }
        return true;
    }

    /** for tests (0.19.0: FishTest's vanilla cod and salmon landed into a bucket): the bite now with a vanilla fish of that type, on the
     *  line as a fish of that tier; returns whether it is on the line */
    public boolean forceBite(EntityType<? extends AbstractFish> type, Tier tier) {
        if (!(this.level() instanceof ServerLevel level) || this.line != Line.WAITING) {
            return false;
        }
        return this.bite(level, new Conjure.Catch(null, type, tier));
    }

    /** a fish is made at the bobber from the reading, takes it with vanilla's splash, bubbles and sound, and is on the line */
    private boolean bite(ServerLevel level, Habitat.Reading r) {
        return this.bite(level, r == null ? null : Conjure.pick(r, this.rodData(), this.vanilla().creel$luck(), this.random));
    }

    private boolean bite(ServerLevel level, Conjure.Catch c) {
        this.timeUntilHooked = 0;
        this.timeUntilLured = 0;
        AbstractFish f = c == null ? null : this.make(level, c);
        if (f == null) {
            return false;
        }
        this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        double y = this.getY() + 0.5;
        int n = (int) (1.0F + this.getBbWidth() * 20.0F);
        level.sendParticles(ParticleTypes.BUBBLE, this.getX(), y, this.getZ(), n, this.getBbWidth(), 0.0, this.getBbWidth(), 0.2);
        level.sendParticles(ParticleTypes.FISHING, this.getX(), y, this.getZ(), n, this.getBbWidth(), 0.0, this.getBbWidth(), 0.2);
        this.fish = f;
        this.fishClass = c.tier();
        this.fishWasKept = f.isPersistenceRequired();
        ((MobAccessor) f).creel$setPersistenceRequired(true);      // on the line: kept, and outside the spawn budgets
        this.vanilla().creel$setHookedEntity(f);                   // the bobber now rides the fish (FishingHookMixin)
        this.line = Line.BITE;
        this.core = new FightCore(this.random, c.tier());
        this.lineTicks = 0;
        return true;
    }

    /** the fish, sized as any fish is, placed in the water under the bobber, wholly under the surface; null if it does not fit there */
    private AbstractFish make(ServerLevel level, Conjure.Catch c) {
        Water.Column col = Water.column(level, this.blockPosition());
        if (col == null) {
            return null;
        }
        AbstractFish f = c.type().create(level, EntitySpawnReason.NATURAL);
        if (f == null) {
            return null;
        }
        double x = this.getX(), z = this.getZ();
        f.snapTo(x, col.floor(), z, this.random.nextFloat() * 360.0F, 0.0F);
        f.finalizeSpawn(level, level.getCurrentDifficultyAt(this.blockPosition()), EntitySpawnReason.NATURAL, null);
        for (double y = Math.max(col.floor(), col.surface() + 0.9 - f.getBbHeight()); y >= col.floor(); y -= 1.0) {
            f.setPos(x, y, z);
            if (Water.isWater(level, BlockPos.containing(x, y, z)) && level.noCollision(f)) {
                level.addFreshEntity(f);
                return f;
            }
        }
        f.discard();
        return null;
    }

    // ---- the fish on the line ----

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved() || this.fish == null) {
            return;
        }
        if (!this.fish.isAlive() || this.getHookedIn() != this.fish) {
            this.letGo();               // killed, or taken off the line some other way
            return;
        }
        this.lineTicks++;
        if (this.line == Line.BITE && this.lineTicks > SHAKE_FREE_TICKS) {
            this.shakeFree();
            return;
        }
        FishingData.Fight cfg = FishingData.fight();
        if (this.line == Line.RUN || this.line == Line.PULL || this.line == Line.REST) {
            this.fight(cfg);
        }
        if (!this.isRemoved()) {
            this.syncFight();
        }
    }

    // ---- the fight (0.14.0, reworked 0.15.0) ----

    /** the first reel after the bite: a fish with stamina starts calm, in a rest (0.16.1); a tiny one (no stamina) just comes in */
    private void beginFight() {
        FishingData.Fight cfg = FishingData.fight();
        Size size = this.fish instanceof FishEntity fe ? fe.size() : Size.ORDINARY;
        this.core.begin(cfg, cfg.stamina(this.fishClass, size));
        this.fought = this.core.fighting();
        this.line = this.core.line();
        Player owner = this.getPlayerOwner();
        if (owner != null) {
            this.holdDistance = flatDistance(this.fish, owner);
        }
    }

    /** a run begins: its way (away from the player, within about 23 degrees), the bank side, a splash near the surface */
    private void runBegins() {
        Player owner = this.getPlayerOwner();
        Vec3 away = owner == null ? Vec3.ZERO : this.fish.position().subtract(owner.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            float a = this.random.nextFloat() * Mth.TWO_PI;
            away = new Vec3(Mth.cos(a), 0, Mth.sin(a));
        }
        this.heading = away.normalize().yRot((this.random.nextFloat() - 0.5F) * 0.8F);
        this.bankSide = this.random.nextBoolean() ? 1 : -1;
        if (this.nearTop(this.fish)) {
            this.level().playSound(null, this.fish.getX(), this.fish.getY(), this.fish.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                0.35F, 0.9F + this.random.nextFloat() * 0.3F);
        }
    }

    /** (0.16.0) the fish is beaten: it never fights again, and it is marked by a splash at the surface (particles and the vanilla splash) */
    private void beaten() {
        this.line = Line.ON_LINE;
        this.atEnd = false;
        beatenSplashes++;
        if (this.level() instanceof ServerLevel level) {
            AbstractFish f = this.fish;
            int top = Water.surface(level, f.blockPosition());
            double y = top != Water.NO_SURFACE ? top + 1.0 : f.getY() + f.getBbHeight();
            double w = Math.max(0.3, f.getBbWidth() * 0.6);
            level.sendParticles(ParticleTypes.SPLASH, f.getX(), y, f.getZ(), 14, w, 0.0, w, 0.0);
            level.sendParticles(ParticleTypes.BUBBLE, f.getX(), y - 0.3, f.getZ(), 6, w * 0.5, 0.1, w * 0.5, 0.05);
            level.playSound(null, f.getX(), y, f.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    /** whether the water's surface is just over the fish (the block above its top is not water) */
    private boolean nearTop(AbstractFish f) {
        return !Water.isWater(this.level(), BlockPos.containing(f.getX(), f.getY() + f.getBbHeight() + 1.0, f.getZ()));
    }

    /** where the bobber rides the fish: what vanilla's line length is measured from */
    private static double lineOut(AbstractFish f, Player owner) {
        return new Vec3(f.getX(), f.getY(0.8), f.getZ()).distanceTo(owner.position());
    }

    /** the fish's horizontal distance from the player */
    private static double flatDistance(AbstractFish f, Player owner) {
        return f.position().subtract(owner.position()).horizontalDistance();
    }

    /** the yaw that faces a mob along a horizontal direction (x, z) */
    private static float yawAlong(double x, double z) {
        return (float) (Mth.atan2(z, x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** a freed fish darts away from the player (horizontally), when it is not right above or below them */
    private static void pushAway(AbstractFish f, Player owner) {
        Vec3 away = f.position().subtract(owner.position()).multiply(1, 0, 1);
        if (away.lengthSqr() > 1.0E-4) {
            f.setDeltaMovement(f.getDeltaMovement().add(away.normalize().scale(0.3)));
        }
    }

    /** the fish turned to face straight away from the player (fighting the line: pulling or running) */
    private static void faceAway(AbstractFish f, Player owner) {
        Vec3 d = f.position().subtract(owner.position());
        if (d.horizontalDistanceSqr() < 1.0E-6) {
            return;
        }
        float yaw = yawAlong(d.x, d.z);
        f.setYRot(yaw);
        f.setYBodyRot(yaw);
        f.setYHeadRot(yaw);
    }

    /** One tick of a rest, a pull or a run, in the hook's own tick: the fish moves as its stage has it (unless it is being reeled, which
     *  moves it), then the fight's mind (FightCore) takes the tick: stamina, the snap counter, the stage's end and its roll. Its answer is
     *  acted on here: a new run's way, a rest's hold, the beaten splash, the snap. */
    private void fight(FishingData.Fight cfg) {
        Player owner = this.getPlayerOwner();
        if (owner == null) {
            return;
        }
        boolean reeling = this.reeling();
        AbstractFish f = this.fish;
        switch (this.line) {
            case RUN -> this.runTick(f, owner, cfg, reeling);
            case PULL -> this.pullTick(f, owner, reeling);
            default -> this.restTick(f, owner, cfg, reeling);
        }
        Line was = this.line;
        int ev = this.core.tick(cfg, reeling);
        if (ev == FightCore.SNAP) {
            this.snap(owner, cfg);
            return;
        }
        this.line = this.core.line();
        if (this.line != Line.RUN) {
            this.atEnd = false;
        }
        if (ev == FightCore.BEATEN) {
            this.beaten();
        } else if (ev == FightCore.CHANGED && this.line != was) {
            if (this.line == Line.RUN) {
                this.runBegins();
            } else if (this.line == Line.REST) {
                this.holdDistance = flatDistance(f, owner);
            }
        }
    }

    /** a run's tick: the fish runs on (unless reeled, which moves it), with its cues */
    private void runTick(AbstractFish f, Player owner, FishingData.Fight cfg, boolean reeling) {
        // at the end of the line (once there, until it is a block back in: the reel's little gain does not free it)
        this.atEnd = lineOut(f, owner) >= cfg.endOfLine() - (this.atEnd ? 1.0 : 0.0);
        if (!reeling) {
            this.runOn(f, owner, cfg);
        } else {
            f.getNavigation().stop();
            faceAway(f, owner);
        }
        this.cue(f);
    }

    /** a pull's tick: it turns away and tugs where it is: no line taken; reeled, it comes in slowly (CastingHook.reel) */
    private void pullTick(AbstractFish f, Player owner, boolean reeling) {
        f.getNavigation().stop();
        faceAway(f, owner);
        if (!reeling) {
            f.setDeltaMovement(0, f.getDeltaMovement().y, 0);
        }
        this.tug(f);
    }

    /** a rest's tick */
    private void restTick(AbstractFish f, Player owner, FishingData.Fight cfg, boolean reeling) {
        double h = flatDistance(f, owner);
        if (reeling) {
            this.holdDistance = h;                  // reeled in: it is held from here
        } else {
            // calm: it holds or drifts, never swims away. Its own swimming is stopped, its movement eased, and anything that
            // would carry it further than where it was left is taken back
            f.getNavigation().stop();
            Vec3 v = f.getDeltaMovement().scale(cfg.restDrag());
            if (h > this.holdDistance + 0.1 && h > 1.0E-4) {
                Vec3 back = owner.position().subtract(f.position()).multiply(1, 0, 1).normalize().scale(Math.min(0.1, h - this.holdDistance));
                v = new Vec3(back.x, v.y, back.z);
            }
            f.setDeltaMovement(v);
            this.holdDistance = Math.min(this.holdDistance, h);
        }
    }

    /** a pulling fish near the surface shows it a little: a few rings of water every few ticks, no sound */
    private void tug(AbstractFish f) {
        if (!(this.level() instanceof ServerLevel level) || this.tickCount % 6 != 0 || !this.nearTop(f)) {
            return;
        }
        int top = Water.surface(level, f.blockPosition());
        double y = top != Water.NO_SURFACE ? top + 1.0 : Mth.floor(f.getY() + f.getBbHeight()) + 1.0;
        double w = f.getBbWidth() * 0.3;
        level.sendParticles(ParticleTypes.FISHING, f.getX(), y, f.getZ(), 2, w, 0.0, w, 0.0);
    }

    /** the running fish swims its heading, fast; where water does not go on that way it turns along the bank; at the end of the line
     *  it is held there */
    private void runOn(AbstractFish f, Player owner, FishingData.Fight cfg) {
        Vec3 dir = this.heading;
        f.getNavigation().stop();           // its own swimming would turn it about; on a run it goes where the run goes, head first
        if (this.atEnd) {
            f.setDeltaMovement(0, 0, 0);
            return;
        }
        if (!this.openAhead(f, dir)) {
            Vec3 side = new Vec3(-dir.z * this.bankSide, 0, dir.x * this.bankSide);
            if (this.openAhead(f, side)) {
                dir = side;
            } else if (this.openAhead(f, side.scale(-1))) {
                this.bankSide = -this.bankSide;
                dir = side.scale(-1);
            } else {
                f.setDeltaMovement(0, 0, 0);            // boxed in: it pulls where it is
                return;
            }
        }
        double s = cfg.runSpeed(this.fishClass) * this.core.runShare(cfg);     // (0.16.0) by tier; a tiring fish runs slower, takes less line
        f.setDeltaMovement(dir.x * s, 0, dir.z * s);
        float yaw = yawAlong(dir.x, dir.z);      // head first, away
        f.setYRot(yaw);
        f.setYBodyRot(yaw);
        f.setYHeadRot(yaw);
    }

    /** whether there is water a little ahead of the fish that way, at its middle */
    private boolean openAhead(AbstractFish f, Vec3 dir) {
        double reach = f.getBbWidth() * 0.5 + 0.6;
        return Water.isWater(this.level(), BlockPos.containing(f.getX() + dir.x * reach, f.getY() + f.getBbHeight() * 0.5, f.getZ() + dir.z * reach));
    }

    /** a run near the surface shows: splashes and the fish's swimming, every few ticks */
    private void cue(AbstractFish f) {
        if (!(this.level() instanceof ServerLevel level) || (this.tickCount & 1) != 0 || !this.nearTop(f)) {
            return;
        }
        double y = Mth.floor(f.getY() + f.getBbHeight()) + 1.0;
        int top = Water.surface(level, f.blockPosition());
        if (top != Water.NO_SURFACE) {
            y = top + 1.0;
        }
        double w = f.getBbWidth() * 0.5;
        level.sendParticles(ParticleTypes.SPLASH, f.getX(), y, f.getZ(), 3, w, 0.0, w, 0.0);
        level.sendParticles(ParticleTypes.FISHING, f.getX(), y, f.getZ(), 2, w, 0.0, w, 0.0);
        if (this.tickCount % 8 == 0) {
            level.playSound(null, f.getX(), y, f.getZ(), this.random.nextBoolean() ? SoundEvents.FISH_SWIM : SoundEvents.GENERIC_SPLASH,
                SoundSource.NEUTRAL, 0.3F, 0.9F + this.random.nextFloat() * 0.3F);
        }
    }

    /** (0.16.2) a running fish reeled against too long: the line parts. The fish swims off alive and free, the tool break sound plays at the player, the rod
     *  loses snap_damage uses; the line is gone (nothing else is lost) */
    private void snap(Player owner, FishingData.Fight cfg) {
        AbstractFish f = this.fish;
        this.letGo();
        pushAway(f, owner);
        this.level().playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F,
            0.8F + this.random.nextFloat() * 0.4F);
        snapSounds++;
        InteractionHand hand = this.rodHand(owner);
        if (hand != null && cfg.snapDamage() > 0) {
            owner.getItemInHand(hand).hurtAndBreak(cfg.snapDamage(), owner, hand);
        }
        this.snappedOff = f;
        this.discard();
    }

    /** the fish's level to every client, only when it changes */
    private void syncFight() {
        int value = this.bendLevel();
        if (((HookLevel) this).creel$fishLevel() != value) {
            ((HookLevel) this).creel$setFishLevel(value);
        }
    }

    /** the fish comes off the line, an ordinary fish again (its own persistence back); the bobber falls back to the water */
    private void letGo() {
        if (this.fish != null && this.fish.isAlive()) {
            ((MobAccessor) this.fish).creel$setPersistenceRequired(this.fishWasKept);
        }
        this.fish = null;
        this.fishClass = null;
        this.line = Line.WAITING;
        this.lastCore = this.core != null ? this.core : this.lastCore;
        this.core = null;
        this.atEnd = false;
        this.fought = false;
        this.syncFight();
        this.reading = null;
        this.timeUntilLured = 0;
        this.timeUntilHooked = 0;
        if (this.getHookedIn() != null) {
            this.vanilla().creel$setHookedEntity(null);
        }
    }

    /** not reeled in time: the fish throws the hook and darts away from the player */
    private void shakeFree() {
        AbstractFish f = this.fish;
        this.letGo();
        Player owner = this.getPlayerOwner();
        if (owner != null) {
            pushAway(f, owner);
        }
        this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.15F, 0.7F);
    }

    /** One tick of held use (CastingRodItem.onUseTick, server): the fish is drawn toward the player through the water, kept under the
     *  surface, at its tier's reel speed (0.16.1: a fighting fish at the pace that brings it in as it tires, FightCore.pacedReel); near
     *  the player a beaten or tired resting fish is landed. Returns whether there is still a fish to reel. */
    public boolean reel(Player player) {
        if (this.fish == null || this.line == Line.WAITING) {
            return false;
        }
        this.lastReel = this.tickCount;
        if (this.line == Line.BITE) {
            this.beginFight();              // the hook is set: a fish with fight in it begins calm, in a rest (0.16.1)
        }
        AbstractFish f = this.fish;
        Vec3 d = player.position().subtract(f.position());
        double h = d.horizontalDistance();
        boolean nearTop = this.nearTop(f);
        boolean near = h <= LAND_DISTANCE || (h <= LAND_AT_BANK && f.horizontalCollision);
        FishingData.Fight cfg = FishingData.fight();
        // (0.16.1) at the bank: a beaten fish is landed, and so is a resting one with at most land_below of its stamina left (the fight
        // is over); a fresher resting fish is spooked by the bank into a pull. A pulling or running fish is held off there.
        boolean tiredRest = this.line == Line.REST && this.core.staminaShare() <= cfg.landBelow();
        if (near && nearTop && (this.line == Line.ON_LINE || tiredRest)) {
            this.land(player);
            return false;
        }
        if (near && this.line == Line.REST && !tiredRest) {
            this.core.spook(cfg);
            this.line = this.core.line();
        }
        boolean fighting = this.line == Line.RUN || this.line == Line.PULL;
        // against a run the reel gains almost nothing, against a pull a little
        double gain = switch (this.line) {
            case RUN -> cfg.runReelGain();
            case PULL -> cfg.pullReelGain();
            case ON_LINE -> this.fought ? cfg.beatenReelGain() : 1.0;     // (0.16.0) a beaten fish comes in faster: the limp part is short
            default -> 1.0;
        };
        double reelSpeed = FishingData.reelSpeed(this.fishClass);
        double speed;
        if (this.line == Line.ON_LINE) {
            speed = reelSpeed * gain;
        } else {
            // (0.16.1) the pace: a fighting fish comes in no faster than brings it to the landing distance about as it tires; one with
            // fight left is never drawn nearer than that (only while it pulls or runs: a resting one was landed or spooked above)
            speed = near ? 0 : FightCore.pacedReel(cfg, reelSpeed, h - LAND_DISTANCE, this.core.stamina()) * gain;
        }
        // the line pulls up as well as in: the fish rises to just under the surface and is kept there
        double vy = Water.isWater(this.level(), BlockPos.containing(f.getX(), f.getY() + f.getBbHeight() + 0.2, f.getZ())) ? 0.06 : 0.0;
        f.setDeltaMovement(d.x / h * speed, vy, d.z / h * speed);
        // drawn head first; a fish fighting the line is dragged tail first, facing away
        float yaw = yawAlong(d.x, d.z) + (fighting ? 180.0F : 0.0F);
        f.setYRot(yaw);
        f.setYBodyRot(yaw);
        // the reel's creak, steady (as 0.13.0): the fish's state is told by the click below
        if (this.reelTicks++ % 5 == 0) {
            this.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS,
                0.35F, 1.5F + this.random.nextFloat() * 0.2F);
        }
        this.click(player, cfg);
        return true;
    }

    /** (0.15.0; 0.16.2 by the fish's state alone) The reel's click, only while a hooked fish is reeled: vanilla's hi-hat note (the
     *  hotbar's tick), click_ticks apart for the fish's state (rest, bitten or beaten 17, pull or easing 9, run 4: about 70, 133 and 300
     *  a minute), at that state's pitch. It never quickens with time: the run's racing tempo is the warning to let go. */
    private void click(Player player, FishingData.Fight cfg) {
        int gap = cfg.clickGap(this.line);
        int since = this.tickCount - this.lastClick;
        if (since < gap) {
            return;
        }
        this.lastClickGap = since;
        this.lastClick = this.tickCount;
        this.clicks++;
        float pitch = (float) cfg.clickPitch(this.line);
        this.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS,
            (float) cfg.clickVolume(), pitch);
    }

    /** lifted out in a short arc to the player's feet, alive; the line is gone, the rod loses 1, the catch counts; (0.17.0) and the chance
     *  of a treasure is rolled (Treasure); (0.19.0) unless the hand without the rod takes it: a water bucket, a shulker box or a bundle
     *  (CatchInto), when there is no arc */
    private void land(Player player) {
        AbstractFish f = this.fish;
        this.letGo();
        // out of the water first (it is just under the surface while reeled), so the bank it is pressed against cannot stop the arc
        int top = Water.surface(this.level(), f.blockPosition());
        if (top != Water.NO_SURFACE) {
            Vec3 was = f.position();
            f.setPos(was.x, Math.max(was.y, top + 1.05), was.z);
            if (!this.level().noCollision(f)) {
                f.setPos(was.x, was.y, was.z);
            }
        }
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH, f.getX(), f.getY(), f.getZ(), 6, f.getBbWidth() * 0.5, 0.0, f.getBbWidth() * 0.5, 0.0);
        }
        InteractionHand hand = this.rodHand(player);
        // (0.19.0) into the other hand's bucket, box or bundle: then it is gone and is not flung (from a boat it would fall back in)
        boolean taken = this.level() instanceof ServerLevel level && CatchInto.take(level, player, hand, f);
        if (!taken) {
            this.fling(f, player);
        }
        this.level().playSound(null, f.getX(), f.getY(), f.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 1.2F);
        this.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
            1.0F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
        // (0.17.0) the treasure: one roll, here only (a fish lost never gets here), thrown from the fish to the player; before the rod
        // is worn, so the loot sees the rod as it was
        if (this.level() instanceof ServerLevel level) {
            Treasure.roll(level, this, f, player, hand != null ? player.getItemInHand(hand) : ItemStack.EMPTY);
        }
        if (hand != null) {
            player.getItemInHand(hand).hurtAndBreak(1, player, hand);
        }
        player.awardStat(Stats.FISH_CAUGHT, 1);
        this.landed = f;
        this.discard();
    }

    /** (0.19.0: split out of land, unchanged) the short arc to the player's feet, alive, and the landed fish's mark */
    private void fling(AbstractFish f, Player player) {
        Vec3 d = player.position().subtract(f.position());
        // up to a block above the higher of the two, and down at the player's feet: the flight's length in ticks by the game's own air
        // physics (gravity 0.08, drag 0.98 up and down, 0.91 sideways), and the sideways speed that covers the distance in that time,
        // aimed a little short so the fish drops in front of the player and not onto them
        double vy = Math.sqrt(0.16 * (Math.max(0.0, d.y) + 1.0));
        double y = 0.0, v = vy, reach = 0.0, k = 1.0;
        for (int t = 0; t < 100 && (t < 2 || v > 0.0 || y > d.y); t++) {
            y += v;
            v = (v - 0.08) * 0.98;
            reach += k;
            k *= 0.91;
        }
        double s = 0.85 / reach;
        f.setDeltaMovement(d.x * s, vy, d.z * s);
        f.needsSync = true;
        if (f instanceof FishEntity fe) {
            fe.markLanded();        // 0.12.4: it flops on in the block it comes down on, not back into the water (FishEntity.getFlopSound)
        }
    }

    /** our rod reels the line in with use only when nothing is on it (a fish on it is reeled by holding use); as vanilla, then */
    @Override
    public int retrieve(ItemStack rod) {
        if (this.fish != null) {
            this.letGo();
            this.discard();
            return 0;
        }
        return super.retrieve(rod);
    }

    /** the line gone any way (put away, too far, the player gone, reeled in): the fish swims free */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.fish != null) {
            this.letGo();
        }
        super.remove(reason);
    }

    /** Vanilla's hook state is a private enum; this sets it by reflection, on both sides, at the one moment it matters: when a hook takes
     *  or loses an entity while bobbing. Vanilla's own rod never does (it takes entities only in flight), so for it this changes nothing. */
    public static final class State {
        private static final Field FIELD;
        private static final Object FLYING, BOBBING, HOOKED;

        static {
            Field f = null;
            Object fly = null, bob = null, hook = null;
            try {
                f = FishingHook.class.getDeclaredField("currentState");
                f.setAccessible(true);
                for (Object o : f.getType().getEnumConstants()) {
                    switch (((Enum<?>) o).name()) {
                        case "FLYING" -> fly = o;
                        case "BOBBING" -> bob = o;
                        case "HOOKED_IN_ENTITY" -> hook = o;
                        default -> { }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.error("creel: cannot reach the fishing hook's state, the bobber will not ride a hooked fish", e);
                f = null;
            }
            FIELD = f;
            FLYING = fly;
            BOBBING = bob;
            HOOKED = hook;
        }

        private State() {}

        public static void afterHookedChanged(FishingHook hook, boolean hasEntity) {
            if (FIELD == null) {
                return;
            }
            try {
                Object s = FIELD.get(hook);
                if ((hasEntity && s == BOBBING) || (!hasEntity && s == HOOKED)) {
                    FIELD.set(hook, FLYING);
                }
            } catch (IllegalAccessException e) {
                LOG.error("creel: cannot set the fishing hook's state", e);
            }
        }
    }
}
