package tech.kayys.aqli.cli.commands;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Shared file-integrity operations for generated media artifacts and their sidecars. */
public final class MediaFileIntegrity {

    private MediaFileIntegrity() {
    }

    public static String sha256Hex(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        char[] table = "0123456789abcdef".toCharArray();
        byte[] bytes = digest.digest();
        char[] output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            output[i * 2] = table[value >>> 4];
            output[i * 2 + 1] = table[value & 0x0f];
        }
        return new String(output);
    }
}
