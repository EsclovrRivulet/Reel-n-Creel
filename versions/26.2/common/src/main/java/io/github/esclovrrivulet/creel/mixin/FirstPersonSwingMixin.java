package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** FirstPersonSwingMixin for MINECRAFT 26.2 (replaces the base copy in the 26.2 build; what it does is described there). 26.2's arm is
 *  drawn by ItemInHandRenderer.submitArmWithItem (26.3: FirstPersonHandsAndItemsRenderer); its third float argument is the same swing
 *  progress, so the same ordinal. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonSwingMixin {
    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0, expect = 0)
    private float creel$noCastSwing(float attack, @Local(argsOnly = true) ItemStack stack) {
        return stack.getItem() instanceof CastingRodItem && RodAnim.justCast() ? 0.0F : attack;
    }
}
