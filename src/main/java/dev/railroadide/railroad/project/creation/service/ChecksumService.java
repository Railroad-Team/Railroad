package dev.railroadide.railroad.project.creation.service;

import java.nio.file.Path;

/**
 * Computes and verifies checksums for files used during project creation.
 */
public interface ChecksumService {
    /**
     * Compute a checksum of the given file using the given algorithm (e.g. SHA-256).
     *
     * @param file the file whose contents are hashed
     * @param algorithm the message digest algorithm name
     * @return the checksum encoded as hexadecimal text
     * @throws Exception if the algorithm is unavailable or the file cannot be read
     */
    String compute(Path file, String algorithm) throws Exception;

    /**
     * Verify that the file matches an expected checksum.
     *
     * @param file the file to verify
     * @param algorithm the message digest algorithm name
     * @param expectedHex the expected hexadecimal checksum
     * @return {@code true} if the computed checksum matches the expected checksum
     * @throws Exception if the algorithm is unavailable or the file cannot be read
     */
    boolean verify(Path file, String algorithm, String expectedHex) throws Exception;
}
