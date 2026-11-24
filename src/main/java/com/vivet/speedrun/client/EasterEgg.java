package com.vivet.speedrun.client;

import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.media.Media;
import com.vivet.speedrun.media.MediaImages;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class EasterEgg {
    private static final long CAUGHT_SHOWN_MS = 1400L;

    private static final long FADE_OUT_MS = 700L;

    private static final float FLASH_SHARE = 0.18F;

    private static Identifier showing;
    private static float aspect = 1.0F;
    private static long shownAt;

    private static long shownFor = CAUGHT_SHOWN_MS;

    private EasterEgg() {
    }

    private static int presses;

    private static int told;
    private static long toldAt;

    private static final long TELL_GAP_MS = 500L;

    public static void press() {
        Cues.nothingHappened();
        presses += 1;
        tellPresses(false);
    }

    public static void tickSettings() {
        tellPresses(false);
    }

    public static void leftSettings() {
        tellPresses(true);
        presses = 0;
        told = 0;
        toldAt = 0L;
    }

    private static void tellPresses(boolean now) {
        if (presses == told) return;
        long at = System.currentTimeMillis();
        if (!now && at - toldAt < TELL_GAP_MS) return;
        told = presses;
        toldAt = at;
        com.vivet.speedrun.p2p.Hub.nothingPressed(presses);
    }

    public static void caught() {
        if (isShowing()) return;
        if (!MediaImages.isReady(Media.CAUGHT)) return;

        show(Media.CAUGHT, CAUGHT_SHOWN_MS);
        Cues.wompWomp();
    }

    private static void show(String media, long holdMs) {
        showing = MediaImages.texture(media);
        aspect = MediaImages.aspect(media);
        shownFor = holdMs;
        shownAt = System.currentTimeMillis();
    }

    public static boolean isShowing() {
        return showing != null && age() < shownFor + FADE_OUT_MS;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!isShowing()) {
            showing = null;
            return;
        }

        float alpha = fade();
        if (alpha <= 0.0F) return;

        Theme.fill(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                Theme.withAlpha(0x000000, (int) (255 * alpha)));

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int width = screenWidth;
        int height = Math.round(width / aspect);
        if (height > screenHeight) {
            height = screenHeight;
            width = Math.round(height * aspect);
        }

        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, showing, x, y, 0.0F, 0.0F,
                width, height, width, height, Theme.withAlpha(0xFFFFFF, (int) (255 * alpha)));

        float flash = flash();
        if (flash > 0.0F) {
            Theme.fill(graphics, 0, 0, screenWidth, screenHeight,
                    Theme.withAlpha(0xFFFFFF, (int) (255 * flash * alpha)));
        }
    }

    private static float flash() {
        long age = age();
        if (age < shownFor) return 0.0F;

        return Math.min(1.0F, ((age - shownFor) / (float) FADE_OUT_MS) / FLASH_SHARE);
    }

    private static float fade() {
        long age = age();

        if (age < shownFor) return 1.0F;

        return Math.max(0.0F, 1.0F - (age - shownFor) / (float) FADE_OUT_MS);
    }

    private static long age() {
        return System.currentTimeMillis() - shownAt;
    }
}
