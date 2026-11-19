package io.github.esclovrrivulet.creel.fishing;

/** (0.14.0; 0.15.0 the bend level; 0.16.0 seven stages; 0.16.2 the level alone) The fight as every client sees it: one byte of synced
 *  data added to vanilla's fishing hook (mixin.FishingHookMixin), the fish's LEVEL, set from its state (CastingHook.bendLevel): NONE no
 *  fish, CALM a fish bitten, resting or beaten (safe to reel), TENSION a fish pulling or easing off after a run (reel with care), DANGER a
 *  fish running (do not reel). The rod's bend (client.RodFlex) is the STAGE: the level's stage (1, 3 or 5) plus one while the holder
 *  reels, which every client already knows from the holder's use state (stage); the line's sag (client.RodLine) follows the level. Only
 *  our hook (CastingHook) ever writes the byte, and only when it changes; on any other hook it stays 0 and is never sent (a value at its
 *  default is not part of the spawn data). (Named HookStrain until 0.16.4, from 0.14.0, when the byte also carried a strain.) */
public interface HookLevel {
    int NONE = 0, CALM = 1, TENSION = 2, DANGER = 3;

    /** the synced byte: the fish's level */
    int creel$fishLevel();

    /** sets the synced byte (the fish's level) */
    void creel$setFishLevel(int value);

    /** the synced level of a hook (NONE to DANGER); NONE for none */
    static int level(Object hook) {
        return hook instanceof HookLevel h ? Math.max(NONE, Math.min(DANGER, h.creel$fishLevel())) : NONE;
    }

    /** (0.16.0) the bend stage, 0 to 6: 0 with no fish; 1 calm, 3 tension, 5 danger; one more while the player reels */
    static int stage(int level, boolean reeling) {
        return level <= NONE ? 0 : level * 2 - 1 + (reeling ? 1 : 0);
    }

    /** (0.16.2) how taut the line is drawn for a level, 0 (vanilla's sag) to 1 (straight): slack calm, straighter in tension, straight
     *  in danger */
    static float taut(int level) {
        return level >= DANGER ? 1.0F : level == TENSION ? 0.5F : 0.0F;
    }
}
