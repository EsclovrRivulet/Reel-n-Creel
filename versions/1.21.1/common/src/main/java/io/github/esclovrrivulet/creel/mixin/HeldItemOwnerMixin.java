package io.github.esclovrrivulet.creel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.esclovrrivulet.creel.client.HeldRodModels;

/** (1.21.1 build only) Client only, cosmetic, optional: who holds the item being drawn. ItemInHandRenderer.renderItem draws every item
 *  held by an entity (first person, third person, on the head) and knows the entity; the item renderer it calls does not, but a held
 *  rod's model is chosen by its holder's state (client.HeldRodModels). The entity is kept from the head of the call to its return.
 *  Read only; nothing is replaced. (26.x has the holder on the item render state: ItemOwner.) */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemOwnerMixin {
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), require = 0, expect = 0)
    private void creel$holder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                              MultiBufferSource buffers, int light, CallbackInfo ci) {
        HeldRodModels.holder(entity);
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN"), require = 0, expect = 0)
    private void creel$holderEnd(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        HeldRodModels.holder(null);
    }
}
