package com.vivet.speedrun.client;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.hud.Notices;
import com.vivet.speedrun.client.screen.SessionScreen;
import com.vivet.speedrun.client.screen.VivetTitleScreen;
import com.vivet.speedrun.p2p.Hub;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;

public final class InviteHold {
    private InviteHold() {
    }

    public static boolean free() {
        return !Session.isActive();
    }

    public static void take(Minecraft minecraft, Hub.Invite invite) {
        Hub.dropInvite(invite);
        Notices.dismiss(invite.uuid());
        Cues.queueJoined();

        Session.join(invite.room(),
                () -> minecraft.execute(() -> open(minecraft)),
                reason -> minecraft.execute(() -> {
                    VivetSpeedrun.LOGGER.info("Could not accept invite: {}",
                            reason);
                    Cues.deny();
                    open(minecraft);
                }));
    }

    private static void open(Minecraft minecraft) {
        minecraft.gui.setScreen(new SessionScreen(
                minecraft.level == null ? VivetTitleScreen.forShowing() : null));
    }
}
