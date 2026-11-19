package io.github.esclovrrivulet.creel.client;

/** Added to vanilla's fishing hook render state (mixin.FishingHookRenderStateMixin): whether the line is drawn as ours (the owner holds
 *  one of our rods in the holding arm), and (0.16.2) how taut it is drawn (RodLine.taut, from the fish's level), set when the state is
 *  extracted and read when the line is submitted. */
public interface RodLineState {
    boolean creel$ourLine();

    void creel$setOurLine(boolean ours);

    float creel$taut();

    void creel$setTaut(float taut);
}
