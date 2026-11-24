package com.vivet.speedrun.client.hud;

import com.vivet.speedrun.client.screen.OpponentCard;
import com.vivet.speedrun.client.ui.Marquee;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.RankEmblem;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiScale;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class OpponentHud {
    private static final int WIDTH = 232;

    private static final int PADDING = 10;

    private static final int ROW_H = 11;

    private static final int FACE = 28;

    private static final int EMBLEM_W = 96;
    private static final int EMBLEM_H = 84;

    private static final int COMPACT_H = 30;

    private static final float CLOCK_SCALE = 1.6F;
    private static final int CLOCK_H = 15;

    private static final float MAX_HEIGHT_SHARE = 0.66F;
    private static final int ROOM_FOR_RUN_CARD = 214;

    private OpponentHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font) {
        String name = opponentName();
        if (name.isEmpty()) return;

        OpponentCard.request(name);

        MojangAuth.Profile held = OpponentCard.profileOf(name);
        Racer racer = racerNamed(name);

        float opacity = 1.0F;

        boolean full = fits(graphics, name, held, racer);
        int height = measureHeight(font, name, held, racer, full);

        int x = UiScale.guiWidth(graphics) - WIDTH - 4;
        int y = 4;

        int accent = held == null ? Theme.TEXT_FAINT : Ranks.colorOf(Ranks.displayRank(held));

        Theme.roundedRect(graphics, x, y, WIDTH, height, Theme.fade(Theme.PANEL, opacity));
        Theme.roundedOutline(graphics, x, y, WIDTH, height, Theme.fade(Theme.BORDER, opacity));

        int centre = x + WIDTH / 2;
        int cursor = y + PADDING;

        graphics.centeredText(font, Component.translatable("vivet.hud.racing"), centre, cursor,
                Theme.fade(Theme.TEXT_FAINT, opacity));
        cursor += ROW_H + 3;

        cursor = drawWho(graphics, font, centre, cursor, name, held, accent, opacity);
        cursor = drawRank(graphics, font, x, centre, cursor, held, opacity, full);
        drawRace(graphics, font, x, centre, cursor, name, racer, accent, opacity);
    }

    private static int drawWho(GuiGraphicsExtractor graphics, Font font, int centre, int y,
                               String name, MojangAuth.Profile held, int accent, float opacity) {
        String uuid = accountIdOf(name, held);

        if (uuid != null) {
            int box = PlayerHead.framedSize(FACE);
            PlayerHead.drawFramed(graphics, name, uuid, centre - box / 2, y, FACE,
                    Theme.fade(accent, opacity));
            y += box + 5;
        }

        NameLabel label = NameLabel.of(name);
        if (label != null) {
            graphics.centeredText(font, label.animatedTag(name), centre, y,
                    Theme.fade(Theme.TEXT, opacity));
            y += ROW_H;
        }

        int room = WIDTH - PADDING * 2;
        if (font.width(name) <= room) {
            NameLabel.drawCentred(graphics, font, label, name, centre, y,
                    Theme.fade(Theme.TEXT, opacity));
        } else {
            Marquee.drawCentred(graphics, font, name, centre, y, room,
                    Theme.fade(label == null ? Theme.TEXT : label.nameColor(name), opacity));
        }

        return y + ROW_H + 4;
    }

    private static int drawRank(GuiGraphicsExtractor graphics, Font font, int x, int centre, int y,
                                MojangAuth.Profile held, float opacity, boolean full) {
        Theme.divider(graphics, x + PADDING, y, WIDTH - PADDING * 2);
        y += 7;

        if (held == null) {
            graphics.centeredText(font,
                    Component.translatable(OpponentCard.isLoading()
                            ? "vivet.hud.looking_up" : "vivet.hud.no_record"),
                    centre, y, Theme.fade(Theme.TEXT_FAINT, opacity));
            return y + ROW_H + 5;
        }

        com.vivet.speedrun.p2p.Wardrobe.Item worn = Session.isRanked()
                || held.emblem == null || held.emblem.isEmpty()
                ? null : com.vivet.speedrun.p2p.Wardrobe.item(held.emblem);

        if (worn != null) {
            int box = full ? EMBLEM_H : COMPACT_H - 4;
            int top = full ? y : y + 2;
            com.vivet.speedrun.client.ui.Cosmetics.ready(worn);
            com.vivet.speedrun.client.ui.Cosmetics.draw(graphics, font, worn, centre - box / 2, top,
                    box, box, System.currentTimeMillis(), true, false, Ranks.displayRank(held));
            y += (full ? EMBLEM_H : COMPACT_H) + 2;
        } else if (full) {
            RankEmblem.draw(graphics, centre, y + EMBLEM_H / 2, Ranks.displayRank(held), held.placed);
            y += EMBLEM_H + 2;
        } else {
            RankEmblem.drawCompact(graphics, centre, y + 14, Ranks.displayRank(held), held.placed);
            y += COMPACT_H + 2;
        }

        for (RecordLine line : recordLines(held)) {
            graphics.centeredText(font, Component.literal(line.text()), centre, y,
                    Theme.fade(line.color(), opacity));
            y += ROW_H;
        }

        return y + 4;
    }

    private record RecordLine(String text, int color) {
    }

    private static List<RecordLine> recordLines(MojangAuth.Profile held) {
        List<RecordLine> lines = new ArrayList<>();

        lines.add(new RecordLine(
                held.placed
                        ? Ranks.nameOf(Ranks.displayRank(held))
                        : "Placements " + held.placementGames + "/" + held.placementMatches,
                Ranks.colorOf(Ranks.displayRank(held))));

        if (held.rating != null) {
            String points = held.rating + " pts";
            if (held.peak != null && held.peak > held.rating) points += "   Peak " + held.peak;
            lines.add(new RecordLine(points, Theme.TEXT));
        }

        lines.add(new RecordLine(
                held.wins + "W   " + held.losses + "L   " + held.draws + "D", Theme.TEXT_MUTED));

        if (held.streak != 0) {
            lines.add(new RecordLine(
                    held.streak > 0
                            ? held.streak + " win streak"
                            : (-held.streak) + " losses in a row",
                    held.streak > 0 ? Theme.WARNING : Theme.TEXT_FAINT));
        }

        if (held.abandonStreak > 0) {
            lines.add(new RecordLine("Leaves matches", Theme.DANGER));
        }
        return lines;
    }

    private static void drawRace(GuiGraphicsExtractor graphics, Font font, int x, int centre,
                                 int y, String name, Racer racer, int accent, float opacity) {
        if (racer == null) return;

        Theme.divider(graphics, x + PADDING, y, WIDTH - PADDING * 2);
        y += 7;

        int awaySeconds = Session.awaySecondsLeft();
        if (name.equals(Session.awayName()) && awaySeconds >= 0) {
            graphics.centeredText(font, Component.literal("DISCONNECTED"), centre, y,
                    Theme.fade(Theme.DANGER, opacity));
            graphics.centeredText(font,
                    Component.literal("Forfeits in " + Time.format(awaySeconds)),
                    centre, y + ROW_H, Theme.fade(Theme.WARNING, opacity));
            return;
        }

        boolean finished = racer.status == Racer.Status.FINISHED;
        boolean done = racer.isDone();

        String clock = done && !finished
                ? racer.statusText()
                : Time.formatPrecise(finished
                        ? (racer.centis > 0 ? racer.centis : Time.toCentis(racer.seconds))
                        : racer.projectedCentis());

        int color = finished ? Theme.SUCCESS : done ? Theme.TEXT_FAINT : accent;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centre - font.width(clock) * CLOCK_SCALE / 2.0F, y);
        pose.scale(CLOCK_SCALE, CLOCK_SCALE);
        graphics.text(font, Component.literal(clock), 0, 0, Theme.fade(color, opacity));
        pose.popMatrix();
        y += CLOCK_H + 2;

        graphics.centeredText(font,
                Component.literal(finished ? "finished" : done ? "out" : "elapsed"),
                centre, y, Theme.fade(Theme.TEXT_FAINT, opacity));
        y += ROW_H;

        if (!racer.milestones.isEmpty()) {
            Marquee.drawCentred(graphics, font,
                    racer.milestones.get(racer.milestones.size() - 1),
                    centre, y, WIDTH - PADDING * 2, Theme.fade(Theme.TEXT_FAINT, opacity));
        }
    }

    private static int measureHeight(Font font, String name, MojangAuth.Profile held, Racer racer,
                                     boolean full) {
        int height = PADDING * 2 + ROW_H + 3;

        if (accountIdOf(name, held) != null) height += PlayerHead.framedSize(FACE) + 5;
        if (NameLabel.of(name) != null) height += ROW_H;
        height += ROW_H + 4;

        if (held == null) {
            height += 7 + ROW_H + 5;
        } else {
            height += 7 + (full ? EMBLEM_H : COMPACT_H) + 2 + recordLines(held).size() * ROW_H + 4;
        }

        if (racer != null) {
            int awaySeconds = Session.awaySecondsLeft();
            if (name.equals(Session.awayName()) && awaySeconds >= 0) {
                height += 7 + ROW_H * 2;
            } else {
                height += 7 + CLOCK_H + 2 + ROW_H;
                if (!racer.milestones.isEmpty()) height += ROW_H;
            }
        }
        return height;
    }

    private static String accountIdOf(String name, MojangAuth.Profile held) {
        if (held != null && PlayerHead.canDraw(held.uuid)) return held.uuid;

        String fromRoom = Session.accountIdOf(name);
        return PlayerHead.canDraw(fromRoom) ? fromRoom : null;
    }

    private static String opponentName() {
        if (!Session.isActive()) return "";

        for (Racer racer : Session.racers()) {
            if (!Session.isLocalName(racer.name)) return racer.name;
        }
        return "";
    }

    private static boolean fits(GuiGraphicsExtractor graphics, String name,
                                MojangAuth.Profile held, Racer racer) {
        if (UiScale.guiWidth(graphics) < WIDTH + ROOM_FOR_RUN_CARD) return false;

        Font font = net.minecraft.client.Minecraft.getInstance().font;
        return measureHeight(font, name, held, racer, true)
                <= UiScale.guiHeight(graphics) * MAX_HEIGHT_SHARE;
    }

    private static Racer racerNamed(String name) {
        for (Racer racer : Session.racers()) {
            if (racer.name.equals(name)) return racer;
        }
        return null;
    }
}
