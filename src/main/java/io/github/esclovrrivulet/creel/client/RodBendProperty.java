package io.github.esclovrrivulet.creel.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.CreelMod;

import java.lang.reflect.Field;

/** The item model property "creel:rod_bend" (0.13.0, client only): the bent step of one of our held rods to draw, a whole number from
 *  RodTable.FLEX_MIN to FLEX_MAX (0 straight), chosen by RodFlex. The rods' item files (written by tools/rod_models.py) pick the held
 *  model by it with vanilla's own range_dispatch, inside vanilla's fishing_rod/cast condition. Vanilla's own list of such properties
 *  has no public way in; Fabric makes the list's field accessible (its transitive access wideners) and it is filled here once, at
 *  client start, before any resource is read. */
public record RodBendProperty() implements RangeSelectItemModelProperty {
    private static final Logger LOG = LoggerFactory.getLogger("creel");
    public static final Identifier ID = Identifier.fromNamespaceAndPath(CreelMod.MOD_ID, "rod_bend");
    public static final MapCodec<RodBendProperty> MAP_CODEC = MapCodec.unit(new RodBendProperty());

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        return RodFlex.step(stack, owner);
    }

    @Override
    public MapCodec<RodBendProperty> type() {
        return MAP_CODEC;
    }

    /** adds the property to vanilla's list (CreelModClient; test/RodModelCheck). By reflection on purpose: Fabric widens the field
     *  only at run time, so javac, which compiles against the plain game jar (build.sh, no Gradle, no widened jar), cannot see it.
     *  It fails soft: the error is logged and the rest of the mod goes on. */
    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            Field f = RangeSelectItemModelProperties.class.getDeclaredField("ID_MAPPER");
            f.setAccessible(true);
            ((ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends RangeSelectItemModelProperty>>) f.get(null)).put(ID, MAP_CODEC);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("creel: cannot register the rods' bend property; the rods' held models will not load", e);
        }
    }
}
