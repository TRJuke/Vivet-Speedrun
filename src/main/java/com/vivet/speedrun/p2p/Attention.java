package com.vivet.speedrun.p2p;

import net.minecraft.client.Minecraft;

public final class Attention {
    private Attention() {
    }

    public static boolean somebodyIsLooking() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.isWindowActive();
    }

    public static long interval(long watched, long away) {
        return somebodyIsLooking() ? watched : away;
    }
}
