package com.vivet.speedrun.p2p;

import java.util.Locale;

public final class InviteCode {
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String PREFIX = "VIVET";
    private static final int LENGTH = 10;

    private InviteCode() {
    }

    public static String normalize(String input) {
        if (input == null) return null;

        String cleaned = input.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (cleaned.startsWith(PREFIX)) cleaned = cleaned.substring(PREFIX.length());

        if (cleaned.length() != LENGTH) return null;

        StringBuilder folded = new StringBuilder(LENGTH);
        for (int i = 0; i < cleaned.length(); i++) {
            char character = fold(cleaned.charAt(i));
            if (ALPHABET.indexOf(character) < 0) return null;
            folded.append(character);
        }

        return PREFIX + "-" + folded.substring(0, 5) + "-" + folded.substring(5, 10);
    }

    private static char fold(char character) {
        return switch (character) {
            case 'I', 'L' -> '1';
            case 'O' -> '0';
            case 'U' -> 'V';
            default -> character;
        };
    }
}
