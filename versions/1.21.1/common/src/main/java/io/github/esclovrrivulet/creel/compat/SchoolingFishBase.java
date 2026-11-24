package io.github.esclovrrivulet.creel.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.level.Level;

/** (1.21.1 build only) What entity.FishEntity extends in the 1.21.1 build (versions/1.21.1/java_renames.txt puts it in place of
 *  AbstractSchoolingFish): 1.21.1 saves and loads an entity's data as a CompoundTag where 26.x has ValueOutput and ValueInput, so the
 *  game's calls are passed on to FishEntity's own methods, with the tag in this build's ValueOutput and ValueInput. Nothing else. */
public abstract class SchoolingFishBase extends AbstractSchoolingFish {
    protected SchoolingFishBase(EntityType<? extends AbstractSchoolingFish> type, Level level) {
        super(type, level);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        this.addAdditionalSaveData(new ValueOutput(tag));
    }

    /** FishEntity's (26.x's form); this one writes the game's own data */
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out.tag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.readAdditionalSaveData(new ValueInput(tag));
    }

    /** FishEntity's (26.x's form); this one reads the game's own data */
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in.tag());
    }
}
