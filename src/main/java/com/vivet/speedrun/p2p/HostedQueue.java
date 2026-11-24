package com.vivet.speedrun.p2p;

import com.vivet.speedrun.game.Difficulty;

public final class HostedQueue {
    private static volatile boolean waiting;
    private static volatile int position;
    private static volatile Difficulty band = Difficulty.HARD;
    private static volatile boolean setSeed;
    private static volatile RankedApi.HostedTicket ready;
    private static volatile String failed;

    private HostedQueue() {
    }

    public static void joined(int place, Difficulty racing, boolean onSetSeed) {
        position = Math.max(1, place);
        band = racing;
        setSeed = onSetSeed;
        ready = null;
        failed = null;
        waiting = true;
    }

    public static void leave() {
        if (!waiting) return;
        waiting = false;
        ready = null;
        RankedApi.leaveHostedQueue();
    }

    public static boolean waiting() {
        return waiting;
    }

    public static int position() {
        return position;
    }

    public static RankedApi.HostedTicket takeReady() {
        RankedApi.HostedTicket ticket = ready;
        ready = null;
        return ticket;
    }

    public static String takeFailure() {
        String reason = failed;
        failed = null;
        return reason;
    }

    static void place(int place, String error) {
        if (!waiting) return;
        if (error != null && !error.isBlank()) {
            waiting = false;
            failed = error;
            return;
        }
        if (place > 0) position = place;
    }

    static void turn(String match, String host, int port) {
        if (!waiting) return;
        RankedApi.HostedTicket ticket = new RankedApi.HostedTicket();
        ticket.match = match;
        ticket.host = host;
        ticket.port = port;
        ticket.band = band.id();
        ticket.setSeed = setSeed;
        waiting = false;
        ready = ticket;
    }
}
