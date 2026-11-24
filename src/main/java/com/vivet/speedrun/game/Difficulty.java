package com.vivet.speedrun.game;

public enum Difficulty {
    EASY("easy", "Quick", 0xFF5FD35F),
    MEDIUM("medium", "Medium", 0xFFE8C34A),
    HARD("hard", "Hard", 0xFFE8863C),
    EXTREME("extreme", "Extreme", 0xFFE0523C),
    IMPOSSIBLE("impossible", "Impossible", 0xFFB44BE0);

    private final String id;
    private final String displayName;
    private final int color;

    Difficulty(String id, String displayName, int color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public String id() {
        return this.id;
    }

    public int color() {
        return this.color;
    }

    public String displayName() {
        return this.displayName;
    }

    public Difficulty next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public static Difficulty byId(String id) {
        for (Difficulty d : values()) {
            if (d.id.equalsIgnoreCase(id)) return d;
        }
        return MEDIUM;
    }
}
