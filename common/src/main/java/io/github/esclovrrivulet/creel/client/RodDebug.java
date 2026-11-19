package io.github.esclovrrivulet.creel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.esclovrrivulet.creel.fishing.CastingRodItem;

/** The debug read-outs (client only), each toggled by a command typed in chat, off by default.
 *  /creelline: once a second while one of our rods is held, it says in chat and in the log, for each hand, whether the pose our rod is
 *  drawn with was captured lately (and so is used for the line's start) and where the first-person tip it gives is on screen, in
 *  fractions of the half-width and half-height.
 *  /creelstage (0.16.2): an action-bar line for the local player with the stage the rod is BEING DRAWN at and why, for example
 *  "stage 4 of 6: tension, reeling" (RodFlex.stageText, from what RodFlex last drew, not from the server). Sent when it changes, and
 *  again every STAGE_KEEP ticks while it does not, so the action bar does not fade while it holds. */
public final class RodDebug {
    private RodDebug() {}

    private static final Logger LOG = LoggerFactory.getLogger("creel");
    /** the action bar fades after about three seconds: the unchanged stage line is sent again this often */
    private static final int STAGE_KEEP = 50;
    private static boolean on, stageOn;
    private static int ticks, stageTicks;
    private static String lastStage = "";

    /** the client commands' names; the loader part builds the two literals (its own client command source) and sends the feedback
     *  that toggleLine and toggleStage return */
    public static final String LINE_COMMAND = "creelline", STAGE_COMMAND = "creelstage";

    /** /creelline: turns the line read-out on or off; returns the feedback to show */
    public static Component toggleLine() {
        on = !on;
        return Component.literal("Reel n Creel line debug " + (on ? "on: once a second while a rod of ours is held" : "off"));
    }

    /** /creelstage: turns the stage read-out on or off; returns the feedback to show */
    public static Component toggleStage() {
        stageOn = !stageOn;
        lastStage = "";
        return Component.literal("Reel n Creel stage read-out " + (stageOn ? "on: the rod's drawn stage in the action bar" : "off"));
    }

    /** at the end of every client tick */
    public static void onEndClientTick(Minecraft mc) {
        if (stageOn) {
            stage(mc);
        }
        if (!on || ++ticks < 20) {
            return;
        }
        ticks = 0;
        Player player = mc.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof CastingRodItem || player.getOffhandItem().getItem() instanceof CastingRodItem)) {
            return;
        }
        String text = "creel line: " + (mc.options.getCameraType().isFirstPerson() ? "first person" : "third person") + "; " + RodPose.debugText() + "; " + RodLine.lastDrawn();
        LOG.info(text);
        player.sendSystemMessage(Component.literal(text));
    }

    /** the stage line: sent when it changes (or to keep it on screen) */
    private static void stage(Minecraft mc) {
        Player player = mc.player;
        if (player == null) {
            return;
        }
        String text = RodFlex.stageText(player);
        if (text.equals(lastStage) && ++stageTicks < STAGE_KEEP) {
            return;
        }
        stageTicks = 0;
        lastStage = text;
        player.sendOverlayMessage(Component.literal(text));
    }
}
