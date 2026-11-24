package io.github.esclovrrivulet.creel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import io.github.esclovrrivulet.creel.CreelMod;
import io.github.esclovrrivulet.creel.compat.ClientBridge;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;
import io.github.esclovrrivulet.creel.fishing.RodTable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** (1.21.1 build only, client) Our rods, and vanilla's fishing rod, carrot on a stick and warped fungus on a stick, drawn in the hand
 *  with their 3D held models and flat everywhere else, as 26.x's item definitions draw them (assets/<namespace>/items/<id>.json: the
 *  flat model in the inventory, on the ground and in a frame, the held model in any other context, picked by the cast and, for our
 *  rods, the bend step).
 *
 *  1.21.1 has no item definitions: an item has one model, models/item/<id>.json (left flat), and that model's overrides. So each of
 *  these items has a second, "held root" model, creel:item/held/<namespace>/<id>, made from its 26.x definition by tools/convert_data.py
 *  (the item_models rule): its overrides pick the same held models by the same choices, through two item properties of ours (cast and
 *  bend, below). The loader part registers the nine root models to be loaded (Fabric's model loading plugin, NeoForge's additional
 *  models) and gives here the way to find them baked. mixin.HeldItemModelMixin hands each item drawn to held(): for these items, in
 *  any context but GUI, GROUND and FIXED, the root model's choice is drawn instead of the flat model.
 *
 *  The holder: the overrides need who holds the item (whether their line is out, how far their rod is bent). ItemRenderer.render is
 *  not told; mixin.HeldItemOwnerMixin keeps the entity ItemInHandRenderer.renderItem draws for (first and third person, any holder)
 *  while it draws.
 *
 *  The mark: what 26.x keeps on its item render state (client.HeldRodMark, mixin.ItemModelResolverMixin): which of our rods is being
 *  drawn, in which hand context, for which player and bent to which step, so that the same render call can turn it (client.RodAnim,
 *  after the display transform) and capture the pose it was drawn with (client.RodPose, before the pose is popped). Set for every item
 *  drawn, cleared once captured. Render thread only. */
public final class HeldRodModels {
    private HeldRodModels() {}

    /** the cast: 1 when the holder's line is out and this stack is in the arm it leaves from (26.x's minecraft:fishing_rod/cast, with
     *  the arm as the mod decides it there: compat.ClientBridge.fishingArm), else 0 */
    public static final ResourceLocation CAST = ResourceLocation.fromNamespaceAndPath(CreelMod.MOD_ID, "cast");
    /** the bend step to draw (client.RodFlex), as (step + BEND_OFFSET) / BEND_SPAN: an item property here is a number from 0 to 1 */
    public static final ResourceLocation BEND = ResourceLocation.fromNamespaceAndPath(CreelMod.MOD_ID, "rod_bend");
    /** the bend's encoding, the same numbers as tools/convert_data.py's BEND_OFFSET and BEND_SPAN: steps -8 to 23 fit, exact in a float */
    public static final int BEND_OFFSET = 8, BEND_SPAN = 32;
    /** vanilla's three items drawn in the hand with our models (tools/stick_rods.py) */
    private static final List<Item> STICKS = List.of(Items.FISHING_ROD, Items.CARROT_ON_A_STICK, Items.WARPED_FUNGUS_ON_A_STICK);

    /** gives an item property to the loader's registry */
    @FunctionalInterface
    public interface PropertySink {
        void register(Item item, ResourceLocation id, ClampedItemPropertyFunction function);
    }

    private static Function<ResourceLocation, BakedModel> lookup;
    private static Map<Item, ResourceLocation> roots;
    private static LivingEntity holder;

    // the mark (see above)
    private static RodTable.Rod rod;
    private static ItemDisplayContext context;
    private static int playerId, step;

    /** the item a held root model is for: our rods first, then vanilla's three */
    private static List<Item> items() {
        List<Item> out = new ArrayList<>(CreelMod.RODS.values());
        out.addAll(STICKS);
        return out;
    }

    /** an item's held root model, creel:item/held/<namespace>/<path> (the name tools/convert_data.py writes it under) */
    private static ResourceLocation root(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return ResourceLocation.fromNamespaceAndPath(CreelMod.MOD_ID, "item/held/" + id.getNamespace() + "/" + id.getPath());
    }

    /** the held root models to load (the loader part registers them; needs the items registered) */
    public static List<ResourceLocation> rootModels() {
        return items().stream().map(HeldRodModels::root).toList();
    }

    /** how the loader finds a model it loaded: the baked model, or null or the missing model when it has none */
    public static void lookup(Function<ResourceLocation, BakedModel> find) {
        lookup = find;
    }

    /** our two item properties: the cast for our rods and vanilla's fishing rod, the bend for our rods (the loader part, at client
     *  start; the properties are read only when a held root model is resolved, held below) */
    public static void registerProperties(PropertySink sink) {
        ClampedItemPropertyFunction cast = (stack, level, entity, seed) -> cast(stack, entity);
        ClampedItemPropertyFunction bend = (stack, level, entity, seed) -> (RodFlex.step(stack, entity) + BEND_OFFSET) / (float) BEND_SPAN;
        for (Item item : CreelMod.RODS.values()) {
            sink.register(item, CAST, cast);
            sink.register(item, BEND, bend);
        }
        sink.register(Items.FISHING_ROD, CAST, cast);
    }

    private static float cast(ItemStack stack, LivingEntity entity) {
        return entity instanceof Player player && player.fishing != null
            && ClientBridge.heldByArm(player, ClientBridge.fishingArm(player)) == stack ? 1.0F : 0.0F;
    }

    /** the entity ItemInHandRenderer.renderItem draws an item for, while it does (null after) */
    public static void holder(LivingEntity entity) {
        holder = entity;
    }

    /** the model to draw this stack with in that context: the held root model's choice for one of these items in a hand (any context but
     *  the inventory, the ground and a frame), else the model given. Marks the item for RodAnim and RodPose (above). */
    public static BakedModel held(BakedModel model, ItemStack stack, ItemDisplayContext ctx) {
        rod = null;
        if (lookup == null || stack.isEmpty() || ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED) {
            return model;
        }
        if (roots == null) {
            Map<Item, ResourceLocation> m = new IdentityHashMap<>();
            for (Item item : items()) {
                m.put(item, root(item));
            }
            roots = m;
        }
        ResourceLocation id = roots.get(stack.getItem());
        if (id == null) {
            return model;
        }
        Minecraft mc = Minecraft.getInstance();
        BakedModel root = lookup.apply(id);
        if (root == null || root == mc.getModelManager().getMissingModel()) {
            return model;                   // not loaded (a resource pack left it out): the flat picture, as before
        }
        LivingEntity entity = holder;
        BakedModel chosen = root.getOverrides().resolve(root, stack, mc.level, entity, 0);
        mark(stack, ctx, entity);
        return chosen != null ? chosen : root;
    }

    /** as 26.x's ItemModelResolverMixin marks the render state: our rod (or vanilla's fishing rod, for its pose only) in a hand of a
     *  player, after the bend step was picked */
    private static void mark(ItemStack stack, ItemDisplayContext ctx, LivingEntity entity) {
        boolean hand = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!hand || !(entity instanceof Player player)) {
            return;
        }
        if (stack.getItem() instanceof CastingRodItem item) {
            rod = item.rod();
            step = RodFlex.drawn(player, stack);
        } else if (stack.is(Items.FISHING_ROD)) {
            rod = StickRodTable.FISHING_ROD;
            step = 0;
        } else {
            return;
        }
        context = ctx;
        playerId = player.getId();
    }

    /** just after the display transform: our rod's own animation (never vanilla's fishing rod's, which is marked for its pose only) */
    public static void animate(PoseStack.Pose pose) {
        if (rod != null && rod != StickRodTable.FISHING_ROD) {
            RodAnim.apply(playerId, rod, context, pose);
        }
    }

    /** just before the pose is popped: the pose the marked rod was drawn with, for the line's start; the mark is then cleared, so an
     *  item drawn around this one never takes it for its own */
    public static void capture(Matrix4f pose) {
        if (rod != null) {
            RodPose.capture(playerId, rod, context, pose, step);
            rod = null;
        }
    }
}
