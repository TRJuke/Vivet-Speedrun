package com.vivet.speedrun.client;

import com.vivet.speedrun.media.Media;
import com.vivet.speedrun.media.MediaSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public final class Cues {
    private record Note(SoundEvent sound, float volume, float pitch, int delayTicks) {
    }

    private record Pending(Note note, long dueTick) {
    }

    private static final List<Pending> pending = new ArrayList<>();
    private static long clientTick;

    private Cues() {
    }

    public static void queuePop() {
        play(
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 1.5F, 3),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 2.0F, 6),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 2.0F, 9));
    }

    public static void tutorialGo() {
        play(
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.26F, 2),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 1.5F, 4),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 2.0F, 7));
    }

    public static void queueJoined() {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.6F, 0));
    }

    public static void queueWaiting() {
        play(
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.0F, 4));
    }

    public static void queueLeft() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.8F, 3));
    }

    public static void itemDrop() {
        play(
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.6F, 2),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.9F, 2.0F, 4),
                note(SoundEvents.UI_TOAST_IN, 0.5F, 1.4F, 4));
    }

    public static void matchWon() {
        play(
                note(SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.5F, 3),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9F, 2.0F, 6),
                note(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F, 8));
    }

    public static void matchLost() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.75F, 4),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6F, 0.6F, 8));
    }

    public static void matchEnded() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 1.0F, 5));
    }

    public static void rivalMoved() {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35F, 1.9F, 0));
    }

    public static void rankUp() {
        play(
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8F, 1.6F, 2),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9F, 2.0F, 4),
                note(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.2F, 4));
    }

    public static void rankDown() {
        play(
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.9F, 2),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F, 5));
    }

    public static void eloGranted(boolean up) {
        if (up) {
            play(
                    note(SoundEvents.IRON_GOLEM_REPAIR, 0.8F, 1.3F, 0),
                    note(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.6F, 1.8F, 3));
            return;
        }
        play(
                note(SoundEvents.IRON_GOLEM_REPAIR, 0.8F, 0.7F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F, 3));
    }

    public static void eloFlat() {
        play(note(SoundEvents.IRON_GOLEM_REPAIR, 0.6F, 1.0F, 0));
    }

    public static void xpGranted() {
        play(
                note(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.0F, 0),
                note(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.3F, 2));
    }

    public static void coinsGranted() {
        play(
                note(SoundEvents.ITEM_PICKUP, 0.5F, 1.4F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.8F, 1),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 2.0F, 3));
    }

    public static void discordLinked() {
        coinsGranted();
        play(
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.5F, 5),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 2.0F, 8));
    }

    public static void linkCode() {
        play(
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5F, 1.4F, 0),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5F, 1.8F, 2));
    }

    public static void linkCodeShown(boolean shown) {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, shown ? 1.6F : 1.1F, 0));
    }

    public static void linkCodeExpired() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.75F, 3));
    }

    public static void bought() {
        play(
                note(SoundEvents.ITEM_PICKUP, 0.5F, 0.9F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.5F, 2),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.6F, 5),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 2.0F, 8),
                note(SoundEvents.PLAYER_LEVELUP, 0.5F, 1.4F, 8));
    }

    public static void buyHold(int quarter) {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35F, 0.9F + quarter * 0.2F, 0));
    }

    public static void levelUp() {
        play(
                note(SoundEvents.PLAYER_LEVELUP, 0.8F, 1.1F, 0),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 2.0F, 3));
    }

    public static void collected() {
        play(
                note(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F, 8),
                note(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.7F, 1.5F, 10),
                note(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.8F, 2.0F, 13));
    }

    public static void achievement() {
        play(
                note(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.35F, 1.2F, 8),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.5F, 10),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 2.0F, 13));
    }

    public static void questDone() {
        play(
                note(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.6F, 1.3F, 0),
                note(SoundEvents.ITEM_PICKUP, 0.5F, 1.4F, 3),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.6F, 5),
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 2.0F, 8));
    }

    public static void questFold() {
        play(
                note(SoundEvents.BOOK_PAGE_TURN, 0.7F, 0.9F, 0),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.2F, 1),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35F, 0.9F, 3));
    }

    public static void questUnfold() {
        play(
                note(SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35F, 0.9F, 1),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.2F, 3));
    }

    public static void questReroll() {
        play(
                note(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F, 0),
                note(SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.4F, 3),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.8F, 5));
    }

    public static void claimed() {
        play(
                note(SoundEvents.ITEM_PICKUP, 0.5F, 1.2F, 0),
                note(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.4F, 1),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.8F, 3),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 2.2F, 5));
    }

    public static void practiceBest() {
        play(
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.5F, 3),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 2.0F, 6));
    }

    public static void nextReady() {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.5F, 1.5F, 0));
    }

    public static void badNews() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.6F, 4));
    }

    public static void opponentLeft() {
        play(
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.7F, 0.9F, 0),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.7F, 0.9F, 6));
    }

    public static void carryOnStarted() {
        play(
                note(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.7F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.7F, 1.4F, 3),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.6F, 6));
    }

    public static void carryOnLow() {
        play(
                note(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 1.2F, 5));
    }

    public static void carryOnGone() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.8F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F, 5),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6F, 0.5F, 10));
    }

    public static void opponentReturned() {
        play(
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 1.8F, 3));
    }

    public static void watchStarted() {
        play(
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.6F, 1.0F, 0),
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.6F, 1.5F, 3),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 2.0F, 6));
    }

    public static void spectateTeleport() {
        play(note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.5F, 2.0F, 0));
    }

    public static void watchSwitched() {
        play(
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.5F, 1.4F, 0),
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.5F, 1.8F, 2));
    }

    public static void watchEnded() {
        play(
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.6F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.6F, 1.2F, 4),
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F, 8));
    }

    public static void recordTick(int centisLeft, boolean offbeat) {
        float urgency = Math.max(0.0F, Math.min(1.0F, (1000 - centisLeft) / 900.0F));

        float volume = 0.35F + 0.35F * urgency;
        float pitch = (offbeat ? 1.1F : 1.5F) + 0.35F * urgency;

        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), volume, pitch, 0));
    }

    public static void recordGone() {
        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.9F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F, 4));
    }

    public static void confirm() {
        play(note(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.3F, 0));
    }

    public static void deny() {
        play(note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F, 0));
    }

    public static void markedRead() {
        play(
                note(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.2F, 0),
                note(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 1.7F, 2));
    }

    public static void discarded() {
        play(
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.45F, 1.3F, 0),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 0.9F, 2));
    }

    public static void unfolded(boolean opening) {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, opening ? 1.4F : 1.0F, 0));
    }

    public static void swept() {
        play(
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.5F, 1.5F, 0),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.1F, 2),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 0.8F, 4));
    }

    public static void replayOpened() {
        play(
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.45F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.45F, 1.6F, 2),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 1.4F, 4));
    }

    public static void replayEnded() {
        play(
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4F, 1.2F, 0),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 0.9F, 3));
    }

    public static void wompWomp() {
        play(
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.9F, 0.80F, 0),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.9F, 0.72F, 5),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.9F, 0.64F, 10),

                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.85F, 0.60F, 16),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.80F, 0.56F, 19),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.75F, 0.53F, 22),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.70F, 0.51F, 25),
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.65F, 0.50F, 28));

        MediaSounds.play(Media.VINE_BOOM, 1.0F);
    }

    public static void nothingHappened() {
        play(note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.25F, 0.7F, 0));
    }

    public static void introSwoop() {
        play(note(SoundEvents.BREEZE_SHOOT, 0.8F, 1.0F, 0));
    }

    public static void introCard(int index) {
        float pitch = 0.85F + 0.26F * Math.min(index, 3);

        play(
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.62F, pitch * 0.7F, 0),
                note(SoundEvents.NOTE_BLOCK_HAT.value(), 0.32F, 1.8F, 0),
                note(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.52F, pitch, 1));
    }

    public static void introVersusFall() {
        play(note(SoundEvents.WIND_CHARGE_THROW, 0.5F, 0.8F, 0));
    }

    public static void introVersus() {
        play(
                note(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.55F, 1.05F, 0),
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.60F, 0.5F, 0));
    }

    public static void introWaiting(boolean offbeat) {
        play(note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.20F, offbeat ? 0.55F : 0.70F, 0));
    }

    public static void introOut() {
        clear();
        MediaSounds.play(Media.BELLEND, 1.0F);
    }

    private static Note note(SoundEvent sound, float volume, float pitch, int delayTicks) {
        return new Note(sound, volume, pitch, delayTicks);
    }

    private static void play(Note... notes) {
        synchronized (pending) {
            for (Note note : notes) {
                if (note.delayTicks() <= 0) {
                    start(note);
                } else {
                    pending.add(new Pending(note, clientTick + note.delayTicks()));
                }
            }
        }
    }

    public static void tick() {
        synchronized (pending) {
            clientTick++;
            if (pending.isEmpty()) return;

            pending.removeIf(waiting -> {
                if (waiting.dueTick() > clientTick) return false;
                start(waiting.note());
                return true;
            });
        }
    }

    public static void clear() {
        synchronized (pending) {
            pending.clear();
        }

        MediaSounds.stopAll();
    }

    private static void start(Note note) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) return;

        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(note.sound(), note.pitch(), note.volume()));
    }
}
