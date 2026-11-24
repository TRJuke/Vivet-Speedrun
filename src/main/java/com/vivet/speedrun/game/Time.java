package com.vivet.speedrun.game;

public final class Time {
    public static final int CENTIS_PER_SECOND = 100;

    private Time() {
    }

    public static int toSeconds(int centis) {
        return Math.max(0, centis) / CENTIS_PER_SECOND;
    }

    public static int toCentis(int seconds) {
        return Math.max(0, seconds) * CENTIS_PER_SECOND;
    }

    public static String formatPrecise(int totalCentis) {
        if (totalCentis < 0) totalCentis = 0;

        int hundredths = totalCentis % CENTIS_PER_SECOND;
        return format(totalCentis / CENTIS_PER_SECOND) + ".%02d".formatted(hundredths);
    }

    public static String format(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, seconds);
        }
        return "%d:%02d".formatted(minutes, seconds);
    }
}
