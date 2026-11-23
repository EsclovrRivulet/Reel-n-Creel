package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** UseItemDipMixin for MINECRAFT 26.2 and 26.1.2 (replaces the base copy in those builds; what it does is described there). There
 *  startUseItem tells the hand renderer, ItemInHandRenderer.itemUsed(InteractionHand), not the player (26.3: LocalPlayer.itemUsed), so
 *  the player whose hand it is comes from the game. */
@Mixin(Minecraft.class)
public abstract class UseItemDipMixin {
    @WrapWithCondition(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;itemUsed(Lnet/minecraft/world/InteractionHand;)V"),
        require = 0, expect = 0)
    private boolean creel$noDipForOurRods(ItemInHandRenderer renderer, InteractionHand hand) {
        LocalPlayer player = ((Minecraft) (Object) this).player;
        return player == null || !(player.getItemInHand(hand).getItem() instanceof CastingRodItem);
    }
}
