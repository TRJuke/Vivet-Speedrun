package com.vivet.speedrun.media;

public final class Media {
    public static final String CAUGHT = "easteregg.caught";

    public static final String VINE_BOOM = "cue.vine_boom";

    public static final String BELLEND = "cue.bellend";

    public static String clipOf(String labelId) {
        return "label." + labelId + ".clip";
    }

    public static String soundOf(String labelId) {
        return "label." + labelId + ".sound";
    }

    public static String backgroundOf(String setId) {
        return "background." + setId;
    }

    private Media() {
    }

    public static void tick() {
        MediaImages.prepare(CAUGHT);
        MediaSounds.prepare(VINE_BOOM);
        MediaSounds.prepare(BELLEND);

        for (com.vivet.speedrun.client.ui.NameLabel label
                : com.vivet.speedrun.client.ui.NameLabel.values()) {
            if (label == com.vivet.speedrun.client.ui.NameLabel.RANK) continue;

            MediaClips.prepare(clipOf(label.id()));
            MediaSounds.prepare(soundOf(label.id()));
        }

        MediaImages.tick();
        MediaClips.tick();
        MediaSounds.tick();
    }
}
