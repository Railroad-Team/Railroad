package dev.railroadide.railroad.ide.sst.project;

import com.google.common.base.Suppliers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Striped;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.classparser.ClassScanException;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Binary symbol index built from class directories, JAR archives, and JMOD archives.
 */
public final class JavaLibrarySymbolIndex extends JavaStubSymbolIndex {
    private static final Cache<ArchiveVersion, SharedArchive> LOADED_ARCHIVES = CacheBuilder.newBuilder()
        .weakValues().build();
    private static final Striped<Lock> ARCHIVE_LOCKS = Striped.lazyWeakLock(256);

    private final JavaLibrarySymbolIndex unscoped;
    private final List<ScannedRoot> archives;

    private JavaLibrarySymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName,
        Map<String, Supplier<DocumentUri>> locations,
        List<ScannedRoot> archives,
        Runnable activityCheck
    ) {
        super(classStubsByQualifiedName, sourceByQualifiedName,
            Collections.unmodifiableMap(Maps.transformValues(Map.copyOf(locations), Supplier::get)),
            Map.of(), activityCheck, false);
        unscoped = this;
        this.archives = List.copyOf(archives);
    }

    private JavaLibrarySymbolIndex(
        JavaLibrarySymbolIndex unscoped,
        Map<String, String> aliases
    ) {
        super(unscoped, aliases);
        this.unscoped = unscoped;
        this.archives = unscoped.archives;
    }

    /**
     * Scans unique library roots and indexes readable class files, logging individual scan failures.
     *
     * @param roots the class directories, JARs, or JMODs to scan; unavailable roots are skipped
     * @return the symbol index for successfully parsed library classes
     */
    public static JavaLibrarySymbolIndex build(List<Path> roots) {
        return build(roots, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Scans captured binary roots with cooperative cancellation and deadline checks. Roots are scanned in parallel,
     * and indexes using the same enabled stub cache share immutable archive entries.
     *
     * @param roots binary roots in lookup order
     * @param activityCheck request activity check, run on the calling thread
     * @return complete index; cancelled scans never publish a partial index
     */
    public static JavaLibrarySymbolIndex build(List<Path> roots, Runnable activityCheck) {
        return build(roots, activityCheck, JavaLibraryStubCache.shared().orElse(null));
    }

    /**
     * Scans captured binary roots in parallel, reading and storing archives through {@code cache}.
     *
     * @param roots binary roots in lookup order; a class found in an earlier root takes precedence
     * @param activityCheck request activity check, run on the calling thread
     * @param cache the stub cache to use, or {@code null} to parse every archive
     * @return complete index; cancelled scans never publish a partial index
     */
    public static JavaLibrarySymbolIndex build(
        List<Path> roots,
        Runnable activityCheck,
        @Nullable JavaLibraryStubCache cache
    ) {
        return build(roots, activityCheck, cache, "railroad.activity.libraries");
    }

    /**
     * Builds an index using the specified phase label for its root scan, including JDK module scans.
     *
     * @param roots binary roots in lookup order; a class found in an earlier root takes precedence
     * @param activityCheck request activity check, run on the calling thread
     * @param cache the stub cache to use, or {@code null} to parse every archive
     * @param phase translation key of the progress phase shown while scanning
     * @return complete index; cancelled scans never publish a partial index
     */
    public static JavaLibrarySymbolIndex build(
        List<Path> roots,
        Runnable activityCheck,
        @Nullable JavaLibraryStubCache cache,
        String phase
    ) {
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        Map<String, ClassStub> stubsByQualifiedName = new LinkedHashMap<>();
        Map<String, Path> sourceByQualifiedName = new LinkedHashMap<>();
        Map<String, Supplier<DocumentUri>> locations = new LinkedHashMap<>();
        List<Path> uniqueRoots = List.copyOf(new LinkedHashSet<>(roots));
        var progress = ActivityProgress.current();
        Runnable processed = progress.count(phase, ActivityProgress.Unit.COUNT, uniqueRoots.size());
        List<ScannedRoot> scannedRoots = scanRoots(uniqueRoots, activityCheck, cache, processed,
            uniqueRoots.size() == 1 ? progress : ActivityProgress.NONE);
        progress.indeterminate("railroad.activity.finalizing");
        List<ScannedRoot> archives = new ArrayList<>();
        for (ScannedRoot root : scannedRoots) {
            List<ScannedClass> retained = new ArrayList<>();
            for (ScannedClass scanned : root.visible(stubsByQualifiedName.keySet(), activityCheck)) {
                ActivityCheckpoint.poll(activityCheck);
                String qualifiedName = scanned.qualifiedName();
                if (qualifiedName == null || qualifiedName.isBlank())
                    continue;

                if (stubsByQualifiedName.putIfAbsent(qualifiedName, scanned.stub()) == null) {
                    sourceByQualifiedName.put(qualifiedName,
                        root.archive() == null ? scanned.origin() : root.archive());
                    locations.put(qualifiedName, scanned.location());
                    if (root.shared() != null) {
                        retained.add(scanned);
                    }
                }
            }

            if (!retained.isEmpty()) {
                archives.add(new ScannedRoot(List.copyOf(retained), root.archive(), root.shared()));
            }
        }

        activityCheck.run();
        return new JavaLibrarySymbolIndex(stubsByQualifiedName, sourceByQualifiedName, locations, archives,
            activityCheck);
    }

    @Override
    public JavaSymbolIndex forAttachedSource(Path archive, Runnable check) {
        if (unscoped != this)
            return unscoped.forAttachedSource(archive, check);

        check.run();
        Map<String, String> aliases = Map.of();
        try {
            aliases = JavaSourceRelocations.aliases(archive, binaryOrigins(), classStubsByQualifiedName(), check);
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Failed to read package relocations for attached source from {}", archive, exception);
        }

        JavaLibrarySymbolIndex scoped = aliases.isEmpty() ? this : new JavaLibrarySymbolIndex(this, aliases);
        try {
            return JavaLibrarySources.shared().withSourceOnlyTypes(archive, scoped, this, check);
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Failed to index source-only declarations from {}", archive, exception);
            return scoped;
        }
    }

    /**
     * Scans roots in parallel and returns their classes in root order, so earlier roots keep precedence.
     */
    private static List<ScannedRoot> scanRoots(
        List<Path> roots,
        Runnable activityCheck,
        @Nullable JavaLibraryStubCache cache,
        Runnable processed,
        ActivityProgress entriesProgress
    ) {
        if (roots.isEmpty())
            return List.of();

        var cancelled = new AtomicBoolean();
        int threads = Math.min(roots.size(), Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            var thread = new Thread(runnable, "railroad-library-index");
            thread.setDaemon(true);
            return thread;
        });

        try {
            List<Future<ScannedRoot>> scans = roots.stream()
                .map(root -> executor.submit(() -> {
                    ScannedRoot scanned = scanRoot(root, cache, cancelled, entriesProgress);
                    processed.run();
                    return scanned;
                }))
                .toList();
            List<ScannedRoot> results = new ArrayList<>(scans.size());
            for (Future<ScannedRoot> scan : scans) {
                results.add(await(scan, activityCheck));
            }

            return results;
        } finally {
            cancelled.set(true);
            executor.shutdownNow();
        }
    }

    private static <T> T await(Future<T> future, Runnable activityCheck) {
        while (true) {
            activityCheck.run();
            try {
                return future.get(25, TimeUnit.MILLISECONDS);
            } catch (TimeoutException _) {
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Library indexing was interrupted");
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof RuntimeException runtime)
                    throw runtime;

                if (exception.getCause() instanceof Error error)
                    throw error;

                throw new IllegalStateException("Failed to scan library root", exception.getCause());
            }
        }
    }

    private static ScannedRoot scanRoot(
        Path root,
        @Nullable JavaLibraryStubCache cache,
        AtomicBoolean cancelled,
        ActivityProgress entriesProgress
    ) {
        if (root == null || Files.notExists(root) || !Files.isReadable(root))
            return ScannedRoot.EMPTY;

        if (Files.isDirectory(root))
            return new ScannedRoot(scanDirectory(root, cancelled), null, null);

        String fileName = root.getFileName() == null ? "" : root.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jar") || fileName.endsWith(".jmod"))
            return scanArchive(root, cache, cancelled, entriesProgress);

        return ScannedRoot.EMPTY;
    }

    private static List<ScannedClass> scanDirectory(Path root, AtomicBoolean cancelled) {
        List<ScannedClass> classes = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.peek(_ -> checkCancelled(cancelled))
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .filter(path -> !isDescriptorClass(path.getFileName().toString()))
                .forEach(path -> {
                    try {
                        ClassStub stub = BinaryStubPublication.copy(ClassStubParser.parse(path));
                        classes.add(new ScannedClass(stub.getFullName(), stub, path,
                            Suppliers.ofInstance(DocumentUri.fromPath(path))));
                    } catch (ClassScanException exception) {
                        Railroad.LOGGER.warn("Ignoring unreadable class file {}", path, exception);
                    }
                });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library directory {}", root, exception);
        }

        return classes;
    }

    private static ScannedRoot scanArchive(
        Path archive,
        @Nullable JavaLibraryStubCache cache,
        AtomicBoolean cancelled,
        ActivityProgress entriesProgress
    ) {
        try {
            if (cache == null)
                return new ScannedRoot(toScannedClasses(archive, parseArchive(archive, cancelled, entriesProgress),
                    cancelled), archive, null);

            Path path = archive.toAbsolutePath().normalize();
            Lock lock = ARCHIVE_LOCKS.get(path);
            lock.lockInterruptibly();
            try {
                checkCancelled(cancelled);
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                var version = new ArchiveVersion(cache, path, attributes.size(),
                    attributes.lastModifiedTime().toMillis());
                SharedArchive existing = LOADED_ARCHIVES.getIfPresent(version);
                if (existing != null)
                    return new ScannedRoot(List.of(), archive, existing);

                List<JavaLibraryStubCache.ArchiveClass> entries = readArchive(version, cancelled, entriesProgress);
                if (entries.isEmpty())
                    return ScannedRoot.EMPTY;

                List<ScannedClass> classes = toScannedClasses(path, entries, cancelled);
                var shared = new SharedArchive(version, entries, classes);
                LOADED_ARCHIVES.put(version, shared);
                return new ScannedRoot(classes, archive, shared);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted waiting for library archive " + archive);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library archive {}", archive, exception);
            return ScannedRoot.EMPTY;
        }
    }

    private static List<JavaLibraryStubCache.ArchiveClass> readArchive(
        ArchiveVersion version,
        AtomicBoolean cancelled,
        ActivityProgress entriesProgress
    ) throws IOException {
        JavaLibraryStubCache cache = version.cache();
        Path archive = version.path();
        try {
            Optional<List<JavaLibraryStubCache.ArchiveClass>> cached = cache.load(archive, version.size(),
                version.lastModified());
            if (cached.isPresent())
                return cached.get();
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Ignoring unreadable library stub cache for {}", archive, exception);
        }

        List<JavaLibraryStubCache.ArchiveClass> parsed = parseArchive(archive, cancelled, entriesProgress);
        try {
            cache.save(archive, version.size(), version.lastModified(), parsed);
        } catch (IOException | RuntimeException exception) {
            Railroad.LOGGER.warn("Failed to cache library stubs for {}", archive, exception);
        }

        return parsed;
    }

    private static List<JavaLibraryStubCache.ArchiveClass> parseArchive(
        Path archive,
        AtomicBoolean cancelled,
        ActivityProgress entriesProgress
    ) throws IOException {
        List<JavaLibraryStubCache.ArchiveClass> parsed = new ArrayList<>();
        try (JarFile jarFile = new JarFile(archive.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            Runnable processed = entriesProgress.count("railroad.activity.archive_entries", ActivityProgress.Unit.COUNT,
                jarFile.size());
            for (; entries.hasMoreElements(); processed.run()) {
                checkCancelled(cancelled);
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory())
                    continue;

                String entryName = entry.getName();
                if (!entryName.endsWith(".class") || isDescriptorClass(entryName))
                    continue;

                try (InputStream input = jarFile.getInputStream(entry)) {
                    ClassStub stub = BinaryStubPublication.copy(ClassStubParser.parse(new ClassReader(input)));
                    parsed.add(new JavaLibraryStubCache.ArchiveClass(entryName, stub));
                } catch (CancellationException exception) {
                    throw exception;
                } catch (Exception exception) {
                    Railroad.LOGGER.warn("Ignoring unreadable class entry {} in {}", entryName, archive, exception);
                }
            }
        }

        return parsed;
    }

    private static List<ScannedClass> toScannedClasses(
        Path archive,
        List<JavaLibraryStubCache.ArchiveClass> classes,
        AtomicBoolean cancelled
    ) {
        String prefix = archivePrefix(archive);
        List<ScannedClass> scanned = new ArrayList<>(classes.size());
        for (JavaLibraryStubCache.ArchiveClass archiveClass : classes) {
            checkCancelled(cancelled);
            ClassStub stub = archiveClass.stub();
            scanned.add(ScannedClass.inArchive(stub, archive, prefix, archiveClass.entryName()));
        }

        return List.copyOf(scanned);
    }

    private static void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get())
            throw new CancellationException("Library indexing was cancelled");
    }

    /**
     * Creates an archive address retaining the exact entry, including a JMOD classes/ prefix.
     *
     * @param archive path to the archive containing the class
     * @param entry archive-relative entry name
     * @return URI retaining the archive and entry identity
     */
    public static DocumentUri archiveEntry(Path archive, String entry) {
        return archiveEntry(archivePrefix(archive), entry);
    }

    private static String archivePrefix(Path archive) {
        return "jar:" + archive.toAbsolutePath().normalize().toUri().toASCIIString() + "!/";
    }

    private static DocumentUri archiveEntry(String archivePrefix, String entry) {
        return DocumentUri.parse(archivePrefix + encodeEntry(entry));
    }

    private static String encodeEntry(String entry) {
        for (int index = 0; index < entry.length(); index++) {
            if (!isUnreservedPathCharacter(entry.charAt(index))) {
                try {
                    return new URI(null, null, "/" + entry, null).getRawPath().substring(1);
                } catch (URISyntaxException exception) {
                    throw new IllegalArgumentException("Invalid archive entry", exception);
                }
            }
        }

        return entry;
    }

    /**
     * Characters a URI path keeps as they are; class entries rarely contain anything else.
     */
    private static boolean isUnreservedPathCharacter(char character) {
        return character >= 'a' && character <= 'z'
            || character >= 'A' && character <= 'Z'
            || character >= '0' && character <= '9'
            || "/-._~$!&'()*+,;=:@".indexOf(character) >= 0;
    }

    private static boolean isDescriptorClass(String name) {
        return name.endsWith("module-info.class") || name.endsWith("package-info.class");
    }

    private record ScannedClass(String qualifiedName, ClassStub stub, Path origin, Supplier<DocumentUri> location) {
        private static ScannedClass inArchive(ClassStub stub, Path archive, String prefix, String entry) {
            return new ScannedClass(stub.getFullName(), stub, archive,
                Suppliers.memoize(() -> archiveEntry(prefix, entry)));
        }
    }

    private record ScannedRoot(List<ScannedClass> classes, @Nullable Path archive, @Nullable SharedArchive shared) {
        private static final ScannedRoot EMPTY = new ScannedRoot(List.of(), null, null);

        private List<ScannedClass> visible(Set<String> shadowed, Runnable activityCheck) {
            return shared != null && classes.isEmpty() ? shared.visible(shadowed, activityCheck) : classes;
        }
    }

    private record ArchiveVersion(JavaLibraryStubCache cache, Path path, long size, long lastModified) {
    }

    private static final class SharedArchive {
        private final ArchiveVersion version;
        private final List<ArchiveEntry> entries;

        private SharedArchive(
            ArchiveVersion version,
            List<JavaLibraryStubCache.ArchiveClass> parsed,
            List<ScannedClass> classes
        ) {
            this.version = version;
            Set<String> names = new HashSet<>();
            List<ArchiveEntry> entries = new ArrayList<>(classes.size());
            for (int i = 0; i < classes.size(); i++) {
                ScannedClass scanned = classes.get(i);
                if (names.add(scanned.qualifiedName())) {
                    entries.add(new ArchiveEntry(parsed.get(i).entryName(), scanned));
                }
            }

            this.entries = List.copyOf(entries);
        }

        private List<ScannedClass> visible(Set<String> shadowed, Runnable activityCheck) {
            List<ScannedClass> visible = new ArrayList<>();
            Map<Integer, ArchiveEntry> missing = new LinkedHashMap<>();
            for (ArchiveEntry entry : entries) {
                ActivityCheckpoint.poll(activityCheck);
                if (!shadowed.contains(entry.qualifiedName)) {
                    ScannedClass scanned = entry.scanned.get();
                    if (scanned == null) {
                        missing.put(visible.size(), entry);
                    }

                    visible.add(scanned);
                }
            }

            if (missing.isEmpty())
                return visible;

            Path path = version.path();
            try (var jar = new JarFile(path.toFile())) {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                if (attributes.size() != version.size()
                    || attributes.lastModifiedTime().toMillis() != version.lastModified())
                    throw new CancellationException("Library archive changed during indexing: " + path);

                String prefix = archivePrefix(path);
                for (Map.Entry<Integer, ArchiveEntry> item : missing.entrySet()) {
                    activityCheck.run();
                    ArchiveEntry entry = item.getValue();
                    try {
                        visible.set(item.getKey(), entry.restore(jar, path, prefix));
                    } catch (CancellationException exception) {
                        throw exception;
                    } catch (IOException | RuntimeException exception) {
                        Railroad.LOGGER.warn("Ignoring unreadable class entry {} in {}", entry.name, path, exception);
                    }
                }
            } catch (IOException exception) {
                Railroad.LOGGER.error("Error scanning library archive {}", path, exception);
            }

            visible.removeIf(Objects::isNull);
            return visible;
        }
    }

    private static final class ArchiveEntry {
        private final String name;
        private final String qualifiedName;
        private volatile WeakReference<ScannedClass> scanned;

        private ArchiveEntry(String name, ScannedClass scanned) {
            this.name = name;
            this.qualifiedName = scanned.qualifiedName();
            this.scanned = new WeakReference<>(scanned);
        }

        private synchronized ScannedClass restore(JarFile jar, Path path, String prefix) throws IOException {
            ScannedClass existing = scanned.get();
            if (existing != null)
                return existing;

            JarEntry entry = jar.getJarEntry(name);
            if (entry == null)
                throw new IOException("Missing class entry " + name);

            try (InputStream input = jar.getInputStream(entry)) {
                var stub = BinaryStubPublication.copy(ClassStubParser.parse(new ClassReader(input)));
                var result = ScannedClass.inArchive(stub, path, prefix, name);
                scanned = new WeakReference<>(result);
                return result;
            }
        }
    }
}
