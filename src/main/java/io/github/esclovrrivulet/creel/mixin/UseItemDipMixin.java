package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** Client only, cosmetic, optional. Each time use is pressed and the item takes it, the game tells the first-person hand the item was
 *  "used", which drops the held item out of view and brings it back up (the re-equip dip). For our rods every press is a hold that
 *  changes nothing in the hand, so with the line out a run of short clicks made the rod bob down and up on every click. For one of our
 *  rods that notice is left out; for anything else it is made as always. MixinExtras' @WrapWithCondition on that one call in
 *  startUseItem (it chains with other mods' wraps); require = 0. */
@Mixin(Minecraft.class)
public abstract class UseItemDipMixin {
    @WrapWithCondition(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUsed(Lnet/minecraft/world/InteractionHand;)V"),
        require = 0, expect = 0)
    private boolean creel$noDipForOurRods(LocalPlayer player, InteractionHand hand) {
        return !(player.getItemInHand(hand).getItem() instanceof CastingRodItem);
    }
}
