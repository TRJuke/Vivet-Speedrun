package com.vivet.speedrun.replay;

import java.util.List;

public final class LoadedReplay {
    public record View(ReplayMatch.Pov pov, ReplayTrace trace, boolean world, int protocol) {
        public String name() {
            return pov.name == null || pov.name.isEmpty() ? "Player" : pov.name;
        }

        public String uuid() {
            return pov.uuid == null ? "" : pov.uuid;
        }
    }

    private final ReplayMatch match;
    private final List<View> views;

    public LoadedReplay(ReplayMatch match, List<View> views) {
        this.match = match;
        this.views = List.copyOf(views);
    }

    public ReplayMatch match() {
        return match;
    }

    public List<View> views() {
        return views;
    }

    public Long seed() {
        for (View view : views) {
            if (view.trace().seed() != null) return view.trace().seed();
        }
        return null;
    }

    public boolean hasWorld() {
        for (View view : views) {
            if (view.world()) return true;
        }
        return false;
    }

    public boolean worldFromAnotherVersion(int protocol) {
        boolean any = false;
        for (View view : views) {
            if (!view.world()) continue;
            if (view.protocol() == 0 || view.protocol() == protocol) return false;
            any = true;
        }
        return any;
    }

    public int duration() {
        int longest = 1;
        for (View view : views) longest = Math.max(longest, view.trace().endCentis());
        return longest;
    }
}
