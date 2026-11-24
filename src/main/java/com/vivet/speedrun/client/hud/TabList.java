package com.vivet.speedrun.client.hud;

import com.vivet.speedrun.client.ClientState;
import com.vivet.speedrun.client.HostedConnect;
import com.vivet.speedrun.client.ui.AccountBanner;
import com.vivet.speedrun.client.ui.QuestPlate;
import com.vivet.speedrun.client.ui.Ranks;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiScale;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.game.AccountXp;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.p2p.Achievements;
import com.vivet.speedrun.p2p.CollectionBook;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.p2p.Quests;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TabList {
    private static final int PAD = 6;
    private static final int GAP = 6;
    private static final int TITLE = 22;
    private static final int ROW = 13;
    private static final int FACE = 16;

    private static final int PLAYERS_W = 150;
    private static final int QUESTS_W = 216;
    private static final int PROFILE_W = 168;

    private static final int GOLD = 0xFFE8C34A;
    private static final int PLAYERS = 0xFF55E0E0;
    private static final int PROFILE = 0xFF5FD35F;

    private TabList() {
    }

    public static boolean wanted() {
        Minecraft minecraft = Minecraft.getInstance();
        return ClientState.isRunning() || HostedConnect.isRaceServer(minecraft.getCurrentServer());
    }

    private record Pair(String label, String value, int colour) {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        UiScale.push(graphics);
        try {
            draw(graphics, minecraft.font, minecraft, UiScale.guiWidth(graphics));
        } finally {
            UiScale.pop(graphics);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int screenWidth) {
        List<PlayerInfo> players = players(minecraft);
        List<Pair> profile = profile();

        Quests.refresh();
        Quests.Book book = Quests.book();
        boolean quests = Quests.current() && book != null && book.list != null;

        int questsH = quests ? 3 * QuestPlate.CARD + 2 * 4 + ROW + 4 : ROW;
        int bodyH = Math.max(players.size() * (FACE + 3), Math.max(profile.size() * ROW, questsH));
        int plateH = TITLE + bodyH + PAD;

        int width = PLAYERS_W + QUESTS_W + PROFILE_W + 2 * GAP + PAD * 2;
        int headH = 14;
        int height = PAD + headH + plateH + PAD;
        int x = (screenWidth - width) / 2;
        int y = 8;

        Theme.roundedBox(graphics, x, y, width, height, 6, Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, width, height, 6, Theme.PLATE_EDGE);

        Component heading = Component.literal("VIVET ").withStyle(ChatFormatting.BOLD)
                .append(Component.literal("SPEEDRUN").withStyle(s -> s.withBold(true).withColor(0x5AA9F0)));
        graphics.centeredText(font, heading, x + width / 2, y + PAD + 2, Theme.TEXT);

        int px = x + PAD;
        int py = y + PAD + headH;

        plate(graphics, font, px, py, PLAYERS_W, plateH, VanillaIcon.Kind.COMPASS,
                Component.translatable("vivet.tab.players", players.size()), PLAYERS);
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        int rowY = py + TITLE;
        for (PlayerInfo info : players) {
            boolean me = info.getProfile().id().equals(self);
            PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), px + 7, rowY, FACE);
            String name = cut(font, info.getProfile().name(), PLAYERS_W - 7 - FACE - 6 - 18);
            graphics.text(font, Component.literal(name), px + 7 + FACE + 5, rowY + 4,
                    me ? Theme.SUCCESS : Theme.TEXT, true);
            drawPing(graphics, px + PLAYERS_W - 16, rowY + 4, info.getLatency());
            rowY += FACE + 3;
        }
        px += PLAYERS_W + GAP;

        plate(graphics, font, px, py, QUESTS_W, plateH, VanillaIcon.Kind.QUEST,
                Component.translatable("vivet.quests.title"), QuestPlate.ACCENT);
        if (quests) {
            int cardY = py + TITLE;
            for (int i = 0; i < 3 && i < book.list.length; i++) {
                if (book.list[i] == null) continue;
                QuestPlate.drawCard(graphics, font, book.list[i], px + 6, cardY, QUESTS_W - 12);
                cardY += QuestPlate.CARD + 4;
            }
            long until = Quests.untilReset();
            if (until >= 0) {
                String resets = Component.translatable("vivet.quests.resets", QuestPlate.clockOf(until)).getString();
                graphics.centeredText(font, Component.literal(resets), px + QUESTS_W / 2, cardY + 2, Theme.TEXT_MUTED);
            }
        } else {
            graphics.centeredText(font, Component.translatable("vivet.quests.waiting"), px + QUESTS_W / 2,
                    py + TITLE + 2, Theme.TEXT_MUTED);
        }
        px += QUESTS_W + GAP;

        plate(graphics, font, px, py, PROFILE_W, plateH, VanillaIcon.Kind.STAR,
                Component.translatable("vivet.tab.profile"), PROFILE);
        rows(graphics, font, profile, px, py + TITLE, PROFILE_W);
    }

    private static void plate(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                              VanillaIcon.Kind icon, Component title, int colour) {
        Theme.roundedBox(graphics, x, y, width, height, 5, Theme.PLATE_LIT);
        Theme.roundedBoxOutline(graphics, x, y, width, height, 5, Theme.withAlpha(colour, 0x99));
        VanillaIcon.draw(graphics, icon, x + 14, y + 11, 16);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), x + 26, y + 7, colour, true);
    }

    private static void rows(GuiGraphicsExtractor graphics, Font font, List<Pair> pairs, int x, int y, int width) {
        int left = x + 7;
        int right = x + width - 7;
        for (Pair pair : pairs) {
            if (pair == null) {
                y += ROW / 2;
                continue;
            }
            graphics.text(font, Component.literal(pair.label()), left, y, Theme.TEXT_MUTED, true);
            int room = right - left - font.width(pair.label()) - 8;
            String value = cut(font, pair.value(), room);
            graphics.text(font, Component.literal(value), right - font.width(value), y, pair.colour(), true);
            y += ROW;
        }
    }

    private static void drawPing(GuiGraphicsExtractor graphics, int x, int y, int latency) {
        int lit = latency < 0 ? 0 : latency < 150 ? 5 : latency < 300 ? 4 : latency < 600 ? 3
                : latency < 1000 ? 2 : 1;
        int colour = lit >= 4 ? Theme.SUCCESS : lit == 3 ? Theme.WARNING : Theme.DANGER;
        for (int i = 0; i < 5; i++) {
            int h = i + 3;
            Theme.fill(graphics, x + i * 2, y + 8 - h, x + i * 2 + 1, y + 8,
                    i < lit ? colour : Theme.withAlpha(0xFFFFFFFF, 0x22));
        }
    }

    private static String cut(Font font, String text, int room) {
        if (text == null) return "";
        if (font.width(text) <= room) return text;
        String plain = text;
        while (plain.length() > 1 && font.width(plain + "...") > room) plain = plain.substring(0, plain.length() - 1);
        return plain.trim() + "...";
    }

    private static List<PlayerInfo> players(Minecraft minecraft) {
        List<PlayerInfo> sorted = new ArrayList<>();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return sorted;
        sorted.addAll(connection.getListedOnlinePlayers());
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        sorted.sort(Comparator.comparing((PlayerInfo info) -> !info.getProfile().id().equals(self))
                .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static List<Pair> profile() {
        List<Pair> rows = new ArrayList<>();
        MojangAuth.Profile profile = MojangAuth.knownProfile();
        rows.add(new Pair("Level", Integer.toString(AccountXp.current(null, null).level()), PLAYERS));
        String rankId = profile != null && profile.placed ? Ranks.displayRank(profile) : "unranked";
        int rankColour = profile != null && profile.placed ? Ranks.colorOf(rankId) : Theme.TEXT_MUTED;

        rows.add(new Pair("Rank", AccountBanner.rankText(profile), Theme.mix(rankColour, Theme.TEXT, 0.35F)));
        rows.add(new Pair("Rating", AccountBanner.pointsText(profile), Theme.TEXT));
        rows.add(null);
        Integer coins = MojangAuth.coins();
        if (coins != null) rows.add(new Pair("Vivet Coins", NumberFormat.getIntegerInstance(Locale.US).format(coins), GOLD));
        rows.add(new Pair("Collection", CollectionBook.found() + "/" + ItemTiers.collection().size(), Theme.TEXT));
        int unclaimed = Achievements.unclaimed();
        if (unclaimed > 0) rows.add(new Pair("Achievements", unclaimed + " to claim", 0xFFFF9A2E));
        return rows;
    }
}
