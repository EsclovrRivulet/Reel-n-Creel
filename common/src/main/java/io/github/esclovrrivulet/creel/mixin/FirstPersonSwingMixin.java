package io.github.esclovrrivulet.creel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import io.github.esclovrrivulet.creel.client.RodAnim;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** Client only, cosmetic, optional. The server sends the game's swing with every cast (so other players see the arm go forward in
 *  third person). In first person that swing turns the held item by up to 80 degrees and lays our long rods flat into the screen; our
 *  own whip (client.RodAnim) shows the cast there instead. So for one of our rods, and only within a moment of a cast of ours, the
 *  swing's progress handed to the first-person arm is read as none. Any other swing (a punch with the rod in hand, any other item)
 *  is untouched. A @ModifyVariable on the method's argument at its head (it chains with other mods); require = 0. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonSwingMixin {
    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0, expect = 0)
    private float creel$noCastSwing(float attack, @Local(argsOnly = true) ItemStack stack) {
        return stack.getItem() instanceof CastingRodItem && RodAnim.justCast() ? 0.0F : attack;
    }
}
