package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.config.ConfigManager;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class Reports {
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Pattern MATCH_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private static final int NOTE_MAX = 200;

    private Reports() {
    }

    public static void report(String matchId, int reason, String note, Consumer<String> onDone) {
        if (matchId == null || !MATCH_ID.matcher(matchId).matches()) {
            onDone.accept("vivet.report.failed");
            return;
        }

        String token = MojangAuth.token();
        if (token == null || token.isBlank()) {
            onDone.accept("vivet.report.failed");
            return;
        }

        send("/report", GSON.toJson(new Report(token, matchId, reason, trimmed(note))), onDone);
    }

    public static void reportPlayer(String uuid, int reason, String note, Consumer<String> onDone) {
        String who = uuid == null ? "" : uuid.trim().toLowerCase();
        if (!who.matches("[0-9a-f]{32}")) {
            onDone.accept("vivet.report.failed");
            return;
        }

        String token = MojangAuth.token();
        if (token == null || token.isBlank()) {
            onDone.accept("vivet.report.failed");
            return;
        }

        send("/report/player", GSON.toJson(new PlayerReport(token, who, reason, trimmed(note))),
                onDone);
    }

    private static String trimmed(String note) {
        String trimmed = note == null ? "" : note.strip();
        return trimmed.length() > NOTE_MAX ? trimmed.substring(0, NOTE_MAX) : trimmed;
    }

    private static void send(String path, String body, Consumer<String> onDone) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(MojangAuth.httpBase(ConfigManager.get().relayUrl) + path))
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        CompletableFuture<HttpResponse<String>> sent =
                MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString());

        sent.whenComplete((response, error) -> {
            if (error == null && response != null && response.statusCode() == 200) {
                onDone.accept("");
                return;
            }

            boolean blocked = error == null && response != null && response.statusCode() == 403;
            onDone.accept(blocked ? blockedText(response.body()) : "vivet.report.failed");
        });
    }

    private static String blockedText(String body) {
        try {
            Refusal refusal = GSON.fromJson(body, Refusal.class);
            if (refusal != null && refusal.text != null && !refusal.text.isBlank()) return refusal.text;
        } catch (RuntimeException ignored) {
        }
        return "vivet.report.blocked";
    }

    private static final class Refusal {
        String text;
    }

    private record Report(String token, String matchId, int reason, String note) {
    }

    private record PlayerReport(String token, String uuid, int reason, String note) {
    }
}
