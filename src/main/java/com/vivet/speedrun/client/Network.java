package com.vivet.speedrun.client;

import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.net.Payloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class Network {
    private Network() {
    }

    public static void revealDone() {
        send(Payloads.Action.REVEAL_DONE);
    }

    public static void ready() {
        send(Payloads.Action.READY);
    }

    public static void started() {
        send(Payloads.Action.STARTED);
    }

    public static void toggleRun() {
        send(Payloads.Action.TOGGLE_RUN);
    }

    public static void reroll() {
        send(Payloads.Action.REROLL);
    }

    public static void spectate() {
        send(Payloads.Action.SPECTATE);
    }

    public static void teleportToRacer() {
        send(Payloads.Action.TELEPORT);
    }

    public static void nextPlayer() {
        send(Payloads.Action.NEXT_PLAYER);
    }

    public static void forfeit() {
        send(Payloads.Action.FORFEIT);
    }

    public static void draw() {
        send(Payloads.Action.DRAW);
    }

    public static void playAgain() {
        send(Payloads.Action.PLAY_AGAIN);
    }

    public static void winByDefault() {
        send(Payloads.Action.WIN_BY_DEFAULT);
    }

    public static void loseRace() {
        send(Payloads.Action.LOSE_RACE);
    }

    public static void applyModifiers() {
        send(Payloads.Action.APPLY_MODIFIERS);
    }

    public static void startRun() {
        send(Payloads.Action.PLAY_AGAIN);
    }

    public static boolean startShared(String itemId, Difficulty difficulty) {
        if (!ClientPlayNetworking.canSend(Payloads.StartShared.TYPE)) return false;

        ClientPlayNetworking.send(new Payloads.StartShared(itemId, difficulty));
        return true;
    }

    public static void setTarget(String itemId) {
        if (ClientPlayNetworking.canSend(Payloads.SetTarget.TYPE)) {
            ClientPlayNetworking.send(new Payloads.SetTarget(itemId));
        }
    }

    private static void send(Payloads.Action action) {
        if (ClientPlayNetworking.canSend(Payloads.RunAction.TYPE)) {
            ClientPlayNetworking.send(new Payloads.RunAction(action));
        }
    }
}
