package com.vivet.speedrun.client.ui;

public final class Bar {
    private final int gap;
    private int leftCursor;
    private int rightCursor;

    public Bar(int x, int width, int gap) {
        this.leftCursor = x;
        this.rightCursor = x + width;
        this.gap = gap;
    }

    public int left(int width) {
        int x = this.leftCursor;
        this.leftCursor += width + this.gap;
        return x;
    }

    public int right(int width) {
        this.rightCursor -= width;
        int x = this.rightCursor;
        this.rightCursor -= this.gap;
        return x;
    }

    public int remaining() {
        return this.rightCursor - this.leftCursor;
    }

    public boolean fits(int width) {
        return remaining() >= width;
    }
}
