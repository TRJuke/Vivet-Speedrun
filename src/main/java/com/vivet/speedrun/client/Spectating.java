package com.vivet.speedrun.client;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.RunRecord;
import com.vivet.speedrun.game.Target;
import com.vivet.speedrun.game.Time;
import com.vivet.speedrun.net.Payloads;
import com.vivet.speedrun.p2p.Watching;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

public final class Spectating {
    private static final long LEAVE_AFTER_MS = 4_000L;

    private static volatile boolean active;

    private static volatile boolean asking;

    private static Supplier<Screen> origin = () -> null;

    private static String racer = "";
    private static String itemId = "";
    private static Target target;
    private static ItemStack stack = ItemStack.EMPTY;
    private static int centis;
    private static Difficulty band = Difficulty.MEDIUM;
    private static String kind = "";
    private static boolean running;
    private static boolean away;
    private static String next = "";
    private static boolean staff;

    private static boolean heard;

    private static String endLine = "";
    private static long endedAt;

    private static long startedAt;

    private Spectating() {
    }

    public static void start(Minecraft minecraft, Screen from, String uuid, String name) {
        if (asking || active) {
            Cues.nothingHappened();
            return;
        }
        asking = true;
        Cues.confirm();

        Watching.watch(uuid,
                ticket -> minecraft.execute(() -> {
                    asking = false;
                    if (ticket.error != null || ticket.host == null || ticket.port <= 0) {
                        com.vivet.speedrun.client.hud.Notices.watchRefused(uuid, name,
                                Component.literal(ticket.error == null
                                        ? "Couldn't start spectating." : ticket.error));
                        return;
                    }
                    go(minecraft, from, ticket);
                }),
                reason -> minecraft.execute(() -> {
                    asking = false;
                    com.vivet.speedrun.client.hud.Notices.watchRefused(uuid, name,
                            Component.literal(reason));
                }));
    }

    private static void go(Minecraft minecraft, Screen from, Watching.Ticket ticket) {
        if (minecraft.level != null) {
            Cues.deny();
            return;
        }

        active = true;
        heard = false;
        origin = () -> from;
        racer = ticket.racer == null ? "" : ticket.racer;
        kind = ticket.kind == null ? "" : ticket.kind;
        Difficulty known = ticket.band == null ? null : Difficulty.byId(ticket.band);
        band = known == null ? Difficulty.MEDIUM : known;
        staff = Boolean.TRUE.equals(ticket.staff);
        itemId = "";
        target = null;
        stack = ItemStack.EMPTY;
        centis = 0;
        running = true;
        away = false;
        next = "";
        endLine = "";
        endedAt = 0L;
        startedAt = System.currentTimeMillis();

        VivetSpeedrun.LOGGER.info("Spectating {}'s match{}", racer,
                staff ? " as staff" : "");
        HostedConnect.watch(minecraft, from, ticket.host, ticket.port);
    }

    public static void update(Payloads.Watch watch) {
        if (!active || endedAt != 0L) return;

        boolean first = !heard;
        heard = true;

        if (!first && !racer.isEmpty() && !racer.equals(watch.racer())) {
            Cues.watchSwitched();
            say(Component.translatable("vivet.watch.now", watch.racer()), ChatFormatting.GRAY);
        } else if (first) {
            Cues.watchStarted();
            say(Component.translatable("vivet.watch.started", watch.racer()), ChatFormatting.GRAY);
        }

        racer = watch.racer();
        centis = watch.centis();
        band = watch.band();
        kind = watch.kind();
        running = watch.running();
        away = watch.away();
        next = watch.next() == null ? "" : watch.next();

        if (!watch.itemId().equals(itemId)) {
            itemId = watch.itemId();
            target = ItemTiers.byId(itemId);
            stack = target == null ? new ItemStack(Items.BARRIER) : target.displayStack();
        }
    }

    public static void ended(Payloads.WatchEnd end) {
        if (!active || endedAt != 0L) return;

        String line = lineFor(end);
        say(Component.literal(line), ChatFormatting.YELLOW);

        if (end.next() != null && !end.next().isEmpty()) return;

        endLine = line;
        endedAt = System.currentTimeMillis();
        running = false;
        centis = end.centis();
        Cues.watchEnded();
    }

    private static String lineFor(Payloads.WatchEnd end) {
        String name = end.racer();
        String winner = end.winnerName() == null ? "" : end.winnerName();
        String detail = end.detail() == null ? "" : end.detail();

        if (end.outcome() == RunRecord.Outcome.DRAW) {
            return Component.translatable("vivet.watch.end.draw").getString();
        }
        if (end.outcome() == RunRecord.Outcome.WON && "found it".equals(detail)) {
            Target found = ItemTiers.byId(end.itemId());
            String item = found == null ? end.itemId() : found.displayName().getString();
            return Component.translatable("vivet.watch.end.found", name, item,
                    Time.formatPrecise(end.centis())).getString();
        }
        if (end.outcome() == RunRecord.Outcome.WON) {
            return Component.translatable("vivet.watch.end.won", name).getString();
        }
        if (!winner.isEmpty() && !winner.equals(name)) {
            return Component.translatable("vivet.watch.end.won", winner).getString();
        }
        return switch (detail) {
            case "gave up" -> Component.translatable("vivet.watch.end.gave_up", name).getString();
            case "time cap" -> Component.translatable("vivet.watch.end.time_up").getString();
            case "left", "went idle" -> Component.translatable("vivet.watch.end.left", name).getString();
            default -> Component.translatable("vivet.watch.end.over").getString();
        };
    }

    private static void say(Component line, ChatFormatting colour) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.player.sendSystemMessage(line.copy().withStyle(colour));
    }

    public static void tick(Minecraft minecraft) {
        if (!active) return;
        holdBlind(minecraft);

        if (HostedConnect.watchRetryPending()) {
            if (HostedConnect.retryDue(System.currentTimeMillis())) HostedConnect.reconnect(minecraft);
            return;
        }

        if (minecraft.getConnection() == null
                && !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.ConnectScreen)
                && System.currentTimeMillis() - startedAt > 1_000L) {
            VivetSpeedrun.LOGGER.info("Spectating ended before the world loaded");
            clear();
            return;
        }

        if (endedAt == 0L) return;
        if (System.currentTimeMillis() - endedAt < LEAVE_AFTER_MS) return;

        endedAt = Long.MAX_VALUE;
        WorldActions.trace("spectated match ended");
        WorldActions.quitRaceTo(minecraft, HostedConnect::destination);
    }

    public static void teleportToRacer() {
        if (!active || endedAt != 0L) {
            Cues.deny();
            return;
        }

        if (locked() || com.vivet.speedrun.client.replay.SpectateEyes.inside()) {
            Cues.nothingHappened();
            return;
        }
        Cues.spectateTeleport();
        Network.teleportToRacer();
    }

    public static void switchPlayer() {
        if (!active || endedAt != 0L) {
            Cues.deny();
            return;
        }
        if (com.vivet.speedrun.client.replay.SpectateEyes.available()) {
            if (locked() || com.vivet.speedrun.client.replay.SpectateEyes.inside()) {
                Cues.nothingHappened();
                return;
            }
            Cues.spectateTeleport();
            Network.spectate();
            return;
        }
        if (next.isEmpty()) {
            Cues.deny();
            return;
        }
        Network.spectate();
    }

    public static void nextPlayer() {
        if (!active || endedAt != 0L || !staff || next.isEmpty()) {
            Cues.deny();
            return;
        }
        Network.nextPlayer();
    }

    public static boolean staff() {
        return staff;
    }

    public static boolean locked() {
        return active && !staff && com.vivet.speedrun.client.replay.SpectateEyes.available();
    }

    public static boolean blind() {
        return locked() && endedAt == 0L && !com.vivet.speedrun.client.replay.SpectateEyes.inside();
    }

    public static void holdBlind(Minecraft minecraft) {
        if (blind() && minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
    }

    public static void clear() {
        if (!active) return;
        active = false;
        heard = false;
        endLine = "";
        endedAt = 0L;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean heard() {
        return heard;
    }

    public static String racer() {
        return racer;
    }

    public static ItemStack target() {
        return stack;
    }

    public static String targetName() {
        return target == null ? "" : target.displayName().getString();
    }

    public static int centis() {
        return centis;
    }

    public static Difficulty band() {
        return band;
    }

    public static String modeName() {
        return switch (kind) {
            case "solo" -> Component.translatable("vivet.watch.mode.solo").getString();
            case "ranked" -> Component.translatable("vivet.watch.mode.ranked").getString();
            default -> Component.translatable("vivet.watch.mode.unranked").getString();
        };
    }

    public static boolean away() {
        return away;
    }

    public static String next() {
        return next;
    }

    public static String endLine() {
        return endLine;
    }
}
