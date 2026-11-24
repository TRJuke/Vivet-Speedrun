package com.vivet.speedrun.client;

import com.vivet.speedrun.client.screen.RankedScreen;
import com.vivet.speedrun.p2p.RankedApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class HostedConnect {
    private static volatile ServerData current;

    private static volatile ServerData watching;
    private static volatile Screen watchedFrom;

    private HostedConnect() {
    }

    public static void join(Minecraft minecraft, Screen parent, RankedApi.HostedTicket ticket) {
        connect(minecraft, parent, ticket.host, ticket.port, true);
    }

    private static ServerData connect(Minecraft minecraft, Screen parent, String host, int port,
                                      boolean race) {
        String address = host + ":" + port;
        ServerData server = new ServerData("Vivet", address, ServerData.Type.OTHER);
        current = server;
        lastParent = parent;
        retries = 0;
        retryAt = 0L;

        ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(address), server,
                false, null);
        return server;
    }

    private static final long[] RETRY_DELAYS_MS = {3_000L, 6_000L, 12_000L, 24_000L};
    private static final long RETRY_SPREAD_MS = 1_500L;

    private static volatile Screen lastParent;
    private static volatile int retries;
    private static volatile long retryAt;

    public static boolean retryRefusedLogin(ServerData server, net.minecraft.network.chat.Component reason) {
        if (server == null || server != current) return false;

        boolean watch = watching == current;
        if (watch ? !Spectating.isActive() : !HostedLoading.isActive()) return false;
        if (!mojangSaidNotNow(reason)) return false;
        if (retries >= RETRY_DELAYS_MS.length) return false;

        long wait = RETRY_DELAYS_MS[retries++]
                + java.util.concurrent.ThreadLocalRandom.current().nextLong(RETRY_SPREAD_MS + 1L);
        retryAt = System.currentTimeMillis() + wait;
        if (watch) {
            Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.GenericMessageScreen(
                    net.minecraft.network.chat.Component.translatable("connect.connecting")));
        } else {
            HostedLoading.forgetConnection();
        }

        com.vivet.speedrun.VivetSpeedrun.LOGGER.warn("Mojang refused the server join, retrying in {}s ({}/{})",
                wait / 1000L, retries, RETRY_DELAYS_MS.length);
        return true;
    }

    public static boolean watchRetryPending() {
        return retryAt != 0L && watching != null && watching == current;
    }

    public static boolean retryDue(long now) {
        if (retryAt == 0L || now < retryAt) return false;
        retryAt = 0L;
        return true;
    }

    public static void reconnect(Minecraft minecraft) {
        ServerData server = current;
        if (server == null) return;
        if (retries == 0) {
            com.vivet.speedrun.VivetSpeedrun.LOGGER.info("Joining the server");
        } else {
            com.vivet.speedrun.VivetSpeedrun.LOGGER.info("Retrying the server join ({}/{})",
                    retries, RETRY_DELAYS_MS.length);
        }
        ConnectScreen.startConnecting(lastParent, minecraft, ServerAddress.parseString(server.ip),
                server, false, null);
    }

    private static boolean mojangSaidNotNow(net.minecraft.network.chat.Component reason) {
        if (reason == null) return false;
        if (!(reason.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents outer)
                || !"disconnect.loginFailedInfo".equals(outer.getKey())) {
            return false;
        }
        for (Object arg : outer.getArgs()) {
            if (arg instanceof net.minecraft.network.chat.Component inner
                    && inner.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key) {
                if ("disconnect.loginFailedInfo.serversUnavailable".equals(key.getKey())) return true;
                continue;
            }
            String text = arg instanceof net.minecraft.network.chat.Component inner ? inner.getString() : String.valueOf(arg);
            if (text.contains("429")) return true;
        }
        return false;
    }

    public static void watch(Minecraft minecraft, Screen from, String host, int port) {
        watchedFrom = from;
        watching = connect(minecraft, from, host, port, false);
    }

    public static boolean isRaceServer(ServerData server) {
        return server != null && server == current;
    }

    public static boolean raced() {
        return current != null;
    }

    public static void joinMatch(Minecraft minecraft, Screen parent,
                                 com.vivet.speedrun.game.Difficulty band,
                                 RankedApi.HostedTicket ticket, boolean resume) {
        if (minecraft.getUser() != null) {
            com.vivet.speedrun.session.Session.setLocalName(minecraft.getUser().getName());
        }
        boolean ranked = "ranked".equals(ticket.kind) || Boolean.TRUE.equals(ticket.ranked);

        com.vivet.speedrun.session.Session.beginHostedMatch(ticket.match, band, ranked,
                ticket.opponent, ticket.opponentUuid, resume);
        HostedRejoin.joined(ticket, band, false);
        join(minecraft, parent, ticket);
        HostedLoading.begin(minecraft, parent,
                ranked ? LoadingTakeover.Kind.RANKED : LoadingTakeover.Kind.PUBLIC);
    }

    public static Screen destination() {
        if (watching != null && watching == current && watchedFrom != null) return watchedFrom;

        boolean unranked = com.vivet.speedrun.session.Session.wasPublic()
                && !com.vivet.speedrun.session.Session.wasRanked();
        return unranked ? com.vivet.speedrun.client.screen.FindMatchScreen.returnDestination()
                : RankedScreen.returnDestination();
    }
}
