package com.vivet.speedrun.replay;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReplayChatTest {
    private static final char SECTION = (char) 0xA7;

    @Test
    void sectionSignsAndControlCharactersAreStripped() {
        ReplayTrace.Sample start = new ReplayTrace.Sample(0, 0, 64, 0, 0, 0, 0, 20, 20, 300, 0, 0, 0,
                ReplayEvent.START, Map.of(), List.of());
        String said = "a" + SECTION + "cb" + (char) 7 + "c" + SECTION + "ld";
        byte[] bytes = ReplayTrace.of(List.of(start), List.of(ReplayTrace.Moment.chat(10, "Steve", said)), null)
                .encodeBytes();
        assertNotNull(bytes);

        ReplayTrace back = ReplayTrace.decodeBytes(bytes, ReplayTrace.MAX_FILE_BYTES * 4);
        assertNotNull(back);

        assertEquals("acbcld", back.moments().get(0).text());
    }
}
