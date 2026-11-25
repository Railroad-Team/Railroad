package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.document.api.*;
import org.jetbrains.annotations.Nullable;
import javax.lang.model.SourceVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides readable Java source for compiled library and JDK classes, so that navigation can open them.
 * <p>
 * Source attached to the binary is preferred: a {@code -sources.jar} next to a library jar (Maven layout) or in a
 * sibling directory of the Gradle module cache, and the JDK's {@code src.zip} for JDK classes. When no source is
 * attached, a declaration-only view is rendered from the class metadata instead.
 * <p>
 * Either view is written to a read-only file under this instance's directory, so editors open it like any other
 * file. Text is normalized to {@code \n} line endings so the written file matches the returned snapshot exactly.
 * Instances are thread-safe.
 */
public final class JavaLibrarySources {
    private static volatile @Nullable JavaLibrarySources shared;

    private final Path directory;

    /**
     * Creates a source provider that writes viewed sources below {@code directory}.
     *
     * @param directory directory holding materialized library sources
     */
    public JavaLibrarySources(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    /**
     * Sets the directory used by {@link #shared()}.
     *
     * @param directory directory holding materialized library sources
     */
    public static void enable(Path directory) {
        shared = new JavaLibrarySources(directory);
    }

    /**
     * Returns the application's source provider, using a temporary directory until {@link #enable(Path)} is called.
     *
     * @return shared source provider
     */
    public static JavaLibrarySources shared() {
        JavaLibrarySources current = shared;
        if (current != null)
            return current;
        synchronized (JavaLibrarySources.class) {
            if (shared == null) {
                shared = new JavaLibrarySources(
                    Path.of(System.getProperty("java.io.tmpdir"), "railroad-library-sources"));
            }
            return shared;
        }
    }

    /**
     * Returns the directory holding materialized library sources.
     *
     * @return absolute normalized directory
     */
    public Path directory() {
        return directory;
    }

    /**
     * Reads an attached source entry without materializing an editor file or rendering a stub.
     *
     * @param binary address of the compiled top-level class
     * @param topLevel metadata of the top-level class
     * @return source text and identity, if an attachment contains this class
     */
    public Optional<TextDocumentSnapshot> readAttachedSource(DocumentUri binary, ClassStub topLevel) {
        String relative = topLevel.getFullName().replace('.', '/') + ".java";
        try {
            return attachedSource(binary, relative).map(source -> {
                DocumentUri uri = JavaLibrarySymbolIndex.archiveEntry(source.archive(), source.entry());
                return new TextDocumentSnapshot(
                    new DocumentId(UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8))), uri,
                    DocumentVersion.initial(), "java",
                    new DocumentOrigin(BackingKind.ARCHIVE_ENTRY, ProvenanceKind.ATTACHED_SOURCE,
                        List.of(new OriginReference(binary))),
                    normalize(source.text()), StandardCharsets.UTF_8);
            });
        } catch (IOException | UncheckedIOException | InvalidPathException exception) {
            Railroad.LOGGER.warn("Failed to read attached source for {}", binary, exception);
            return Optional.empty();
        }
    }

    /**
     * Materializes the source of a compiled top-level class and captures it as a document.
     *
     * @param binary address of the compiled class, such as a {@code jar:} or {@code jrt:} entry
     * @param topLevel metadata of the top-level class to view
     * @param classStubs every class known to the index, used to render nested classes when no source is attached
     * @return captured read-only source, or empty when the view could not be written
     */
    public Optional<TextDocumentSnapshot> open(
        DocumentUri binary,
        ClassStub topLevel,
        Map<String, ClassStub> classStubs
    ) {
        Objects.requireNonNull(binary, "binary");
        Objects.requireNonNull(topLevel, "topLevel");
        Objects.requireNonNull(classStubs, "classStubs");
        String relative = topLevel.getFullName().replace('.', '/') + ".java";
        try {
            Optional<AttachedSource> attached = attachedSource(binary, relative);
            String text;
            String key;
            ProvenanceKind provenance;
            if (attached.isPresent()) {
                text = attached.get().text();
                key = archivePath(binary).map(path -> "sources/" + hash(path.toString()))
                    .orElseGet(() -> "sources/" + hash(attached.get().archive() + "!/" + attached.get().entry()));
                provenance = ProvenanceKind.ATTACHED_SOURCE;
            } else {
                text = JavaStubSourceRenderer.render(topLevel, classStubs, binary.toString());
                key = "compiled/" + hash(binary.toString());
                provenance = ProvenanceKind.DECOMPILED;
            }

            text = normalize(text);
            Path file = directory.resolve(key).resolve(relative).normalize();
            write(file, text);
            DocumentUri uri = DocumentUri.fromPath(file);
            return Optional.of(new TextDocumentSnapshot(
                new DocumentId(UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8))), uri,
                DocumentVersion.initial(), "java",
                new DocumentOrigin(BackingKind.FILE, provenance, List.of(new OriginReference(binary))), text,
                StandardCharsets.UTF_8));
        } catch (IOException | UncheckedIOException | InvalidPathException exception) {
            Railroad.LOGGER.warn("Failed to provide source for {}", binary, exception);
            return Optional.empty();
        }
    }

    /**
     * Finds source attached to a compiled class.
     *
     * @param binary address of the compiled class
     * @param relative package-relative path of the top-level source file, such as {@code java/lang/String.java}
     * @return attached source, or empty when none is found
     * @throws IOException if a candidate source archive cannot be read
     */
    private Optional<AttachedSource> attachedSource(DocumentUri binary, String relative) throws IOException {
        URI value = binary.value();
        String scheme = value.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("jrt")) {
            String path = value.getPath();
            if (path == null || path.length() < 2)
                return Optional.empty();
            String module = path.substring(1, path.indexOf('/', 1) < 0 ? path.length() : path.indexOf('/', 1));
            Path home = Path.of(System.getProperty("java.home"));
            return read(List.of(home.resolve("lib/src.zip"), home.resolve("src.zip")),
                List.of(module + "/" + relative, relative));
        }
        if (!scheme.equals("jar"))
            return Optional.empty();

        String specific = value.getRawSchemeSpecificPart();
        int separator = specific.indexOf("!/");
        if (separator < 0)
            return Optional.empty();
        Path archive = Path.of(URI.create(specific.substring(0, separator)));
        String name = archive.getFileName().toString();
        if (name.endsWith(".jmod")) {
            String module = name.substring(0, name.length() - ".jmod".length());
            Path home = archive.getParent() == null ? null : archive.getParent().getParent();
            return home == null
                ? Optional.empty()
                : read(List.of(home.resolve("lib/src.zip"), home.resolve("src.zip")),
                    List.of(module + "/" + relative, relative));
        }
        if (name.equals("rt.jar")) {
            List<Path> candidates = new ArrayList<>();
            for (Path ancestor = archive.getParent(); ancestor != null
                && candidates.size() < 6; ancestor = ancestor.getParent()) {
                candidates.add(ancestor.resolve("src.zip"));
                candidates.add(ancestor.resolve("lib/src.zip"));
            }
            return read(candidates, List.of(relative));
        }
        return read(sourceArchives(archive), List.of(relative));
    }

    /**
     * Lists locally available source attachments for a library jar, in navigation lookup order.
     *
     * @param archive binary root from the project classpath
     * @return matching source archives
     * @throws IOException if a cache directory cannot be listed
     */
    public static List<Path> sourceArchives(Path archive) throws IOException {
        if (archive.getFileName() == null || !archive.getFileName().toString().endsWith(".jar"))
            return List.of();
        String name = archive.getFileName().toString();
        List<String> names = sourceArchiveNames(name);
        List<Path> candidates = new ArrayList<>();
        Path parent = archive.getParent();
        if (parent != null) {
            names.stream().map(parent::resolve).forEach(candidates::add);
            Path version = parent.getParent();
            if (isCacheHash(parent) && version != null && Files.isDirectory(version)) {
                try (Stream<Path> hashes = Files.list(version)) {
                    hashes.filter(JavaLibrarySources::isCacheHash).filter(Files::isDirectory).sorted()
                        .flatMap(hash -> names.stream().map(hash::resolve)).forEach(candidates::add);
                }
            }
        }
        return candidates.stream().distinct().filter(Files::isRegularFile).toList();
    }

    /**
     * Writes a source file from a library jar's attached sources into the folder that navigation uses for that jar,
     * so the debugger and the editor refer to the same read-only file.
     *
     * @param binary library jar from the classpath
     * @param relative path of the source file inside the source archive, such as {@code net/example/Thing.java}
     * @return the materialized source file, or empty when no attached source archive contains it
     */
    public Optional<Path> materializeAttachedSource(Path binary, String relative) {
        Objects.requireNonNull(binary, "binary");
        Objects.requireNonNull(relative, "relative");
        Path archive = binary.toAbsolutePath().normalize();
        try {
            Optional<AttachedSource> attached = read(sourceArchives(archive), List.of(relative));
            if (attached.isEmpty())
                return Optional.empty();

            Path root = viewRoot(archive);
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root))
                return Optional.empty();

            write(file, normalize(attached.get().text()));
            return Optional.of(file);
        } catch (IOException | UncheckedIOException | InvalidPathException exception) {
            Railroad.LOGGER.warn("Failed to provide attached source {} for {}", relative, archive, exception);
            return Optional.empty();
        }
    }

    /**
     * Returns the folder holding the materialized attached sources of a library jar. Package directories below it
     * mirror the source archive, so it acts as a source root.
     *
     * @param binary library jar from the classpath
     * @return absolute normalized folder, which may not exist yet
     */
    public Path viewRoot(Path binary) {
        return directory.resolve("sources").resolve(hash(binary.toAbsolutePath().normalize().toString()));
    }

    private static Optional<AttachedSource> read(List<Path> archives, List<String> entries) throws IOException {
        for (Path archive : archives) {
            if (!Files.isRegularFile(archive))
                continue;
            try (var zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
                for (String entryName : entries) {
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null || entry.isDirectory())
                        continue;
                    try (var input = zip.getInputStream(entry)) {
                        return Optional.of(new AttachedSource(archive.toAbsolutePath().normalize(), entryName,
                            new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Adds declarations shipped only as source to the owning archive's analysis view. */
    public JavaSymbolIndex withSourceOnlyTypes(
        Path binary,
        JavaSymbolIndex symbols,
        JavaSymbolIndex unscoped,
        Runnable check
    )
        throws IOException {
        var builder = JavaProjectSemanticIndex.builder();
        var texts = new HashMap<Path, String>();
        var extractor = new JavaProjectSemanticExtractor();
        var view = directory.resolve("sources").resolve(hash(binary.toString()));
        for (Path archive : sourceArchives(binary)) {
            try (var zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
                for (var entries = zip.entries(); entries.hasMoreElements();) {
                    check.run();
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".java"))
                        continue;
                    String qualified = name.substring(0, name.length() - 5).replace('/', '.');
                    if (!SourceVersion.isName(qualified) || !name.equals(qualified.replace('.', '/') + ".java")
                        || symbols.declaredQualifiedNames().contains(qualified))
                        continue;
                    Path path = view.resolve(name);
                    if (texts.containsKey(path))
                        continue;
                    String text;
                    try (var input = zip.getInputStream(entry)) {
                        text = normalize(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    }
                    var uri = JavaLibrarySymbolIndex.archiveEntry(archive, name);
                    var snapshot = new TextDocumentSnapshot(
                        new DocumentId(UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8))), uri,
                        DocumentVersion.initial(), "java", DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
                    var file = extractor.extract(path, snapshot, check);
                    if (file.declaredSymbols().stream().noneMatch(symbol -> qualified.equals(symbol.qualifiedName()))
                        || file.declaredSymbols().stream()
                            .anyMatch(symbol -> symbol.qualifiedName() != null
                                && symbols.declaredQualifiedNames().contains(symbol.qualifiedName())))
                        continue;
                    write(path, text);
                    builder.putFile(file);
                    texts.put(path, text);
                }
            }
        }
        return texts.isEmpty()
            ? symbols
            : new CompositeJavaSymbolIndex(List.of(symbols, builder.build()), unscoped, texts);
    }

    private static Optional<Path> archivePath(DocumentUri entry) {
        URI uri = entry.value();
        if (!"jar".equalsIgnoreCase(uri.getScheme()))
            return Optional.empty();
        String name = uri.getRawSchemeSpecificPart();
        int end = name.indexOf("!/");
        if (end < 0)
            return Optional.empty();
        URI archive = URI.create(name.substring(0, end));
        return "file".equalsIgnoreCase(archive.getScheme())
            ? Optional.of(Path.of(archive).toAbsolutePath().normalize())
            : Optional.empty();
    }

    /**
     * Identifies the binary that owns an attached source, including a materialized view reopened by file path.
     * Only an artifact on the requesting project's imported classpath can own the view.
     *
     * @param document requesting document
     * @param roots ordered binary roots
     * @return owning archive when known
     */
    public Optional<Path> ownerArchive(DocumentSnapshot document, List<Path> roots) {
        if (document.origin().provenance() == ProvenanceKind.ATTACHED_SOURCE) {
            for (OriginReference source : document.origin().sources()) {
                var archive = archivePath(source.uri()).filter(roots::contains);
                if (archive.isPresent())
                    return archive;
            }
        }
        Optional<Path> attached = archivePath(document.uri());
        if (attached.isPresent()) {
            Path source = attached.get();
            if (roots.contains(source))
                return Optional.of(source);
            return roots.stream().filter(root -> isSourceAttachment(source, root)).findFirst();
        }
        Path file = document.uri().filePath().orElse(null);
        return ownerArchive(file, roots);
    }

    private static boolean isSourceAttachment(Path source, Path binary) {
        if (binary.getFileName() == null || source.getFileName() == null)
            return false;
        String name = binary.getFileName().toString();
        if (!name.endsWith(".jar"))
            return false;
        if (!sourceArchiveNames(name).contains(source.getFileName().toString()))
            return false;
        Path parent = binary.getParent();
        Path sourceParent = source.getParent();
        return parent.equals(sourceParent) || (isCacheHash(parent) && isCacheHash(sourceParent)
            && parent.getParent().equals(sourceParent.getParent()));
    }

    private static boolean isCacheHash(Path directory) {
        return directory.getFileName() != null && directory.getFileName().toString().matches("[0-9a-f]{1,40}");
    }

    private static List<String> sourceArchiveNames(String binaryName) {
        String base = binaryName.substring(0, binaryName.length() - ".jar".length());
        String sources = base + "-sources.jar";
        return base.endsWith("-universal")
            ? List.of(sources, base.substring(0, base.length() - "-universal".length()) + "-sources.jar")
            : List.of(sources);
    }

    /** Finds the imported binary owning a materialized attached source file. */
    public Optional<Path> ownerArchive(@Nullable Path file, List<Path> roots) {
        Path views = directory.resolve("sources");
        if (file == null || !file.startsWith(views) || file.getNameCount() < views.getNameCount() + 2)
            return Optional.empty();
        String key = views.relativize(file).getName(0).toString();
        return roots.stream().filter(root -> hash(root.toString()).equals(key)).findFirst();
    }

    private static String normalize(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void write(Path file, String text) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(text))
            return;

        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            if (Files.exists(file)) {
                file.toFile().setWritable(true);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        file.toFile().setWritable(false);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Source text read from an archive attached to a compiled class.
     *
     * @param archive source archive
     * @param entry entry name within the archive
     * @param text decoded source text
     */
    private record AttachedSource(Path archive, String entry, String text) {
    }
}
