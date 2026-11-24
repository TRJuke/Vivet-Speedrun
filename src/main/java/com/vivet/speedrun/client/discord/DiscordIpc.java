package com.vivet.speedrun.client.discord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DiscordIpc implements AutoCloseable {
    static final int HANDSHAKE = 0;
    static final int FRAME = 1;
    static final int CLOSE = 2;
    static final int PING = 3;
    static final int PONG = 4;

    record Frame(int op, String json) {
    }

    private final RandomAccessFile pipe;
    private final SocketChannel socket;
    private final ByteBuffer pending = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);

    private DiscordIpc(RandomAccessFile pipe, SocketChannel socket) {
        this.pipe = pipe;
        this.socket = socket;
    }

    static DiscordIpc open() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            if (windows) {
                try {
                    return new DiscordIpc(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"), null);
                } catch (IOException notThere) {
                    continue;
                }
            }
            for (Path path : unixCandidates(i)) {
                if (!Files.exists(path)) continue;
                try {
                    SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(path));
                    channel.configureBlocking(false);
                    return new DiscordIpc(null, channel);
                } catch (IOException | UnsupportedOperationException notThere) {
                }
            }
        }
        throw new IOException("Discord not running");
    }

    private static List<Path> unixCandidates(int i) {
        List<Path> out = new ArrayList<>();
        String name = "discord-ipc-" + i;
        for (String variable : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String dir = System.getenv(variable);
            if (dir == null || dir.isBlank()) continue;
            out.add(Path.of(dir, name));
            out.add(Path.of(dir, "app", "com.discordapp.Discord", name));
            out.add(Path.of(dir, "snap.discord", name));
        }
        out.add(Path.of("/tmp", name));
        return out;
    }

    void write(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(op).putInt(body.length).put(body).flip();
        if (this.pipe != null) {
            this.pipe.write(frame.array());
        } else {
            long deadline = System.currentTimeMillis() + 2_000L;
            while (frame.hasRemaining()) {
                if (this.socket.write(frame) == 0) {
                    if (System.currentTimeMillis() > deadline) throw new IOException("write timed out");
                    Thread.onSpinWait();
                }
            }
        }
    }

    List<Frame> poll() throws IOException {
        if (this.pipe != null) {
            long available = this.pipe.length();
            if (available > 0) {
                byte[] bytes = new byte[(int) Math.min(available, this.pending.remaining())];
                int read = this.pipe.read(bytes);
                if (read < 0) throw new IOException("closed");
                this.pending.put(bytes, 0, read);
            }
        } else {
            int read = this.socket.read(this.pending);
            if (read < 0) throw new IOException("closed");
        }

        List<Frame> frames = new ArrayList<>();
        this.pending.flip();
        while (this.pending.remaining() >= 8) {
            this.pending.mark();
            int op = this.pending.getInt();
            int length = this.pending.getInt();
            if (length < 0 || length > 60 * 1024) throw new IOException("bad message");
            if (this.pending.remaining() < length) {
                this.pending.reset();
                break;
            }
            byte[] body = new byte[length];
            this.pending.get(body);
            frames.add(new Frame(op, new String(body, StandardCharsets.UTF_8)));
        }
        this.pending.compact();
        return frames;
    }

    @Override
    public void close() {
        try {
            if (this.pipe != null) this.pipe.close();
            if (this.socket != null) this.socket.close();
        } catch (IOException ignored) {
        }
    }
}
