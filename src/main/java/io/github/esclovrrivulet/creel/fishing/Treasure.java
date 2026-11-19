package io.github.esclovrrivulet.creel.fishing;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import io.github.esclovrrivulet.creel.CreelMod;

/** (0.17.0, docs/FISHING.md "Rewards") The treasure with a landed fish: rolled once, only at the landing (CastingHook.land), with the
 *  rod's chance (FishingData.treasureChance: the rod's own, plus treasure.luck_of_the_sea of it per level of Luck of the Sea, the level
 *  the bite reads). What comes is our own loot table, data/creel/loot_table/gameplay/fishing/treasure.json (a datapack may change it),
 *  rolled in vanilla's fishing context; it is thrown from the fish to the player as vanilla's fishing loot is (FishingHook.retrieve),
 *  and picked up as any item is: into the inventory if there is room, else it lies at the player's feet. No message, no sound of its own. */
public final class Treasure {
    public static final ResourceKey<LootTable> TABLE = ResourceKey.create(Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "gameplay/fishing/treasure"));

    /** for the tests: the chance used at the landing instead of the data's, when 0 or more (-1: the data's) */
    static double forced = -1;

    private Treasure() {}

    /** the chance for a fish landed on that hook's line: its rod and its Luck of the Sea level */
    static double chance(CastingHook hook) {
        return forced >= 0 ? forced : FishingData.treasureChance(hook.rod(), hook.luck());
    }

    /** vanilla's fishing loot context: where the fish is, the rod, the hook, luck as vanilla's (the hook's luck plus the player's) */
    static LootParams params(ServerLevel level, Vec3 at, ItemStack rod, Entity hook, float luck) {
        return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, at).withParameter(LootContextParams.TOOL, rod)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, hook).withLuck(luck).create(LootContextParamSets.FISHING);
    }

    /** At the landing: one roll of the chance; on success the table's items are thrown from the fish's position to the player. Returns
     *  how many item entities were thrown. */
    static int roll(ServerLevel level, CastingHook hook, Entity fish, Player player, ItemStack rod) {
        if (hook.getRandom().nextDouble() >= chance(hook)) {
            return 0;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(TABLE);
        int thrown = 0;
        for (ItemStack stack : table.getRandomItems(params(level, fish.position(), rod, hook, hook.luck() + player.getLuck()))) {
            ItemEntity item = new ItemEntity(level, fish.getX(), fish.getY(), fish.getZ(), stack);
            // vanilla's own throw of fishing loot (FishingHook.retrieve)
            double dx = player.getX() - fish.getX(), dy = player.getY() - fish.getY(), dz = player.getZ() - fish.getZ();
            item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level.addFreshEntity(item);
            thrown++;
        }
        return thrown;
    }
}
