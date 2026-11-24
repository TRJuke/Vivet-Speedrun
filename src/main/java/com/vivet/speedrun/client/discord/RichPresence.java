package com.vivet.speedrun.client.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.client.ClientState;
import com.vivet.speedrun.client.Spectating;
import com.vivet.speedrun.client.replay.ReplayPlayback;
import com.vivet.speedrun.client.screen.FindMatchScreen;
import com.vivet.speedrun.client.ui.Cosmetics;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Practice;
import com.vivet.speedrun.game.RunMode;
import com.vivet.speedrun.p2p.CollectionBook;
import com.vivet.speedrun.p2p.DiscordLink;
import com.vivet.speedrun.p2p.MojangAuth;
import com.vivet.speedrun.session.Racer;
import com.vivet.speedrun.session.Session;

import net.minecraft.client.Minecraft;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RichPresence {
    private static final String CLIENT_ID = "1552388902873206975";

    private static final String GET_VIVET = "https://vivetspeedrun.com";

    private static final long SEND_GAP_MS = 4_000L;
    private static final long RETRY_MS = 15_000L;

    private static volatile String wanted;
    private static volatile boolean enabled;

    private static Thread worker;
    private static boolean loggedRefusal;
    private static int tickCount;
    private static final long LAUNCHED = System.currentTimeMillis() / 1000L;

    private static long runStart;

    private RichPresence() {
    }

    public static void start() {
        enabled = ConfigManager.get().discordStatus;
        if (worker != null) return;
        worker = new Thread(RichPresence::loop, "vivet-discord-status");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    public static void settingChanged() {
        enabled = ConfigManager.get().discordStatus;
        if (!enabled) wanted = null;
    }

    public static void tick(Minecraft minecraft) {
        if (++tickCount % 20 != 0) return;
        if (!enabled) {
            wanted = null;
            return;
        }
        try {
            wanted = build(minecraft).toString();
        } catch (RuntimeException e) {
            VivetSpeedrun.LOGGER.debug("Could not update Discord status", e);
        }
    }

    private static JsonObject build(Minecraft minecraft) {
        String details;
        String state = null;
        long start = LAUNCHED;
        boolean timed = true;
        boolean inRun = false;
        int partySize = 0;
        int partyMax = 0;

        MojangAuth.Profile profile = MojangAuth.knownProfile();

        if (ReplayPlayback.isActive()) {
            details = bold("Watching a replay");
        } else if (Spectating.isActive()) {
            String who = Spectating.racer();
            details = who == null || who.isBlank() ? bold("Spectating a match") : bold("Spectating") + " " + who;
        } else if (Session.isActive() && Session.isRanked() && !Session.isRankedSolo()) {
            details = bold("Ranked 1v1");
            timed = false;
        } else if (ClientState.isRunning()) {
            inRun = true;
            RunMode mode = RunMode.current();
            String modeName = bold(Practice.isActive() ? "Practice"
                    : switch (mode) {
                        case RANKED_SOLO, SOLO -> "Solo";
                        case RANKED_MATCH -> "Ranked 1v1";
                        case PUBLIC -> "Unranked Match";
                        case PRIVATE -> "Private Lobby";
                    });

            String item = Session.isSetSeed() ? "Set Seed" : ClientState.targetName().getString();
            details = item.isBlank() ? modeName : modeName + ": " + item;

            List<String> rivals = rivals();
            if (mode.isVersus() && rivals.size() == 1) state = "vs " + rivals.get(0);
            else if (mode.isVersus() && rivals.size() > 1) state = "vs " + rivals.size() + " players";
            else state = ClientState.difficulty().displayName();
            if (mode == RunMode.PUBLIC) {
                partySize = 2;
                partyMax = 2;
            }

            long began = System.currentTimeMillis() / 1000L - ClientState.elapsedSeconds();
            if (runStart == 0L || Math.abs(began - runStart) > 3L) runStart = began;
            start = runStart;
        } else if (Session.isActive()) {
            boolean unranked = Session.isPublicMatch();
            details = bold(unranked ? "Unranked Match" : "Private Lobby");
            List<String> rivals = rivals();
            state = rivals.isEmpty() ? "Waiting for players"
                    : unranked && rivals.size() == 1 ? "vs " + rivals.get(0)
                    : rivals.size() + (rivals.size() == 1 ? " player" : " players") + " in lobby";
        } else if (searching(minecraft)) {
            details = bold("Searching for a match");
        } else {
            details = bold("In the menu");
            state = menuLine(profile);
        }
        if (!inRun) runStart = 0L;

        JsonObject activity = new JsonObject();
        activity.addProperty("type", 0);
        activity.addProperty("details", cut(details));
        if (state != null && !state.isBlank()) activity.addProperty("state", cut(state));

        if (timed) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", start);
            activity.add("timestamps", timestamps);
        }
        if (partyMax > 0) {
            JsonObject party = new JsonObject();
            party.addProperty("id", "vivet-" + LAUNCHED);
            JsonArray size = new JsonArray();
            size.add(partySize);
            size.add(partyMax);
            party.add("size", size);
            activity.add("party", party);
        }

        JsonObject assets = new JsonObject();
        String emblem = emblemAsset(profile);
        String level = profile != null && profile.level > 0 ? " | Level " + profile.level : "";
        if (emblem != null) {
            assets.addProperty("large_image", emblem);
            assets.addProperty("large_text", cut(rankLine(profile)));
            assets.addProperty("small_image", COIN);
            assets.addProperty("small_text", cut("Vivet Speedrun" + level));
        } else {
            assets.addProperty("large_image", COIN);
            assets.addProperty("large_text", cut("Vivet Speedrun" + level));
        }
        activity.add("assets", assets);

        JsonArray buttons = new JsonArray();

        buttons.add(button("Get Vivet Speedrun", GET_VIVET));
        buttons.add(button("Join the Discord", DiscordLink.INVITE));
        activity.add("buttons", buttons);
        return activity;
    }

    private static final String COIN = "vivet_coin";

    private static List<String> rivals() {
        return Session.racers().stream().map((Racer racer) -> racer.name)
                .filter(name -> name != null && !name.equals(Session.localName())).toList();
    }

    private static String menuLine(MojangAuth.Profile profile) {
        List<String> parts = new java.util.ArrayList<>();
        if (profile != null && profile.level > 0) parts.add("Level " + profile.level);
        int found = CollectionBook.found();
        int total = ItemTiers.collection().size();
        if (found > 0 && total > 0) {
            parts.add(NUMBER.format(found) + "/" + NUMBER.format(total) + " items found");
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private static String emblemAsset(MojangAuth.Profile profile) {
        if (profile == null) return null;
        String worn = profile.emblem;
        if (worn != null && !worn.isBlank() && !"rank".equals(profile.emblemMode)) {
            String rank = Cosmetics.rankOf(worn);
            if (rank != null) return "rank_" + rank.toLowerCase(Locale.ROOT);
            String set = worn.startsWith("emblem.") ? worn.substring("emblem.".length()) : worn;
            if (OWNED_EMBLEMS.contains(set)) return "emblem_" + set;
        }
        if (!profile.placed || profile.rank == null) return null;
        String rung = profile.speedTitle != null && !profile.speedTitle.isBlank() ? profile.speedTitle
                : profile.speedKing ? "speed_king" : profile.rank;
        return "rank_" + rung.toLowerCase(Locale.ROOT);
    }

    private static final java.util.Set<String> OWNED_EMBLEMS = java.util.Set.of("speed_god", "valeska");

    private static String rankLine(MojangAuth.Profile profile) {
        if (profile == null || !profile.placed || profile.rankName == null) return "Unranked";

        String title = profile.speedTitle != null && !profile.speedTitle.isBlank() ? profile.speedTitle
                : profile.speedKing ? "speed_king" : null;
        StringBuilder line = new StringBuilder(title != null ? SPEED_TITLES.getOrDefault(title, profile.rankName)
                : profile.rankName);
        if (profile.rating != null) line.append(" | ").append(NUMBER.format(profile.rating)).append(" Elo");
        if (profile.position != null && profile.position > 0) line.append(" | #").append(profile.position);
        return line.toString();
    }

    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);

    private static final java.util.Map<String, String> SPEED_TITLES = java.util.Map.of(
            "speed_king", "Speed King", "speed_prince", "Speed Prince",
            "speed_knight", "Speed Knight", "speed_squire", "Speed Squire");

    static String bold(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 'A' && c <= 'Z') out.appendCodePoint(0x1D5D4 + (c - 'A'));
            else if (c >= 'a' && c <= 'z') out.appendCodePoint(0x1D5EE + (c - 'a'));
            else if (c >= '0' && c <= '9') out.appendCodePoint(0x1D7EC + (c - '0'));
            else out.append(c);
        }
        return out.toString();
    }

    private static boolean searching(Minecraft minecraft) {
        net.minecraft.client.gui.screens.Screen screen = minecraft.gui == null ? null : minecraft.gui.screen();
        return screen instanceof FindMatchScreen find && find.searching();
    }

    private static JsonObject button(String label, String url) {
        JsonObject button = new JsonObject();
        button.addProperty("label", label);
        button.addProperty("url", url);
        return button;
    }

    private static String cut(String text) {
        String value = text.length() < 2 ? text + " " : text;
        return value.length() > 128 ? value.substring(0, 127) : value;
    }

    private static void loop() {
        DiscordIpc ipc = null;
        boolean ready = false;
        long lastTry = 0L;
        long lastSend = 0L;
        String sent = null;
        boolean cleared = true;

        while (true) {
            try {
                Thread.sleep(1_000L);
                long now = System.currentTimeMillis();
                String activity = enabled ? wanted : null;

                if (ipc == null) {
                    if (activity == null || now - lastTry < RETRY_MS) continue;
                    lastTry = now;
                    ipc = DiscordIpc.open();
                    ready = false;
                    sent = null;
                    ipc.write(DiscordIpc.HANDSHAKE, "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}");
                    continue;
                }

                for (DiscordIpc.Frame frame : ipc.poll()) {
                    if (frame.op() == DiscordIpc.PING) ipc.write(DiscordIpc.PONG, frame.json());
                    else if (frame.op() == DiscordIpc.CLOSE) throw new java.io.IOException("Discord closed the connection");
                    else if (frame.op() == DiscordIpc.FRAME && isReady(frame.json())) ready = true;
                    else if (frame.op() == DiscordIpc.FRAME && !loggedRefusal && frame.json().contains("\"ERROR\"")) {
                        loggedRefusal = true;
                        VivetSpeedrun.LOGGER.info("Discord rejected the status: {}", frame.json());
                    }
                }
                if (!ready) {
                    if (now - lastTry > 10_000L) throw new java.io.IOException("no response from Discord");
                    continue;
                }

                if (activity == null) {
                    if (!cleared) {
                        ipc.write(DiscordIpc.FRAME, command(null));
                        cleared = true;
                        sent = null;
                    }

                    if (!enabled) {
                        ipc.close();
                        ipc = null;
                    }
                    continue;
                }
                if (activity.equals(sent) || now - lastSend < SEND_GAP_MS) continue;
                ipc.write(DiscordIpc.FRAME, command(activity));
                sent = activity;
                cleared = false;
                lastSend = now;
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (ipc != null) ipc.close();
                ipc = null;
                ready = false;
                sent = null;
                cleared = true;
            }
        }
    }

    private static boolean isReady(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() && parsed.getAsJsonObject().has("evt")
                    && "READY".equals(parsed.getAsJsonObject().get("evt").getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String command(String activity) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (activity != null) args.add("activity", JsonParser.parseString(activity));
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        return command.toString();
    }
}
