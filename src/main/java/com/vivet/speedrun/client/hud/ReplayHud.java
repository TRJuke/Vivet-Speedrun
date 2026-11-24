package com.vivet.speedrun.client.hud;

import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.client.ui.Theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ReplayHud {
    private static final int STRIP = 4;

    private ReplayHud() {
    }

    public static int lift() {
        return ReplayPlayback.isActive() ? STRIP + 1 : 0;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ReplayPlayback playback = ReplayPlayback.get();
        if (playback == null) return;

        int width = graphics.guiWidth();
        int top = graphics.guiHeight() - STRIP;
        float done = Math.max(0.0F, Math.min(1.0F, playback.wall() / (float) playback.durationMs()));
        int filled = Math.round(done * width);

        Theme.fill(graphics, 0, top, width, top + STRIP, Theme.PANEL_SUNKEN);
        if (filled > 0) {
            Theme.fill(graphics, 0, top + 1, filled, top + STRIP, playback.isPaused()
                    ? Theme.darken(Theme.ACCENT, 0.6F) : Theme.ACCENT);
        }
        Theme.fill(graphics, Math.max(0, filled - 1), top, Math.min(width, filled + 1), top + STRIP, Theme.TEXT);
    }
}
