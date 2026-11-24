package io.github.esclovrrivulet.creel.habitat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.esclovrrivulet.creel.fish.Size;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.entity.FishEntity;
import io.github.esclovrrivulet.creel.entity.SummonCommand;
import io.github.esclovrrivulet.creel.fish.Species;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import io.github.esclovrrivulet.creel.compat.Compat;

/** /creel (permission level 2), the mod's readings in words (docs/HABITAT.md section 8):
 *  water [pos]    the class, band and grade where the player stands or looks (or at pos), and the fish that could appear there, by size
 *                 class with their weights; returns the grade (0 none, 1 narrow, 2 open, 3 deep)
 *  census [pos]   our fish nearby by species, the five budgets in use and vanilla's water-ambient pool; returns our fish counted
 *  biomes         every biome in the registry with the class and bands the mod reads for it, then a summary; returns the biome count
 *  summon <fish> [size]   see entity.SummonCommand (docs/SIZES.md)
 *  0.16.5: the test-only subcommands (fits, spawnlist, spawnfill, spawnfaults, vanillacap, salmonshare, sizes, buckettest, resave,
 *  bite, fightstate, fishtest) are in the test-only jar (test/creeltest, habitat.CreelTestCommands), which adds them to this /creel */
public final class CreelCommands {
    private CreelCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("creel").requires(Compat.gameMasters())
            .then(Commands.literal("water")
                .executes(ctx -> water(ctx.getSource(), lookedAt(ctx.getSource())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> water(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
            .then(Commands.literal("census")
                .executes(ctx -> census(ctx.getSource(), ctx.getSource().getPosition()))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> census(ctx.getSource(), Vec3.atCenterOf(BlockPosArgument.getBlockPos(ctx, "pos"))))))
            .then(Commands.literal("biomes").executes(ctx -> biomes(ctx.getSource())));
        SummonCommand.addTo(root, CreelCommands::lookedAt);      // summon (docs/SIZES.md)
        dispatcher.register(root);
    }

    private static void say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    /** the water block a player looks at within 64 blocks, else where the source stands */
    private static BlockPos lookedAt(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            HitResult hit = player.pick(64.0, 1.0F, true);
            if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && Water.isWater(player.level(), b.getBlockPos())) {
                return b.getBlockPos();
            }
            return player.blockPosition();
        }
        return BlockPos.containing(source.getPosition());
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }

    private static int water(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        Habitat.Reading r = Habitat.read(level, pos);
        String at = "creel water at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " in " + biomeId(r.biome());
        if (r.column() == null) {
            say(source, at + ": no water here");
            return 0;
        }
        if (r.place() == null) {
            say(source, at + ": no fish in this biome (creel:no_fish or a cave biome)");
            return 0;
        }
        Habitat.BandReading b = r.band();
        say(source, at + ": class " + r.place().key() + " (biome reads " + r.kind().name().toLowerCase(Locale.ROOT) + "), band " + b.band().key()
            + " (" + b.source() + (Double.isNaN(b.climate()) ? "" : " " + fmt(b.climate())) + "), grade " + r.grade().key()
            + " (surface " + r.column().surface() + ", " + r.column().depth() + " deep)");
        for (SizeClass k : SizeClass.values()) {
            List<String> fits = new ArrayList<>();
            for (Species s : FishSpawner.species(k)) {
                FishHabitat h = HabitatData.get(s.id());
                if (r.suits(h)) {
                    fits.add(s.id() + " " + h.weight());
                }
            }
            say(source, "creel could appear, " + k.label() + " (budget " + Budgets.get(k).text() + "), weight: "
                + (fits.isEmpty() ? "none" : String.join(", ", fits)));
        }
        return r.grade().ordinal();
    }

    private static int census(CommandSourceStack source, Vec3 c) {
        ServerLevel level = source.getLevel();
        FishSpawner.Present present = FishSpawner.present(level, c);
        Map<String, Integer> ours = new TreeMap<>();
        List<String> budgets = new ArrayList<>();
        int counted = 0;
        for (SizeClass k : SizeClass.values()) {
            for (FishEntity f : present.of(k)) {
                ours.merge(f.species().id(), 1, Integer::sum);
            }
            counted += present.count(k);
            budgets.add(k.label() + " " + present.count(k) + " of " + Budgets.get(k).max());
        }
        say(source, "creel census within " + FishSpawner.RANGE + " of " + fmt(c.x) + " " + fmt(c.y) + " " + fmt(c.z) + ": "
            + (ours.isEmpty() ? "none of ours" : ours.toString()));
        say(source, "creel census: budgets in use (named or kept fish not counted): " + String.join(", ", budgets));
        Map<Size, Integer> sizes = new EnumMap<>(Size.class);
        Map<String, Map<String, Integer>> bigOrSmall = new TreeMap<>();
        for (SizeClass k : SizeClass.values()) {
            for (FishEntity f : present.of(k)) {
                sizes.merge(f.size(), 1, Integer::sum);
                if (f.size() != Size.ORDINARY) {
                    bigOrSmall.computeIfAbsent(f.size().key, x -> new TreeMap<>()).merge(f.species().id(), 1, Integer::sum);
                }
            }
        }
        say(source, "creel census: sizes " + (sizes.isEmpty() ? "none" : sizes.toString().toLowerCase(Locale.ROOT))
            + (bigOrSmall.isEmpty() ? "" : "; " + bigOrSmall));
        int pool = 0, poolOurs = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(128), m -> m.getType().getCategory() == MobCategory.WATER_AMBIENT
                && !m.isPersistenceRequired() && !m.requiresCustomPersistence())) {
            pool++;
            if (EntityType.getKey(m.getType()).getNamespace().equals(CreelMod.MOD_ID)) {
                poolOurs++;
            }
        }
        NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
        String levelWide = state == null ? "" : ", level-wide " + state.getMobCategoryCounts().getInt(MobCategory.WATER_AMBIENT) + " of cap "
            + MobCategory.WATER_AMBIENT.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / 289;
        say(source, "creel census: vanilla's water-ambient pool, separate from ours: " + pool + " within 128 (" + poolOurs + " of them ours; cap "
            + MobCategory.WATER_AMBIENT.getMaxInstancesPerChunk() + " per player" + levelWide + ")");
        List<String> capped = new ArrayList<>();
        for (Map.Entry<EntityType<?>, Integer> e : VanillaCaps.caps().entrySet()) {
            capped.add(EntityType.getKey(e.getKey()) + " " + VanillaCaps.count(level, e.getKey()) + " loaded of cap " + VanillaCaps.scaledCap(level, e.getKey())
                + " (" + e.getValue() + " per player)");
        }
        say(source, "creel census: vanilla caps (natural spawns stop at the cap): " + (capped.isEmpty() ? "off" : String.join(", ", capped)));
        return counted;
    }

    private static int biomes(CommandSourceStack source) {
        Registry<Biome> registry = source.getServer().registryAccess().lookupOrThrow(Registries.BIOME);
        ClimateTable climate = ClimateTable.of(source.getServer().registryAccess());
        Set<Holder<Biome>> overworld = source.getServer().overworld().getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        Map<Habitat.Kind, Integer> kinds = new EnumMap<>(Habitat.Kind.class);
        Map<Band, Integer> bands = new EnumMap<>(Band.class);
        List<String> odd = new ArrayList<>();
        int n = 0, ow = 0, fallback = 0;
        List<Holder.Reference<Biome>> all = new ArrayList<>(registry.listElements().toList());
        all.sort((a, b) -> a.key().identifier().toString().compareTo(b.key().identifier().toString()));
        for (Holder.Reference<Biome> h : all) {
            n++;
            String id = h.key().identifier().toString();
            float temp = h.value().getBaseTemperature();
            if (!overworld.contains(h)) {
                say(source, "creel biome " + id + ": not an overworld biome, no fish");
                continue;
            }
            ow++;
            Habitat.Offer o = Habitat.offer(h::is, h.key(), temp, climate);
            kinds.merge(o.kind(), 1, Integer::sum);
            if (o.kind() != Habitat.Kind.NONE) {
                for (Band b : o.bands()) {
                    bands.merge(b, 1, Integer::sum);
                }
            }
            List<String> flags = new ArrayList<>();
            if (!o.source().equals("climate") && !o.source().equals("tag")) {
                fallback++;
                flags.add("no climate entry");
            }
            Band own = Band.ofBiomeTemperature(temp);
            if (o.source().equals("climate") && o.kind() != Habitat.Kind.SEA && o.kind() != Habitat.Kind.DEEP_SEA && !o.bands().isEmpty()) {
                Band lo = o.bands().iterator().next(), hi = lo;
                for (Band b : o.bands()) {
                    hi = b;
                }
                if (own.ordinal() < lo.ordinal() - 1 || own.ordinal() > hi.ordinal() + 1) {
                    flags.add("own temperature " + temp + " reads " + own.key() + ", far from its climate");
                }
            }
            List<String> offered = new ArrayList<>();
            int total = 0;
            if (o.kind() != Habitat.Kind.NONE) {
                for (SizeClass k : SizeClass.values()) {
                    int m = 0;
                    for (Species s : FishSpawner.species(k)) {
                        FishHabitat fh = HabitatData.get(s.id());
                        if (fh != null && o.takes(fh)) {
                            m++;
                        }
                    }
                    total += m;
                    offered.add(k.letter + " " + m);
                }
                if (total == 0) {
                    flags.add("no fish offered");
                }
            }
            if (!flags.isEmpty()) {
                odd.add(id + " (" + String.join("; ", flags) + ")");
            }
            say(source, "creel biome " + id + ": class " + kindText(o.kind()) + ", bands " + o.bandsText() + " (" + o.source() + "), temp " + temp
                + ", species it can offer " + total + (offered.isEmpty() ? "" : " (" + String.join(", ", offered) + ")")
                + (flags.isEmpty() ? "" : " [" + String.join("; ", flags) + "]"));
        }
        say(source, "creel biomes: this level's generator " + source.getLevel().getChunkSource().getGenerator().getClass().getSimpleName() + " with "
            + source.getLevel().getChunkSource().getGenerator().getBiomeSource().getClass().getSimpleName() + ", climate sampler "
            + (Habitat.sampler(source.getLevel()) != null ? "read" : "none (biome temperatures used)"));
        say(source, "creel biomes: " + n + " in the registry, " + ow + " overworld; climate list holds " + climate.size()
            + "; by class " + kinds + "; biomes able to hold each band " + bands + "; " + fallback + " without a climate entry");
        say(source, "creel biomes odd: " + (odd.isEmpty() ? "none" : String.join(", ", odd)));
        return n;
    }

    private static String kindText(Habitat.Kind k) {
        return switch (k) {
            case NONE -> "none (no fish)";
            case LAND -> "fresh, or highland by height";
            case SEA -> "coast, ocean or deep sea by depth";
            default -> k.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }
}
