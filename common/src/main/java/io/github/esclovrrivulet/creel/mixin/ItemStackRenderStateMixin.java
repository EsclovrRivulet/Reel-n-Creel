package io.github.esclovrrivulet.creel.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import io.github.esclovrrivulet.creel.client.HeldRodMark;
import io.github.esclovrrivulet.creel.fishing.RodTable;

/** Client only: four fields on vanilla's item render state, which of our rods it draws, for whom and (0.13.0) bent to which step
 *  (client.HeldRodMark). No injection, so nothing here can clash with another mod. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements HeldRodMark {
    @Unique
    private RodTable.Rod creel$rod;
    @Unique
    private ItemDisplayContext creel$context;
    @Unique
    private int creel$playerId = -1;
    @Unique
    private int creel$step;

    @Override
    public void creel$markStep(int step) {
        this.creel$step = step;
    }

    @Override
    public int creel$step() {
        return this.creel$step;
    }

    @Override
    public void creel$markRod(RodTable.Rod rod, ItemDisplayContext context, int playerId) {
        this.creel$rod = rod;
        this.creel$context = context;
        this.creel$playerId = playerId;
    }

    @Override
    public RodTable.Rod creel$rod() {
        return this.creel$rod;
    }

    @Override
    public ItemDisplayContext creel$context() {
        return this.creel$context;
    }

    @Override
    public int creel$playerId() {
        return this.creel$playerId;
    }
}
