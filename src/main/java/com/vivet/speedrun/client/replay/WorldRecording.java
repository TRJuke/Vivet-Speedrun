package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.replay.ReplayContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.GameProtocols;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldRecording implements AutoCloseable {
    static final Map<PacketType<?>, Integer> PLAY = new HashMap<>();

    static {
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> PLAY.put(type, id));
    }

    static int id(PacketType<?> type) {
        return PLAY.getOrDefault(type, -1);
    }

    private static final int CHUNK_WITH_LIGHT = id(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT);
    private static final int FORGET_CHUNK = id(GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK);
    private static final int LIGHT_UPDATE = id(GamePacketTypes.CLIENTBOUND_LIGHT_UPDATE);
    private static final int BLOCK_UPDATE = id(GamePacketTypes.CLIENTBOUND_BLOCK_UPDATE);
    private static final int SECTION_UPDATE = id(GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE);
    private static final int BLOCK_ENTITY = id(GamePacketTypes.CLIENTBOUND_BLOCK_ENTITY_DATA);
    private static final int BLOCK_EVENT = id(GamePacketTypes.CLIENTBOUND_BLOCK_EVENT);

    public static final long NO_CHUNK = Long.MIN_VALUE;

    private final RandomAccessFile file;
    public final ReplayContainer.Meta meta;
    public final List<ReplayContainer.Record> body;
    private final List<ReplayContainer.Block> blocks;
    private final long worldOffset;

    private int count;
    private long[] wall = new long[4096];
    private int[] block = new int[4096];
    private int[] offset = new int[4096];
    private int[] length = new int[4096];
    private int[] packet = new int[4096];
    private byte[] kind = new byte[4096];
    private long[] chunk = new long[4096];

    private final Map<Integer, byte[]> cache = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
            return size() > 8;
        }
    };

    private WorldRecording(RandomAccessFile file, ReplayContainer.Meta meta,
                           List<ReplayContainer.Record> body, List<ReplayContainer.Block> blocks,
                           long worldOffset) {
        this.file = file;
        this.meta = meta;
        this.body = body;
        this.blocks = blocks;
        this.worldOffset = worldOffset;
    }

    public static WorldRecording open(Path path, int protocol) {
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(path.toFile(), "r");
            long size = file.length();
            if (size > ReplayContainer.MAX_FILE_BYTES) return fail(file, "too large");

            byte[] head = new byte[(int) Math.min(size, 1024)];
            file.readFully(head);
            ReplayContainer.Header header = ReplayContainer.readHeader(head, size);
            if (header == null || !header.hasWorld()) return fail(file, "no world");

            ReplayContainer.Meta meta = ReplayContainer.decodeMeta(read(file, header.find(ReplayContainer.SECTION_META)));
            if (meta == null) return fail(file, "no metadata");
            if (meta.protocol() != protocol) return fail(file, "wrong version (" + meta.protocol() + ")");

            List<ReplayContainer.Record> body = ReplayContainer.decodeBody(read(file, header.find(ReplayContainer.SECTION_BODY)));
            ReplayContainer.Section world = header.find(ReplayContainer.SECTION_WORLD);
            List<ReplayContainer.Block> blocks = ReplayContainer.decodeIndex(
                    read(file, header.find(ReplayContainer.SECTION_WORLD_INDEX)), world.length());
            if (body == null || blocks == null || blocks.isEmpty()) return fail(file, "unreadable");

            WorldRecording recording = new WorldRecording(file, meta, List.copyOf(body), blocks, world.offset());
            if (!recording.index()) return fail(file, "corrupt block");
            return recording;
        } catch (IOException | RuntimeException error) {
            VivetSpeedrun.LOGGER.warn("Could not open recording world: {}", error.toString());
            if (file != null) {
                try {
                    file.close();
                } catch (IOException ignored) {
                }
            }
            return null;
        }
    }

    static volatile String lastRefusal = "";

    private static WorldRecording fail(RandomAccessFile file, String why) throws IOException {
        lastRefusal = why;
        VivetSpeedrun.LOGGER.info("Recording world not opened: {}", why);
        file.close();
        return null;
    }

    private static byte[] read(RandomAccessFile file, ReplayContainer.Section section) throws IOException {
        if (section == null || section.length() > 256L * 1024 * 1024) throw new IOException("missing section");
        byte[] bytes = new byte[(int) section.length()];
        file.seek(section.offset());
        file.readFully(bytes);
        return bytes;
    }

    private boolean index() throws IOException {
        for (int b = 0; b < this.blocks.size(); b++) {
            ReplayContainer.Block at = this.blocks.get(b);
            byte[] raw = unpack(b);
            if (raw == null) return false;

            int[] cursor = { 0 };
            long time = at.firstWall();
            int inBlock = 0;
            while (cursor[0] < raw.length) {
                if (++inBlock > ReplayContainer.MAX_BLOCK_RECORDS
                        || this.count >= ReplayContainer.MAX_TOTAL_RECORDS) return false;
                long since = readVarLong(raw, cursor);

                if (since < 0 || since > ReplayContainer.MAX_GAP_MS) return false;
                time += since;
                int recordKind = raw[cursor[0]++] & 0xFF;
                int size = ReplayContainer.readVarint(raw, cursor);
                if (size < 0 || cursor[0] + size > raw.length) return false;
                add(time - this.meta.startedAt(), b, cursor[0], size, recordKind, raw);
                cursor[0] += size;
            }
        }
        return true;
    }

    private void add(long relative, int b, int start, int size, int recordKind, byte[] raw) {
        if (this.count == this.wall.length) grow();
        if (recordKind == ReplayContainer.WORLD_CHUNK_AGAIN && again(relative, raw, start, size)) return;
        int id = -1;
        long key = NO_CHUNK;
        if (size > 0 && (recordKind == ReplayContainer.WORLD_PLAY || recordKind == ReplayContainer.WORLD_CONFIG)) {
            int[] at = { start };
            id = ReplayContainer.readVarint(raw, at);
            if (recordKind == ReplayContainer.WORLD_PLAY) key = chunkOf(id, raw, at[0], start + size);
        }
        this.wall[this.count] = relative;
        this.block[this.count] = b;
        this.offset[this.count] = start;
        this.length[this.count] = size;
        this.packet[this.count] = id;
        this.kind[this.count] = (byte) recordKind;
        this.chunk[this.count] = key;
        this.count++;
    }

    private boolean again(long relative, byte[] raw, int start, int size) {
        int target;
        try {
            target = ReplayContainer.readVarint(raw, new int[] { start });
        } catch (IndexOutOfBoundsException malformed) {
            return false;
        }
        if (size <= 0 || target < 0 || target >= this.count || this.kind[target] != ReplayContainer.WORLD_PLAY
                || this.packet[target] != CHUNK_WITH_LIGHT) {
            return false;
        }
        this.wall[this.count] = relative;
        this.block[this.count] = this.block[target];
        this.offset[this.count] = this.offset[target];
        this.length[this.count] = this.length[target];
        this.packet[this.count] = CHUNK_WITH_LIGHT;
        this.kind[this.count] = ReplayContainer.WORLD_PLAY;
        this.chunk[this.count] = this.chunk[target];
        this.count++;
        return true;
    }

    private static long chunkOf(int id, byte[] raw, int at, int end) {
        try {
            if (id == CHUNK_WITH_LIGHT && at + 8 <= end) {
                return pack(readInt(raw, at), readInt(raw, at + 4));
            }
            if (id == FORGET_CHUNK && at + 8 <= end) {
                long packed = readLong(raw, at);
                return pack((int) packed, (int) (packed >> 32));
            }
            if (id == LIGHT_UPDATE) {
                int[] cursor = { at };
                int x = ReplayContainer.readVarint(raw, cursor);
                int z = ReplayContainer.readVarint(raw, cursor);
                return pack(x, z);
            }
            if ((id == BLOCK_UPDATE || id == BLOCK_ENTITY || id == BLOCK_EVENT) && at + 8 <= end) {
                long pos = readLong(raw, at);
                return pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
            }
            if (id == SECTION_UPDATE && at + 8 <= end) {
                long section = readLong(raw, at);
                return pack(SectionPos.x(section), SectionPos.z(section));
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
        return NO_CHUNK;
    }

    private static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    private void grow() {
        int size = this.wall.length * 2;
        this.wall = java.util.Arrays.copyOf(this.wall, size);
        this.block = java.util.Arrays.copyOf(this.block, size);
        this.offset = java.util.Arrays.copyOf(this.offset, size);
        this.length = java.util.Arrays.copyOf(this.length, size);
        this.packet = java.util.Arrays.copyOf(this.packet, size);
        this.kind = java.util.Arrays.copyOf(this.kind, size);
        this.chunk = java.util.Arrays.copyOf(this.chunk, size);
    }

    public int count() {
        return this.count;
    }

    public long wall(int index) {
        return this.wall[index];
    }

    public int packetId(int index) {
        return this.packet[index];
    }

    public int kind(int index) {
        return this.kind[index];
    }

    public long chunk(int index) {
        return this.chunk[index];
    }

    public ReplayContainer.Pointer pointer(int index) {
        if (this.kind[index] != ReplayContainer.WORLD_POINTER) return null;
        return ReplayContainer.pointer(frame(index));
    }

    public byte[] frame(int index) {
        byte[] raw;
        try {
            raw = unpack(this.block[index]);
        } catch (IOException error) {
            return null;
        }
        if (raw == null) return null;
        byte[] out = new byte[this.length[index]];
        System.arraycopy(raw, this.offset[index], out, 0, out.length);
        return out;
    }

    public int indexAt(long relative) {
        int low = 0;
        int high = this.count;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (this.wall[mid] < relative) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private byte[] unpack(int index) throws IOException {
        byte[] raw = this.cache.get(index);
        if (raw != null) return raw;
        ReplayContainer.Block at = this.blocks.get(index);
        byte[] packed = new byte[at.compressed()];
        synchronized (this.file) {
            this.file.seek(this.worldOffset + at.offset());
            this.file.readFully(packed);
        }
        raw = ReplayContainer.inflate(packed, ReplayContainer.MAX_BLOCK_RAW);
        if (raw == null || raw.length != at.raw()) return null;
        this.cache.put(index, raw);
        return raw;
    }

    @Override
    public void close() {
        try {
            this.file.close();
        } catch (IOException ignored) {
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

    private static int readInt(byte[] in, int at) {
        return ((in[at] & 0xFF) << 24) | ((in[at + 1] & 0xFF) << 16) | ((in[at + 2] & 0xFF) << 8) | (in[at + 3] & 0xFF);
    }

    private static long readLong(byte[] in, int at) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (in[at + i] & 0xFF);
        return value;
    }
}
