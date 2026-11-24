package com.vivet.speedrun.game;

public enum DimensionPool {
    ANY("any", "Off"),
    NETHER("nether", "On");

    private final String id;
    private final String title;

    DimensionPool(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() {
        return this.id;
    }

    public DimensionPool next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public boolean isNetherOnly() {
        return this == NETHER;
    }

    public static DimensionPool byId(String id) {
        for (DimensionPool pool : values()) {
            if (pool.id.equalsIgnoreCase(id)) return pool;
        }
        return ANY;
    }
}
