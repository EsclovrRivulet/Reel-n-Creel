package io.github.esclovrrivulet.creel.fishing;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One of the mod's five rods (docs/FISHING.md; RodTable): each hooks fish of two size tiers only and casts its own distance.
 *
 *  One rule: hold to act (0.12.0, after Cast n Chill).
 *  - No line out: holding use charges the cast, from nothing to full over the charge time, held at full as long as it is held, with a
 *    rising creak as it starts and a click at full; letting go casts, the bobber's launch vanilla's times the rod's cast times the power
 *    (a tap's to a full charge's), the throw's pitch rising with the power.
 *  - The line out, nothing on it: holding winds the bobber in along the water; letting go leaves it there; near the player the line is
 *    in and the bobber gone.
 *  - A fish on the line: holding reels it in (CastingHook.reel).
 *
 *  Not a FishingRodItem since 0.12.0: animation mods (Punchy) recognise vanilla's rod by that class and would animate ours as theirs;
 *  ours has its own animation (client.RodAnim). What vanilla's rod class gave us is replaced: the cast picture and the line's side come
 *  from FishingHookRenderer.getHoldingArm, which client mixin FishingHookRendererMixin extends to our rods; enchantments come from item
 *  tags, durability from the item's own properties, the statistics and the hook are ours. */
public class CastingRodItem extends Item {
    private final RodTable.Rod rod;

    /** what each player's current hold is for (server): set when the hold starts, read when it is let go. SPENT (0.12.4): a wind or a
     *  reel that has finished (the line is in, the fish landed) while use is still held; the hold goes on doing nothing until it is let
     *  go, so holding on never turns into a new charge: only a fresh press casts. (Before, the hold was stopped there, and the game,
     *  seeing use still held, started a new one at once: a charge.) */
    private enum Hold { CHARGE, WIND, SPENT }

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    public CastingRodItem(RodTable.Rod rod, Properties properties) {
        super(properties);
        this.rod = rod;
    }

    public RodTable.Rod rod() {
        return this.rod;
    }

    /** every use is a hold: charging with no line out, winding or reeling with it out (decided again each tick in onUseTick) */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            HOLDS.put(player.getUUID(), player.fishing == null ? Hold.CHARGE : Hold.WIND);
            if (player.fishing == null) {
                creak(level, player, 0.8F);
            }
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** the hold lasts as long as the button is held */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** no vanilla use pose: the animation is ours (client.RodAnim) */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    private static void creak(Level level, Player player, float pitch) {
        Holder<SoundEvent> s = SoundEvents.CROSSBOW_LOADING_START;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), s, SoundSource.PLAYERS, 0.25F, pitch);
    }

    /** each tick of the hold, on the server: the charge's sounds, or one wind or one reel on the line */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == Hold.CHARGE) {
            int held = this.getUseDuration(stack, entity) - remaining;
            int full = FishingData.handling().chargeTicks();
            if (held == Math.max(1, full / 2)) {
                creak(level, player, 1.1F);                         // the creak rises as the charge builds
            } else if (held == full) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.4F, 1.6F);
            }
            return;
        }
        if (hold == Hold.SPENT) {
            return;
        }
        if (!(player.fishing instanceof CastingHook hook)) {
            if (hold == Hold.WIND) {
                HOLDS.put(player.getUUID(), Hold.SPENT);       // the line went while winding: hold on, no cast until let go
            } else {
                HOLDS.remove(player.getUUID());
                player.stopUsingItem();
            }
            return;
        }
        boolean still = hook.fish() != null ? hook.reel(player) : hook.wind(player);
        if (!still) {
            HOLDS.put(player.getUUID(), Hold.SPENT);           // wound in or landed: the hold stays, spent, until it is let go
        }
    }

    /** let go: a charge casts; a wind, a reel or a spent hold simply stops */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return false;
        }
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold != Hold.CHARGE || player.fishing != null || !(level instanceof ServerLevel server)) {
            return false;
        }
        int held = this.getUseDuration(stack, entity) - remaining;
        this.cast(server, player, stack, held);
        return true;
    }

    /** the cast after a charge of that many ticks: vanilla's throw times the rod's cast times the power */
    public void cast(ServerLevel level, Player player, ItemStack stack, int heldTicks) {
        FishingData.Handling h = FishingData.handling();
        double power = h.power(heldTicks);
        float pitch = (0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)) * (0.85F + 0.3F * (float) h.charge(heldTicks));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, pitch);
        int lure = (int) (EnchantmentHelper.getFishingTimeReduction(level, stack, player) * 20.0F);
        int luck = EnchantmentHelper.getFishingLuckBonus(level, stack, player);
        Projectile.spawnProjectile(new CastingHook(player, level, luck, lure, this.rod.id(), power), level, stack);
        InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(hand, SwingAnimation.DEFAULT, true);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.causeUseVibration(player, GameEvent.ITEM_INTERACT_START);
    }
}
