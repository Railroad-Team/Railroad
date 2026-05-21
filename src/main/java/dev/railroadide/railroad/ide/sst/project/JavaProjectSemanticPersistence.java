package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Persists {@link JavaProjectSemanticIndex} entries as a compact binary cache under
 * the project root.
 */
public final class JavaProjectSemanticPersistence implements ProjectLanguageIndexPersistence<JavaProjectSemanticIndex> {
    private static final String CACHE_DIRECTORY = ".railroad/index/semantic";
    private static final String ENTRIES_DIRECTORY = "files";
    private static final String MANIFEST_FILE = "project-semantic-index.bin";
    private static final String MANIFEST_MAGIC = "RSSTIDX1";
    private static final String ENTRY_MAGIC = "RSSTFIL1";
    private static final int FORMAT_VERSION = 1;

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public @Nullable JavaProjectSemanticIndex loadIfCurrent(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        Path manifestPath = manifestPath(normalizedRoot);
        if (Files.notExists(manifestPath))
            return null;

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(manifestPath)))) {
            verifyHeader(input, MANIFEST_MAGIC);
            int fileCount = input.readInt();

            JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
            for (int index = 0; index < fileCount; index++) {
                Path sourcePath = normalizedRoot.resolve(input.readUTF()).normalize();
                long expectedLastModified = input.readLong();
                String expectedContentHash = input.readUTF();
                String entryFileName = input.readUTF();

                if (Files.notExists(sourcePath))
                    return null;

                if (Files.getLastModifiedTime(sourcePath).toMillis() != expectedLastModified)
                    return null;

                if (!expectedContentHash.equals(contentHash(sourcePath)))
                    return null;

                Path entryPath = entriesDirectory(normalizedRoot).resolve(entryFileName);
                if (Files.notExists(entryPath))
                    return null;

                builder.putFile(readEntry(entryPath, normalizedRoot));
            }

            return builder.build();
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public void save(Path projectRoot, JavaProjectSemanticIndex index) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        try {
            Files.createDirectories(entriesDirectory(normalizedRoot));

            List<ManifestEntry> manifestEntries = new ArrayList<>();
            List<JavaProjectSemanticIndex.SourceFileIndex> files = index.files().values().stream()
                .sorted(Comparator.comparing(file -> file.path().toString()))
                .toList();

            for (JavaProjectSemanticIndex.SourceFileIndex file : files) {
                if (Files.notExists(file.path()))
                    continue;

                String relativePath = normalizedRoot.relativize(file.path()).toString();
                long lastModified = Files.getLastModifiedTime(file.path()).toMillis();
                String contentHash = contentHash(file.path());
                String entryFileName = entryFileName(relativePath);

                writeEntry(entriesDirectory(normalizedRoot).resolve(entryFileName), normalizedRoot, file);
                manifestEntries.add(new ManifestEntry(relativePath, lastModified, contentHash, entryFileName));
            }

            writeManifest(manifestPath(normalizedRoot), manifestEntries);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist semantic index cache for " + normalizedRoot, exception);
        }
    }

    @Override
    public void delete(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        FileUtils.deleteFolder(cacheDirectory(normalizedRoot));
    }

    private static JavaProjectSemanticIndex.SourceFileIndex readEntry(Path entryPath, Path projectRoot) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(entryPath)))) {
            verifyHeader(input, ENTRY_MAGIC);
            Path sourcePath = projectRoot.resolve(input.readUTF()).normalize();
            String packageName = readNullableString(input);

            int importCount = input.readInt();
            List<JavaProjectSemanticIndex.ImportDescriptor> imports = new ArrayList<>(importCount);
            for (int index = 0; index < importCount; index++) {
                imports.add(new JavaProjectSemanticIndex.ImportDescriptor(
                    input.readUTF(),
                    input.readBoolean(),
                    input.readBoolean()
                ));
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
    }

    private static void writeEntry(Path entryPath, Path projectRoot, JavaProjectSemanticIndex.SourceFileIndex file) throws IOException {
        Files.createDirectories(entryPath.getParent());
        Path tempFile = Files.createTempFile(entryPath.getParent(), "semantic-entry", ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                writeHeader(output, ENTRY_MAGIC);
                output.writeUTF(projectRoot.relativize(file.path()).toString());
                writeNullableString(output, file.packageName());

                output.writeInt(file.imports().size());
                for (JavaProjectSemanticIndex.ImportDescriptor importDescriptor : file.imports()) {
                    output.writeUTF(importDescriptor.qualifiedName());
                    output.writeBoolean(importDescriptor.isStatic());
                    output.writeBoolean(importDescriptor.isWildcard());
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

            Files.move(tempFile, entryPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void writeManifest(Path manifestPath, List<ManifestEntry> entries) throws IOException {
        Files.createDirectories(manifestPath.getParent());
        Path tempFile = Files.createTempFile(manifestPath.getParent(), "semantic-manifest", ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                writeHeader(output, MANIFEST_MAGIC);
                output.writeInt(entries.size());
                for (ManifestEntry entry : entries) {
                    output.writeUTF(entry.relativePath());
                    output.writeLong(entry.lastModified());
                    output.writeUTF(entry.contentHash());
                    output.writeUTF(entry.entryFileName());
                }
            }

            Files.move(tempFile, manifestPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void writeHeader(DataOutputStream output, String magic) throws IOException {
        output.writeUTF(magic);
        output.writeInt(FORMAT_VERSION);
    }

    private static void verifyHeader(DataInputStream input, String expectedMagic) throws IOException {
        String actualMagic = input.readUTF();
        int version = input.readInt();
        if (!expectedMagic.equals(actualMagic)) {
            throw new IOException("Unexpected semantic index magic: " + actualMagic);
        }
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported semantic index version: " + version);
        }
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
            byte[] content = Files.readAllBytes(sourceFile);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static String entryFileName(String relativePath) {
        return HexFormat.of().formatHex(digest(relativePath)) + ".bin";
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes());
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

    private static Path entriesDirectory(Path projectRoot) {
        return cacheDirectory(projectRoot).resolve(ENTRIES_DIRECTORY);
    }

    private static Path manifestPath(Path projectRoot) {
        return cacheDirectory(projectRoot).resolve(MANIFEST_FILE);
    }

    private record ManifestEntry(
        String relativePath,
        long lastModified,
        String contentHash,
        String entryFileName
    ) {
    }
}
