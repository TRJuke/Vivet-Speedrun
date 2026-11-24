package com.vivet.speedrun.client.hud;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.client.ui.AchievementText;
import com.vivet.speedrun.client.ui.InboxIcons;
import com.vivet.speedrun.client.ui.NameLabel;
import com.vivet.speedrun.client.ui.PlayerHead;
import com.vivet.speedrun.client.ui.QuestText;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.VanillaIcon;
import com.vivet.speedrun.media.MediaSounds;
import com.vivet.speedrun.p2p.Chat;
import com.vivet.speedrun.p2p.Hub;
import com.vivet.speedrun.p2p.Inbox;
import com.vivet.speedrun.p2p.Quests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class Notices {
    private static final long HOLD_MS = 8_000L;

    private static final long INVITE_HOLD_MS = 30_000L;

    private static final long MESSAGE_HOLD_MS = 14_000L;

    private static final long SLIDE_MS = 220L;

    private static final int WIDTH = 190;

    private static final int WIDE = 216;

    private static final int HEIGHT = 40;

    private static final int TOP = 8;

    private static final int PAD = 6;
    private static final int FACE = 24;

    private static final int PICTURE = 32;
    private static final int RADIUS = 6;

    private Notices() {
    }

    private record Notice(String uuid, String name, String label, Component line, int accent,
                          Kind kind, ItemStack picture, long at, String sound, Runnable cue,
                          InboxIcons.Kind section) {
        Notice(String uuid, String name, String label, Component line, int accent, Kind kind,
               ItemStack picture, long at, String sound, Runnable cue) {
            this(uuid, name, label, line, accent, kind, picture, at, sound, cue, null);
        }

        long life() {
            return switch (this.kind) {
                case INVITE -> INVITE_HOLD_MS;
                case MESSAGE -> MESSAGE_HOLD_MS;
                case PLAIN, COLLECTED, ACHIEVEMENT, QUEST, DISCORD -> HOLD_MS;
            };
        }

        long life(boolean waiting) {
            return waiting ? Math.min(life(), HOLD_MS) : life();
        }

        int width() {
            return this.kind == Kind.COLLECTED || this.kind == Kind.ACHIEVEMENT || this.kind == Kind.QUEST
                    || this.kind == Kind.DISCORD ? WIDE : WIDTH;
        }

        Notice now(long now) {
            return new Notice(this.uuid, this.name, this.label, this.line, this.accent, this.kind,
                    this.picture, now, this.sound, this.cue, this.section);
        }
    }

    private enum Kind {
        PLAIN, INVITE, MESSAGE, COLLECTED, ACHIEVEMENT, QUEST, DISCORD
    }

    private static final ArrayDeque<Notice> TURNS = new ArrayDeque<>();

    private static volatile List<Notice> showing = List.of();

    private static volatile long stamp;

    public static void invited(Hub.Invite invite) {
        if (invite == null) return;

        add(new Notice(invite.uuid(), invite.name(), invite.label(),
                Component.translatable("vivet.notice.invited"), Theme.SUCCESS, Kind.INVITE, null,
                System.currentTimeMillis(), null, Cues::queuePop));
    }

    public static void cameOnline(String uuid, String name) {
        if (name == null || name.isBlank()) return;

        add(new Notice(uuid, name, Hub.labelOf(uuid),
                Component.translatable("vivet.notice.online"), Theme.ACCENT, Kind.PLAIN, null,
                System.currentTimeMillis(), null, Cues::nextReady));
    }

    public static void arrived(Inbox.Line line) {
        if (line == null) return;

        String kind = line.kind == null ? "" : line.kind;
        String name = line.actor_name == null ? "" : line.actor_name;

        if (kind.equals("achievement")) return;

        Component says;
        int accent;

        switch (kind) {
            case "friend_request" -> {
                says = Component.translatable("vivet.notice.request");
                accent = Theme.SUCCESS;
            }
            case "friend_accepted" -> {
                says = Component.translatable("vivet.notice.accepted");
                accent = Theme.SUCCESS;
            }
            case "welcome" -> {
                says = Component.translatable("vivet.inbox.welcome.title");
                accent = Theme.DANGER;
            }
            case "cosmetic" -> {
                says = Component.translatable("vivet.notice.cosmetic");
                accent = Theme.ACCENT;
            }
            case "message" -> {
                says = line.title == null || line.title.isBlank()
                        ? Component.translatable("vivet.notice.personal")
                        : Component.literal(line.title);
                accent = Theme.WARNING;
            }
            case "report" -> {
                says = Component.translatable("vivet.notice.report");
                accent = Theme.WARNING;
            }
            case "ban" -> {
                says = line.title == null || line.title.isBlank()
                        ? Component.translatable("vivet.notice.ban")
                        : Component.literal(line.title);
                accent = Theme.DANGER;
            }
            case "coins", "refund" -> {
                says = line.title == null || line.title.isBlank()
                        ? Component.translatable("vivet.notice.news")
                        : Component.literal(line.title);
                accent = 0xFFE8C34A;
            }
            default -> {
                says = line.title == null || line.title.isBlank()
                        ? Component.translatable("vivet.notice.news")
                        : Component.literal(line.title);
                accent = Theme.ACCENT;
            }
        }

        boolean fromSomebody = !name.isBlank();

        Runnable cue = kind.equals("ban") ? Cues::badNews
                : kind.equals("coins") ? Cues::coinsGranted : Cues::nextReady;

        add(new Notice(fromSomebody ? line.actor_uuid : null,
                fromSomebody ? name : Component.translatable("vivet.notice.from").getString(),
                fromSomebody ? Hub.labelOf(line.actor_uuid) : "",
                says, accent, Kind.PLAIN, null, System.currentTimeMillis(), null, cue,
                fromSomebody ? null : InboxIcons.kindOf(kind)));
    }

    public static void collected(ItemStack picture, Component what) {
        add(new Notice(null, "", "", what == null ? Component.empty() : what,
                Theme.SUCCESS, Kind.COLLECTED, picture, System.currentTimeMillis(), null, Cues::collected));
    }

    public static void achieved(String id, int node, int nodes, boolean hidden, String rarity,
                                String sound) {
        if (id == null) return;
        String heading = hidden ? "vivet.notice.achievement.secret" : "vivet.notice.achievement";

        if (sound != null && !sound.isBlank()) MediaSounds.prepare(sound);
        add(new Notice(null, heading, "",
                AchievementText.nodeName(id, node, nodes, true),
                AchievementText.colour(rarity == null ? "common" : rarity), Kind.ACHIEVEMENT, null,
                System.currentTimeMillis(), sound == null || sound.isBlank() ? null : sound,
                Cues::achievement));
    }

    public static void quest(Quests.Done done) {
        if (done == null || done.id == null) return;
        String rarity = done.rarity == null ? "common" : done.rarity;
        add(new Notice(null, "vivet.notice.quest", "",
                Component.literal(QuestText.line(done.id, done.target == null ? 1 : done.target, done.text)),
                QuestText.colour(rarity), Kind.QUEST, null, System.currentTimeMillis(), null,
                Cues::questDone));
    }

    public static void discordLinked(int gained) {
        Component coins = gained > 0
                ? Component.translatable("vivet.notice.discord_coins",
                        java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(gained))
                : Component.empty();
        add(new Notice(null, "vivet.notice.discord", "", coins, 0xFFE8C34A, Kind.DISCORD, null,
                System.currentTimeMillis(), null, Cues::discordLinked));
    }

    private static volatile String soundDue;
    private static volatile long soundDueUntil;
    private static final long SOUND_WAIT_MS = 8_000L;

    private static void arrive(Notice notice) {
        if (notice.cue() != null) notice.cue().run();
        if (notice.kind() == Kind.ACHIEVEMENT && notice.sound() != null) {
            MediaSounds.prepare(notice.sound());
            soundDueUntil = System.currentTimeMillis() + SOUND_WAIT_MS;
            soundDue = notice.sound();
        }
    }

    private static void playDueSound(long now) {
        String due = soundDue;
        if (due == null) return;
        if (MediaSounds.lengthMs(due) > 0) {
            soundDue = null;
            MediaSounds.play(due, 1.0F);
        } else if (now > soundDueUntil) {
            soundDue = null;
        }
    }

    public static void watchRefused(String uuid, String name, Component why) {
        add(new Notice(uuid, name == null ? "" : name, Hub.labelOf(uuid),
                why == null ? Component.empty() : why, Theme.DANGER, Kind.PLAIN, null,
                System.currentTimeMillis(), null, Cues::deny));
    }

    public static void inviteRefused(String uuid, String name) {
        add(new Notice(uuid, name == null ? "" : name, Hub.labelOf(uuid),
                Component.translatable("vivet.notice.invite_refused"), Theme.DANGER, Kind.PLAIN, null,
                System.currentTimeMillis(), null, Cues::deny));
    }

    private static boolean up(Notice notice, long now, boolean waiting) {
        return now - notice.at() < notice.life(waiting) + SLIDE_MS;
    }

    private static void add(Notice notice) {
        long now = System.currentTimeMillis();
        synchronized (TURNS) {
            if (notice.uuid() != null) {
                TURNS.removeIf(held -> notice.uuid().equals(held.uuid()));
                for (Notice held : showing) {
                    if (notice.uuid().equals(held.uuid()) && up(held, now, false)) {
                        showing = List.of(new Notice(notice.uuid(), notice.name(), notice.label(),
                                notice.line(), notice.accent(), notice.kind(), notice.picture(),
                                held.at(), notice.sound(), notice.cue(), notice.section()));
                        stamp = now;
                        return;
                    }
                }
            }
            boolean busy = !TURNS.isEmpty()
                    || showing.stream().anyMatch(held -> up(held, now, true));
            if (busy) {
                TURNS.addLast(notice);
                stamp = now;
                return;
            }
            showing = List.of(notice.now(now));
        }
        stamp = now;
        arrive(notice);
    }

    private static void nextTurn(long now) {
        Notice fresh;
        synchronized (TURNS) {
            if (showing.stream().anyMatch(held -> up(held, now, true))) return;
            Notice waiting = TURNS.pollFirst();
            if (waiting == null) return;
            fresh = waiting.now(now);
            showing = List.of(fresh);
        }
        stamp = now;
        arrive(fresh);
    }

    public static void dismiss(String uuid) {
        if (uuid == null) return;

        synchronized (TURNS) {
            TURNS.removeIf(held -> uuid.equals(held.uuid()));
        }
        List<Notice> next = new ArrayList<>(showing);
        if (!next.removeIf(held -> uuid.equals(held.uuid()))) return;

        showing = List.copyOf(next);
        stamp = System.currentTimeMillis();
    }

    public static void messaged(Chat.Arrival arrival) {
        if (arrival == null || arrival.name().isBlank()) return;

        add(new Notice(arrival.uuid(), arrival.name(), Hub.labelOf(arrival.uuid()),
                Component.literal(arrival.body()), Theme.ACCENT, Kind.MESSAGE, null,
                System.currentTimeMillis(), null, Cues::nextReady));
    }

    public static long stamp() {
        return stamp;
    }

    public static void render(GuiGraphicsExtractor graphics, int screenWidth) {
        long now = System.currentTimeMillis();
        playDueSound(now);

        boolean waiting;
        synchronized (TURNS) {
            waiting = !TURNS.isEmpty();
        }
        if (waiting) nextTurn(now);

        List<Notice> held = showing;
        if (held.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        synchronized (TURNS) {
            waiting = !TURNS.isEmpty();
        }
        List<Notice> alive = new ArrayList<>(held.size());
        for (Notice notice : held) {
            if (up(notice, now, waiting)) alive.add(notice);
        }

        if (alive.size() != held.size()) showing = List.copyOf(alive);
        if (alive.isEmpty()) return;

        for (int row = 0; row < alive.size(); row++) {
            Notice notice = alive.get(row);
            int settled = TOP + row * (HEIGHT + 4);

            int x = (screenWidth - notice.width()) / 2;
            draw(graphics, minecraft.font, notice, x, settled + offsetOf(notice, now, waiting));
        }
    }

    private static int offsetOf(Notice notice, long now, boolean waiting) {
        long age = now - notice.at();
        if (age < SLIDE_MS) return travel(1.0F - ease(age / (float) SLIDE_MS));

        long left = notice.life(waiting) + SLIDE_MS - age;
        if (left < SLIDE_MS) return travel(1.0F - ease(left / (float) SLIDE_MS));

        return 0;
    }

    private static int travel(float amount) {
        return -Math.round((HEIGHT + TOP + 4) * Math.min(1.0F, Math.max(0.0F, amount)));
    }

    private static float ease(float t) {
        float clamped = Math.min(1.0F, Math.max(0.0F, t));
        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, Notice notice, int x,
                             int y) {
        int width = notice.width();

        Theme.roundedBox(graphics, x, y, width, HEIGHT, RADIUS, Theme.PLATE);
        Theme.roundedBoxOutline(graphics, x, y, width, HEIGHT, RADIUS,
                Theme.withAlpha(notice.accent(), 0xAA));

        if (notice.kind() == Kind.COLLECTED) {
            drawCollected(graphics, font, notice, x, y, width);
            return;
        }
        if (notice.kind() == Kind.ACHIEVEMENT) {
            drawAchieved(graphics, font, notice, x, y, width);
            return;
        }
        if (notice.kind() == Kind.QUEST) {
            drawQuest(graphics, font, notice, x, y, width);
            return;
        }
        if (notice.kind() == Kind.DISCORD) {
            drawDiscord(graphics, font, notice, x, y, width);
            return;
        }

        int faceX = x + PAD + 3;
        int faceY = y + (HEIGHT - FACE) / 2;

        if (notice.uuid() != null) {
            PlayerHead.draw(graphics, notice.name(), notice.uuid(), faceX, faceY, FACE);
            Theme.roundedBoxOutline(graphics, faceX - 1, faceY - 1, FACE + 2, FACE + 2, 2,
                    Theme.withAlpha(notice.accent(), 0x88));
        } else {
            Theme.roundedBox(graphics, faceX, faceY, FACE, FACE, 3,
                    Theme.withAlpha(notice.accent(), 0x33));
            Theme.roundedBoxOutline(graphics, faceX, faceY, FACE, FACE, 3,
                    Theme.withAlpha(notice.accent(), 0x88));

            if (notice.section() != null) {
                InboxIcons.icon(graphics, notice.section(), faceX + FACE / 2, faceY + FACE / 2,
                        notice.accent());
            }
        }

        int textX = faceX + FACE + PAD;
        int room = x + width - PAD - textX;

        NameLabel label = notice.label() == null || notice.label().isBlank()
                ? NameLabel.of(notice.name()) : NameLabel.byId(notice.label());

        int nameY = y + HEIGHT / 2 - font.lineHeight;
        NameLabel.drawLeft(graphics, font, label, notice.name(), textX, nameY, Theme.TEXT);

        drawCut(graphics, font, notice.line(), textX, nameY + font.lineHeight + 2, room,
                Theme.TEXT_MUTED);
    }

    private static void drawCollected(GuiGraphicsExtractor graphics, Font font, Notice notice,
                                      int x, int y, int width) {
        int boxX = x + PAD;
        int boxY = y + (HEIGHT - PICTURE) / 2;

        ItemStack picture = notice.picture();
        if (picture != null && !picture.isEmpty()) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(boxX, boxY);
            pose.scale(PICTURE / 16, PICTURE / 16);
            graphics.item(picture, 0, 0);
            pose.popMatrix();
        }

        int textX = boxX + PICTURE + PAD;
        int room = x + width - PAD - textX;

        int headY = y + HEIGHT / 2 - font.lineHeight;
        graphics.text(font, Component.translatable("vivet.notice.collected"), textX, headY,
                Theme.TEXT, true);

        drawCut(graphics, font, notice.line(), textX, headY + font.lineHeight + 2, room,
                Theme.withAlpha(notice.accent(), 0xFF));
    }

    private static void drawAchieved(GuiGraphicsExtractor graphics, Font font, Notice notice,
                                     int x, int y, int width) {
        int boxX = x + PAD;
        int size = AchievementText.TROPHY_SIZE;
        int iconX = boxX + (PICTURE - size) / 2;
        int iconY = y + (HEIGHT - size) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, AchievementText.TROPHY, iconX, iconY, 0.0F, 0.0F,
                size, size, size, size, 0xFFFFFFFF);

        int textX = boxX + PICTURE + PAD;
        int room = x + width - PAD - textX;

        int headY = y + HEIGHT / 2 - font.lineHeight;
        graphics.text(font, Component.translatable(notice.name()), textX, headY, Theme.TEXT, true);

        com.vivet.speedrun.client.ui.Marquee.drawSpotify(graphics, font, notice.line(), textX,
                headY + font.lineHeight + 2, room, Theme.withAlpha(notice.accent(), 0xFF));
    }

    private static void drawQuest(GuiGraphicsExtractor graphics, Font font, Notice notice,
                                  int x, int y, int width) {
        int boxX = x + PAD;
        VanillaIcon.draw(graphics, VanillaIcon.Kind.QUEST, boxX + PICTURE / 2, y + HEIGHT / 2, PICTURE);

        int textX = boxX + PICTURE + PAD;
        int room = x + width - PAD - textX;

        int headY = y + HEIGHT / 2 - font.lineHeight;
        graphics.text(font, Component.translatable(notice.name()), textX, headY, Theme.TEXT, true);

        drawCut(graphics, font, notice.line(), textX, headY + font.lineHeight + 2, room,
                Theme.withAlpha(notice.accent(), 0xFF));
    }

    private static void drawDiscord(GuiGraphicsExtractor graphics, Font font, Notice notice,
                                    int x, int y, int width) {
        int boxX = x + PAD;
        VanillaIcon.draw(graphics, VanillaIcon.Kind.DISCORD, boxX + PICTURE / 2, y + HEIGHT / 2, PICTURE);

        int textX = boxX + PICTURE + PAD;
        boolean paid = !notice.line().getString().isEmpty();
        int headY = paid ? y + HEIGHT / 2 - font.lineHeight : y + (HEIGHT - font.lineHeight) / 2;
        graphics.text(font, Component.translatable(notice.name()), textX, headY, Theme.TEXT, true);
        if (!paid) return;

        int lineY = headY + font.lineHeight + 2;
        VanillaIcon.draw(graphics, VanillaIcon.Kind.COIN, textX + 8, lineY + 3, 16);
        drawCut(graphics, font, notice.line(), textX + 18, lineY, x + width - PAD - textX - 18,
                Theme.withAlpha(notice.accent(), 0xFF));
    }

    private static void drawCut(GuiGraphicsExtractor graphics, Font font, Component line, int x,
                                int y, int room, int colour) {
        String text = line.getString();
        if (font.width(text) <= room) {
            graphics.text(font, line, x, y, colour, true);
            return;
        }

        while (text.length() > 1 && font.width(text + "...") > room) {
            text = text.substring(0, text.length() - 1);
        }
        graphics.text(font, Component.literal(text + "..."), x, y, colour, true);
    }
}
