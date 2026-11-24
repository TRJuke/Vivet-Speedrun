package com.vivet.speedrun.p2p;

import com.google.gson.Gson;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.replay.LoadedReplay;
import com.vivet.speedrun.replay.ReplayContainer;
import com.vivet.speedrun.replay.ReplayMatch;
import com.vivet.speedrun.replay.ReplayStore;
import com.vivet.speedrun.replay.ReplayTrace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class ReplayApi {
    private static final Gson GSON = new Gson();

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(40);

    private static final ExecutorService FETCHER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "vivet-replay-fetch");
        thread.setDaemon(true);
        return thread;
    });

    private ReplayApi() {
    }

    public static final class Answer {
        public Long now;
        public Boolean staff;
        public List<ReplayMatch> matches;
        public String error;
    }

    private static volatile List<ReplayMatch> mine;
    private static volatile boolean mineFailed;
    private static volatile List<ReplayMatch> everyone;
    private static volatile boolean staff;
    private static volatile long stamp;
    private static final AtomicBoolean FETCHING_MINE = new AtomicBoolean();
    private static final AtomicBoolean FETCHING_ALL = new AtomicBoolean();

    public static long stamp() {
        return stamp;
    }

    public static List<ReplayMatch> mine() {
        return mine;
    }

    public static boolean mineFailed() {
        return mineFailed;
    }

    public static List<ReplayMatch> everyone() {
        return everyone;
    }

    public static boolean isStaff() {
        return staff || Hub.isStaff();
    }

    public static void loadMine() {
        String token = MojangAuth.token();
        if (token == null) {
            mineFailed = true;
            stamp = System.currentTimeMillis();
            return;
        }
        if (!FETCHING_MINE.compareAndSet(false, true)) return;

        MojangAuth.get(base() + "/replays?t=" + encode(token))
                .thenApply(body -> GSON.fromJson(body, Answer.class))
                .whenComplete((answer, error) -> {
                    FETCHING_MINE.set(false);
                    if (error != null || answer == null || answer.error != null || answer.matches == null) {
                        VivetSpeedrun.LOGGER.info("Could not load replays: {}",
                                error != null ? error.toString()
                                        : answer == null ? "empty response" : answer.error);
                        mineFailed = true;
                        stamp = System.currentTimeMillis();
                        return;
                    }

                    List<ReplayMatch> usable = usable(answer.matches);
                    staff = Boolean.TRUE.equals(answer.staff);
                    mine = usable;
                    mineFailed = false;
                    stamp = System.currentTimeMillis();

                    Set<String> listed = new HashSet<>();
                    for (ReplayMatch match : usable) {
                        for (ReplayMatch.Pov pov : match.povs) listed.add(pov.file);
                    }
                    ReplayStore.keepOnly(listed);
                });
    }

    private static volatile String[] wantedEveryone;

    public static void loadEveryone(String query, boolean reported) {
        String token = MojangAuth.token();
        if (token == null) return;
        if (!FETCHING_ALL.compareAndSet(false, true)) {
            wantedEveryone = new String[] { query == null ? "" : query, reported ? "1" : "" };
            return;
        }
        wantedEveryone = null;

        String url = base() + "/replays/all?t=" + encode(token)
                + (query == null || query.isBlank() ? "" : "&q=" + encode(query.trim()))
                + (reported ? "&reported=1" : "");
        MojangAuth.get(url)
                .thenApply(body -> GSON.fromJson(body, Answer.class))
                .whenComplete((answer, error) -> {
                    FETCHING_ALL.set(false);
                    if (error != null || answer == null || answer.error != null || answer.matches == null) {
                        everyone = List.of();
                    } else {
                        everyone = usable(answer.matches);
                    }
                    stamp = System.currentTimeMillis();

                    String[] next = wantedEveryone;
                    if (next != null) loadEveryone(next[0], !next[1].isEmpty());
                });
    }

    public static final class MatchAnswer {
        public Boolean staff;
        public ReplayMatch match;
        public String error;
    }

    public static void loadMatch(String matchId, Consumer<ReplayMatch> onReady, Consumer<String> onFailed) {
        String token = MojangAuth.token();
        if (token == null || matchId == null || matchId.isBlank()) {
            onFailed.accept("vivet.replays.unavailable");
            return;
        }
        MojangAuth.get(base() + "/replays/match?t=" + encode(token) + "&match=" + encode(matchId))
                .thenApply(body -> GSON.fromJson(body, MatchAnswer.class))
                .whenComplete((answer, error) -> {
                    if (error != null || answer == null || answer.error != null || answer.match == null) {
                        VivetSpeedrun.LOGGER.info("Could not load the replay of {}: {}", matchId,
                                error != null ? error.toString()
                                        : answer == null ? "empty response" : answer.error);
                        onFailed.accept("vivet.replays.unavailable");
                        return;
                    }
                    List<ReplayMatch> usable = usable(List.of(answer.match));
                    if (usable.isEmpty()) {
                        onFailed.accept("vivet.replays.unavailable");
                        return;
                    }
                    ReplayMatch match = usable.get(0);
                    MojangAuth.Identity me = MojangAuth.identity();
                    boolean played = false;
                    for (ReplayMatch.Player player : match.players) {
                        if (me != null && me.uuid != null && me.uuid.equals(player.uuid)) played = true;
                    }
                    match.outsider = !played && !Boolean.TRUE.equals(answer.staff);
                    onReady.accept(match);
                });
    }

    public static void forgetEveryone() {
        everyone = null;
        stamp = System.currentTimeMillis();
    }

    public static void open(ReplayMatch match, Consumer<LoadedReplay> onReady, Consumer<String> onFailed) {
        if (match == null || !match.isUsable()) {
            onFailed.accept("vivet.replays.unavailable");
            return;
        }
        FETCHER.execute(() -> {
            List<LoadedReplay.View> views = new ArrayList<>();
            for (ReplayMatch.Pov pov : match.povs) {
                byte[] front = ReplayStore.cachedTrace(pov.file);
                if (front == null) {
                    front = downloadFront(pov);
                    if (front != null) ReplayStore.saveTrace(pov.file, front);
                }
                if (front == null) continue;
                Front read = readFront(front, pov.bytes);
                ReplayTrace trace = read.trace() == null ? null
                        : ReplayTrace.decodeBytes(read.trace(), ReplayTrace.MAX_DECOMPRESSED_REMOTE);
                if (trace != null && match.outsider) trace = withoutTypedChat(trace);
                if (trace != null && !trace.isEmpty()) views.add(new LoadedReplay.View(pov, trace, read.world(), read.protocol()));
            }
            if (views.isEmpty()) {
                onFailed.accept("vivet.replays.unavailable");
                return;
            }
            onReady.accept(new LoadedReplay(match, views));
        });
    }

    private record Front(byte[] trace, boolean world, int protocol) {
    }

    private static Front readFront(byte[] front, long fileLength) {
        if (!ReplayContainer.isContainer(front)) return new Front(front, false, 0);

        ReplayContainer.Header header = ReplayContainer.readHeader(front, Math.max(0, fileLength));
        if (header == null) return new Front(null, false, 0);
        ReplayContainer.Section trace = header.find(ReplayContainer.SECTION_TRACE);
        if (trace == null || trace.end() > front.length) return new Front(null, false, 0);

        int protocol = 0;
        ReplayContainer.Section meta = header.find(ReplayContainer.SECTION_META);
        if (meta != null && meta.end() <= front.length) {
            try {
                ReplayContainer.Meta read = ReplayContainer.decodeMeta(
                        Arrays.copyOfRange(front, (int) meta.offset(), (int) meta.end()));
                if (read != null) protocol = read.protocol();
            } catch (RuntimeException unreadable) {
            }
        }
        return new Front(Arrays.copyOfRange(front, (int) trace.offset(), (int) trace.end()), header.hasWorld(),
                protocol);
    }

    private static byte[] downloadFront(ReplayMatch.Pov pov) {
        String url = checkedUrl(pov);
        if (url == null) return null;

        byte[] head = fetch(url, 0, ReplayContainer.HEAD_BYTES - 1, ReplayContainer.HEAD_BYTES);
        if (head == null) return null;
        if (!ReplayContainer.isContainer(head)) {
            if (head.length < ReplayContainer.HEAD_BYTES || pov.bytes <= head.length) return head;
            return fetch(url, 0, -1, ReplayTrace.MAX_FILE_BYTES);
        }

        ReplayContainer.Header header = ReplayContainer.readHeader(head, Math.max(0, pov.bytes));
        ReplayContainer.Section trace = header == null ? null : header.find(ReplayContainer.SECTION_TRACE);
        if (trace == null || trace.end() > ReplayTrace.MAX_FILE_BYTES + (long) header.headerLength()) return null;

        long end = trace.end();
        ReplayContainer.Section meta = header.find(ReplayContainer.SECTION_META);
        if (meta != null && meta.offset() == trace.end() && meta.length() < 64 * 1024) end = meta.end();

        if (end <= head.length) return Arrays.copyOf(head, (int) end);

        byte[] rest = fetch(url, head.length, end - 1, (int) (end - head.length));
        if (rest == null || rest.length < end - head.length) return null;
        byte[] whole = Arrays.copyOf(head, (int) end);
        System.arraycopy(rest, 0, whole, head.length, whole.length - head.length);
        return whole;
    }

    private static String checkedUrl(ReplayMatch.Pov pov) {
        String url = pov.url == null ? "" : pov.url;
        if (!url.startsWith("https://") || !url.endsWith("/r/" + pov.file)) {
            VivetSpeedrun.LOGGER.warn("Rejected a bad replay URL");
            return null;
        }
        return url;
    }

    private static byte[] fetch(String url, long first, long last, int max) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET();
            if (last >= 0) builder.header("Range", "bytes=" + first + "-" + last);
            HttpResponse<InputStream> response = MojangAuth.http()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status != 200 && status != 206) {
                    VivetSpeedrun.LOGGER.info("Replay download returned {}", status);
                    return null;
                }

                if (status == 200 && first > 0) body.skipNBytes(first);

                ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 256 * 1024));
                byte[] chunk = new byte[64 * 1024];
                int read;
                while (out.size() < max && (read = body.read(chunk, 0, Math.min(chunk.length, max - out.size()))) != -1) {
                    out.write(chunk, 0, read);
                }
                return out.toByteArray();
            }
        } catch (IOException | IllegalArgumentException e) {
            VivetSpeedrun.LOGGER.info("Replay download failed: {}", e.toString());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public static void fetchWorlds(LoadedReplay replay, BiConsumer<Long, Long> progress,
                                   Consumer<List<Path>> onDone) {
        FETCHER.execute(() -> {
            long total = 0;
            for (LoadedReplay.View view : replay.views()) {
                if (view.world() && ReplayStore.cachedWhole(view.pov().file, view.pov().bytes) == null) {
                    total += Math.max(0, view.pov().bytes);
                }
            }
            long all = Math.max(1, total);
            long[] done = { 0 };

            List<Path> paths = new ArrayList<>();
            for (LoadedReplay.View view : replay.views()) {
                if (!view.world()) {
                    paths.add(null);
                    continue;
                }
                Path cached = ReplayStore.cachedWhole(view.pov().file, view.pov().bytes);
                if (cached == null) {
                    long before = done[0];
                    cached = downloadWhole(view.pov(), bytes -> progress.accept(before + bytes, all));
                    done[0] += Math.max(0, view.pov().bytes);
                }
                paths.add(cached);
            }
            onDone.accept(paths);
        });
    }

    private static Path downloadWhole(ReplayMatch.Pov pov, LongConsumer progress) {
        String url = checkedUrl(pov);
        Path writing = ReplayStore.downloading(pov.file);
        if (url == null || writing == null || pov.bytes > ReplayContainer.MAX_FILE_BYTES) return null;
        try {
            ReplayStore.ensureFolder();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<InputStream> response = MojangAuth.http().send(request, HttpResponse.BodyHandlers.ofInputStream());

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            long got = 0;
            try (InputStream body = response.body(); OutputStream out = Files.newOutputStream(writing)) {
                if (response.statusCode() != 200) {
                    VivetSpeedrun.LOGGER.info("Replay download returned {}", response.statusCode());
                    return null;
                }
                byte[] chunk = new byte[256 * 1024];
                int read;
                while ((read = body.read(chunk)) != -1) {
                    got += read;
                    if (got > ReplayContainer.MAX_FILE_BYTES) return null;
                    digest.update(chunk, 0, read);
                    out.write(chunk, 0, read);
                    progress.accept(got);
                }
            }
            if (pov.bytes > 0 && got != pov.bytes) {
                VivetSpeedrun.LOGGER.warn("Replay download was the wrong size");
                return null;
            }

            if (pov.sha256.isEmpty()) {
                VivetSpeedrun.LOGGER.info("Replay has no hash, kept on size");
            } else if (!java.util.HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(pov.sha256)) {
                VivetSpeedrun.LOGGER.warn("Replay failed its hash check, deleted");
                return null;
            }
            return ReplayStore.keepWhole(pov.file, writing);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return null;
        } catch (IOException | IllegalArgumentException e) {
            VivetSpeedrun.LOGGER.info("Replay download failed: {}", e.toString());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            try {
                Files.deleteIfExists(writing);
            } catch (IOException ignored) {
            }
        }
    }

    private static ReplayTrace withoutTypedChat(ReplayTrace trace) {
        List<ReplayTrace.Moment> kept = new ArrayList<>(trace.moments().size());
        for (ReplayTrace.Moment moment : trace.moments()) {
            if (moment.type() != com.vivet.speedrun.replay.ReplayMoment.CHAT) kept.add(moment);
        }
        return kept.size() == trace.moments().size() ? trace : ReplayTrace.of(trace.samples(), kept, trace.seed());
    }

    private static List<ReplayMatch> usable(List<ReplayMatch> matches) {
        List<ReplayMatch> out = new ArrayList<>();
        for (ReplayMatch match : matches) {
            if (match == null) continue;
            match.sanitize();
            if (match.isUsable()) out.add(match);
        }
        return List.copyOf(out);
    }

    private static String base() {
        return MojangAuth.httpBase(ConfigManager.get().relayUrl);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
