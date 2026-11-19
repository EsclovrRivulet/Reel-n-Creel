package io.github.esclovrrivulet.creel.client;

import net.minecraft.world.item.ItemDisplayContext;
import io.github.esclovrrivulet.creel.fishing.RodTable;

/** Added to vanilla's ItemStackRenderState (mixin.ItemStackRenderStateMixin): which of our rods, if any, the state is about to draw,
 *  in which hand context and for which player; set when the state is filled (mixin.ItemModelResolverMixin), read when its layers are
 *  submitted (mixin.ItemLayerRenderStateMixin), where the pose the rod is drawn with is captured (RodPose). */
public interface HeldRodMark {
    void creel$markRod(RodTable.Rod rod, ItemDisplayContext context, int playerId);

    /** 0.13.0: the bent step of the held model picked for it (RodFlex) */
    void creel$markStep(int step);

    int creel$step();

    RodTable.Rod creel$rod();

    ItemDisplayContext creel$context();

    int creel$playerId();
}
