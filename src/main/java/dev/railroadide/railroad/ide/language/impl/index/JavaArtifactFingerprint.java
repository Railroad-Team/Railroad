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

/** Metadata change detection for explicit Java configuration refreshes. */
public final class JavaArtifactFingerprint {
    private JavaArtifactFingerprint() {
    }

    public static String capture(List<Path> orderedRoots, Path jdkHome) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeInt(orderedRoots.size());
                for (Path root : orderedRoots) {
                    Path normalized = normalize(root);
                    metadata(output, normalized);
                    if (Files.isDirectory(normalized)) {
                        try (var paths = Files.walk(normalized)) {
                            List<Path> classes = paths.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(".class"))
                                .sorted().toList();
                            output.writeInt(classes.size());
                            for (Path file : classes) {
                                metadata(output, file);
                            }
                        }
                    }
                }
                Path selected = normalize(jdkHome == null ? runningHome() : jdkHome);
                metadata(output, selected);
                metadata(output, selected.resolve("release"));
                List<Path> archives = jdkArchives(selected);
                output.writeBoolean(!archives.isEmpty());
                if (archives.isEmpty()) {
                    Path running = runningHome();
                    metadata(output, running);
                    metadata(output, running.resolve("release"));
                    archives = List.of(running.resolve("lib/modules"));
                }
                output.writeInt(archives.size());
                for (Path archive : archives) {
                    metadata(output, archive);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to fingerprint Java artifacts", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static List<Path> jdkArchives(Path home) throws IOException {
        for (String candidate : List.of("jre/lib/rt.jar", "lib/rt.jar")) {
            Path archive = home.resolve(candidate);
            if (Files.isRegularFile(archive))
                return List.of(archive);
        }
        Path jmods = home.resolve("jmods");
        if (Files.isDirectory(jmods)) {
            try (var paths = Files.list(jmods)) {
                return paths.filter(Files::isRegularFile)
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
