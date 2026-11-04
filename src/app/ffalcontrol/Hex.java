package app.ffalcontrol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lowercase hex. SHA-256 digests are compared with client-supplied
 * {@code expected_sha256} values, which must match {@code [0-9a-f]{64}}.
 */
final class Hex {
    private Hex() {}

    static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        return encode(digest);
    }

    static String encode(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >> 4) & 0xf, 16));
            out.append(Character.forDigit(value & 0xf, 16));
        }
        return out.toString();
    }
}
