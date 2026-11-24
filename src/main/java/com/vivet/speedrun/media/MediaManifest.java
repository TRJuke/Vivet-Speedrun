package com.vivet.speedrun.media;

import com.vivet.speedrun.VivetSpeedrun;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

public final class MediaManifest {
    private static final String MAGIC = "vivet-media-1";

    private static final long MAX_ENTRY_BYTES = 16L * 1024 * 1024;

    private static final int MAX_ENTRIES = 256;

    public static final class Entry {
        public String id;
        public String url;
        public String sha256;
        public Long size;

        public String kind;
    }

    public Long generatedAt;
    public List<Entry> entries;
    public String signature;

    public boolean verify(String publicKeyBase64) {
        if (this.generatedAt == null || this.signature == null || this.signature.isBlank()) {
            return refuse("incomplete");
        }
        if (this.entries == null || this.entries.isEmpty()) return refuse("empty");
        if (this.entries.size() > MAX_ENTRIES) return refuse("too many entries");

        for (Entry entry : this.entries) {
            if (isBlank(entry.id) || isBlank(entry.url) || isBlank(entry.sha256)
                    || entry.size == null) {
                return refuse("incomplete entry");
            }

            if (!entry.url.startsWith("https://")) return refuse("not https");

            if (!entry.sha256.matches("[0-9a-fA-F]{64}")) return refuse("bad hash");
            if (entry.size <= 0 || entry.size > MAX_ENTRY_BYTES) {
                return refuse("bad size for " + entry.id);
            }

            if (!entry.id.matches("[a-z0-9]+(?:[._][a-z0-9]+)*")) {
                return refuse("bad id");
            }
        }

        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(signedForm().getBytes(StandardCharsets.UTF_8));

            if (!verifier.verify(Base64.getDecoder().decode(this.signature))) {
                return refuse("bad signature");
            }
            return true;
        } catch (Exception e) {
            return refuse("could not check signature (" + e.getClass().getSimpleName() + ")");
        }
    }

    private String signedForm() {
        List<Entry> sorted = new ArrayList<>(this.entries);
        sorted.sort(Comparator.comparing(entry -> entry.id));

        StringBuilder canonical = new StringBuilder(MAGIC).append('\n').append(this.generatedAt);
        for (Entry entry : sorted) {
            canonical.append('\n')
                    .append(entry.id).append('\t')
                    .append(entry.url).append('\t')
                    .append(entry.sha256).append('\t')
                    .append(entry.size).append('\t')
                    .append(entry.kind == null ? "" : entry.kind);
        }
        return canonical.toString();
    }

    private static boolean refuse(String why) {
        VivetSpeedrun.LOGGER.warn("Media list rejected: {}", why);
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
