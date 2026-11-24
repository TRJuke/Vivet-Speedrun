package com.vivet.speedrun.client.hud;

import com.vivet.speedrun.client.ClientState;
import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.Spectating;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.Marquee;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiScale;
import com.vivet.speedrun.client.ui.Wordmark;
import com.vivet.speedrun.game.PersonalBests;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.p2p.RankedApi;
import com.vivet.speedrun.p2p.Standings;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class RunHud {
    private static final int MARGIN = 6;

    private static final int PADDING = 10;

    private static final int ITEM_SIZE = 16;
    private static final int ITEM_DRAWN = 24;

    private static final int PANEL_WIDTH = 214;

    private static final int PACE_ROW_H = 11;

    private static final int STANDING_ROW_H = 13;

    private static final float CLOCK_SCALE = 1.6F;

    private static final int CLOCK_H = 15;

    private RunHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) return;

        boolean f3 = minecraft.debugEntries.isOverlayVisible();

        if (com.vivet.speedrun.client.replay.ReplayPlayback.isActive()) {
            if (!f3) drawCard(graphics, minecraft.font, replayCard());
            return;
        }

        if (Spectating.isActive()) {
            if (Spectating.blind()) {
                Theme.fill(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
            }
            if (Spectating.heard() && !f3) drawCard(graphics, minecraft.font, watchCard());
            return;
        }

        drawLoadingBanner(graphics, minecraft);

        drawSpectatingBanner(graphics, minecraft);

        tickTowardTheRecord();

        tickCarryOn();

        if (!ClientState.isRunning() || f3) return;

        OpponentHud.render(graphics, minecraft.font);

        int height = measureHeight();

        int x = MARGIN;
        int y = MARGIN;

        float opacity = 1.0F;
        int accent = ClientState.difficulty().color();

        Theme.roundedRect(graphics, x, y, PANEL_WIDTH, height, Theme.fade(Theme.PANEL, opacity));
        Theme.roundedOutline(graphics, x, y, PANEL_WIDTH, height, Theme.fade(Theme.BORDER, opacity));

        int cursor = y + PADDING;
        Font font = minecraft.font;

        cursor = drawTarget(graphics, font, x, cursor, opacity);
        cursor = drawClock(graphics, font, x, cursor, opacity);
        cursor = drawCarryOn(graphics, font, x, cursor, opacity);
        cursor = drawTier(graphics, font, x, cursor, opacity, accent);
        cursor = drawPace(graphics, font, x, cursor, opacity);
        cursor = drawStandings(graphics, font, x, cursor, opacity);
        cursor = drawRivalGap(graphics, font, x, cursor, opacity);

        drawWatermark(graphics, x, cursor, opacity);
    }

    private static void drawWatermark(GuiGraphicsExtractor graphics, int x, int y,
                                      float opacity) {
        Theme.divider(graphics, x + PADDING, y, PANEL_WIDTH - PADDING * 2);
        Wordmark.draw(graphics, x + PADDING, y + 5, opacity);
    }

    private static int lastBeat = -1;

    private static int beatingToward;

    private static int beatsSounded;

    private static final int DOUBLE_UNDER_CENTIS = 5 * Time.CENTIS_PER_SECOND;

    private static void tickTowardTheRecord() {
        if (!ClientState.isRunning() || ItemRoll.isConcealing()) {
            silence();
            beatingToward = 0;
            return;
        }

        int elapsed = ClientState.elapsedCentis();
        int target = nextTargetCentis(elapsed);

        if (beatingToward > 0 && beatingToward != target && elapsed >= beatingToward) {
            if (lastBeat >= 0) Cues.recordGone();
            silence();
        }
        beatingToward = target;

        if (target <= 0) {
            silence();
            return;
        }

        int left = target - elapsed;
        if (left <= 0 || left > PRECISE_UNDER_CENTIS) {
            silence();
            return;
        }

        int half = Time.CENTIS_PER_SECOND / 2;
        int beat = (left + half - 1) / half;
        if (left > DOUBLE_UNDER_CENTIS && beat % 2 != 0) beat += 1;

        if (beat == lastBeat) return;
        lastBeat = beat;

        Cues.recordTick(left, nextNote());
    }

    private static void silence() {
        lastBeat = -1;
        beatsSounded = 0;
    }

    private static boolean nextNote() {
        return beatsSounded++ % 2 == 1;
    }

    private static int nextTargetCentis(int elapsed) {
        int soonest = 0;

        for (PaceRow row : paceRows()) {
            int target = row.targetCentis();
            if (target <= elapsed) continue;
            if (soonest == 0 || target < soonest) soonest = target;
        }
        return soonest;
    }

    private static int drawPace(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                float opacity) {
        if (ItemRoll.isConcealing()) return y;

        List<PaceRow> rows = paceRows();
        if (rows.isEmpty()) return y;

        Theme.divider(graphics, x + PADDING, y, PANEL_WIDTH - PADDING * 2);

        graphics.text(font, Component.translatable("vivet.hud.to_beat"), x + PADDING, y + 5,
                Theme.fade(Theme.TEXT_FAINT, opacity));

        int cursor = y + 18;

        for (PaceRow row : rows) {
            drawPaceRow(graphics, font, x, cursor, row, opacity);
            cursor += PACE_ROW_H;
        }
        return cursor + 3;
    }

    private record PaceRow(String label, int targetCentis, String note, int color) {
    }

    private static final int PRECISE_UNDER_CENTIS = 10 * Time.CENTIS_PER_SECOND;

    private static String countdown(int centis) {
        return centis <= PRECISE_UNDER_CENTIS
                ? Time.formatPrecise(centis)
                : Time.format(Time.toSeconds(centis));
    }

    private static List<PaceRow> paceRows() {
        List<PaceRow> rows = new ArrayList<>();

        int elapsed = ClientState.elapsedCentis();
        String itemId = ClientState.targetId();
        RunMode mode = RunMode.current();

        Standings.adoptYourBest(mode, Session.isSetSeed(), itemId);

        int best = PersonalBests.bestCentisOf(
                PersonalBests.itemKey(mode, Session.isSetSeed(), itemId));

        if (Practice.isActive()) {
            rows.add(best >= 0
                    ? paceRow("Practice Best", best, elapsed)
                    : new PaceRow("Practice Best", 0, "first try", Theme.TEXT_FAINT));

            int real = realBest(itemId);
            if (real > 0) rows.add(paceRow("Your Best", real, elapsed));

            return rows;
        }

        rows.add(best >= 0
                ? paceRow("Your Best", best, elapsed)
                : new PaceRow("Your Best", 0, "first try", Theme.TEXT_FAINT));

        if (!mode.isRecorded()) return rows;

        Standings.Answer boards = Standings.current();
        if (boards == null) return rows;

        if (Session.isSetSeed()) {
            Integer seedBest = Standings.bestCentis(boards.seed);
            if (seedBest != null) rows.add(paceRow("Seed Record", seedBest, elapsed));

            addCutoff(rows, "Seed Top 100", boards.seed, elapsed);
            return rows;
        }

        Integer itemBest = Standings.bestCentis(boards.item);
        if (itemBest != null) rows.add(paceRow("Item Record", itemBest, elapsed));

        addCutoff(rows, "Item Top 100", boards.item, elapsed);
        addCutoff(rows, bandLabel(), boards.band, elapsed);

        return rows;
    }

    private static int realBest(String itemId) {
        Target chosen = itemId == null ? null : ItemTiers.byId(itemId);
        if (chosen == null) return 0;

        RankedApi.Found found = com.vivet.speedrun.p2p.CollectionBook.get(
                ItemTiers.ratingKey(chosen));
        return found == null || found.best == null ? 0 : found.best;
    }

    private static String bandLabel() {
        return ClientState.difficulty().displayName() + " Top 100";
    }

    private static void addCutoff(List<PaceRow> rows, String label, Standings.Board board,
                                  int elapsed) {
        if (board == null) return;

        Integer cutoff = Standings.cutoffCentis(board);
        if (cutoff == null) {
            rows.add(new PaceRow(label, 0, "any finish", Theme.SUCCESS));
            return;
        }
        rows.add(paceRow(label, cutoff, elapsed));
    }

    private static PaceRow paceRow(String label, int targetCentis, int elapsedCentis) {
        int difference = targetCentis - elapsedCentis;

        if (difference < 0) return new PaceRow(label, targetCentis, "missed", Theme.DANGER);

        int color = difference <= PRECISE_UNDER_CENTIS ? Theme.DANGER
                : difference <= 30 * Time.CENTIS_PER_SECOND ? Theme.WARNING
                : Theme.SUCCESS;

        return new PaceRow(label, targetCentis, countdown(difference) + " left", color);
    }

    private static void drawPaceRow(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                    PaceRow row, float opacity) {
        String note = row.note();
        int noteWidth = font.width(note);

        graphics.text(font, Component.literal(note),
                x + PANEL_WIDTH - PADDING - noteWidth, y, Theme.fade(row.color(), opacity));

        int used = noteWidth;
        String value = row.targetCentis() > 0 ? Time.formatPrecise(row.targetCentis()) : "";
        if (!value.isEmpty()) {
            int valueWidth = font.width(value);
            graphics.text(font, Component.literal(value),
                    x + PANEL_WIDTH - PADDING - used - 6 - valueWidth, y,
                    Theme.fade(Theme.TEXT_MUTED, opacity));
            used += 6 + valueWidth;
        }

        Marquee.drawLeft(graphics, font, row.label(), x + PADDING, y,
                PANEL_WIDTH - PADDING * 2 - used - 6, Theme.fade(Theme.TEXT_FAINT, opacity));
    }

    private static int drawRivalGap(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                    float opacity) {
        int leading = leadingCentis();
        if (leading <= 0) return y;

        int left = leading - ClientState.elapsedCentis();
        String text = left > 0
                ? countdown(left) + " to lead"
                : "Lead lost";

        graphics.text(font, Component.literal(text), x + PADDING, y,
                Theme.fade(left > PRECISE_UNDER_CENTIS ? Theme.WARNING : Theme.DANGER, opacity));
        return y + 11;
    }

    private static int leadingCentis() {
        int best = 0;
        for (Racer racer : Session.racers()) {
            if (racer.status != Racer.Status.FINISHED) continue;
            if (racer.name.equals(Session.localName())) continue;

            int centis = racer.centis > 0 ? racer.centis : Time.toCentis(racer.seconds);
            if (best == 0 || centis < best) best = centis;
        }
        return best;
    }

    private static void drawLoadingBanner(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        List<String> waiting = Session.stillLoading();
        if (waiting.isEmpty()) return;

        Font font = minecraft.font;
        Component headline = Component.translatable("vivet.hud.waiting_to_load");
        Component names = Component.literal(String.join(", ", waiting));

        int left = Session.startWaitSecondsLeft();
        Component clock = left < 0
                ? Component.empty()
                : Component.translatable("vivet.hud.giving_up_in", Time.format(left));

        int width = Math.max(Math.max(font.width(headline), font.width(names)),
                font.width(clock)) + 24;
        int height = left < 0 ? 30 : 41;
        int x = (UiScale.guiWidth(graphics) - width) / 2;
        int y = 16;

        Theme.roundedRect(graphics, x, y, width, height, Theme.PANEL);
        Theme.roundedOutline(graphics, x, y, width, height, Theme.BORDER);

        graphics.centeredText(font, headline, x + width / 2, y + 7, Theme.TEXT);
        graphics.centeredText(font, names, x + width / 2, y + 18, Theme.TEXT_MUTED);

        if (left >= 0) {
            graphics.centeredText(font, clock, x + width / 2, y + 29, Theme.TEXT_FAINT);
        }
    }

    private static void drawSpectatingBanner(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!com.vivet.speedrun.client.ResultsPrompt.isParked()) return;

        Font font = minecraft.font;
        Component headline = Component.translatable("vivet.hud.spectating");
        Component hint = Component.translatable("vivet.hud.spectating_hint",
                com.vivet.speedrun.client.VivetSpeedrunClient.OPEN_SCREEN
                        .getTranslatedKeyMessage());

        int width = Math.max(font.width(headline), font.width(hint)) + 24;
        int height = 30;
        int x = (UiScale.guiWidth(graphics) - width) / 2;
        int y = 16;

        Theme.roundedRect(graphics, x, y, width, height, Theme.PANEL);
        Theme.roundedOutline(graphics, x, y, width, height, Theme.BORDER);

        graphics.centeredText(font, headline, x + width / 2, y + 7, Theme.TEXT);
        graphics.centeredText(font, hint, x + width / 2, y + 18, Theme.TEXT_FAINT);
    }

    private static int drawTarget(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                  float opacity) {
        boolean concealed = ItemRoll.isConcealing();

        int tile = ITEM_DRAWN + 6;
        Theme.roundedRect(graphics, x + PADDING, y, tile, tile,
                Theme.fade(Theme.PANEL_SUNKEN, opacity));
        Theme.roundedOutline(graphics, x + PADDING, y, tile, tile,
                Theme.fade(Theme.BORDER, opacity));

        if (!concealed) {
            float scale = ITEM_DRAWN / (float) ITEM_SIZE;
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + PADDING + 3, y + 3);
            pose.scale(scale, scale);
            graphics.item(ClientState.target(), 0, 0);
            pose.popMatrix();
        } else {
            graphics.centeredText(font, Component.literal("?"),
                    x + PADDING + tile / 2, y + tile / 2 - 4,
                    Theme.fade(Theme.TEXT_FAINT, opacity));
        }

        int textX = x + PADDING + tile + 8;
        int room = x + PANEL_WIDTH - PADDING - textX;

        int nameY = y + (tile - font.lineHeight) / 2;

        Marquee.drawLeft(graphics, font,
                concealed ? "???" : ClientState.targetName().getString(),
                textX, nameY, room,
                Theme.fade(concealed ? Theme.TEXT_FAINT : Theme.TEXT, opacity));

        return y + tile + 7;
    }

    private static int drawClock(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                 float opacity) {
        int color = Theme.TEXT;
        String clock = Time.formatPrecise(ClientState.elapsedCentis());

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + PADDING, y);
        pose.scale(CLOCK_SCALE, CLOCK_SCALE);
        graphics.text(font, Component.literal(clock), 0, 0, Theme.fade(color, opacity));
        pose.popMatrix();

        int clockWidth = Math.round(font.width(clock) * CLOCK_SCALE);
        graphics.text(font, Component.literal("elapsed"),
                x + PADDING + clockWidth + 6, y + CLOCK_H - font.lineHeight,
                Theme.fade(Theme.TEXT_FAINT, opacity));

        return y + CLOCK_H + 3;
    }

    private static int drawCarryOn(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                   float opacity) {
        if (!Session.isCarryingOn()) return y;

        float share = Session.carryOnShare();
        int percent = (int) Math.floor(share * 100.0F);
        boolean out = percent <= 0;

        int color = out ? Theme.DANGER
                : percent <= 25 ? Theme.WARNING
                : Theme.SUCCESS;

        int inner = PANEL_WIDTH - PADDING * 2;

        int cursor = y - CARRY_ON_LIFT;
        graphics.text(font, Component.translatable("vivet.hud.alone"), x + PADDING, cursor,
                Theme.fade(Theme.TEXT_FAINT, opacity));
        cursor += font.lineHeight + 1;

        long millis = Session.carryOnMillisLeft();
        String left = Time.format((int) ((millis + 999L) / 1000L));
        graphics.text(font, Component.translatable("vivet.hud.alone.left", left),
                x + PADDING, cursor, Theme.fade(Theme.TEXT, opacity));

        Component worth = out
                ? Component.translatable("vivet.hud.alone.none")
                : Component.translatable("vivet.hud.alone.share", percent);
        int worthWidth = font.width(worth.getString());

        if (font.width(left) + 12 <= inner - worthWidth) {
            graphics.text(font, worth, x + PADDING + inner - worthWidth, cursor,
                    Theme.fade(color, opacity));
        }
        cursor += font.lineHeight + 3;

        Theme.progressBar(graphics, x + PADDING, cursor, inner, 4, share,
                Theme.fade(color, opacity));

        return cursor + 4 + 6;
    }

    private static final int CARRY_ON_LIFT = 3;

    private static int carryOnHeight(Font font) {
        if (!Session.isCarryingOn()) return 0;
        return font.lineHeight * 2 + 1 + 3 + 4 + 6 - CARRY_ON_LIFT;
    }

    private static int carryOnStage;

    private static void tickCarryOn() {
        if (!Session.isCarryingOn()) {
            carryOnStage = 0;
            return;
        }

        int percent = (int) Math.floor(Session.carryOnShare() * 100.0F);
        int stage = percent <= 0 ? 3 : percent <= 25 ? 2 : 1;
        if (stage <= carryOnStage) return;

        carryOnStage = stage;
        switch (stage) {
            case 1 -> Cues.carryOnStarted();
            case 2 -> Cues.carryOnLow();
            default -> Cues.carryOnGone();
        }
    }

    private static int drawTier(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                float opacity, int accent) {
        String label = ClientState.difficulty().displayName();
        int width = font.width(label) + 14;

        Theme.badge(graphics, x + PADDING, y, width, 13, Theme.fade(accent, opacity));
        graphics.text(font, Component.literal(label), x + PADDING + 7, y + 3,
                Theme.fade(accent, opacity));

        String mode = RunMode.current().displayName();
        graphics.text(font, Component.literal(mode),
                x + PANEL_WIDTH - PADDING - font.width(mode), y + 3,
                Theme.fade(Theme.TEXT_FAINT, opacity));

        return y + 18;
    }

    private static int drawStandings(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                     float opacity) {
        List<Racer> racers = Session.racers();
        if (racers.size() < 2) return y;

        Theme.divider(graphics, x + PADDING, y, PANEL_WIDTH - PADDING * 2);
        int cursor = y + 6;

        for (Racer racer : racers) {
            int color = switch (racer.status) {
                case FINISHED -> Theme.SUCCESS;
                case OUT, DISCONNECTED -> Theme.TEXT_FAINT;
                case RACING -> Theme.TEXT_MUTED;
            };

            String right = racer.status == Racer.Status.FINISHED
                    ? Time.formatPrecise(racer.centis > 0
                            ? racer.centis : Time.toCentis(racer.seconds))
                    : racer.status == Racer.Status.RACING && !Session.isLocalName(racer.name)
                            && racer.liveAt != 0L
                                    ? Time.formatPrecise(racer.projectedCentis())
                                    : racer.statusText();
            int rightWidth = font.width(right);

            NameLabel wearing = NameLabel.of(racer.name);

            int tagWidth = NameLabel.tagWidthOf(font, wearing, racer.name);
            int room = PANEL_WIDTH - PADDING * 2 - rightWidth - 8 - tagWidth;

            if (font.width(racer.name) <= room) {
                NameLabel.drawTaggedLeft(graphics, font, wearing, racer.name, x + PADDING, cursor,
                        Theme.fade(color, opacity));
            } else {
                if (wearing != null) wearing.drawTag(graphics, font, x + PADDING, cursor, racer.name);
                Marquee.drawLeft(graphics, font, racer.name, x + PADDING + tagWidth, cursor, room,
                        Theme.fade(wearing == null ? color : wearing.nameColor(racer.name), opacity));
            }

            graphics.text(font, Component.literal(right),
                    x + PANEL_WIDTH - PADDING - rightWidth, cursor, Theme.fade(color, opacity));

            cursor += STANDING_ROW_H;
        }
        return cursor;
    }

    private record WatchCard(String racer, net.minecraft.world.item.ItemStack target, String targetName,
                             int centis, boolean away, com.vivet.speedrun.game.Difficulty band, String mode,
                             String watchingWord, List<Component> under, boolean ended) {
    }

    private static WatchCard watchCard() {
        String racer = Spectating.racer();
        String next = Spectating.next();
        String ended = Spectating.endLine();

        List<Component> under = new ArrayList<>();
        if (!ended.isEmpty()) {
            under.add(Component.literal(ended));
        } else {
            boolean eyes = com.vivet.speedrun.client.replay.SpectateEyes.available();
            boolean inside = eyes && com.vivet.speedrun.client.replay.SpectateEyes.inside();

            boolean locked = Spectating.locked();
            if (!locked && !inside) {
                under.add(Component.translatable("vivet.watch.teleport",
                        com.vivet.speedrun.client.VivetSpeedrunClient.REROLL.getTranslatedKeyMessage(),
                        racer));
            }
            if (locked) {
            } else if (inside) {
                under.add(Component.translatable("vivet.watch.leave_eyes",
                        net.minecraft.client.Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage()));
            } else if (eyes) {
                under.add(Component.translatable("vivet.watch.enter_eyes",
                        com.vivet.speedrun.client.VivetSpeedrunClient.OPEN_SCREEN.getTranslatedKeyMessage()));
            } else if (!next.isEmpty()) {
                under.add(Component.translatable("vivet.watch.switch",
                        com.vivet.speedrun.client.VivetSpeedrunClient.OPEN_SCREEN
                                .getTranslatedKeyMessage(), next));
            }
            if (eyes && Spectating.staff() && !next.isEmpty()) {
                under.add(Component.literal("Press = for Next Player"));
            }
        }
        return new WatchCard(racer, Spectating.target(), Spectating.targetName(), Spectating.centis(),
                Spectating.away(), Spectating.band(), Spectating.modeName(),
                Component.translatable("vivet.watch.watching").getString(), under, !ended.isEmpty());
    }

    private static WatchCard replayCard() {
        var playback = com.vivet.speedrun.client.replay.ReplayPlayback.get();
        var match = playback.replay().match();
        Target target = ItemTiers.byId(match.itemId);
        net.minecraft.world.item.ItemStack stack = target == null
                ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER)
                : target.displayStack();
        String racer = playback.watchedName();

        List<Component> under = new ArrayList<>();
        under.add(Component.translatable("vivet.watch.teleport",
                com.vivet.speedrun.client.VivetSpeedrunClient.REROLL.getTranslatedKeyMessage(), racer));
        String other = playback.otherName();
        if (!other.isEmpty()) {
            under.add(Component.translatable("vivet.watch.switch",
                    com.vivet.speedrun.client.VivetSpeedrunClient.OPEN_SCREEN.getTranslatedKeyMessage(), other));
        }
        under.add(Component.translatable("vivet.replay.controls_hint",
                com.vivet.speedrun.client.VivetSpeedrunClient.REPLAY_CONTROLS.getTranslatedKeyMessage()));

        com.vivet.speedrun.game.Difficulty band = com.vivet.speedrun.game.Difficulty.byId(match.band);
        return new WatchCard(racer, stack, target == null ? "" : target.displayName().getString(),
                playback.raceCentis(), playback.away(),
                band == null ? com.vivet.speedrun.game.Difficulty.MEDIUM : band,
                match.mode().displayName(), Component.translatable("vivet.replay.watching").getString(),
                under, false);
    }

    private static void drawCard(GuiGraphicsExtractor graphics, Font font, WatchCard card) {
        String racer = card.racer();
        boolean away = card.away();
        List<Component> under = card.under();

        int height = PADDING * 2 + ITEM_DRAWN + 6 + 7 + CLOCK_H + 3 + 18 + 6 + STANDING_ROW_H
                + under.size() * 11 + 5 + Wordmark.height();

        int x = MARGIN;
        int y = MARGIN;
        float opacity = 1.0F;
        int accent = card.band().color();

        Theme.roundedRect(graphics, x, y, PANEL_WIDTH, height, Theme.fade(Theme.PANEL, opacity));
        Theme.roundedOutline(graphics, x, y, PANEL_WIDTH, height, Theme.fade(Theme.BORDER, opacity));

        int cursor = y + PADDING;

        int tile = ITEM_DRAWN + 6;
        Theme.roundedRect(graphics, x + PADDING, cursor, tile, tile, Theme.fade(Theme.PANEL_SUNKEN, opacity));
        Theme.roundedOutline(graphics, x + PADDING, cursor, tile, tile, Theme.fade(Theme.BORDER, opacity));
        float scale = ITEM_DRAWN / (float) ITEM_SIZE;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + PADDING + 3, cursor + 3);
        pose.scale(scale, scale);
        graphics.item(card.target(), 0, 0);
        pose.popMatrix();

        int textX = x + PADDING + tile + 8;
        Marquee.drawLeft(graphics, font, card.targetName(), textX,
                cursor + (tile - font.lineHeight) / 2, x + PANEL_WIDTH - PADDING - textX,
                Theme.fade(Theme.TEXT, opacity));
        cursor += tile + 7;

        String clock = Time.formatPrecise(card.centis());
        pose.pushMatrix();
        pose.translate(x + PADDING, cursor);
        pose.scale(CLOCK_SCALE, CLOCK_SCALE);
        graphics.text(font, Component.literal(clock), 0, 0, Theme.fade(Theme.TEXT, opacity));
        pose.popMatrix();
        int clockWidth = Math.round(font.width(clock) * CLOCK_SCALE);
        graphics.text(font, Component.translatable(away ? "vivet.watch.away" : "vivet.watch.elapsed"),
                x + PADDING + clockWidth + 6, cursor + CLOCK_H - font.lineHeight,
                Theme.fade(away ? Theme.WARNING : Theme.TEXT_FAINT, opacity));
        cursor += CLOCK_H + 3;

        String band = card.band().displayName();
        int badge = font.width(band) + 14;
        Theme.badge(graphics, x + PADDING, cursor, badge, 13, Theme.fade(accent, opacity));
        graphics.text(font, Component.literal(band), x + PADDING + 7, cursor + 3,
                Theme.fade(accent, opacity));
        String mode = card.mode();
        graphics.text(font, Component.literal(mode), x + PANEL_WIDTH - PADDING - font.width(mode),
                cursor + 3, Theme.fade(Theme.TEXT_FAINT, opacity));
        cursor += 18;

        Theme.divider(graphics, x + PADDING, cursor, PANEL_WIDTH - PADDING * 2);
        cursor += 6;
        Component watchingWord = Component.literal(card.watchingWord());
        graphics.text(font, watchingWord, x + PADDING, cursor, Theme.fade(Theme.TEXT_FAINT, opacity));
        int nameX = x + PADDING + font.width(watchingWord) + 4;
        NameLabel wearing = NameLabel.of(racer);
        int room = x + PANEL_WIDTH - PADDING - nameX;
        if (font.width(racer) + NameLabel.tagWidthOf(font, wearing, racer) <= room) {
            NameLabel.drawTaggedLeft(graphics, font, wearing, racer, nameX, cursor,
                    Theme.fade(Theme.TEXT, opacity));
        } else {
            Marquee.drawLeft(graphics, font, racer, nameX, cursor, room, Theme.fade(Theme.TEXT, opacity));
        }
        cursor += STANDING_ROW_H;

        for (Component line : under) {
            Marquee.drawLeft(graphics, font, line.getString(), x + PADDING, cursor,
                    PANEL_WIDTH - PADDING * 2,
                    Theme.fade(card.ended() ? Theme.WARNING : Theme.TEXT_MUTED, opacity));
            cursor += 11;
        }

        drawWatermark(graphics, x, cursor, opacity);
    }

    private static int measureHeight() {
        int height = PADDING * 2;
        height += ITEM_DRAWN + 6 + 7;
        height += CLOCK_H + 3;
        height += carryOnHeight(Minecraft.getInstance().font);
        height += 18;

        if (!ItemRoll.isConcealing()) {
            int rows = paceRows().size();
            if (rows > 0) height += 21 + rows * PACE_ROW_H;
        }

        int racers = Session.racers().size();
        if (racers >= 2) height += 6 + racers * STANDING_ROW_H;

        if (leadingCentis() > 0) height += 11;

        height += 5 + Wordmark.height();

        return height;
    }
}
