package dev.railroadide.railroad.ide.language.impl.index;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;

/** Metadata change detection for explicit Java configuration refreshes. */
public final class JavaArtifactFingerprint {
    private JavaArtifactFingerprint() {
    }

    /**
     * Fingerprints ordered binary roots and JDK artifact metadata for explicit refresh detection.
     * Directory roots include metadata for their class files; file contents are not hashed.
     *
     * @param orderedRoots binary roots in classpath precedence order
     * @param jdkHome selected JDK home, or {@code null} for the running JDK; unavailable JDK archives
     *            fall back to the running JDK's module image
     * @return hexadecimal SHA-256 digest of artifact paths and metadata
     * @throws UncheckedIOException if artifact metadata cannot be read
     */
    public static String capture(List<Path> orderedRoots, Path jdkHome) {
        return capture(orderedRoots, jdkHome, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Captures artifact metadata with cooperative cancellation during directory and archive traversal.
     *
     * @param orderedRoots ordered binary roots
     * @param jdkHome selected JDK, or null for the running runtime
     * @param activityCheck immediate cancellation/deadline check
     * @return complete fingerprint; interrupted scans produce no fingerprint
     */
    public static String capture(List<Path> orderedRoots, Path jdkHome, Runnable activityCheck) {
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeInt(orderedRoots.size());
                for (Path root : orderedRoots) {
                    activityCheck.run();
                    Path normalized = normalize(root);
                    metadata(output, normalized);
                    if (Files.isDirectory(normalized)) {
                        try (var paths = Files.walk(normalized)) {
                            List<Path> classes = paths.peek(_ -> activityCheck.run()).filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(".class"))
                                .sorted().toList();
                            output.writeInt(classes.size());
                            for (Path file : classes) {
                                activityCheck.run();
                                metadata(output, file);
                            }
                        }
                    }
                }
                Path selected = normalize(jdkHome == null ? runningHome() : jdkHome);
                metadata(output, selected);
                metadata(output, selected.resolve("release"));
                List<Path> archives = jdkArchives(selected, activityCheck);
                output.writeBoolean(!archives.isEmpty());
                if (archives.isEmpty()) {
                    Path running = runningHome();
                    metadata(output, running);
                    metadata(output, running.resolve("release"));
                    archives = List.of(running.resolve("lib/modules"));
                }
                output.writeInt(archives.size());
                for (Path archive : archives) {
                    activityCheck.run();
                    metadata(output, archive);
                }
            }
            activityCheck.run();
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to fingerprint Java artifacts", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static List<Path> jdkArchives(Path home, Runnable activityCheck) throws IOException {
        for (String candidate : List.of("jre/lib/rt.jar", "lib/rt.jar")) {
            Path archive = home.resolve(candidate);
            if (Files.isRegularFile(archive))
                return List.of(archive);
        }
        Path jmods = home.resolve("jmods");
        if (Files.isDirectory(jmods)) {
            try (var paths = Files.list(jmods)) {
                return paths.peek(_ -> activityCheck.run()).filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jmod"))
                    .sorted().toList();
            }
        }
        return List.of();
    }

    private static void metadata(DataOutputStream output, Path path) throws IOException {
        output.writeUTF(normalize(path).toString());
        if (Files.notExists(path)) {
            output.writeUTF("missing");
            return;
        }
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        output.writeUTF(attributes.isRegularFile() ? "file" : attributes.isDirectory() ? "directory" : "other");
        output.writeLong(attributes.size());
        output.writeUTF(attributes.lastModifiedTime().toString());
    }

    private static Path runningHome() {
        return normalize(Path.of(System.getProperty("java.home")));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
