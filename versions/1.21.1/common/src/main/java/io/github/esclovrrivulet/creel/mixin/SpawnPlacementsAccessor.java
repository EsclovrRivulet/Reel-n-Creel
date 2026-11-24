package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (1.21.1 build only, called by the Fabric entry point) Vanilla's SpawnPlacements.register is private. On 26.x the Fabric build calls
 *  it by reflection under Mojang's name; Fabric runs 1.21.1 with its own names (intermediary), so here an invoker, which Loom's
 *  remapping resolves. (NeoForge registers placements through its own event, RegisterSpawnPlacementsEvent.) */
@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsAccessor {
    @Invoker("register")
    static <T extends Mob> void creel$register(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
                                               SpawnPlacements.SpawnPredicate<T> predicate) {
        throw new AssertionError("creel: the SpawnPlacements invoker was not applied");
    }
}
