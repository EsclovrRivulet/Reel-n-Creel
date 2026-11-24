package io.github.esclovrrivulet.creel.mixin;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (1.21.1 build only) Client only, optional. 1.21.1 refuses a model element turned by any angle but -45, -22.5, 0, 22.5 and 45 degrees
 *  (BlockElement's deserializer, getAngle); 26.x takes any angle (CuboidModelElement), and both bake the turn the same way, as a turn
 *  by that angle about the element's axis and origin (FaceBakery; no rescale in our models). Our rods' held and bent models and the
 *  Fish Spawn's fringe and corner templates (72 models) turn their elements by other angles, so without this they load as the missing
 *  model. Here the angle is taken as written, whatever it is, as 26.x takes it; only a value that is not a number is left to the game's
 *  own check (and its error). For every other model nothing changes: an angle the game allows is the same angle either way. */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public abstract class BlockElementAngleMixin {
    @Inject(method = "getAngle(Lcom/google/gson/JsonObject;)F", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void creel$anyAngle(JsonObject rotation, CallbackInfoReturnable<Float> cir) {
        float angle = GsonHelper.getAsFloat(rotation, "angle");
        if (Float.isFinite(angle)) {
            cir.setReturnValue(angle);
        }
    }
}
