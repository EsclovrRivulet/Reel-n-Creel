package io.github.esclovrrivulet.creel.entity;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.fish.FishTable;
import io.github.esclovrrivulet.creel.fish.Size;
import io.github.esclovrrivulet.creel.fish.Sizes;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import io.github.esclovrrivulet.creel.compat.Compat;

/** /creel summon (docs/SIZES.md), registered under /creel by habitat.CreelCommands (0.16.5: moved out of SizeCommands, whose test
 *  subcommands now live in the test-only jar, test/creeltest):
 *  summon <fish> [small|ordinary|large|trophy]   that fish where the player stands, or in the water block looked at; with a size,
 *                 exactly that size (no wobble), else rolled as any fish's; returns 1 */
public final class SummonCommand {
    private SummonCommand() {}

    static final List<String> SIZE_KEYS = Arrays.stream(Size.values()).map(s -> s.key).toList();

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root, java.util.function.Function<CommandSourceStack, BlockPos> lookedAt) {
        root.then(Commands.literal("summon")
            .then(Commands.argument("fish", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(FishTable.BY_ID.keySet(), b))
                .executes(ctx -> summon(ctx.getSource(), lookedAt.apply(ctx.getSource()), StringArgumentType.getString(ctx, "fish"), null))
                .then(Commands.argument("size", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(SIZE_KEYS, b))
                    .executes(ctx -> summon(ctx.getSource(), lookedAt.apply(ctx.getSource()), StringArgumentType.getString(ctx, "fish"),
                        StringArgumentType.getString(ctx, "size"))))));
    }

    /** a fish in words: name, species, size and factor, hitbox and health (also used by the test commands, test/creeltest) */
    static String describe(FishEntity f) {
        return f.getName().getString() + " (" + f.species().id() + ", " + f.size().key + String.format(Locale.ROOT, " x%.3f", f.sizeFactor())
            + String.format(Locale.ROOT, ", hitbox %.3f x %.3f, health %.0f of %.0f)", f.getBbWidth(), f.getBbHeight(), f.getHealth(), f.getMaxHealth());
    }

    private static int summon(CommandSourceStack source, BlockPos p, String id, String sizeKey) {
        EntityType<FishEntity> type = CreelMod.FISH.get(id);
        Size size = sizeKey == null ? null : Size.byKey(sizeKey);
        if (type == null || (sizeKey != null && size == null)) {
            source.sendFailure(Component.literal("creel summon: no fish " + id + (sizeKey != null && size == null ? " or no size " + sizeKey : "")));
            return 0;
        }
        ServerLevel level = source.getLevel();
        FishEntity f = Compat.create(type, level, EntitySpawnReason.COMMAND);
        if (f == null) {
            return 0;
        }
        f.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, source.getRotation().y, 0.0F);
        f.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.COMMAND, null);
        if (size != null) {
            f.setSize(size, Sizes.scale(size), true);
        }
        level.addFreshEntityWithPassengers(f);
        source.sendSuccess(() -> Component.literal("creel summon: " + describe(f) + " at " + p.toShortString()), false);
        return 1;
    }
}
