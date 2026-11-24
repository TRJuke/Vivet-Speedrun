package com.vivet.speedrun.replay;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ReplayContainer {
    private static final byte[] MAGIC = { 'V', 'R', 'X' };
    public static final byte VERSION = 1;

    public static final int SECTION_TRACE = 1;
    public static final int SECTION_META = 2;
    public static final int SECTION_BODY = 3;
    public static final int SECTION_WORLD_INDEX = 4;
    public static final int SECTION_WORLD = 5;

    public static final int WORLD_PLAY = 0;
    public static final int WORLD_CONFIG = 1;

    public static final int WORLD_RECONNECT = 2;

    public static final int WORLD_CHUNK_AGAIN = 3;

    public static final int WORLD_POINTER = 4;

    public static final int WORLD_BOOK = 5;

    public static final int WORLD_BOOK_PICK = 6;

    public static final int WORLD_DRAG = 7;

    public static final int WORLD_RIDE_JUMP = 8;

    public static final int WORLD_OWN_SCREEN = 9;

    public static final int BODY_OVERWORLD = 0;
    public static final int BODY_NETHER = 1;
    public static final int BODY_END = 2;

    private static final int MAX_SECTIONS = 16;

    public static final int HEAD_BYTES = 256 * 1024;

    public static final long MAX_FILE_BYTES = 512L * 1024 * 1024;

    public static final int BLOCK_TARGET = 256 * 1024;
    public static final int MAX_BLOCK_RAW = 16 * 1024 * 1024;

    public static final int MAX_FRAME = 8 * 1024 * 1024 + 64;

    private static final int MAX_BLOCKS = 200_000;
    private static final int MAX_HOLDS = 4_096;
    private static final int MAX_BODY_RAW = 128 * 1024 * 1024;

    public static final long MAX_TOTAL_RAW = 1024L * 1024 * 1024;
    public static final int MAX_TOTAL_RECORDS = 4_000_000;

    public static final long MAX_GAP_MS = 24L * 60 * 60 * 1000;
    public static final int MAX_BLOCK_RECORDS = 1_000_000;

    private static final int MAX_BODY_RECORDS = 2_000_000;

    private ReplayContainer() {
    }

    public record Section(int kind, long offset, long length) {
        public long end() {
            return offset + length;
        }
    }

    public record Header(int version, List<Section> sections, int headerLength) {
        public Section find(int kind) {
            for (Section section : sections) if (section.kind() == kind) return section;
            return null;
        }

        public boolean hasWorld() {
            Section world = find(SECTION_WORLD);
            return world != null && world.length() > 0 && find(SECTION_WORLD_INDEX) != null;
        }
    }

    public static boolean isContainer(byte[] head) {
        return head != null && head.length >= 4 && head[0] == MAGIC[0] && head[1] == MAGIC[1]
                && head[2] == MAGIC[2];
    }

    public static int headerLength(int sections) {
        return 3 + 1 + 4 + sections * 17;
    }

    public static Header readHeader(byte[] head, long fileLength) {
        if (!isContainer(head) || head.length < 8) return null;
        int version = head[3] & 0xFF;
        if (version != VERSION) return null;

        int count = readInt(head, 4);
        if (count < 1 || count > MAX_SECTIONS) return null;
        int length = headerLength(count);
        if (head.length < length) return null;

        List<Section> sections = new ArrayList<>(count);
        int at = 8;
        for (int i = 0; i < count; i++) {
            int kind = head[at] & 0xFF;
            long offset = readLong(head, at + 1);
            long size = readLong(head, at + 9);
            at += 17;
            if (offset < length || size < 0 || offset + size < offset) return null;
            if (fileLength > 0 && offset + size > fileLength) return null;
            sections.add(new Section(kind, offset, size));
        }
        return new Header(version, List.copyOf(sections), length);
    }

    public static byte[] writeHeader(List<Section> sections) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(headerLength(sections.size()));
        out.write(MAGIC[0]);
        out.write(MAGIC[1]);
        out.write(MAGIC[2]);
        out.write(VERSION);
        writeInt(out, sections.size());
        for (Section section : sections) {
            out.write(section.kind());
            writeLong(out, section.offset());
            writeLong(out, section.length());
        }
        return out.toByteArray();
    }

    public record Hold(long fromMs, long lengthMs) {
    }

    public record Meta(int protocol, long startedAt, long endedAt, int finalCentis, List<Hold> holds,
                       String uuid, String name, String skinValue, String skinSignature,
                       boolean truncated) {
        public int raceCentis(long wallMs) {
            if (wallMs <= 0) return 0;
            long held = 0;
            for (Hold hold : holds) {
                if (hold.fromMs() >= wallMs) break;
                held += Math.min(hold.lengthMs(), wallMs - hold.fromMs());
            }
            long centis = Math.max(0, (wallMs - held) / 10L);
            return (int) Math.min(centis, Math.max(0, finalCentis));
        }

        public long wallMsFor(int centis) {
            long target = Math.max(0, centis) * 10L;
            long wall = target;
            for (Hold hold : holds) {
                if (hold.fromMs() > wall) break;
                wall += hold.lengthMs();
            }
            return Math.min(wall, durationMs());
        }

        public long durationMs() {
            return Math.max(1, endedAt - startedAt);
        }
    }

    public static byte[] encodeMeta(Meta meta) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        writeInt(out, meta.protocol());
        writeLong(out, meta.startedAt());
        writeLong(out, meta.endedAt());
        writeInt(out, meta.finalCentis());
        writeInt(out, Math.min(MAX_HOLDS, meta.holds().size()));
        for (int i = 0; i < meta.holds().size() && i < MAX_HOLDS; i++) {
            writeLong(out, meta.holds().get(i).fromMs());
            writeLong(out, meta.holds().get(i).lengthMs());
        }
        writeString(out, meta.uuid());
        writeString(out, meta.name());
        writeString(out, meta.skinValue());
        writeString(out, meta.skinSignature());
        out.write(meta.truncated() ? 1 : 0);
        return deflate(out.toByteArray(), Deflater.BEST_COMPRESSION);
    }

    public static Meta decodeMeta(byte[] deflated) {
        byte[] raw = inflate(deflated, 1024 * 1024);
        if (raw == null) return null;
        try {
            int[] at = { 0 };
            int protocol = readInt(raw, at);
            long startedAt = readLong(raw, at);
            long endedAt = readLong(raw, at);
            int finalCentis = readInt(raw, at);
            int count = readInt(raw, at);
            if (count < 0 || count > MAX_HOLDS) return null;
            List<Hold> holds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long from = readLong(raw, at);
                long length = readLong(raw, at);
                if (length > 0) holds.add(new Hold(from, length));
            }
            holds.sort((a, b) -> Long.compare(a.fromMs(), b.fromMs()));
            String uuid = readString(raw, at);
            String name = readString(raw, at);
            String skinValue = readString(raw, at);
            String skinSignature = readString(raw, at);
            boolean truncated = at[0] < raw.length && raw[at[0]] != 0;
            if (endedAt < startedAt) return null;
            return new Meta(protocol, startedAt, endedAt, finalCentis, List.copyOf(holds),
                    uuid, name, skinValue, skinSignature, truncated);
        } catch (IndexOutOfBoundsException malformed) {
            return null;
        }
    }

    public record Block(long offset, int compressed, int raw, long firstWall, long lastWall, int records) {
    }

    public static byte[] encodeIndex(List<Block> blocks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 + blocks.size() * 40);
        writeInt(out, blocks.size());
        for (Block block : blocks) {
            writeLong(out, block.offset());
            writeInt(out, block.compressed());
            writeInt(out, block.raw());
            writeLong(out, block.firstWall());
            writeLong(out, block.lastWall());
            writeInt(out, block.records());
        }
        return deflate(out.toByteArray(), Deflater.BEST_COMPRESSION);
    }

    public static List<Block> decodeIndex(byte[] deflated, long worldLength) {
        byte[] raw = inflate(deflated, 64 * 1024 * 1024);
        if (raw == null) return null;
        try {
            int[] at = { 0 };
            int count = readInt(raw, at);
            if (count < 0 || count > MAX_BLOCKS) return null;
            List<Block> blocks = new ArrayList<>(count);
            long totalRaw = 0;
            long totalRecords = 0;
            for (int i = 0; i < count; i++) {
                Block block = new Block(readLong(raw, at), readInt(raw, at), readInt(raw, at),
                        readLong(raw, at), readLong(raw, at), readInt(raw, at));
                if (block.offset() < 0 || block.compressed() <= 0 || block.raw() <= 0
                        || block.raw() > MAX_BLOCK_RAW || block.offset() + block.compressed() > worldLength
                        || block.records() < 0 || block.records() > MAX_BLOCK_RECORDS) {
                    return null;
                }

                totalRaw += block.raw();
                totalRecords += block.records();
                if (totalRaw > MAX_TOTAL_RAW || totalRecords > MAX_TOTAL_RECORDS) return null;
                blocks.add(block);
            }
            return List.copyOf(blocks);
        } catch (IndexOutOfBoundsException malformed) {
            return null;
        }
    }

    public record Record(long wall, int kind, byte[] frame) {
    }

    public static void writeRecord(ByteArrayOutputStream out, long sinceLast, int kind,
                                   byte[] frame, int offset, int length) {
        writeVarLong(out, Math.max(0, sinceLast));
        out.write(kind & 0xFF);
        writeVarint(out, length);
        out.write(frame, offset, length);
    }

    public static List<Record> readRecords(byte[] raw, long firstWall) {
        return readRecords(raw, 0, firstWall, MAX_BLOCK_RECORDS);
    }

    private static List<Record> readRecords(byte[] raw, int start, long firstWall, int maxRecords) {
        List<Record> records = new ArrayList<>();
        int[] at = { start };
        long wall = firstWall;
        try {
            while (at[0] < raw.length) {
                if (records.size() >= maxRecords) return null;
                long since = readVarLong(raw, at);

                if (since < 0 || since > MAX_GAP_MS) return null;
                wall += since;
                int kind = raw[at[0]++] & 0xFF;
                int length = readVarint(raw, at);
                if (length < 0 || length > MAX_FRAME || at[0] + length > raw.length) return null;
                byte[] frame = new byte[length];
                System.arraycopy(raw, at[0], frame, 0, length);
                at[0] += length;
                records.add(new Record(wall, kind, frame));
            }
        } catch (IndexOutOfBoundsException malformed) {
            return null;
        }
        return records;
    }

    public static byte[] encodeBody(List<Record> records) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, records.size() * 40));
        long first = records.isEmpty() ? 0 : records.get(0).wall();
        writeLong(out, first);
        long last = first;
        for (Record record : records) {
            writeRecord(out, record.wall() - last, record.kind(), record.frame(), 0, record.frame().length);
            last = Math.max(last, record.wall());
        }
        return deflate(out.toByteArray(), 6);
    }

    public static List<Record> decodeBody(byte[] deflated) {
        byte[] raw = inflate(deflated, MAX_BODY_RAW);
        if (raw == null || raw.length < 8) return raw == null ? null : List.of();
        long first = readLong(raw, 0);

        return readRecords(raw, 8, first, MAX_BODY_RECORDS);
    }

    public record Pointer(boolean open, int x, int y) {
        public static final Pointer CLOSED = new Pointer(false, 0, 0);
    }

    public static byte[] pointer(Pointer pointer) {
        int x = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, pointer.x()));
        int y = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, pointer.y()));
        return new byte[] { (byte) (pointer.open() ? 1 : 0), (byte) (x >> 8), (byte) x, (byte) (y >> 8), (byte) y };
    }

    public static Pointer pointer(byte[] payload) {
        if (payload == null || payload.length != 5) return null;
        if (payload[0] == 0) return Pointer.CLOSED;
        short x = (short) (((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF));
        short y = (short) (((payload[3] & 0xFF) << 8) | (payload[4] & 0xFF));
        return new Pointer(true, x, y);
    }

    public static byte[] book(com.vivet.speedrun.net.Payloads.Book book) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            com.vivet.speedrun.net.Payloads.Book.CODEC.encode(buffer, book);
            byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out;
        } finally {
            buffer.release();
        }
    }

    public static com.vivet.speedrun.net.Payloads.Book book(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > 512) return null;
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
        try {
            return com.vivet.speedrun.net.Payloads.Book.CODEC.decode(buffer);
        } catch (RuntimeException unreadable) {
            return null;
        } finally {
            buffer.release();
        }
    }

    public static byte[] bookPick(int button) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2);
        writeVarint(out, Math.max(-1, button) + 1);
        return out.toByteArray();
    }

    public static Integer bookPick(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > 5) return null;
        try {
            return readVarint(payload, new int[] { 0 }) - 1;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    public static byte[] ownScreen(com.vivet.speedrun.net.Payloads.OwnScreen screen) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            com.vivet.speedrun.net.Payloads.OwnScreen.CODEC.encode(buffer, screen);
            byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out;
        } finally {
            buffer.release();
        }
    }

    public static com.vivet.speedrun.net.Payloads.OwnScreen ownScreen(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > 40_000) return null;
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
        try {
            return com.vivet.speedrun.net.Payloads.OwnScreen.CODEC.decode(buffer);
        } catch (RuntimeException unreadable) {
            return null;
        } finally {
            buffer.release();
        }
    }

    public static byte[] rideJump(int percent) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2);
        writeVarint(out, Math.max(0, Math.min(100, percent)));
        return out.toByteArray();
    }

    public static Integer rideJump(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > 5) return null;
        try {
            return readVarint(payload, new int[] { 0 });
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    public static byte[] drag(com.vivet.speedrun.net.Payloads.Drag drag) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            com.vivet.speedrun.net.Payloads.Drag.CODEC.encode(buffer, drag);
            byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out;
        } finally {
            buffer.release();
        }
    }

    public static com.vivet.speedrun.net.Payloads.Drag drag(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > 1024) return null;
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
        try {
            return com.vivet.speedrun.net.Payloads.Drag.CODEC.decode(buffer);
        } catch (RuntimeException unreadable) {
            return null;
        } finally {
            buffer.release();
        }
    }

    public static byte[] deflate(byte[] raw, int level) {
        Deflater deflater = new Deflater(level);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] chunk = new byte[64 * 1024];
            while (!deflater.finished()) {
                int wrote = deflater.deflate(chunk);
                if (wrote <= 0 && deflater.needsInput()) break;
                out.write(chunk, 0, wrote);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] deflated, int max) {
        if (deflated == null) return null;
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(deflated);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, Math.max(64, deflated.length * 4)));
            byte[] chunk = new byte[64 * 1024];
            while (!inflater.finished()) {
                int read = inflater.inflate(chunk);
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return null;
                    continue;
                }
                if (out.size() + read > max) return null;
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    public static int readVarint(byte[] in, int[] at) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in[at[0]++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        return -1;
    }

    public static int packetId(byte[] frame) {
        if (frame == null || frame.length == 0) return -1;
        try {
            return readVarint(frame, new int[] { 0 });
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    private static long readVarLong(byte[] in, int[] at) {
        long value = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            int b = in[at[0]++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        return -1;
    }

    public static void writeVarint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeVarLong(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (value >>> shift));
    }

    private static int readInt(byte[] in, int at) {
        return ((in[at] & 0xFF) << 24) | ((in[at + 1] & 0xFF) << 16) | ((in[at + 2] & 0xFF) << 8)
                | (in[at + 3] & 0xFF);
    }

    private static int readInt(byte[] in, int[] at) {
        int value = readInt(in, at[0]);
        at[0] += 4;
        return value;
    }

    private static long readLong(byte[] in, int at) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (in[at + i] & 0xFF);
        return value;
    }

    private static long readLong(byte[] in, int[] at) {
        long value = readLong(in, at[0]);
        at[0] += 8;
        return value;
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 64 * 1024);
        writeVarint(out, length);
        out.write(bytes, 0, length);
    }

    private static String readString(byte[] in, int[] at) {
        int length = readVarint(in, at);
        if (length < 0 || length > 64 * 1024 || at[0] + length > in.length) {
            throw new IndexOutOfBoundsException("string");
        }
        String value = new String(in, at[0], length, StandardCharsets.UTF_8);
        at[0] += length;
        return value;
    }
}
