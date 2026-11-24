package com.vivet.speedrun.replay;

import com.vivet.speedrun.VivetSpeedrun;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ReplayTrace {
    private static final byte[] MAGIC = { 'V', 'R', 'P' };

    public static final byte VERSION = 6;

    private static final byte MIN_READABLE_VERSION = 3;

    private static final int MAX_SAMPLES = 500_000;
    private static final int MAX_MOMENTS = 300_000;
    private static final int MAX_FRAMES = 200_000;

    private static final int MAX_CENTIS = 8_640_000;

    private static final int MAX_PALETTE = 16_384;
    private static final int MAX_ID_BYTES = 160;
    private static final int MAX_SLOTS = 64;
    private static final int MAX_EFFECTS = 32;
    private static final int MAX_ENTITIES_PER_FRAME = 96;

    private static final int MAX_CHAT_BYTES = 256;

    private static final int MAX_NAME_BYTES = 32;

    private static final int MAX_TOTAL_SLOTS = 4_000_000;
    private static final int MAX_TOTAL_EFFECTS = 400_000;
    private static final int MAX_TOTAL_ENTITY_SPOTS = 6_000_000;

    private static final int MIN_SAMPLE_BYTES = 15;

    public static final int MAX_DECOMPRESSED_REMOTE = 64 * 1024 * 1024;

    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    private static final java.util.regex.Pattern ID_PATTERN =
            java.util.regex.Pattern.compile(
                    "^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,64}"
                            + "(\\[[a-z0-9_]{1,32}=[a-z0-9_-]{1,32}"
                            + "(,[a-z0-9_]{1,32}=[a-z0-9_-]{1,32}){0,15}\\])?$");

    private static final java.util.regex.Pattern NAME_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_.-]{1,32}$");

    public static final int FLAG_SPRINTING = 1;
    public static final int FLAG_FLYING = 2;
    public static final int FLAG_RIDING = 4;
    public static final int FLAG_BURNING = 8;
    public static final int FLAG_SNEAKING = 16;

    public static final int FLAG_INVENTORY = 32;

    public static final int FLAG_EFFECTS = 64;

    public static final int FLAG_SWIMMING = 128;

    public static final int FLAG_USING = 256;

    public static final int FLAG_IN_WATER = 512;

    public static final int FLAG_ON_GROUND = 1024;

    private static final int ENCODING_FLAGS = FLAG_INVENTORY | FLAG_EFFECTS;

    public static final int SLOT_HOTBAR = 0;
    public static final int SLOT_MAIN = 9;
    public static final int SLOT_FEET = 36;
    public static final int SLOT_LEGS = 37;
    public static final int SLOT_CHEST = 38;
    public static final int SLOT_HEAD = 39;
    public static final int SLOT_OFFHAND = 40;
    public static final int SLOT_COUNT = 41;

    public record Item(String id, int count) {
    }

    public record Effect(String id, int amplifier, int seconds) {
    }

    public record Sample(int centis, long x, long y, long z,
                         int yaw, int pitch, int dimension,
                         int health, int food, int air, int xpLevel, int selectedSlot,
                         int flags, ReplayEvent event,
                         Map<Integer, Item> inventory, List<Effect> effects) {
        public double blockX() {
            return this.x / 100.0;
        }

        public double blockZ() {
            return this.z / 100.0;
        }

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public Item slot(int slot) {
            return this.inventory.get(slot);
        }
    }

    public record Moment(int centis, ReplayMoment type,
                         int x, int y, int z,
                         String id, int amount, int entityId,
                         String text, String who) {
        public static Moment at(int centis, ReplayMoment type) {
            return new Moment(centis, type, 0, 0, 0, "", 0, 0, "", "");
        }

        public static Moment block(int centis, int x, int y, int z, String state) {
            return new Moment(centis, ReplayMoment.BLOCK_SET, x, y, z, state, 0, 0, "", "");
        }

        public static Moment item(int centis, ReplayMoment type, String id, int amount) {
            return new Moment(centis, type, 0, 0, 0, id, amount, 0, "", "");
        }

        public static Moment entity(int centis, ReplayMoment type, int entityId,
                                    String entityType, int amount) {
            return new Moment(centis, type, 0, 0, 0, entityType, amount, entityId, "", "");
        }

        public static Moment mining(int centis, int x, int y, int z, int stage) {
            return new Moment(centis, ReplayMoment.MINE_PROGRESS, x, y, z, "", stage, 0, "", "");
        }

        public static Moment number(int centis, ReplayMoment type, int amount) {
            return new Moment(centis, type, 0, 0, 0, "", amount, 0, "", "");
        }

        public static Moment chat(int centis, String who, String text) {
            return new Moment(centis, ReplayMoment.CHAT, 0, 0, 0, "", 0, 0, text, who);
        }
    }

    private final List<Sample> samples;
    private final List<Moment> moments;

    private final Long seed;

    private ReplayTrace(List<Sample> samples, List<Moment> moments, Long seed) {
        this.samples = samples;
        this.moments = moments;
        this.seed = seed;
    }

    public static ReplayTrace of(List<Sample> samples, List<Moment> moments, Long seed) {
        return new ReplayTrace(
                List.copyOf(samples),
                moments == null ? List.of() : List.copyOf(moments),
                seed);
    }

    public List<Sample> samples() {
        return this.samples;
    }

    public List<Moment> moments() {
        return this.moments;
    }

    public Long seed() {
        return this.seed;
    }

    public boolean isEmpty() {
        return this.samples.isEmpty();
    }

    public int endCentis() {
        return this.samples.isEmpty() ? 0 : this.samples.get(this.samples.size() - 1).centis();
    }

    public Sample at(int centis) {
        int index = indexAt(centis);
        return index < 0 ? null : this.samples.get(index);
    }

    public int indexAt(int centis) {
        int low = 0;
        int high = this.samples.size() - 1;
        int found = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (this.samples.get(mid).centis() <= centis) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    public byte[] encodeBytes() {
        byte[] deflated = deflate(pack());
        return deflated.length > MAX_FILE_BYTES ? null : deflated;
    }

    private static byte[] deflate(byte[] packed) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(packed);
            deflater.finish();

            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, packed.length / 8));
            byte[] chunk = new byte[64 * 1024];
            while (!deflater.finished()) {
                int wrote = deflater.deflate(chunk);
                if (wrote <= 0) break;
                out.write(chunk, 0, wrote);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private byte[] pack() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                1024 + this.samples.size() * 18 + this.moments.size() * 8);

        out.write(MAGIC[0]);
        out.write(MAGIC[1]);
        out.write(MAGIC[2]);
        out.write(VERSION);

        if (this.seed == null) {
            out.write(0);
        } else {
            out.write(1);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) ((this.seed >> shift) & 0xFF));
            }
        }

        Map<String, Integer> palette = new LinkedHashMap<>();
        for (Sample sample : this.samples) {
            for (Item item : sample.inventory().values()) intern(palette, item.id());
            for (Effect effect : sample.effects()) intern(palette, effect.id());
        }
        for (Moment moment : this.moments) intern(palette, moment.id());

        writeVarint(out, palette.size());
        for (String id : palette.keySet()) {
            byte[] bytes = id.getBytes(StandardCharsets.US_ASCII);
            writeVarint(out, bytes.length);
            out.write(bytes, 0, bytes.length);
        }

        writeSamples(out, palette);
        writeMoments(out, palette);
        writeVarint(out, 0);

        return out.toByteArray();
    }

    private static void intern(Map<String, Integer> palette, String id) {
        if (id == null || id.isEmpty()) return;
        palette.putIfAbsent(id, palette.size());
    }

    private static int index(Map<String, Integer> palette, String id) {
        Integer at = id == null ? null : palette.get(id);
        return at == null ? 0 : at;
    }

    private void writeSamples(ByteArrayOutputStream out, Map<String, Integer> palette) {
        writeVarint(out, this.samples.size());

        int centis = 0;
        long x = 0;
        long y = 0;
        long z = 0;
        Map<Integer, Item> lastInventory = null;
        List<Effect> lastEffects = null;

        for (Sample sample : this.samples) {
            boolean inventoryChanged = !sample.inventory().equals(lastInventory);
            boolean effectsChanged = !sample.effects().equals(lastEffects);

            int flags = sample.flags() & ~ENCODING_FLAGS;
            if (inventoryChanged) flags |= FLAG_INVENTORY;
            if (effectsChanged) flags |= FLAG_EFFECTS;

            writeVarint(out, Math.max(0, sample.centis() - centis));
            writeZigzag(out, sample.x() - x);
            writeZigzag(out, sample.y() - y);
            writeZigzag(out, sample.z() - z);

            writeShort(out, sample.yaw());
            writeShort(out, sample.pitch());
            out.write(sample.dimension() & 0xFF);
            out.write(sample.health() & 0xFF);
            out.write(sample.food() & 0xFF);
            out.write(sample.air() & 0xFF);
            writeVarint(out, Math.max(0, sample.xpLevel()));
            out.write(sample.selectedSlot() & 0xFF);
            writeVarint(out, flags);
            out.write(sample.event().index() & 0xFF);

            if (inventoryChanged) {
                writeVarint(out, sample.inventory().size());
                for (Map.Entry<Integer, Item> entry : sample.inventory().entrySet()) {
                    writeVarint(out, entry.getKey());
                    writeVarint(out, index(palette, entry.getValue().id()));
                    writeVarint(out, entry.getValue().count());
                }
            }

            if (effectsChanged) {
                writeVarint(out, sample.effects().size());
                for (Effect effect : sample.effects()) {
                    writeVarint(out, index(palette, effect.id()));
                    out.write(effect.amplifier() & 0xFF);
                    writeVarint(out, Math.max(0, effect.seconds()));
                }
            }

            centis = sample.centis();
            x = sample.x();
            y = sample.y();
            z = sample.z();
            lastInventory = sample.inventory();
            lastEffects = sample.effects();
        }
    }

    private void writeMoments(ByteArrayOutputStream out, Map<String, Integer> palette) {
        writeVarint(out, this.moments.size());

        int centis = 0;
        int bx = 0;
        int by = 0;
        int bz = 0;

        for (Moment moment : this.moments) {
            writeVarint(out, Math.max(0, moment.centis() - centis));
            out.write(moment.type().index() & 0xFF);

            switch (moment.type()) {
                case BLOCK_SET -> {
                    writeZigzag(out, (long) moment.x() - bx);
                    writeZigzag(out, (long) moment.y() - by);
                    writeZigzag(out, (long) moment.z() - bz);
                    writeVarint(out, index(palette, moment.id()));

                    bx = moment.x();
                    by = moment.y();
                    bz = moment.z();
                }
                case ENTITY_DIED, ATTACK -> {
                    writeVarint(out, Math.max(0, moment.entityId()));
                    writeVarint(out, index(palette, moment.id()));
                }
                case ENTITY_HURT -> {
                    writeVarint(out, Math.max(0, moment.entityId()));
                    writeVarint(out, index(palette, moment.id()));
                    writeVarint(out, Math.max(0, moment.amount()));
                }
                case MINE_PROGRESS -> {
                    writeZigzag(out, (long) moment.x() - bx);
                    writeZigzag(out, (long) moment.y() - by);
                    writeZigzag(out, (long) moment.z() - bz);
                    writeVarint(out, Math.max(0, moment.amount()));

                    bx = moment.x();
                    by = moment.y();
                    bz = moment.z();
                }
                case SWING, HURT, CONTAINER_OPEN, TAMPER, AWAY, PORTAL_LIT ->
                        writeVarint(out, Math.max(0, moment.amount()));
                case USE, EAT, THROW, ADVANCEMENT, ENCHANTED ->
                        writeVarint(out, index(palette, moment.id()));
                case CRAFT, PICKUP, DROP, SMELTED -> {
                    writeVarint(out, index(palette, moment.id()));
                    writeVarint(out, Math.max(0, moment.amount()));
                }
                case KILLED_BY -> {
                    String killer = moment.id();
                    writeVarint(out, killer == null || killer.isEmpty()
                            ? 0 : index(palette, killer) + 1);
                }
                case CHAT -> {
                    writeText(out, moment.who(), MAX_NAME_BYTES);
                    writeText(out, moment.text(), MAX_CHAT_BYTES);
                }
                case DEATH, CONTAINER_CLOSE, SLEEP -> {
                }
            }
            centis = moment.centis();
        }
    }

    private static void writeText(ByteArrayOutputStream out, String text, int limit) {
        String value = text == null ? "" : text;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        if (bytes.length > limit) {
            int end = limit;
            while (end > 0 && (bytes[end] & 0xC0) == 0x80) end--;

            byte[] cut = new byte[end];
            System.arraycopy(bytes, 0, cut, 0, end);
            bytes = cut;
        }

        writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long left = Math.max(0L, value);
        while (left >= 128L) {
            out.write((int) ((left & 0x7FL) | 0x80L));
            left >>>= 7;
        }
        out.write((int) left);
    }

    private static void writeZigzag(ByteArrayOutputStream out, long value) {
        writeVarint(out, (value << 1) ^ (value >> 63));
    }

    public static ReplayTrace decodeBytes(byte[] deflated, int maxDecompressed) {
        if (deflated == null || deflated.length == 0 || deflated.length > MAX_FILE_BYTES) {
            return null;
        }
        try {
            byte[] bytes = inflate(deflated, maxDecompressed);
            return bytes == null ? null : read(bytes);
        } catch (RuntimeException | OutOfMemoryError e) {
            VivetSpeedrun.LOGGER.debug("Could not decode replay file", e);
            return null;
        }
    }

    private static byte[] inflate(byte[] packed, int limit) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);

            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(1024 * 1024,
                    Math.max(1024, packed.length * 4)));
            byte[] chunk = new byte[64 * 1024];

            while (!inflater.finished()) {
                int read = inflater.inflate(chunk);
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return null;
                    continue;
                }

                if (out.size() + read > limit) return null;
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        } catch (DataFormatException | OutOfMemoryError e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int at;
        private boolean bad;

        Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        int left() {
            return this.bytes.length - this.at;
        }

        int read() {
            if (this.at >= this.bytes.length) {
                this.bad = true;
                return 0;
            }
            return this.bytes[this.at++] & 0xFF;
        }

        int readShort() {
            int high = read();
            int low = read();
            return ((high << 8) | low) & 0xFFFF;
        }

        long varint() {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                int byteValue = read();
                if (this.bad) return 0;

                value |= (long) (byteValue & 0x7F) << shift;
                if ((byteValue & 0x80) == 0) return value;
            }
            this.bad = true;
            return 0;
        }

        long zigzag() {
            long raw = varint();
            return (raw >>> 1) ^ -(raw & 1);
        }

        int count(int limit) {
            long value = varint();
            if (this.bad || value < 0 || value > limit) {
                this.bad = true;
                return 0;
            }
            return (int) value;
        }

        byte[] blob(int limit) {
            int length = count(limit);
            if (this.bad || length > left()) {
                this.bad = true;
                return new byte[0];
            }

            byte[] raw = new byte[length];
            System.arraycopy(this.bytes, this.at, raw, 0, length);
            this.at += length;
            return raw;
        }
    }

    private static ReplayTrace read(byte[] bytes) {
        if (bytes.length < MAGIC.length + 3) return null;
        if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != MAGIC[2]) return null;
        if (bytes[3] < MIN_READABLE_VERSION || bytes[3] > VERSION) return null;

        Cursor cursor = new Cursor(bytes);
        cursor.at = 4;

        Long seed = null;
        int hasSeed = cursor.read();
        if (hasSeed == 1) {
            long value = 0;
            for (int i = 0; i < 8; i++) value = (value << 8) | cursor.read();
            if (cursor.bad) return null;
            seed = value;
        } else if (hasSeed != 0) {
            return null;
        }

        String[] palette = readPalette(cursor);
        if (palette == null) return null;

        int[] budget = { MAX_TOTAL_SLOTS, MAX_TOTAL_EFFECTS, MAX_TOTAL_ENTITY_SPOTS };

        List<Sample> samples = readSamples(cursor, palette, budget);
        if (samples == null) return null;

        List<Moment> moments = readMoments(cursor, palette);
        if (moments == null) return null;

        if (!skipFrames(cursor, palette, budget, bytes[3])) return null;

        if (cursor.at != bytes.length) return null;

        return new ReplayTrace(List.copyOf(samples), List.copyOf(moments), seed);
    }

    private static List<Sample> readSamples(Cursor cursor, String[] palette, int[] budget) {
        int count = cursor.count(MAX_SAMPLES);
        if (cursor.bad) return null;

        if ((long) count * MIN_SAMPLE_BYTES > cursor.left()) return null;

        List<Sample> samples = new ArrayList<>(count);

        int centis = 0;
        long x = 0;
        long y = 0;
        long z = 0;
        Map<Integer, Item> inventory = Map.of();
        List<Effect> effects = List.of();

        for (int i = 0; i < count; i++) {
            long dt = cursor.varint();
            x += cursor.zigzag();
            y += cursor.zigzag();
            z += cursor.zigzag();

            int yaw = cursor.readShort();
            int pitch = cursor.readShort();
            int dimension = cursor.read();
            int health = cursor.read();
            int food = cursor.read();
            int air = cursor.read();
            long xpLevel = cursor.varint();
            int selectedSlot = cursor.read();
            long flags = cursor.varint();
            int event = cursor.read();

            if (cursor.bad) return null;

            if ((flags & FLAG_INVENTORY) != 0) {
                inventory = readInventory(cursor, palette, budget);
                if (inventory == null) return null;
            }
            if ((flags & FLAG_EFFECTS) != 0) {
                effects = readEffects(cursor, palette, budget);
                if (effects == null) return null;
            }

            centis += (int) Math.min(dt, MAX_CENTIS);
            if (centis < 0 || centis > MAX_CENTIS) return null;

            samples.add(new Sample(centis, x, y, z, yaw, pitch,
                    Math.min(2, Math.max(0, dimension)),
                    Math.min(200, health), Math.min(20, food), Math.min(150, air),
                    (int) Math.min(1_000_000L, Math.max(0L, xpLevel)),
                    Math.min(8, Math.max(0, selectedSlot)),

                    (int) (flags & 0xFFFF) & ~ENCODING_FLAGS,
                    ReplayEvent.of((byte) event),
                    inventory, effects));
        }
        return samples;
    }

    private static List<Moment> readMoments(Cursor cursor, String[] palette) {
        int count = cursor.count(MAX_MOMENTS);
        if (cursor.bad) return null;
        if ((long) count * 2 > cursor.left()) return null;

        List<Moment> moments = new ArrayList<>(count);

        int centis = 0;
        int bx = 0;
        int by = 0;
        int bz = 0;

        for (int i = 0; i < count; i++) {
            long dt = cursor.varint();
            int raw = cursor.read();
            if (cursor.bad) return null;

            ReplayMoment type = ReplayMoment.of(raw);
            if (type == null) return null;

            centis += (int) Math.min(dt, MAX_CENTIS);
            if (centis < 0 || centis > MAX_CENTIS) return null;

            Moment moment = switch (type) {
                case BLOCK_SET -> {
                    bx = (int) clampCoordinate(bx + cursor.zigzag());
                    by = (int) clampCoordinate(by + cursor.zigzag());
                    bz = (int) clampCoordinate(bz + cursor.zigzag());
                    yield Moment.block(centis, bx, by, bz, lookUp(palette, cursor));
                }
                case ENTITY_DIED, ATTACK -> {
                    int id = cursor.count(Integer.MAX_VALUE - 1);
                    yield Moment.entity(centis, type, id, lookUp(palette, cursor), 0);
                }
                case ENTITY_HURT -> {
                    int id = cursor.count(Integer.MAX_VALUE - 1);
                    String kind = lookUp(palette, cursor);
                    yield Moment.entity(centis, type, id, kind, cursor.count(100_000));
                }
                case MINE_PROGRESS -> {
                    bx = (int) clampCoordinate(bx + cursor.zigzag());
                    by = (int) clampCoordinate(by + cursor.zigzag());
                    bz = (int) clampCoordinate(bz + cursor.zigzag());
                    yield Moment.mining(centis, bx, by, bz, cursor.count(10));
                }
                case SWING, HURT, CONTAINER_OPEN, TAMPER, AWAY, PORTAL_LIT ->
                        Moment.number(centis, type, cursor.count(100_000));
                case USE, EAT, THROW, ADVANCEMENT, ENCHANTED ->
                        Moment.item(centis, type, lookUp(palette, cursor), 0);
                case CRAFT, PICKUP, DROP, SMELTED -> {
                    String id = lookUp(palette, cursor);
                    yield Moment.item(centis, type, id, cursor.count(100_000));
                }
                case KILLED_BY -> {
                    int at = cursor.count(Math.max(0, palette.length));
                    String killer = at > 0 && at - 1 < palette.length ? palette[at - 1] : null;
                    yield Moment.item(centis, type, killer == null ? "" : killer, 0);
                }
                case CHAT -> {
                    String who = readName(cursor);
                    yield Moment.chat(centis, who, readChat(cursor));
                }
                case DEATH, CONTAINER_CLOSE, SLEEP -> Moment.at(centis, type);
            };

            if (cursor.bad) return null;
            moments.add(moment);
        }
        return moments;
    }

    private static boolean skipFrames(Cursor cursor, String[] palette, int[] budget, int version) {
        int count = cursor.count(MAX_FRAMES);
        if (cursor.bad) return false;
        if ((long) count * 2 > cursor.left()) return false;

        int centis = 0;
        for (int i = 0; i < count; i++) {
            long dt = cursor.varint();
            int spotCount = cursor.count(MAX_ENTITIES_PER_FRAME);
            if (cursor.bad) return false;

            budget[2] -= spotCount;
            if (budget[2] < 0) return false;

            centis += (int) Math.min(dt, MAX_CENTIS);
            if (centis < 0 || centis > MAX_CENTIS) return false;

            for (int s = 0; s < spotCount; s++) {
                cursor.count(Integer.MAX_VALUE - 1);
                lookUp(palette, cursor);

                if (version >= 4) cursor.count(Math.max(0, palette.length));
                cursor.zigzag();
                cursor.zigzag();
                cursor.zigzag();
                cursor.read();
                cursor.read();
                if (cursor.bad) return false;
            }
        }
        return true;
    }

    private static long clampCoordinate(long value) {
        return Math.max(-64_000_000L, Math.min(64_000_000L, value));
    }

    private static String lookUp(String[] palette, Cursor cursor) {
        int index = cursor.count(Math.max(0, palette.length - 1));
        if (cursor.bad || palette.length == 0) return null;
        return palette[index];
    }

    private static String[] readPalette(Cursor cursor) {
        int size = cursor.count(MAX_PALETTE);
        if (cursor.bad) return null;

        String[] palette = new String[size];
        for (int i = 0; i < size; i++) {
            byte[] raw = cursor.blob(MAX_ID_BYTES);
            if (cursor.bad) return null;

            String id = new String(raw, StandardCharsets.US_ASCII);
            palette[i] = ID_PATTERN.matcher(id).matches() ? id : null;
        }
        return palette;
    }

    private static String readName(Cursor cursor) {
        byte[] raw = cursor.blob(MAX_NAME_BYTES);
        if (cursor.bad) return "";

        String name = new String(raw, StandardCharsets.UTF_8);
        return NAME_PATTERN.matcher(name).matches() ? name : "";
    }

    private static String readChat(Cursor cursor) {
        byte[] raw = cursor.blob(MAX_CHAT_BYTES);
        if (cursor.bad) return "";

        String text = new String(raw, StandardCharsets.UTF_8);
        StringBuilder clean = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' || Character.isISOControl(c)) continue;
            clean.append(c);
        }
        return clean.toString();
    }

    private static Map<Integer, Item> readInventory(Cursor cursor, String[] palette, int[] budget) {
        int size = cursor.count(MAX_SLOTS);
        if (cursor.bad) return null;

        budget[0] -= size;
        if (budget[0] < 0) return null;

        Map<Integer, Item> slots = new LinkedHashMap<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            int slot = cursor.count(SLOT_COUNT - 1);
            String id = lookUp(palette, cursor);
            int amount = cursor.count(9_999);
            if (cursor.bad) return null;

            if (id != null && amount > 0) slots.put(slot, new Item(id, amount));
        }
        return Map.copyOf(slots);
    }

    private static List<Effect> readEffects(Cursor cursor, String[] palette, int[] budget) {
        int size = cursor.count(MAX_EFFECTS);
        if (cursor.bad) return null;

        budget[1] -= size;
        if (budget[1] < 0) return null;

        List<Effect> effects = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = lookUp(palette, cursor);
            int amplifier = cursor.read();
            int seconds = cursor.count(1_000_000);
            if (cursor.bad) return null;

            if (id != null) effects.add(new Effect(id, amplifier, seconds));
        }
        return List.copyOf(effects);
    }
}
