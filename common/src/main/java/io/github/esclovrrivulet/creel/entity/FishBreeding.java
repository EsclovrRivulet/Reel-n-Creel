package io.github.esclovrrivulet.creel.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** (0.22.0; 0.23.0 the pair's meeting is a thing you see) Breeding our fish with seagrass (docs/SPAWN.md), the owner's design:
 *  - Seagrass used on one of our fish feeds it: one seagrass is used (not in creative), hearts show, and it is in love for LOVE_TICKS.
 *    That is all feeding does (0.23.0): it no longer pairs.
 *  - Two fish of one species in love within PAIR_RANGE blocks of each other find each other (FishMating: the mating goal on each fish
 *    in love looks for a mate every 10 ticks), swim together, go up under the surface, court there, and lay one Fish Spawn of the
 *    species (block.FishSpawnBlock); then both leave love and start a cooldown of COOLDOWN_TICKS, with a little experience as vanilla's
 *    breeding gives. With no place for the spawn, nothing is laid, the cooldown still starts, and a puff of smoke shows it failed.
 *  - A fish on its cooldown, in love already, in a pairing, on someone's line, or a giant (Species.breeds) cannot be fed: nothing
 *    happens and the seagrass stays. Vanilla's fish are not ours and are never fed.
 *  - A fish that breeds also follows a player holding seagrass near it (SeagrassFollowGoal).
 *  What runs: love and the cooldown are game-time stamps on the fish (FishEntity: in love until, cooled down at), saved with it; feeding
 *  compares them once. Every fish that breeds has the mating goal, whose check is one comparison of a stamp with the game time for a
 *  fish not in love; only a fish in love or in a pairing does more (FishMating). The follow goal looks for a player once a second. */
public final class FishBreeding {
    private FishBreeding() {}

    /** how long a fed fish stays in love: 600 ticks, as vanilla's animals */
    public static final int LOVE_TICKS = 600;
    /** after a pairing neither fish can be fed for 6000 ticks (5 minutes), as vanilla's parents wait */
    public static final int COOLDOWN_TICKS = 6000;
    /** how near a fish in love must be to another of its species in love to pair with it */
    public static final double PAIR_RANGE = 8.0;
    /** vanilla's entity event for an animal's hearts (Animal.handleEntityEvent); FishEntity draws them the same way */
    public static final byte HEARTS = 18;

    /** whether a fish can be fed now (the test hooks ask too) */
    public static boolean canFeed(ServerLevel level, FishEntity fish) {
        long now = level.getGameTime();
        return fish.isAlive() && fish.species().breeds() && !fish.onCooldown(now) && !fish.inLove(now) && fish.pairing() == null
            && !onLine(level, fish);
    }

    /** Seagrass used on a fish (FishEntity.mobInteract). On the client nothing is decided (it cannot know the love and the cooldown):
     *  PASS, and the server's answer swings the arm when the fish was fed. On the server: one seagrass used, hearts, in love until now
     *  + LOVE_TICKS; SUCCESS_SERVER when the fish was fed, PASS when it was refused (nothing happens and the seagrass stays). */
    public static InteractionResult feed(FishEntity fish, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.SEAGRASS) || !(fish.level() instanceof ServerLevel level) || !canFeed(level, fish)) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);                       // not in creative (ItemStack.consume, as Mob.usePlayerItem)
        level.broadcastEntityEvent(fish, HEARTS);
        fish.setLoveUntil(level.getGameTime() + LOVE_TICKS);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** whether a fish is on someone's line (any fishing hook, ours or vanilla's, hooked in it): one query of the hooks round it, asked
     *  when a fish is fed, when one in love looks for a mate, by a pair every 10 ticks, and by a fish about to follow seagrass */
    public static boolean onLine(ServerLevel level, FishEntity fish) {
        for (FishingHook hook : level.getEntitiesOfClass(FishingHook.class, fish.getBoundingBox().inflate(4.0))) {
            if (hook.getHookedIn() == fish) {
                return true;
            }
        }
        return false;
    }
}
