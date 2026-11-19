package dev.railroadide.railroad.ide.language.impl.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JavaArtifactFingerprintTest {
    @TempDir
    public Path root;

    @Test
    public void samePathArchiveMetadataReplacementChangesIdentity() throws Exception {
        Path jar = write("dependency.jar", "v1");
        Path jdk = jdk("jdk");
        String first = JavaArtifactFingerprint.capture(List.of(jar), jdk);
        assertEquals(first, JavaArtifactFingerprint.capture(List.of(jar), jdk));

        Files.writeString(jar, "version two");

        assertNotEquals(first, JavaArtifactFingerprint.capture(List.of(jar), jdk));
    }

    @Test
    public void directoryTraversalObservesCancellationBeforeProducingAFingerprint() throws Exception {
        write("classes/demo/A.class", "one");
        write("classes/demo/B.class", "two");
        Path jdk = jdk("jdk");
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> JavaArtifactFingerprint.capture(
            List.of(root.resolve("classes")), jdk, () -> {
                if (checks.incrementAndGet() == 4)
                    throw new CancellationException();
            }));
        assertEquals(4, checks.get());
        assertNotNull(JavaArtifactFingerprint.capture(List.of(root.resolve("classes")), jdk));
    }

    @Test
    public void dependencyOrderContributesToIdentity() throws Exception {
        Path first = write("first.jar", "one");
        Path second = write("second.jar", "two");
        Path jdk = jdk("jdk");

        assertNotEquals(JavaArtifactFingerprint.capture(List.of(first, second), jdk),
            JavaArtifactFingerprint.capture(List.of(second, first), jdk));
    }

    @Test
    public void classDirectoryManifestDetectsNestedFileChangeWithoutDirectoryTimestampChange() throws Exception {
        Path file = write("classes/demo/A.class", "one");
        Path directory = root.resolve("classes");
        Path jdk = jdk("jdk");
        FileTime directoryTime = Files.getLastModifiedTime(directory);
        FileTime fileTime = Files.getLastModifiedTime(file);
        String first = JavaArtifactFingerprint.capture(List.of(directory), jdk);

        Files.writeString(file, "two");
        Files.setLastModifiedTime(file, FileTime.fromMillis(fileTime.toMillis() + 2000));
        Files.setLastModifiedTime(directory, directoryTime);

        assertNotEquals(first, JavaArtifactFingerprint.capture(List.of(directory), jdk));
    }

    @Test
    public void missingRootBecomingPresentChangesIdentity() throws Exception {
        Path dependency = root.resolve("missing.jar");
        Path jdk = jdk("jdk");
        String first = JavaArtifactFingerprint.capture(List.of(dependency), jdk);

        Files.writeString(dependency, "present");

        assertNotEquals(first, JavaArtifactFingerprint.capture(List.of(dependency), jdk));
    }

    @Test
    public void selectedJdkAndSameHomeModuleReplacementChangeIdentity() throws Exception {
        Path firstJdk = jdk("firstJdk");
        Path secondJdk = jdk("secondJdk");
        String first = JavaArtifactFingerprint.capture(List.of(), firstJdk);
        assertNotEquals(first, JavaArtifactFingerprint.capture(List.of(), secondJdk));

        Files.writeString(firstJdk.resolve("jmods/java.base.jmod"), "replacement module");

        assertNotEquals(first, JavaArtifactFingerprint.capture(List.of(), firstJdk));
    }

    @Test
    public void jdkReleaseAndLegacyArchiveContributeToIdentity() throws Exception {
        Path jdk = root.resolve("legacyJdk");
        write("legacyJdk/release", "release one");
        write("legacyJdk/jre/lib/rt.jar", "runtime one");
        String first = JavaArtifactFingerprint.capture(List.of(), jdk);

        Files.writeString(jdk.resolve("release"), "release version two");
        String second = JavaArtifactFingerprint.capture(List.of(), jdk);
        assertNotEquals(first, second);

        Files.writeString(jdk.resolve("jre/lib/rt.jar"), "runtime version two");

        assertNotEquals(second, JavaArtifactFingerprint.capture(List.of(), jdk));
    }

    private Path jdk(String name) throws Exception {
        write(name + "/release", "fake release");
        write(name + "/jmods/java.base.jmod", "fake module");
        return root.resolve(name);
    }

    private Path write(String relative, String contents) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
        return path;
    }
}
