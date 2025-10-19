package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.ChecksumService;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class MessageDigestChecksumService implements ChecksumService {
    @Override
    public String compute(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(new OutputStream() {
                @Override
                public void write(int b) {
                    digest.update((byte) b);
                }
            });
        }

        byte[] hash = digest.digest();
        return toHex(hash);
    }

    @Override
    public boolean verify(Path file, String algorithm, String expectedHex) throws Exception {
        return compute(file, algorithm).equalsIgnoreCase(expectedHex);
    }

    private static String toHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
