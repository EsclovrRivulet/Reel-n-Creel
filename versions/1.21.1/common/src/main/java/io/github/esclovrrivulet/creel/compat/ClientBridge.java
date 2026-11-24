package io.github.esclovrrivulet.creel.compat;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** (1.21.1 build only, client) Three calls of 26.x the main tree's rod drawing makes (client.RodFlex, RodLine, RodAnim) that 1.21.1's
 *  game has no method for; versions/1.21.1/java_renames.txt sends those calls here. Each does what 26.x's own method does. */
public final class ClientBridge {
    private ClientBridge() {}

    /** 26.x's LivingEntity.getItemHeldByArm: the item in the hand on that side of the body */
    public static ItemStack heldByArm(LivingEntity entity, HumanoidArm arm) {
        return arm == entity.getMainArm() ? entity.getMainHandItem() : entity.getOffhandItem();
    }

    /** 26.x's ItemDisplayContext.leftHand: whether the context draws the left hand */
    public static boolean leftHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** 26.x's FishingHookRenderer.getHoldingArm as the mod has it there (vanilla's rule, with mixin.FishingHookRendererMixin's change):
     *  the arm a player's line leaves from and whose rod shows cast, the main arm when the main hand holds one of our rods or any fishing
     *  rod, else the other. 1.21.1's renderer decides it inside its own code instead (mixin.FishingHookRendererMixin there) */
    public static HumanoidArm fishingArm(Player player) {
        return player.getMainHandItem().getItem() instanceof CastingRodItem || player.getMainHandItem().getItem() instanceof FishingRodItem
            ? player.getMainArm() : player.getMainArm().getOpposite();
    }
}
