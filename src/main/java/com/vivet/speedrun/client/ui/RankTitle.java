package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.p2p.MojangAuth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RankTitle {
    public static final String UNRANKED = "unranked";

    private static final int KEEP = 256;

    private static final Map<String, String> KNOWN = new LinkedHashMap<>(32, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > KEEP;
        }
    };

    private RankTitle() {
    }

    public static void remember(String name, String uuid, String rankId, boolean placed) {
        if (name == null || name.isBlank()) return;

        String rank = placed && rankId != null && !rankId.isBlank() ? rankId : UNRANKED;

        if (placed && uuid != null && !uuid.isBlank()) {
            String title = Ranks.speedTitle(uuid);
            if (title != null) rank = title;
        }

        KNOWN.put(name.toLowerCase(Locale.ROOT), rank);
    }

    public static String of(String name) {
        if (name == null || name.isBlank()) return null;

        MojangAuth.Profile me = MojangAuth.knownProfile();
        if (me != null && name.equalsIgnoreCase(me.name)) {
            return me.placed ? Ranks.displayRank(me) : UNRANKED;
        }

        return KNOWN.get(name.toLowerCase(Locale.ROOT));
    }

    public static String tagFor(String name) {
        String rank = of(name);
        if (rank == null) return null;

        return Ranks.nameOf(rank).toUpperCase(Locale.ROOT);
    }

    public static int colourFor(String name) {
        String rank = of(name);

        if (rank == null) return Ranks.colorOf(UNRANKED);

        return Ranks.colorOf(rank);
    }

    public static boolean unranked(String name) {
        return UNRANKED.equals(of(name));
    }
}
