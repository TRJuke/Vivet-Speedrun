package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MojangAuth {
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    public static final class Identity {
        public String token;
        public String uuid;
        public String name;
        public Profile profile;
    }

    public static final class Profile {
        public String uuid;
        public String name;
        public Integer rating;
        public Integer peak;
        public String rank;
        public String rankName;

        public Integer position;

        public Integer[] ladder;
        public boolean placed;
        public int placementGames;
        public int placementMatches;
        public int wins;
        public int losses;
        public int draws;
        public int streak;
        public int bestStreak;

        public Long joined;
        public long bannedUntil;
        public boolean banned;
        public int abandonStreak;
        public int banLevel;
        public Badge[] badges;

        public String label;

        public boolean speedKing;

        public String speedTitle;

        public String bio;

        public String background;

        public String emblem;
        public String emblemMode;

        public int followers;

        public int level;

        public int xp;

        public Integer coins;

        public Boolean discordLinked;

        public int xpInto;

        public int xpNext;

        public Integer hours;

        public Integer matches;

        public Shows shows;

        public boolean allowsRequests = true;

        public boolean allowsWatching = true;

        public boolean watchable;

        public Relation relation;

        public String error;
    }

    public static final class Relation {
        public boolean friend;

        public boolean incoming;

        public boolean outgoing;

        public boolean following;
        public boolean followsYou;

        public boolean blocked;

        public boolean blockedBy;
    }

    public static final class Shows {
        public boolean hours = true;
        public boolean matches = true;
        public boolean record = true;
        public boolean streak = true;

        public boolean bestStreak = true;
        public boolean locker = true;
        public boolean collection = true;
    }

    public static final class Badge {
        public String month;
        public String rank;
        public int points;

        public int place;

        public String source;

        public Long awardedAt;
    }

    private static final class Challenge {
        String nonce;
        String error;
    }

    private static final class VerifyRequest {
        final String nonce;
        final String name;

        final String hwid;

        final Integrity.Report integrity;

        final String mc;

        VerifyRequest(String nonce, String name) {
            this.nonce = nonce;
            this.name = name;
            this.hwid = MachineId.get();
            this.integrity = Integrity.get();
            this.mc = minecraftVersion();
        }

        static String minecraftVersion() {
            return net.minecraft.SharedConstants.getCurrentVersion().name();
        }
    }

    private static final class VerifyResponse {
        String token;
        String uuid;
        String name;
        Profile profile;
        String error;
    }

    private static volatile HttpClient client;

    private static volatile Identity identity;

    private static volatile long expiresAt;

    private static volatile CompletableFuture<Identity> inFlight;

    private static volatile String lastFailure;
    private static volatile long retryNotBefore;
    private static volatile long cooldownMs;

    private static final long FIRST_COOLDOWN_MS = 2_000L;
    private static final long MAX_COOLDOWN_MS = 2L * 60L * 1000L;

    private static final long SLOW_DOWN_MS = 5_000L;

    private MojangAuth() {
    }

    public static Identity identity() {
        return System.currentTimeMillis() < expiresAt ? identity : null;
    }

    public static boolean isSignedIn() {
        return identity() != null;
    }

    public static String token() {
        Identity current = identity();
        return current == null ? null : current.token;
    }

    public static Profile knownProfile() {
        Identity current = identity();
        return current == null ? null : current.profile;
    }

    public static void updateProfile(Profile profile) {
        Identity current = identity;
        if (current == null || profile == null) return;

        if (profile.coins == null && current.profile != null) profile.coins = current.profile.coins;
        if (profile.discordLinked == null && current.profile != null) {
            profile.discordLinked = current.profile.discordLinked;
        }
        current.profile = profile;
    }

    public static Integer coins() {
        Profile profile = knownProfile();
        return profile == null ? null : profile.coins;
    }

    public static void noteCoins(Integer balance) {
        Profile profile = knownProfile();
        if (profile != null && balance != null) profile.coins = balance;
    }

    public static void forget() {
        identity = null;
        expiresAt = 0L;

        refused = true;
    }

    public static void retryNow() {
        retryNotBefore = 0L;
        cooldownMs = 0L;
    }

    public static void signIn(Consumer<Identity> onSuccess, Consumer<String> onFailed) {
        Identity current = identity();
        if (current != null) {
            onSuccess.accept(current);
            return;
        }

        long now = System.currentTimeMillis();
        if (now < retryNotBefore) {
            String remembered = lastFailure;
            onFailed.accept(remembered == null ? "Couldn't sign in." : remembered);
            return;
        }

        attempt(onSuccess, onFailed);
    }

    private static final long RENEW_BEFORE_MS = 30L * 60L * 1000L;

    private static volatile boolean refused;

    public static void keepAlive() {
        if (refused || inFlight != null) return;

        long now = System.currentTimeMillis();
        if (now < retryNotBefore) return;
        if (identity != null && now < expiresAt - RENEW_BEFORE_MS) return;

        attempt(result -> { }, reason -> { });
    }

    private static void attempt(Consumer<Identity> onSuccess, Consumer<String> onFailed) {
        CompletableFuture<Identity> pending = inFlight;
        if (pending == null) {
            pending = handshake();
            inFlight = pending;
        }

        pending.whenComplete((result, error) -> {
            inFlight = null;
            if (error != null || result == null) {
                refused = refusedByRelay(error);
                String reason = error == null ? "Couldn't sign in." : describe(error);

                cooldownMs = cooldownMs == 0L
                        ? FIRST_COOLDOWN_MS
                        : Math.min(MAX_COOLDOWN_MS, cooldownMs * 2L);

                if (slowDown(error)) cooldownMs = Math.max(cooldownMs, SLOW_DOWN_MS);
                retryNotBefore = System.currentTimeMillis() + cooldownMs;
                lastFailure = reason;

                VivetSpeedrun.LOGGER.warn("Sign in failed ({}), retrying in {}s",
                        reason, cooldownMs / 1000L, error);
                onFailed.accept(reason);
                return;
            }

            lastFailure = null;
            retryNotBefore = 0L;
            cooldownMs = 0L;
            refused = false;

            Presence.hello();
            Hub.open();

            Wardrobe.refresh();

            onSuccess.accept(result);
        });
    }

    private static CompletableFuture<Identity> handshake() {
        User user = Minecraft.getInstance().getUser();
        if (user == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("You're not signed in to Minecraft."));
        }

        String base = httpBase(ConfigManager.get().relayUrl);
        String playerName = user.getName();

        VivetSpeedrun.LOGGER.info("Signing in as {} ({})",
                playerName, base);

        return get(base + "/auth/challenge")
                .thenCompose(body -> {
                    Challenge challenge = GSON.fromJson(body, Challenge.class);
                    if (challenge == null || challenge.nonce == null) {
                        VivetSpeedrun.LOGGER.warn("Sign in failed, no challenge: {}", body);
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                challenge != null && challenge.error != null
                                        ? challenge.error
                                        : "Ranked is unavailable right now."));
                    }

                    VivetSpeedrun.LOGGER.info("Verifying with Mojang");

                    return CompletableFuture.supplyAsync(() -> {
                        joinWithRetry(user, challenge.nonce);
                        return challenge.nonce;
                    });
                })
                .thenCompose(nonce -> {
                    VivetSpeedrun.LOGGER.info("Mojang verified, confirming sign in");

                    VivetSpeedrun.LOGGER.info("Install info: {}",
                            Integrity.describe());

                    return post(base + "/auth/verify",
                            GSON.toJson(new VerifyRequest(nonce, playerName)));
                })
                .thenApply(answer -> {
                    String body = answer.body();
                    VerifyResponse response = GSON.fromJson(body, VerifyResponse.class);
                    if (response == null || response.token == null) {
                        VivetSpeedrun.LOGGER.warn("Sign in refused ({}): {}", answer.statusCode(), body);

                        String sentence = response != null && response.error != null
                                ? response.error
                                : "Couldn't verify your account.";

                        throw new java.util.concurrent.CompletionException(answer.statusCode() == 403
                                ? new Refused(sentence)
                                : new NotNow(sentence, answer.statusCode()));
                    }

                    Identity result = new Identity();
                    result.token = response.token;
                    result.uuid = response.uuid;
                    result.name = response.name;
                    result.profile = response.profile;

                    identity = result;

                    expiresAt = System.currentTimeMillis() + 12L * 60L * 60L * 1000L;

                    com.vivet.speedrun.update.Channel.signedIn(body);

                    VivetSpeedrun.LOGGER.info("Signed in as {}",
                            result.name);
                    return result;
                });
    }

    private static void joinWithRetry(User user, String nonce) {
        RuntimeException last = null;

        for (int attempt = 1; attempt <= JOIN_ATTEMPTS; attempt++) {
            try {
                Minecraft.getInstance().services().sessionService()
                        .joinServer(user.getProfileId(), user.getAccessToken(), nonce);
                return;
            } catch (com.mojang.authlib.exceptions.AuthenticationUnavailableException e) {
                VivetSpeedrun.LOGGER.warn("Mojang's session servers are down (try {}/{})",
                        attempt, JOIN_ATTEMPTS);
                last = new java.util.concurrent.CompletionException(e);
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.warn("Mojang refused the sign in: {}: {}",
                        e.getClass().getSimpleName(), e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }

            try {
                Thread.sleep(JOIN_RETRY_MS * attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last;
    }

    private static final int JOIN_ATTEMPTS = 3;
    private static final long JOIN_RETRY_MS = 400L;

    public static HttpClient http() {
        HttpClient existing = client;
        if (existing != null) return existing;

        synchronized (MojangAuth.class) {
            if (client == null) {
                client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER).build();
            }
            return client;
        }
    }

    public static CompletableFuture<String> get(String url) {
        Lifted lifted = liftToken(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(lifted.uri())
                .timeout(TIMEOUT)
                .header("accept", "application/json")
                .GET();
        if (lifted.token() != null) builder.header("Authorization", "Bearer " + lifted.token());
        HttpRequest request = builder.build();

        return http().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public static HttpRequest.Builder request(String url) {
        Lifted lifted = liftToken(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(lifted.uri());
        if (lifted.token() != null) builder.header("Authorization", "Bearer " + lifted.token());
        return builder;
    }

    public record Lifted(URI uri, String token) {
    }

    public static Lifted liftToken(String url) {
        URI uri = URI.create(url);
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) return new Lifted(uri, null);

        String token = null;
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.startsWith("t=")) {
                token = java.net.URLDecoder.decode(part.substring(2), java.nio.charset.StandardCharsets.UTF_8);
                continue;
            }
            if (kept.length() > 0) kept.append('&');
            kept.append(part);
        }
        if (token == null || token.isBlank()) return new Lifted(uri, null);

        String base = url.substring(0, url.indexOf('?'));
        String fragment = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
        return new Lifted(URI.create(base + (kept.length() > 0 ? "?" + kept : "") + fragment), token);
    }

    private static CompletableFuture<HttpResponse<String>> post(String url, String body) {
        HttpRequest request = request(url)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return http().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static String httpBase(String relayUrl) {
        String trimmed = relayUrl == null ? "" : relayUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        if (trimmed.startsWith("wss://")) return "https://" + trimmed.substring(6);
        if (trimmed.startsWith("ws://")) return "http://" + trimmed.substring(5);
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;

        return "https://" + trimmed;
    }

    private static final class Refused extends IllegalStateException {
        Refused(String message) {
            super(message);
        }
    }

    private static final class NotNow extends IllegalStateException {
        final int status;

        NotNow(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    private static boolean slowDown(Throwable error) {
        for (Throwable at = error; at != null; at = at.getCause()) {
            if (at instanceof NotNow notNow && (notNow.status == 429 || notNow.status == 503)) return true;
            if (at instanceof com.mojang.authlib.exceptions.AuthenticationException
                    && String.valueOf(at.getMessage()).contains("429")) return true;
            if (at.getCause() == at) break;
        }
        return false;
    }

    private static boolean refusedByRelay(Throwable error) {
        for (Throwable at = error; at != null; at = at.getCause()) {
            if (at instanceof Refused) return true;
            if (at instanceof com.mojang.authlib.exceptions.InvalidCredentialsException
                    || at instanceof com.mojang.authlib.exceptions.InsufficientPrivilegesException
                    || at instanceof com.mojang.authlib.exceptions.UserBannedException
                    || at instanceof com.mojang.authlib.exceptions.ForcedUsernameChangeException) return true;
            if (at instanceof com.mojang.authlib.exceptions.AuthenticationException) return false;
            if (at.getCause() == at) break;
        }
        return false;
    }

    private static String describe(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();

        if (cause instanceof com.mojang.authlib.exceptions.AuthenticationUnavailableException) {
            return "Mojang's servers aren't responding. Try again in a minute.";
        }
        if (cause instanceof com.mojang.authlib.exceptions.InvalidCredentialsException) {
            return "Your Minecraft session has expired. Restart the launcher and sign in again.";
        }
        if (cause instanceof com.mojang.authlib.exceptions.InsufficientPrivilegesException) {
            return "This account can't play online. Check your Xbox multiplayer settings.";
        }
        if (cause instanceof com.mojang.authlib.exceptions.UserBannedException) {
            return "This account is banned from online play by Mojang.";
        }
        if (cause instanceof com.mojang.authlib.exceptions.AuthenticationException) {
            return "Couldn't verify your Minecraft account. Make sure you're signed in.";
        }
        return detail;
    }
}
