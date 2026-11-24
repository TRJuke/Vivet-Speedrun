package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class Presence {
    private static final Duration GOODBYE_TIMEOUT = Duration.ofMillis(1500);

    private static volatile boolean announced;

    private static final Gson GSON = new Gson();

    private static final class Counted {
        int online;

        String speedKing;

        java.util.Map<String, String> speedTitles;
        String error;
    }

    private Presence() {
    }

    public static void hello() {
        if (announced) return;

        announced = true;
        send("online", false);
    }

    public static void goodbye() {
        if (!announced) return;

        announced = false;
        send("offline", true);
    }

    private static void send(String state, boolean waitForIt) {
        String token = MojangAuth.token();
        if (token == null || token.isBlank()) return;

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/presence";

        String body = "{\"state\":\"" + state + "\",\"token\":\"" + token + "\"}";

        HttpRequest request = MojangAuth.request(url)
                .timeout(GOODBYE_TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            if (waitForIt) {
                MojangAuth.http().send(request, HttpResponse.BodyHandlers.discarding());
                return;
            }

            MojangAuth.http()
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        Counted counted = GSON.fromJson(response.body(), Counted.class);
                        if (counted != null && counted.error == null) {
                            Online.seed(counted.online);

                            if (counted.speedTitles != null) {
                                RankedApi.noteSpeedTitles(counted.speedTitles);
                            } else {
                                RankedApi.noteSpeedKing(counted.speedKing);
                            }
                        }
                    })
                    .exceptionally(error -> null);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Presence update {} failed", state, e);
        }
    }
}
