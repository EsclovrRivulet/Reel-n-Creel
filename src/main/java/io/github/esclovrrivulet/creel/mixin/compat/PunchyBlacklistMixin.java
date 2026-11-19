package io.github.esclovrrivulet.creel.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** Client only, optional, and only when Punchy is installed (creel.compat.mixins.json is not required, and CompatPlugin applies this
 *  only when the mod "punchy" is loaded). Punchy redraws the first-person arms and every held item with its own animation system, for
 *  every item, not only for fishing rods; its one way to leave an item to the game is its item blacklist (punchy_config.json,
 *  "itemBlacklist"; PunchyConfig.isItemBlacklisted, which all its state machines and its first-person renderer consult). Our five rods
 *  are added to that list here, as if the player had listed them, so the game draws them in the hand and our own animation, pose
 *  capture and line apply. A @ModifyReturnValue on a pure test; nothing of Punchy's is replaced. */
@Pseudo
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class PunchyBlacklistMixin {
    @ModifyReturnValue(method = "isItemBlacklisted(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), require = 0, expect = 0)
    private static boolean creel$keepOffOurRods(boolean listed, ItemStack stack) {
        return listed || (stack != null && stack.getItem() instanceof CastingRodItem);
    }
}
