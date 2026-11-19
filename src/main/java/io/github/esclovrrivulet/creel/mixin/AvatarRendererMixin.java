package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** Client only, cosmetic, optional. In third person a player charging a cast with one of our rods (using it with no line out) holds the
 *  arm raised back as for a throw: the game's own THROW_TRIDENT pose, chosen through MixinExtras' @ModifyReturnValue on the arm pose
 *  the renderer picks (it chains with other mods). Driven by what every client knows of every player (the use state, the bobber), so
 *  other players see it too. A use that began with the line out is never a charge, even held on after the line is in (0.12.4). The cast's forward swing is the game's own swing, sent by the server when the cast is made. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @ModifyReturnValue(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
        at = @At("RETURN"), require = 0, expect = 0)
    private static HumanoidModel.ArmPose creel$chargePose(HumanoidModel.ArmPose pose, Avatar avatar, ItemStack stack, InteractionHand hand) {
        if (stack.getItem() instanceof CastingRodItem && avatar instanceof Player player && player.isUsingItem() && player.getUsedItemHand() == hand
            && RodAnim.charging(player)) {         // 0.12.4: not a use that began as a wind or a reel
            return HumanoidModel.ArmPose.THROW_TRIDENT;
        }
        return pose;
    }
}
