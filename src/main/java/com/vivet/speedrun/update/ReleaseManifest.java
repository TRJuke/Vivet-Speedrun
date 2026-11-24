package com.vivet.speedrun.update;

import com.vivet.speedrun.VivetSpeedrun;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class ReleaseManifest {
    public String version;
    public String url;
    public String sha256;
    public Long size;
    public String signature;
    public String notes;

    public transient String sha512;

    private static final long MAX_SIZE_BYTES = 64L * 1024 * 1024;

    public boolean verify(String publicKeyBase64) {
        if (isBlank(this.version) || isBlank(this.url) || isBlank(this.sha256)
                || isBlank(this.signature) || this.size == null) {
            return refuse("incomplete");
        }

        if (!this.url.startsWith("https://")) return refuse("not https");

        if (!this.sha256.matches("[0-9a-fA-F]{64}")) return refuse("bad hash");
        if (this.size <= 0 || this.size > MAX_SIZE_BYTES) return refuse("bad size");

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
        return this.version + "\n" + this.url + "\n" + this.sha256 + "\n" + this.size;
    }

    private static boolean refuse(String why) {
        VivetSpeedrun.LOGGER.warn("Update info rejected: {}", why);
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
