package io.github.esclovrrivulet.creel.client;

/** (0.16.4) Client only: the rods' per-player animation state, held on the player object itself (mixin.PlayerRodStateMixin), one slot
 *  for RodFlex and one for RodAnim, each holding that class's own State. Until 0.16.3 these were maps keyed by entity id, never
 *  cleared: after a reconnect a new entity could get an old id and inherit a cast time from another world's clock. A player object is
 *  never reused, so its state goes with it. (Not a WeakHashMap keyed by the player: Entity.equals and hashCode go by id.) */
public interface RodHolder {
    /** RodFlex's state for this player, or null before its first use */
    Object creel$flexState();

    void creel$setFlexState(Object state);

    /** RodAnim's state for this player, or null before its first use */
    Object creel$animState();

    void creel$setAnimState(Object state);
}
