package io.github.esclovrrivulet.creel.fishing;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** (0.17.0, docs/FISHING.md "Rewards") Every fisherman villager buys our meat for emeralds (the "trades" block of fishing.json: 1 raw
 *  fish, or 4 fish morsels, for 1 emerald) as offers EXTRA to vanilla's: vanilla's trade tags and trade sets are not touched, so ours
 *  never compete with a vanilla offer for a slot. They are appended to the villager's own offer list when a player interacts with a
 *  fisherman, on the server, through Fabric's UseEntityCallback, which runs before Villager.mobInteract: the offers are made then (as
 *  opening the trade screen would make them), ours are added at the end, and vanilla carries on and opens the screen with them in. So
 *  fishermen already in a world get them as well as new ones. Offers are saved with the villager; a later level's offers come after
 *  ours. Nothing runs per tick. */
public final class FishermanTrades {
    private FishermanTrades() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() && !player.isSpectator() && entity instanceof Villager v) {
                addTo(v);
            }
            return InteractionResult.PASS;          // never takes the interaction: vanilla's own runs on
        });
    }

    /** whether that villager is a fisherman */
    static boolean isFisherman(Villager v) {
        return v.getVillagerData().profession().is(VillagerProfession.FISHERMAN);
    }

    /** Adds to a fisherman (server side) whichever of our offers he does not have yet; returns how many were added. Any other villager,
     *  or one with no profession, is left alone. */
    static int addTo(Villager v) {
        if (!isFisherman(v)) {
            return 0;
        }
        MerchantOffers offers = v.getOffers();      // vanilla's offers for his level are made first if he has none yet
        FishingData.Trades t = FishingData.trades();
        int added = 0;
        for (FishingData.Buy b : t.buys()) {
            Item item = BuiltInRegistries.ITEM.getValue(b.item());
            if (item == Items.AIR || has(offers, item)) {
                continue;
            }
            offers.add(new MerchantOffer(new ItemCost(item, b.count()), new ItemStack(Items.EMERALD), t.maxUses(), t.xp(), t.priceMultiplier()));
            added++;
        }
        return added;
    }

    /** an offer is there when one takes that item first and gives emeralds (ours from before, or a datapack's own version of it) */
    static boolean has(MerchantOffers offers, Item item) {
        for (MerchantOffer o : offers) {
            if (o.getItemCostA().item().value() == item && o.getResult().is(Items.EMERALD)) {
                return true;
            }
        }
        return false;
    }
}
