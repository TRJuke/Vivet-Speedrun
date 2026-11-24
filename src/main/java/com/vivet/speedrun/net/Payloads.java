package com.vivet.speedrun.net;

import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.RunRecord;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class Payloads {
    private Payloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VivetSpeedrun.MOD_ID, path);
    }

    private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(E[] values, E fallback) {
        return StreamCodec.of(
                (buffer, value) -> ByteBufCodecs.VAR_INT.encode(buffer, value.ordinal()),
                buffer -> {
                    int ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
                    return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
                });
    }

    private static final StreamCodec<ByteBuf, Difficulty> DIFFICULTY =
            enumCodec(Difficulty.values(), Difficulty.MEDIUM);
    private static final StreamCodec<ByteBuf, RunRecord.Outcome> OUTCOME =
            enumCodec(RunRecord.Outcome.values(), RunRecord.Outcome.LOST);

    public record RunState(
            boolean running,
            String itemId,

            int elapsedCentis,
            Difficulty difficulty,
            int rerollsUsed,

            int activity
    ) implements CustomPacketPayload {
        public static final Type<RunState> TYPE = new Type<>(id("run_state"));

        public static final StreamCodec<ByteBuf, RunState> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.BOOL.encode(buffer, value.running);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.itemId);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.elapsedCentis);
                    DIFFICULTY.encode(buffer, value.difficulty);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.rerollsUsed);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.activity);
                },
                buffer -> new RunState(
                        ByteBufCodecs.BOOL.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        DIFFICULTY.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HistoryEntry(
            String itemId,
            String winnerName,
            RunRecord.Outcome outcome,
            Difficulty difficulty,
            int seconds,
            long date
    ) {
        public static final StreamCodec<ByteBuf, HistoryEntry> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.itemId);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.winnerName);
                    OUTCOME.encode(buffer, value.outcome);
                    DIFFICULTY.encode(buffer, value.difficulty);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.seconds);
                    buffer.writeLong(value.date);
                },
                buffer -> new HistoryEntry(
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        OUTCOME.decode(buffer),
                        DIFFICULTY.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        buffer.readLong()));

        public static HistoryEntry from(RunRecord record) {
            return new HistoryEntry(record.itemId, record.winnerName, record.outcome,
                    record.difficulty, record.seconds, record.date);
        }

        public RunRecord toRecord() {
            RunRecord record = new RunRecord();
            record.itemId = this.itemId;
            record.winnerName = this.winnerName;
            record.outcome = this.outcome;
            record.difficulty = this.difficulty;
            record.seconds = this.seconds;
            record.date = this.date;
            return record;
        }
    }

    public record History(List<HistoryEntry> entries) implements CustomPacketPayload {
        public static final Type<History> TYPE = new Type<>(id("history"));

        public static final StreamCodec<ByteBuf, History> CODEC = StreamCodec.composite(
                ByteBufCodecs.collection(ArrayList::new, HistoryEntry.CODEC), History::entries,
                History::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RunFinished(String itemId, String winnerName, RunRecord.Outcome outcome,
                              int seconds, int centis, String detail)
            implements CustomPacketPayload {
        public static final Type<RunFinished> TYPE = new Type<>(id("run_finished"));

        public static final StreamCodec<ByteBuf, RunFinished> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RunFinished::itemId,
                ByteBufCodecs.STRING_UTF8, RunFinished::winnerName,
                OUTCOME, RunFinished::outcome,
                ByteBufCodecs.VAR_INT, RunFinished::seconds,
                ByteBufCodecs.VAR_INT, RunFinished::centis,
                ByteBufCodecs.STRING_UTF8, RunFinished::detail,
                RunFinished::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Reveal(String itemId, boolean reroll, Difficulty band)
            implements CustomPacketPayload {
        public static final Type<Reveal> TYPE = new Type<>(id("reveal"));

        public static final StreamCodec<ByteBuf, Reveal> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Reveal::itemId,
                ByteBufCodecs.BOOL, Reveal::reroll,
                DIFFICULTY, Reveal::band,
                Reveal::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Go() implements CustomPacketPayload {
        public static final Go INSTANCE = new Go();

        public static final Type<Go> TYPE = new Type<>(id("go"));

        public static final StreamCodec<ByteBuf, Go> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum Action {
        TOGGLE_RUN,
        REROLL,
        FORFEIT,
        DRAW,
        PLAY_AGAIN,

        APPLY_MODIFIERS,

        WIN_BY_DEFAULT,

        LOSE_RACE,

        SPECTATE,

        REVEAL_DONE,

        READY,

        STARTED,

        TELEPORT,

        NEXT_PLAYER,
    }

    public record RunAction(Action action) implements CustomPacketPayload {
        public static final Type<RunAction> TYPE = new Type<>(id("run_action"));

        public static final StreamCodec<ByteBuf, RunAction> CODEC = StreamCodec.composite(
                enumCodec(Action.values(), Action.TOGGLE_RUN), RunAction::action,
                RunAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Pointer(boolean open, short x, short y) implements CustomPacketPayload {
        public static final Type<Pointer> TYPE = new Type<>(id("pointer"));

        public static final StreamCodec<ByteBuf, Pointer> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Pointer::open,
                ByteBufCodecs.SHORT, Pointer::x,
                ByteBufCodecs.SHORT, Pointer::y,
                Pointer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StartShared(String itemId, Difficulty difficulty)
            implements CustomPacketPayload {
        public static final Type<StartShared> TYPE = new Type<>(id("start_shared"));

        public static final StreamCodec<ByteBuf, StartShared> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StartShared::itemId,
                DIFFICULTY, StartShared::difficulty,
                StartShared::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Milestone(String text) implements CustomPacketPayload {
        public static final Type<Milestone> TYPE = new Type<>(id("milestone"));

        public static final StreamCodec<ByteBuf, Milestone> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Milestone::text,
                Milestone::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Line(String json) implements CustomPacketPayload {
        public static final Type<Line> TYPE = new Type<>(id("line"));

        public static final StreamCodec<ByteBuf, Line> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::json,
                Line::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Watch(String racer, String itemId, int centis, Difficulty band, String kind,
                        int flags, String next) implements CustomPacketPayload {
        public static final int RUNNING = 1;

        public static final int AWAY = 2;

        public static final Type<Watch> TYPE = new Type<>(id("watch"));

        public static final StreamCodec<ByteBuf, Watch> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.racer);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.itemId);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.centis);
                    DIFFICULTY.encode(buffer, value.band);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.kind);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.flags);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.next);
                },
                buffer -> new Watch(
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        DIFFICULTY.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer)));

        public boolean running() {
            return (this.flags & RUNNING) != 0;
        }

        public boolean away() {
            return (this.flags & AWAY) != 0;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WatchEnd(String racer, String itemId, String winnerName,
                           RunRecord.Outcome outcome, int centis, String detail, String next)
            implements CustomPacketPayload {
        public static final Type<WatchEnd> TYPE = new Type<>(id("watch_end"));

        public static final StreamCodec<ByteBuf, WatchEnd> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.racer);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.itemId);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.winnerName);
                    OUTCOME.encode(buffer, value.outcome);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.centis);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.detail);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.next);
                },
                buffer -> new WatchEnd(
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        OUTCOME.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Book(boolean open, int tab, int page, boolean craftable, String search)
            implements CustomPacketPayload {
        public static final Book SHUT = new Book(false, 0, 0, false, "");

        public static final int MAX_SEARCH = 50;

        public static final Type<Book> TYPE = new Type<>(id("book"));

        public static final StreamCodec<ByteBuf, Book> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Book::open,
                ByteBufCodecs.VAR_INT, Book::tab,
                ByteBufCodecs.VAR_INT, Book::page,
                ByteBufCodecs.BOOL, Book::craftable,
                ByteBufCodecs.stringUtf8(MAX_SEARCH * 4), Book::search,
                Book::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OwnScreen(int kind, int page, int pages, int line, List<String> text, long pos, boolean front)
            implements CustomPacketPayload {
        public static final int NONE_KIND = 0;
        public static final int SIGN = 1;
        public static final int BOOK_EDIT = 2;
        public static final int BOOK_READ = 3;
        public static final int ANVIL = 4;
        public static final int BOOK_SIGN = 5;

        public static final OwnScreen NONE = new OwnScreen(NONE_KIND, 0, 0, 0, List.of(), 0L, false);

        public static final int MAX_TEXT = 1024;

        public static final Type<OwnScreen> TYPE = new Type<>(id("own_screen"));

        public static final StreamCodec<ByteBuf, OwnScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OwnScreen::kind,
                ByteBufCodecs.VAR_INT, OwnScreen::page,
                ByteBufCodecs.VAR_INT, OwnScreen::pages,
                ByteBufCodecs.VAR_INT, OwnScreen::line,
                ByteBufCodecs.stringUtf8(MAX_TEXT * 4).apply(ByteBufCodecs.list(8)), OwnScreen::text,
                ByteBufCodecs.LONG, OwnScreen::pos,
                ByteBufCodecs.BOOL, OwnScreen::front,
                OwnScreen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RideJump(int percent) implements CustomPacketPayload {
        public static final Type<RideJump> TYPE = new Type<>(id("ride_jump"));

        public static final StreamCodec<ByteBuf, RideJump> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RideJump::percent,
                RideJump::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Drag(int kind, List<Integer> slots) implements CustomPacketPayload {
        public static final Drag NONE = new Drag(-1, List.of());

        public static final int MAX_SLOTS = 128;

        public static final Type<Drag> TYPE = new Type<>(id("drag"));

        public static final StreamCodec<ByteBuf, Drag> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Drag::kind,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_SLOTS)), Drag::slots,
                Drag::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BookPick(int button) implements CustomPacketPayload {
        public static final BookPick NONE = new BookPick(-1);

        public static final Type<BookPick> TYPE = new Type<>(id("book_pick"));

        public static final StreamCodec<ByteBuf, BookPick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BookPick::button,
                BookPick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Eyes(int entity, int flags, double x, double y, double z, float yaw, float pitch,
                       float head, short pointerX, short pointerY, List<byte[]> frames)
            implements CustomPacketPayload {
        public static final int RESET = 1;

        public static final int POSE = 2;

        public static final int POINTER = 4;
        public static final int POINTER_OPEN = 8;

        private static final int MAX_FRAMES = 4096;
        private static final int MAX_FRAME_BYTES = 262_144;

        public static final Type<Eyes> TYPE = new Type<>(id("eyes"));

        public static final StreamCodec<ByteBuf, Eyes> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.VAR_INT.encode(buffer, value.entity);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.flags);
                    ByteBufCodecs.DOUBLE.encode(buffer, value.x);
                    ByteBufCodecs.DOUBLE.encode(buffer, value.y);
                    ByteBufCodecs.DOUBLE.encode(buffer, value.z);
                    ByteBufCodecs.FLOAT.encode(buffer, value.yaw);
                    ByteBufCodecs.FLOAT.encode(buffer, value.pitch);
                    ByteBufCodecs.FLOAT.encode(buffer, value.head);
                    ByteBufCodecs.SHORT.encode(buffer, value.pointerX);
                    ByteBufCodecs.SHORT.encode(buffer, value.pointerY);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.frames.size());
                    for (byte[] frame : value.frames) ByteBufCodecs.byteArray(MAX_FRAME_BYTES).encode(buffer, frame);
                },
                buffer -> {
                    int entity = ByteBufCodecs.VAR_INT.decode(buffer);
                    int flags = ByteBufCodecs.VAR_INT.decode(buffer);
                    double x = ByteBufCodecs.DOUBLE.decode(buffer);
                    double y = ByteBufCodecs.DOUBLE.decode(buffer);
                    double z = ByteBufCodecs.DOUBLE.decode(buffer);
                    float yaw = ByteBufCodecs.FLOAT.decode(buffer);
                    float pitch = ByteBufCodecs.FLOAT.decode(buffer);
                    float head = ByteBufCodecs.FLOAT.decode(buffer);
                    short pointerX = ByteBufCodecs.SHORT.decode(buffer);
                    short pointerY = ByteBufCodecs.SHORT.decode(buffer);
                    int count = ByteBufCodecs.VAR_INT.decode(buffer);
                    if (count < 0 || count > MAX_FRAMES) throw new IllegalArgumentException("Too many frames: " + count);
                    List<byte[]> frames = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) frames.add(ByteBufCodecs.byteArray(MAX_FRAME_BYTES).decode(buffer));
                    return new Eyes(entity, flags, x, y, z, yaw, pitch, head, pointerX, pointerY, frames);
                });

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RaceOver(String matchId, String seed, String itemId)
            implements CustomPacketPayload {
        public static final Type<RaceOver> TYPE = new Type<>(id("race_over"));

        public static final StreamCodec<ByteBuf, RaceOver> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RaceOver::matchId,
                ByteBufCodecs.STRING_UTF8, RaceOver::seed,
                ByteBufCodecs.STRING_UTF8, RaceOver::itemId,
                RaceOver::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetTarget(String itemId) implements CustomPacketPayload {
        public static final Type<SetTarget> TYPE = new Type<>(id("set_target"));

        public static final StreamCodec<ByteBuf, SetTarget> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SetTarget::itemId,
                SetTarget::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
