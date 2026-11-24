package com.vivet.speedrun.client.ui;

import net.minecraft.network.chat.Component;

public final class Plural {
    private Plural() {
    }

    public static Component of(int count, String key) {
        return Component.translatable(count == 1 ? key + ".one" : key, count);
    }
}
