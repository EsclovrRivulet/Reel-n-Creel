package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.HeldRodMark;
import io.github.esclovrrivulet.creel.client.RodFlex;
import io.github.esclovrrivulet.creel.client.StickRodTable;
import net.minecraft.world.item.Items;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** Client only, cosmetic, optional: when an item render state is filled for one of our rods held in a hand (first or third person) by
 *  a player, it is marked with the rod, the hand context and the player (client.HeldRodMark); any other item clears the mark. An
 *  @Inject at the end of the method, after vanilla and any other mod have filled the state; it changes nothing that is drawn. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("RETURN"), require = 0, expect = 0)
    private void creel$markHeldRod(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed,
                                   CallbackInfo ci) {
        if (!((Object) state instanceof HeldRodMark mark)) {
            return;
        }
        boolean hand = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        LivingEntity living = owner == null ? null : owner.asLivingEntity();
        if (hand && living instanceof Player player && stack.getItem() instanceof CastingRodItem rod) {
            mark.creel$markRod(rod.rod(), context, player.getId());
            mark.creel$markStep(RodFlex.drawn(player, stack));      // 0.13.0: the bent step just picked for this rod
        } else if (hand && living instanceof Player player && stack.is(Items.FISHING_ROD)) {
            // (0.24.0) vanilla's Fishing Rod, held as our model: marked so its pose is captured (the line leaves its tip guide); it is
            // never animated or bent (mixin.ItemLayerRenderStateMixin)
            mark.creel$markRod(StickRodTable.FISHING_ROD, context, player.getId());
            mark.creel$markStep(0);
        } else {
            mark.creel$markRod(null, null, -1);
            mark.creel$markStep(0);
        }
    }
}
