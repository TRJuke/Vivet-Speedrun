package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ui.Clip;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.RankEmblem;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class OpponentCard {
    private static String forName = "";
    private static MojangAuth.Profile profile;
    private static String error = "";
    private static boolean loading;

    private OpponentCard() {
    }

    public static void request(String name) {
        if (name == null || name.isBlank() || name.equals(forName) || loading) return;

        forName = name;
        profile = null;
        error = "";
        loading = true;

        RankedApi.loadProfileOf(name,
                loaded -> {
                    if (!name.equals(forName)) return;
                    profile = loaded;
                    loading = false;
                },
                reason -> {
                    if (!name.equals(forName)) return;
                    error = reason;
                    loading = false;
                });
    }

    public static MojangAuth.Profile profileOf(String name) {
        return name != null && name.equals(forName) ? profile : null;
    }

    public static boolean isLoading() {
        return loading;
    }

    public static void clear() {
        forName = "";
        profile = null;
        error = "";
        loading = false;
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
                            int height, String name) {
        MojangAuth.Profile held = name.equals(forName) ? profile : null;

        int accent = held == null ? Theme.ACCENT : Ranks.colorOf(Ranks.displayRank(held));
        Theme.panel(graphics, x, y, width, height, Theme.PANEL_RAISED);

        int centre = x + width / 2;
        int cursor = y + 12;

        cursor = drawName(graphics, font, centre, cursor, name);
        cursor = drawRank(graphics, font, x, centre, cursor, width, held);
        cursor = drawRecord(graphics, font, centre, cursor, held, name);

        Clip.on(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        drawRace(graphics, font, x, centre, cursor, width, y + height, name);
        Clip.off(graphics);
    }

    private static Racer racerNamed(String name) {
        for (Racer candidate : Session.racers()) {
            if (candidate.name.equals(name)) return candidate;
        }
        return null;
    }

    private static int drawName(GuiGraphicsExtractor graphics, Font font, int centre, int y,
                                String name) {
        NameLabel label = NameLabel.of(name);

        if (label != null) {
            Component tag = label.animatedTag(name);
            graphics.centeredText(font, tag, centre, y, Theme.TEXT);
            y += 12;

            NameLabel.drawCentred(graphics, font, name, centre, y, Theme.TEXT);
            return y + 16;
        }

        graphics.centeredText(font, Component.literal(name), centre, y, Theme.TEXT);
        return y + 16;
    }

    private static int drawRank(GuiGraphicsExtractor graphics, Font font, int x, int centre, int y,
                                int width, MojangAuth.Profile held) {
        if (held == null) {
            graphics.centeredText(font, Component.literal(statusLine()), centre, y,
                    Theme.TEXT_FAINT);
            return y + 20;
        }

        RankEmblem.drawCompact(graphics, centre, y + 14, Ranks.displayRank(held), held.placed);
        y += 32;

        String rankName = held.placed
                ? Ranks.nameOf(Ranks.displayRank(held))
                : "Placements " + held.placementGames + "/" + held.placementMatches;
        graphics.centeredText(font, Component.literal(rankName), centre, y,
                Ranks.colorOf(Ranks.displayRank(held)));
        y += 12;

        if (held.rating != null) {
            String points = held.rating + " points"
                    + (held.peak != null && held.peak > held.rating ? "   Peak " + held.peak : "");
            graphics.centeredText(font, Component.literal(points), centre, y, Theme.TEXT_MUTED);
            y += 12;
        }
        return y + 6;
    }

    private static int drawRecord(GuiGraphicsExtractor graphics, Font font, int centre, int y,
                                  MojangAuth.Profile held, String name) {
        if (held == null) return y;

        String record = held.wins + "W   " + held.losses + "L   " + held.draws + "D";
        graphics.centeredText(font, Component.literal(record), centre, y, Theme.TEXT);
        y += 12;

        String streaks = "Streak " + held.streak + "   Best " + held.bestStreak;
        graphics.centeredText(font, Component.literal(streaks), centre, y, Theme.TEXT_FAINT);
        y += 12;

        if (held.abandonStreak > 0 || held.banLevel > 0) {
            graphics.centeredText(font,
                    Component.literal("Left " + Math.max(held.abandonStreak, held.banLevel)
                            + " match" + (held.abandonStreak == 1 ? "" : "es") + " recently"),
                    centre, y, Theme.WARNING);
            y += 12;
        }
        return y + 6;
    }

    private static void drawRace(GuiGraphicsExtractor graphics, Font font, int x, int centre,
                                 int y, int width, int bottom, String name) {
        Racer racer = racerNamed(name);
        if (racer == null) return;

        Theme.divider(graphics, x + 12, y, width - 24);
        y += 8;

        int awaySeconds = Session.awaySecondsLeft();
        if (name.equals(Session.awayName()) && awaySeconds >= 0) {
            graphics.centeredText(font, Component.literal("DISCONNECTED"), centre, y,
                    Theme.DANGER);
            graphics.centeredText(font,
                    Component.literal("Forfeits in " + Time.format(awaySeconds)),
                    centre, y + 12, Theme.WARNING);
            return;
        }

        graphics.centeredText(font, Component.literal(racer.statusText()), centre, y,
                racer.isDone() ? Theme.TEXT_MUTED : Theme.TEXT);
        y += 12;

        int clock = racer.status == Racer.Status.FINISHED
                ? (racer.centis > 0 ? racer.centis : Time.toCentis(racer.seconds))
                : racer.projectedCentis();

        if (clock > 0) {
            graphics.centeredText(font, Component.literal(Time.formatPrecise(clock)), centre, y,
                    Theme.TEXT);
            y += 14;
        }

        if (!racer.milestones.isEmpty() && y + 8 < bottom) {
            String latest = racer.milestones.get(racer.milestones.size() - 1);
            com.vivet.speedrun.client.ui.Marquee.drawCentred(graphics, font, latest, centre, y,
                    width - 16, Theme.TEXT_FAINT);
        }
    }

    private static String statusLine() {
        if (loading) return "Loading...";
        if (!error.isBlank()) return "No ranked games";
        return "No ranked games";
    }
}
