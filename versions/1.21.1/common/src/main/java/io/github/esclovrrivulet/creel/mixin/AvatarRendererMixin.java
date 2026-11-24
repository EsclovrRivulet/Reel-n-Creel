package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** AvatarRendererMixin for MINECRAFT 1.21.1 (replaces the base copy in the 1.21.1 build; what it does is described there). 1.21.1's
 *  player renderer picks the arm pose in PlayerRenderer.getArmPose(player, hand) (private, static; the stack is the hand's item), and
 *  names the raised throwing pose THROW_SPEAR (26.x: THROW_TRIDENT), the same pose. */
@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
    @ModifyReturnValue(method = "getArmPose(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
        at = @At("RETURN"), require = 0, expect = 0)
    private static HumanoidModel.ArmPose creel$chargePose(HumanoidModel.ArmPose pose, AbstractClientPlayer player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof CastingRodItem && player.isUsingItem() && player.getUsedItemHand() == hand
            && RodAnim.charging(player)) {         // 0.12.4: not a use that began as a wind or a reel
            return HumanoidModel.ArmPose.THROW_SPEAR;
        }
        return pose;
    }
}
