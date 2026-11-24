package com.vivet.speedrun.replay;

public enum ReplayEvent {
    SAMPLE,

    START,

    DIMENSION,

    DIED,

    MILESTONE,

    FOUND,

    END;

    private static final ReplayEvent[] VALUES = values();

    public byte index() {
        return (byte) ordinal();
    }

    public static ReplayEvent of(byte value) {
        int index = value & 0xFF;
        return index >= 0 && index < VALUES.length ? VALUES[index] : SAMPLE;
    }
}
