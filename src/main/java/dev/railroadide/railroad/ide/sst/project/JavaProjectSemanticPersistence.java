package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/** Persists the complete Java project semantic index as one atomic binary snapshot. */
public final class JavaProjectSemanticPersistence implements ProjectLanguageIndexPersistence<JavaProjectSemanticIndex> {
    private static final String CACHE_DIRECTORY = ".railroad/index/semantic";
    private static final String SNAPSHOT_FILE = "project-semantic-index.bin";
    private static final String SNAPSHOT_MAGIC = "RSSTIDX1";
    private static final int FORMAT_VERSION = 3;

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public @Nullable JavaProjectSemanticIndex loadIfCurrent(Path projectRoot) {
        return loadIfCurrent(projectRoot, null);
    }

    @Override
    public @Nullable JavaProjectSemanticIndex loadIfCurrent(Path projectRoot, @Nullable Collection<Path> indexedFiles) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        Path snapshot = snapshotPath(normalizedRoot);
        if (Files.notExists(snapshot))
            return null;

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(snapshot)))) {
            verifyHeader(input);
            int fileCount = input.readInt();
            List<ManifestEntry> entries = readManifestEntries(input, fileCount);
            if (!matchesIndexedFiles(normalizedRoot, entries, indexedFiles))
                return null;
            for (ManifestEntry entry : entries) {
                Path sourcePath = normalizedRoot.resolve(entry.relativePath()).normalize();
                if (Files.notExists(sourcePath)
                    || Files.getLastModifiedTime(sourcePath).toMillis() != entry.lastModified()
                    || !entry.contentHash().equals(contentHash(sourcePath))) {
                    return null;
                }
            }

            JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
            for (int index = 0; index < fileCount; index++)
                builder.putFile(readFileIndex(input, normalizedRoot));
            return builder.build();
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void save(Path projectRoot, JavaProjectSemanticIndex index) {
        writeSnapshot(normalizeRoot(projectRoot), index, null);
    }

    @Override
    public void updateFile(Path projectRoot, JavaProjectSemanticIndex index, Path file) {
        writeSnapshot(normalizeRoot(projectRoot), index, FileUtils.normalizePath(file));
    }

    @Override
    public void removeFile(Path projectRoot, JavaProjectSemanticIndex index, Path file) {
        writeSnapshot(normalizeRoot(projectRoot), index, null);
    }

    @Override
    public void delete(Path projectRoot) {
        FileUtils.deleteFolder(cacheDirectory(normalizeRoot(projectRoot)));
    }

    private static void writeSnapshot(Path projectRoot, JavaProjectSemanticIndex index, @Nullable Path changedFile) {
        Path snapshot = snapshotPath(projectRoot);
        try {
            Files.createDirectories(snapshot.getParent());
            Map<String, ManifestEntry> previousEntries = readManifest(snapshot).stream()
                .collect(Collectors.toMap(ManifestEntry::relativePath, entry -> entry, (left, right) -> left));
            List<PersistedFile> files = new ArrayList<>();
            index.files().values().stream()
                .filter(file -> Files.exists(file.path()))
                .sorted(Comparator.comparing(file -> file.path().toString()))
                .forEach(file -> files.add(persistedFile(projectRoot, file, previousEntries, changedFile)));

            Path tempFile = Files.createTempFile(snapshot.getParent(), "semantic-index", ".tmp");
            try {
                try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                    writeHeader(output);
                    output.writeInt(files.size());
                    for (PersistedFile file : files)
                        writeManifestEntry(output, file.manifestEntry());
                    for (PersistedFile file : files)
                        writeFileIndex(output, projectRoot, file.fileIndex());
                }
                moveIntoPlace(tempFile, snapshot);
            } finally {
                Files.deleteIfExists(tempFile);
            }

            Path legacyEntries = cacheDirectory(projectRoot).resolve("files");
            if (Files.exists(legacyEntries))
                FileUtils.deleteFolder(legacyEntries);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist semantic index cache for " + projectRoot, exception);
        }
    }

    private static PersistedFile persistedFile(
        Path projectRoot,
        JavaProjectSemanticIndex.SourceFileIndex file,
        Map<String, ManifestEntry> previousEntries,
        @Nullable Path changedFile
    ) {
        try {
            String relativePath = projectRoot.relativize(file.path()).toString();
            long lastModified = Files.getLastModifiedTime(file.path()).toMillis();
            ManifestEntry previous = previousEntries.get(relativePath);
            boolean canReuseHash = !file.path().equals(changedFile)
                && previous != null
                && previous.lastModified() == lastModified;
            String hash = canReuseHash ? previous.contentHash() : contentHash(file.path());
            return new PersistedFile(new ManifestEntry(relativePath, lastModified, hash), file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<ManifestEntry> readManifest(Path snapshot) {
        if (Files.notExists(snapshot))
            return List.of();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(snapshot)))) {
            verifyHeader(input);
            return readManifestEntries(input, input.readInt());
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static List<ManifestEntry> readManifestEntries(DataInputStream input, int fileCount) throws IOException {
        if (fileCount < 0)
            throw new IOException("Negative semantic index file count");
        List<ManifestEntry> entries = new ArrayList<>(fileCount);
        for (int index = 0; index < fileCount; index++)
            entries.add(new ManifestEntry(input.readUTF(), input.readLong(), input.readUTF()));
        return entries;
    }

    private static boolean matchesIndexedFiles(
        Path projectRoot,
        List<ManifestEntry> entries,
        @Nullable Collection<Path> indexedFiles
    ) {
        if (indexedFiles == null)
            return true;
        Set<Path> expected = indexedFiles.stream()
            .filter(Objects::nonNull)
            .map(FileUtils::normalizePath)
            .collect(Collectors.toUnmodifiableSet());
        Set<Path> persisted = entries.stream()
            .map(entry -> projectRoot.resolve(entry.relativePath()).normalize())
            .collect(Collectors.toUnmodifiableSet());
        return expected.equals(persisted);
    }

    private static JavaProjectSemanticIndex.SourceFileIndex readFileIndex(
        DataInputStream input,
        Path projectRoot
    ) throws IOException {
        Path sourcePath = projectRoot.resolve(input.readUTF()).normalize();
        String packageName = readNullableString(input);
        int importCount = input.readInt();
        List<JavaProjectSemanticIndex.ImportDescriptor> imports = new ArrayList<>(importCount);
        for (int index = 0; index < importCount; index++) {
            imports.add(new JavaProjectSemanticIndex.ImportDescriptor(
                input.readUTF(), input.readBoolean(), input.readBoolean()));
        }
        int symbolCount = input.readInt();
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols = new ArrayList<>(symbolCount);
        for (int index = 0; index < symbolCount; index++) {
            symbols.add(new JavaProjectSemanticIndex.SymbolDescriptor(
                SymbolKind.valueOf(input.readUTF()),
                input.readUTF(),
                readNullableString(input),
                readNullableString(input),
                readNullableString(input),
                sourcePath,
                input.readBoolean(),
                input.readBoolean()
            ));
        }
        return new JavaProjectSemanticIndex.SourceFileIndex(sourcePath, packageName, imports, symbols);
    }

    private static void writeFileIndex(
        DataOutputStream output,
        Path projectRoot,
        JavaProjectSemanticIndex.SourceFileIndex file
    ) throws IOException {
        output.writeUTF(projectRoot.relativize(file.path()).toString());
        writeNullableString(output, file.packageName());
        output.writeInt(file.imports().size());
        for (JavaProjectSemanticIndex.ImportDescriptor descriptor : file.imports()) {
            output.writeUTF(descriptor.qualifiedName());
            output.writeBoolean(descriptor.isStatic());
            output.writeBoolean(descriptor.isWildcard());
        }
        output.writeInt(file.declaredSymbols().size());
        for (JavaProjectSemanticIndex.SymbolDescriptor symbol : file.declaredSymbols()) {
            output.writeUTF(symbol.kind().name());
            output.writeUTF(symbol.simpleName());
            writeNullableString(output, symbol.qualifiedName());
            writeNullableString(output, symbol.ownerQualifiedName());
            writeNullableString(output, symbol.signature());
            output.writeBoolean(symbol.isStatic());
            output.writeBoolean(symbol.isTopLevel());
        }
    }

    private static void writeManifestEntry(DataOutputStream output, ManifestEntry entry) throws IOException {
        output.writeUTF(entry.relativePath());
        output.writeLong(entry.lastModified());
        output.writeUTF(entry.contentHash());
    }

    private static void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException exception) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeHeader(DataOutputStream output) throws IOException {
        output.writeUTF(SNAPSHOT_MAGIC);
        output.writeInt(FORMAT_VERSION);
    }

    private static void verifyHeader(DataInputStream input) throws IOException {
        String magic = input.readUTF();
        int version = input.readInt();
        if (!SNAPSHOT_MAGIC.equals(magic) || version != FORMAT_VERSION)
            throw new IOException("Unsupported semantic index snapshot");
    }

    private static void writeNullableString(DataOutputStream output, @Nullable String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null)
            output.writeUTF(value);
    }

    private static @Nullable String readNullableString(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readUTF() : null;
    }

    private static String contentHash(Path sourceFile) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(sourceFile)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static Path normalizeRoot(Path projectRoot) {
        return FileUtils.normalizePath(Objects.requireNonNull(projectRoot, "projectRoot"));
    }

    private static Path cacheDirectory(Path projectRoot) {
        return projectRoot.resolve(CACHE_DIRECTORY);
    }

    private static Path snapshotPath(Path projectRoot) {
        return cacheDirectory(projectRoot).resolve(SNAPSHOT_FILE);
    }

    private record ManifestEntry(String relativePath, long lastModified, String contentHash) {
    }

    private record PersistedFile(
        ManifestEntry manifestEntry,
        JavaProjectSemanticIndex.SourceFileIndex fileIndex
    ) {
    }
}
