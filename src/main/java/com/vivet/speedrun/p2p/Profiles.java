package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Profiles {
    private static final Gson GSON = new Gson();

    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(15);

    private static final long TTL_MS = 300_000L;

    private static final long RETRY_MS = 30_000L;

    private Profiles() {
    }

    private record Held(MojangAuth.Profile profile, long at) {
    }

    private static final Map<String, Held> CARDS = new ConcurrentHashMap<>();
    private static final Set<String> FETCHING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();

    private static final Set<String> REFUSED = ConcurrentHashMap.newKeySet();

    private static final int MAX_REMEMBERED = 64;

    private static volatile long stamp;

    public static MojangAuth.Profile of(String uuid) {
        String id = normalise(uuid);
        if (id == null) return null;

        MojangAuth.Profile mine = mine();
        if (mine != null && id.equals(normalise(mine.uuid))) return mine;

        Held held = CARDS.get(id);
        if (held == null || System.currentTimeMillis() - held.at() > TTL_MS) fetch(id);

        return held == null ? null : held.profile();
    }

    public static boolean refused(String uuid) {
        String id = normalise(uuid);
        return id != null && REFUSED.contains(id);
    }

    public static void again(String uuid) {
        String id = normalise(uuid);
        if (id == null) return;

        Held held = CARDS.get(id);
        if (held != null) CARDS.put(id, new Held(held.profile(), 0L));

        RETRY_AFTER.remove(id);
        fetch(id);
    }

    private static void fetch(String uuid) {
        Long wait = RETRY_AFTER.get(uuid);
        if (wait != null && System.currentTimeMillis() < wait) return;
        if (!FETCHING.add(uuid)) return;

        String token = MojangAuth.token();
        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/profile?uuid=" + uuid
                + (token == null ? "" : "&t=" + java.net.URLEncoder.encode(
                        token, java.nio.charset.StandardCharsets.UTF_8));

        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, MojangAuth.Profile.class))
                .whenComplete((answer, error) -> {
                    if (error == null && answer != null && answer.uuid != null) {
                        if (CARDS.size() >= MAX_REMEMBERED) CARDS.clear();
                        CARDS.put(uuid, new Held(answer, System.currentTimeMillis()));
                        REFUSED.remove(uuid);
                        RankedApi.noteProfile(answer);
                        stamp = System.currentTimeMillis();
                    } else if (error == null && answer != null && answer.error != null) {
                        CARDS.remove(uuid);
                        REFUSED.add(uuid);
                        RETRY_AFTER.put(uuid, System.currentTimeMillis() + RETRY_MS);
                        stamp = System.currentTimeMillis();
                    } else {
                        RETRY_AFTER.put(uuid, System.currentTimeMillis() + RETRY_MS);
                        VivetSpeedrun.LOGGER.debug("Could not load profile: {}",
                                error == null ? "empty response" : error.toString());
                    }
                    FETCHING.remove(uuid);
                });
    }

    private static final AtomicBoolean REFRESHING = new AtomicBoolean();
    private static volatile long mineAt;

    public static MojangAuth.Profile mine() {
        return MojangAuth.knownProfile();
    }

    public static void refreshMine() {
        if (MojangAuth.token() == null) return;
        if (System.currentTimeMillis() - mineAt < TTL_MS) return;
        if (!REFRESHING.compareAndSet(false, true)) return;

        RankedApi.loadProfile(
                profile -> {
                    mineAt = System.currentTimeMillis();
                    stamp = mineAt;
                    REFRESHING.set(false);
                },
                reason -> {
                    mineAt = System.currentTimeMillis();
                    REFRESHING.set(false);
                });
    }

    public static void save(Map<String, Object> patch) {
        String token = MojangAuth.token();
        if (token == null) return;

        Map<String, Object> body = new HashMap<>(patch);
        body.put("token", token);

        String url = MojangAuth.httpBase(ConfigManager.get().relayUrl) + "/profile/save";

        HttpRequest request = MojangAuth.request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        MojangAuth.http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((answer, error) -> {
                    if (error != null || answer == null) {
                        VivetSpeedrun.LOGGER.debug("Could not save a profile",
                                error);
                        return;
                    }

                    try {
                        MojangAuth.Profile fresh =
                                GSON.fromJson(answer, MojangAuth.Profile.class);

                        if (fresh != null && fresh.uuid != null) {
                            MojangAuth.updateProfile(fresh);
                            mineAt = System.currentTimeMillis();
                            stamp = mineAt;
                        }
                    } catch (Exception ignored) {
                    }
                });
    }

    public static int bioMax() {
        RankedApi.Config config = RankedApi.cachedConfig();
        return config == null || config.bioMax <= 0 ? 180 : config.bioMax;
    }

    public static MojangAuth.Shows showsOf(MojangAuth.Profile profile) {
        if (profile == null || profile.shows == null) return ALL;
        return profile.shows;
    }

    private static final MojangAuth.Shows ALL = new MojangAuth.Shows();

    public static String normalise(String uuid) {
        if (uuid == null) return null;

        String text = uuid.trim().toLowerCase(java.util.Locale.ROOT).replace("-", "");
        return text.matches("[0-9a-f]{32}") ? text : null;
    }
}
