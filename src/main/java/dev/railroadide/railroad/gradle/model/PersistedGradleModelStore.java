package dev.railroadide.railroad.gradle.model;

import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroadplugin.dto.FabricDataModel;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import dev.railroadide.railroadplugin.dto.RailroadProject;
import dev.railroadide.railroadplugin.dto.impl.BasicFabricDataModel;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Persists the last imported {@link GradleBuildModel} of each project, so reopening a project can publish its
 * classpath immediately instead of waiting for a Gradle import.
 * <p>
 * Each snapshot records the content hash of the build inputs it was imported from. A restored snapshot is
 * {@linkplain Restored#current() current} while those inputs are unchanged and its dependency archives still exist.
 * Otherwise it is still returned so callers can use it until a fresh import completes. Snapshots written by a
 * different importer are discarded, because their model classes may not match.
 */
public final class PersistedGradleModelStore {
    private static final String MAGIC = "railroad-gradle-model";
    private static final int FORMAT_VERSION = 1;
    private static final String ABSENT = "-";
    private static final List<String> MODULE_FILES = List.of("build.gradle", "build.gradle.kts", "gradle.properties");
    private static final List<String> ROOT_FILES = List.of(
        "settings.gradle",
        "settings.gradle.kts",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradle/libs.versions.toml",
        "buildSrc/build.gradle",
        "buildSrc/build.gradle.kts",
        "buildSrc/settings.gradle",
        "buildSrc/settings.gradle.kts");
    private static final List<String> MODEL_PACKAGES = List.of("org.gradle.", "dev.railroadide.railroadplugin.");
    private static final ObjectInputFilter MODEL_FILTER = ObjectInputFilter.rejectUndecidedClass(
        ObjectInputFilter.merge(
            PersistedGradleModelStore::filterModelProxy,
            ObjectInputFilter.Config.createFilter(
                "java.lang.*;java.lang.reflect.Proxy;java.util.*;java.io.File;org.gradle.**;dev.railroadide.railroadplugin.**")));

    private final Path directory;
    private final String importerVersion;

    /**
     * Creates a store that keeps one snapshot per project in {@code directory}.
     *
     * @param directory the directory holding the snapshots
     * @param importerVersion identifies the importer that produces models; snapshots from another importer are
     *            discarded
     */
    public PersistedGradleModelStore(Path directory, String importerVersion) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.importerVersion = Objects.requireNonNull(importerVersion, "importerVersion");
    }

    /**
     * Loads the snapshot of a project.
     *
     * @param projectRoot the root directory of the Gradle build
     * @return the restored model, or empty when there is no usable snapshot
     * @throws IOException if the snapshot exists but cannot be read or is malformed
     */
    public Optional<Restored> load(Path projectRoot) throws IOException {
        Path root = canonical(projectRoot);
        Path snapshot = snapshotPath(root);
        if (Files.notExists(snapshot))
            return Optional.empty();

        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(snapshot)))) {
            if (!MAGIC.equals(input.readUTF()) || input.readInt() != FORMAT_VERSION
                || !importerVersion.equals(input.readUTF()) || !root.toString().equals(input.readUTF()))
                return Optional.empty();

            int inputCount = input.readInt();
            boolean current = true;
            for (int index = 0; index < inputCount; index++) {
                Path file = Path.of(input.readUTF());
                String hash = input.readUTF();
                if (current && !hash.equals(hash(file))) {
                    current = false;
                }
            }

            GradleBuildModel model = readModel(input);
            return Optional.of(new Restored(model, current && archivesExist(model)));
        } catch (ClassNotFoundException | ClassCastException | InvalidClassException exception) {
            throw new IOException("Malformed Gradle model snapshot " + snapshot, exception);
        }
    }

    /**
     * Saves a freshly imported model as the snapshot of a project. Nothing is saved when a build input changed after
     * {@code importStarted}, because the model might not reflect it.
     *
     * @param projectRoot the root directory of the Gradle build
     * @param model the imported model
     * @param importStarted when the import that produced {@code model} started
     * @return whether a snapshot was written
     * @throws IOException if the snapshot cannot be written
     */
    public boolean save(Path projectRoot, GradleBuildModel model, Instant importStarted) throws IOException {
        Path root = canonical(projectRoot);
        List<Path> inputs = inputs(root, model);
        for (Path file : inputs) {
            if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isAfter(importStarted))
                return false;
        }

        Path snapshot = snapshotPath(root);
        Files.createDirectories(directory);
        Path tempFile = Files.createTempFile(directory, "gradle-model", ".tmp");
        try {
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                output.writeUTF(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeUTF(importerVersion);
                output.writeUTF(root.toString());
                output.writeInt(inputs.size());
                for (Path file : inputs) {
                    output.writeUTF(file.toString());
                    output.writeUTF(hash(file));
                }

                writeModel(output, model);
            }

            moveIntoPlace(tempFile, snapshot);
            return true;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static List<Path> inputs(Path root, GradleBuildModel model) throws IOException {
        Set<Path> inputs = new LinkedHashSet<>();
        ROOT_FILES.forEach(file -> inputs.add(root.resolve(file)));
        MODULE_FILES.forEach(file -> inputs.add(root.resolve(file)));
        Path gradleDir = root.resolve("gradle");
        if (Files.isDirectory(gradleDir)) {
            try (Stream<Path> files = Files.list(gradleDir)) {
                files.filter(file -> file.getFileName().toString().endsWith(".versions.toml"))
                    .sorted()
                    .forEach(inputs::add);
            }
        }

        if (model.project() != null) {
            for (RailroadModule module : model.project().getModules()) {
                Path moduleDir = FileUtils.normalizePath(module.getProjectDir());
                MODULE_FILES.forEach(file -> inputs.add(moduleDir.resolve(file)));
            }
        }

        return List.copyOf(inputs);
    }

    private static boolean archivesExist(GradleBuildModel model) {
        if (model.project() == null)
            return true;

        for (RailroadModule module : model.project().getModules()) {
            for (List<File> roots : List.of(module.getClasspathRoots(), module.getDependencyRoots())) {
                for (File file : roots) {
                    if (file.getName().endsWith(".jar") && !file.isFile())
                        return false;
                }
            }
        }

        return true;
    }

    private static void writeModel(DataOutputStream output, GradleBuildModel model) throws IOException {
        var objects = new ObjectOutputStream(output);
        objects.writeObject(model.gradleVersion());
        objects.writeObject(model.rootDir() == null ? null : model.rootDir().toString());
        objects.writeObject(serializableCopy(model.fabricData()));
        objects.writeObject(model.project());
        objects.flush();
    }

    private static GradleBuildModel readModel(DataInputStream input) throws IOException, ClassNotFoundException {
        var objects = new ObjectInputStream(input);
        objects.setObjectInputFilter(MODEL_FILTER);
        var gradleVersion = (String) objects.readObject();
        var rootDir = (String) objects.readObject();
        var fabricData = (FabricDataModel) objects.readObject();
        var project = (RailroadProject) objects.readObject();
        return new GradleBuildModel(gradleVersion, rootDir == null ? null : Path.of(rootDir), fabricData, project);
    }

    /** Models fetched through nested build actions are Tooling API proxies over plugin and Gradle interfaces. */
    private static ObjectInputFilter.Status filterModelProxy(ObjectInputFilter.FilterInfo info) {
        Class<?> type = info.serialClass();
        if (type == null || !Proxy.isProxyClass(type))
            return ObjectInputFilter.Status.UNDECIDED;

        for (Class<?> anInterface : type.getInterfaces()) {
            if (MODEL_PACKAGES.stream().noneMatch(anInterface.getName()::startsWith))
                return ObjectInputFilter.Status.REJECTED;
        }

        return ObjectInputFilter.Status.ALLOWED;
    }

    /** Tooling models arrive as proxies that cannot be serialized, so they are copied into the plugin's own type. */
    private static @Nullable FabricDataModel serializableCopy(@Nullable FabricDataModel data) {
        if (data == null || data instanceof BasicFabricDataModel)
            return data;

        FabricDataModel.LoomVersion loom = data.loomVersion();
        return new BasicFabricDataModel(
            data.minecraftVersion(),
            data.mappingsVersion(),
            data.loaderVersion(),
            data.fabricApiVersion(),
            loom == null ? null : new BasicFabricDataModel.BasicLoomVersion(loom.version(), loom.isArchitecturyLoom()));
    }

    /** Keys snapshots by the real path, so different spellings of one directory share a snapshot. */
    private static Path canonical(Path projectRoot) {
        Path normalized = FileUtils.normalizePath(Objects.requireNonNull(projectRoot, "projectRoot"));
        try {
            return normalized.toRealPath();
        } catch (IOException exception) {
            return normalized;
        }
    }

    private Path snapshotPath(Path root) {
        return directory.resolve(sha256(root.toString().getBytes(StandardCharsets.UTF_8)) + ".bin");
    }

    private static String hash(Path file) throws IOException {
        if (!Files.isRegularFile(file))
            return ABSENT;

        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException exception) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * A model restored from a snapshot.
     *
     * @param model the restored model
     * @param current whether the build inputs and dependency archives are unchanged since the model was imported
     */
    public record Restored(GradleBuildModel model, boolean current) {
    }
}
