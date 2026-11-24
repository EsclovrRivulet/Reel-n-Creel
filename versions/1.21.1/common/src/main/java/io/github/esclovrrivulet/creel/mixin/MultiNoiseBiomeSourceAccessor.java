package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (1.21.1 build only) The overworld's climate list is private to its biome source (habitat.ClimateTable reads it, through
 *  compat.Compat.biomeParameters). 26.x reads it by reflection under Mojang's name; Fabric runs 1.21.1 with its own names, so here an
 *  invoker, which Loom's remapping and NeoForge's Mojang names both resolve. */
@Mixin(MultiNoiseBiomeSource.class)
public interface MultiNoiseBiomeSourceAccessor {
    @Invoker("parameters")
    Climate.ParameterList<Holder<Biome>> creel$parameters();
}
