package dev.railroadide.core.project.creation.service;

import java.nio.file.Path;

public interface ChecksumService {
    /**
     * Compute a checksum of the given file using the given algorithm (e.g. SHA-256).
     */
    String compute(Path file, String algorithm) throws Exception;

    /**
     * Verify that the file matches an expected checksum.
     */
    boolean verify(Path file, String algorithm, String expectedHex) throws Exception;
}
