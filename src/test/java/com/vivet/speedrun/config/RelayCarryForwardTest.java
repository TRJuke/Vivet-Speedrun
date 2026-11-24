package com.vivet.speedrun.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelayCarryForwardTest {
    @Test
    void oldDefaultsMoveToTheNewName() {
        assertEquals(ModConfig.DEFAULT_RELAY, ModConfig.carriedForward("https://relay.vivetcorp.store"));
        assertEquals(ModConfig.DEFAULT_RELAY, ModConfig.carriedForward("https://relay.vivetcorp.store/"));
        assertEquals(ModConfig.DEFAULT_RELAY, ModConfig.carriedForward("  https://Relay.VivetCorp.store  "));
        assertEquals(ModConfig.DEFAULT_RELAY, ModConfig.carriedForward("wss://relay.vivetcorp.store"));
        assertEquals(ModConfig.DEFAULT_RELAY, ModConfig.carriedForward("https://vivet-relay.vivetcorp.workers.dev"));
    }

    @Test
    void anAddressSetOnPurposeStays() {
        assertEquals("https://my.own.relay", ModConfig.carriedForward("https://my.own.relay"));
        assertEquals("ws://127.0.0.1:8099", ModConfig.carriedForward("ws://127.0.0.1:8099"));
        assertEquals("https://relay.vivetcorp.store.example", ModConfig.carriedForward("https://relay.vivetcorp.store.example"));
        assertEquals(ModConfig.DEFAULT_RELAY, ModConfig.carriedForward(ModConfig.DEFAULT_RELAY));
    }
}
