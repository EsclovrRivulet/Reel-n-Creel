package io.github.esclovrrivulet.creel.fishing;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import io.github.esclovrrivulet.creel.compat.Compat;

/** (0.19.0, docs/FISHING.md "Landing") Where a landed fish goes when the hand without the rod holds something to take it, so that a
 *  fish landed from a boat at sea, which the arc would drop back into the water, is not lost. Called once, from CastingHook.land, on
 *  the server; nothing runs per tick.
 *  - A water bucket: the fish goes into it alive, by vanilla's own pickup (Bucketable.bucketMobPickup, exactly what using the bucket
 *    on the fish does: its bucket item, the fill sound, the filled-bucket trigger, creative mode).
 *  - One shulker box (any colour) or one bundle: the fish becomes what a player's kill of it drops, rolled from its own loot table as
 *    LivingEntity.dropFromLootTable rolls it (while it is still alive and in the world: our tables read its size), put into the
 *    container; whatever does not fit is thrown to the player as the treasure is (Treasure.toss); the kill's experience comes as an
 *    orb at the player; the fish is then discarded (no death, no second drops).
 *  Anything else, a stack of more than one, or no rod hand: the fish is left to the old landing. */
public final class CatchInto {
    /** a shulker box's slots (ShulkerBoxBlockEntity.CONTAINER_SIZE) */
    static final int BOX_SLOTS = 27;

    /** for the tests: copies of the drops the last fish taken into a container gave, before they were put in */
    static List<ItemStack> lastDrops = List.of();

    private CatchInto() {}

    /** the hand that does not hold the rod, or null when the rod is in neither */
    static InteractionHand otherHand(InteractionHand rodHand) {
        return rodHand == null ? null : rodHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** At the landing, the fish lifted out but not flung: returns true when the other hand took it (then it is gone), false when it is
     *  left to the old landing (nothing was changed) */
    static boolean take(ServerLevel level, Player player, InteractionHand rodHand, AbstractFish fish) {
        InteractionHand other = otherHand(rodHand);
        if (other == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(other);
        if (held.is(Items.WATER_BUCKET)) {
            return Compat.bucketMobPickup(player, other, fish);
        }
        boolean bundle = held.is(ItemTags.BUNDLES);
        if (held.getCount() != 1 || !(bundle || held.is(ItemTags.SHULKER_BOXES))) {
            return false;
        }
        // as vanilla's kill (LivingEntity.shouldDropLoot): no drops from a baby or with mob drops off; then it is landed alive instead
        if (fish.isBaby() || !level.getGameRules().get(GameRules.MOB_DROPS)) {
            return false;
        }
        List<ItemStack> drops = drops(level, player, fish);
        List<ItemStack> copies = new ArrayList<>();
        int total = 0;
        for (ItemStack s : drops) {
            copies.add(s.copy());
            total += s.getCount();
        }
        lastDrops = copies;
        List<ItemStack> left = bundle ? intoBundle(held, drops) : intoBox(held, drops);
        int notIn = 0;
        for (ItemStack s : left) {
            notIn += s.getCount();
            Treasure.toss(level, fish, player, s);
        }
        if (bundle && notIn < total) {
            // vanilla's sound of putting something into a bundle (BundleItem.playInsertSound), heard by the player too
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F,
                0.8F + level.getRandom().nextFloat() * 0.4F);
        }
        // a player's kill gives its experience (LivingEntity.dropExperience, with the kill's enchantment effects), as an orb at the player
        if (fish.shouldDropExperience()) {
            int xp = fish.getExperienceReward(level, player);
            if (xp > 0) {
                ExperienceOrb.award(level, player.position(), xp);
            }
        }
        fish.discard();
        return true;
    }

    /** the fish's own loot table rolled as a kill by this player (LivingEntity.dropFromLootTable: the fish, where it is, a player's
     *  attack, the player as the last to hurt it and their luck), into a list instead of the world */
    private static List<ItemStack> drops(ServerLevel level, Player player, AbstractFish fish) {
        List<ItemStack> out = new ArrayList<>();
        Optional<ResourceKey<LootTable>> table = fish.getLootTable();
        if (table.isPresent()) {
            fish.setLastHurtByPlayer(player, LivingEntity.PLAYER_HURT_EXPERIENCE_TIME);
            DamageSource kill = player.damageSources().playerAttack(player);
            fish.dropFromLootTable(level, kill, true, table.get(), s -> {
                if (!s.isEmpty()) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    /** into a bundle as vanilla's bundle takes items (BundleContents.Mutable.tryInsert: by weight); returns what did not go in */
    private static List<ItemStack> intoBundle(ItemStack bundle, List<ItemStack> drops) {
        BundleContents.Mutable contents = Compat.mutable(bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        List<ItemStack> left = new ArrayList<>();
        boolean any = false;
        for (ItemStack s : drops) {
            if (s.getItem().canFitInsideContainerItems()) {
                any |= contents.tryInsert(s) > 0;
            }
            if (!s.isEmpty()) {
                left.add(s);
            }
        }
        if (any) {
            bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        }
        return left;
    }

    /** into a shulker box's 27 slots: first onto stacks of the same item up to its stack size, then into empty slots; returns what did
     *  not go in (the box is left untouched when nothing did) */
    private static List<ItemStack> intoBox(ItemStack box, List<ItemStack> drops) {
        NonNullList<ItemStack> slots = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
        List<ItemStack> left = new ArrayList<>();
        boolean any = false;
        for (ItemStack s : drops) {
            if (s.getItem().canFitInsideContainerItems()) {
                for (int i = 0; i < BOX_SLOTS && !s.isEmpty(); i++) {
                    ItemStack in = slots.get(i);
                    if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, s)) {
                        int n = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
                        if (n > 0) {
                            in.grow(n);
                            s.shrink(n);
                            any = true;
                        }
                    }
                }
                for (int i = 0; i < BOX_SLOTS && !s.isEmpty(); i++) {
                    if (slots.get(i).isEmpty()) {
                        slots.set(i, s.split(Math.min(s.getCount(), s.getMaxStackSize())));
                        any = true;
                    }
                }
            }
            if (!s.isEmpty()) {
                left.add(s);
            }
        }
        if (any) {
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        }
        return left;
    }
}
