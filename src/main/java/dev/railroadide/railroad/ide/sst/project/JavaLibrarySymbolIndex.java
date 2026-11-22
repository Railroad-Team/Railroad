package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.classparser.ClassScanException;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.ClassStubVisitor;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Binary symbol index built from class directories, JAR archives, and JMOD archives.
 */
public final class JavaLibrarySymbolIndex extends JavaStubSymbolIndex {
    private final JavaLibrarySymbolIndex unscoped;

    private JavaLibrarySymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName,
        Map<String, DocumentUri> locations,
        Runnable activityCheck
    ) {
        super(classStubsByQualifiedName, sourceByQualifiedName, locations, Map.of(), activityCheck, false);
        unscoped = this;
    }

    private JavaLibrarySymbolIndex(
        JavaLibrarySymbolIndex unscoped,
        JavaLibrarySymbolIndex symbols,
        Map<String, String> aliases
    ) {
        super(symbols, aliases);
        this.unscoped = unscoped;
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
     * and archives are read from the {@linkplain JavaLibraryStubCache#shared() shared stub cache} when it is enabled.
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
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        Map<String, ClassStub> stubsByQualifiedName = new LinkedHashMap<>();
        Map<String, Path> sourceByQualifiedName = new LinkedHashMap<>();
        Map<String, DocumentUri> locations = new LinkedHashMap<>();
        List<Path> uniqueRoots = List.copyOf(new LinkedHashSet<>(roots));
        for (List<ScannedClass> scannedRoot : scanRoots(uniqueRoots, activityCheck, cache)) {
            for (ScannedClass scanned : scannedRoot) {
                ActivityCheckpoint.poll(activityCheck);
                String qualifiedName = scanned.stub().getFullName();
                if (qualifiedName == null || qualifiedName.isBlank())
                    continue;

                stubsByQualifiedName.putIfAbsent(qualifiedName, scanned.stub());
                sourceByQualifiedName.putIfAbsent(qualifiedName, scanned.origin());
                locations.putIfAbsent(qualifiedName, scanned.location());
            }
        }

        activityCheck.run();
        return new JavaLibrarySymbolIndex(stubsByQualifiedName, sourceByQualifiedName, locations, activityCheck);
    }

    @Override
    public JavaLibrarySymbolIndex forAttachedSource(Path archive, Runnable check) {
        if (unscoped != this)
            return unscoped.forAttachedSource(archive, check);
        check.run();
        try {
            var declarations = AccessDeclarationVisitor.read(archive, check);
            Map<String, ClassStub> changed = new HashMap<>();
            for (var entry : declarations.entrySet()) {
                check.run();
                var location = binaryLocations().get(entry.getKey());
                if (location == null)
                    continue;
                var connection = location.value().toURL().openConnection();
                connection.setUseCaches(false);
                try (var in = connection.getInputStream()) {
                    var reader = new ClassReader(in);
                    var original = lookupClassStub(entry.getKey());
                    if (!BinaryStubPublication.copy(ClassStubParser.parse(reader)).equals(original))
                        continue;
                    var visitor = entry.getValue();
                    reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
                    var scoped = BinaryStubPublication.copy(visitor.createClassStub());
                    if (!scoped.equals(original)) {
                        changed.put(entry.getKey(), scoped);
                    }
                } catch (IOException | IllegalArgumentException | ClassScanException exception) {
                    Railroad.LOGGER.warn("Failed to read access declarations for {} from {}", entry.getKey(), location,
                        exception);
                }
            }
            JavaLibrarySymbolIndex view = this;
            if (!changed.isEmpty()) {
                var stubs = new HashMap<>(classStubsByQualifiedName());
                stubs.putAll(changed);
                view = new JavaLibrarySymbolIndex(stubs, binaryOrigins(), binaryLocations(), check);
            }
            Map<String, String> aliases = Map.of();
            try {
                aliases = JavaSourceRelocations.aliases(archive, binaryOrigins(), view.classStubsByQualifiedName(),
                    check);
            } catch (IOException exception) {
                Railroad.LOGGER.warn("Failed to read package relocations for attached source from {}", archive,
                    exception);
            }
            return view == this && aliases.isEmpty() ? this : new JavaLibrarySymbolIndex(this, view, aliases);
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Failed to read access declarations for attached source from {}", archive, exception);
            return this;
        }
    }

    /** Scans roots in parallel and returns their classes in root order, so earlier roots keep precedence. */
    private static List<List<ScannedClass>> scanRoots(
        List<Path> roots,
        Runnable activityCheck,
        @Nullable JavaLibraryStubCache cache
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
            List<Future<List<ScannedClass>>> scans = roots.stream()
                .map(root -> executor.submit(() -> scanRoot(root, cache, cancelled)))
                .toList();
            List<List<ScannedClass>> results = new ArrayList<>(scans.size());
            for (Future<List<ScannedClass>> scan : scans) {
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

    private static List<ScannedClass> scanRoot(
        Path root,
        @Nullable JavaLibraryStubCache cache,
        AtomicBoolean cancelled
    ) {
        if (root == null || Files.notExists(root) || !Files.isReadable(root))
            return List.of();

        if (Files.isDirectory(root))
            return scanDirectory(root, cancelled);

        String fileName = root.getFileName() == null ? "" : root.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jar") || fileName.endsWith(".jmod"))
            return scanArchive(root, cache, cancelled);

        return List.of();
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
                        classes.add(new ScannedClass(stub, path, DocumentUri.fromPath(path)));
                    } catch (ClassScanException exception) {
                        Railroad.LOGGER.warn("Ignoring unreadable class file {}", path, exception);
                    }
                });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library directory {}", root, exception);
        }

        return classes;
    }

    private static List<ScannedClass> scanArchive(
        Path archive,
        @Nullable JavaLibraryStubCache cache,
        AtomicBoolean cancelled
    ) {
        long size;
        long lastModified;
        try {
            size = Files.size(archive);
            lastModified = Files.getLastModifiedTime(archive).toMillis();
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library archive {}", archive, exception);
            return List.of();
        }

        if (cache != null) {
            try {
                Optional<List<JavaLibraryStubCache.ArchiveClass>> cached = cache.load(archive, size, lastModified);
                if (cached.isPresent())
                    return toScannedClasses(archive, cached.get(), cancelled);
            } catch (IOException exception) {
                Railroad.LOGGER.warn("Ignoring unreadable library stub cache for {}", archive, exception);
            }
        }

        List<JavaLibraryStubCache.ArchiveClass> parsed = new ArrayList<>();
        try (JarFile jarFile = new JarFile(archive.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
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
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library archive {}", archive, exception);
            return List.of();
        }

        if (cache != null) {
            try {
                cache.save(archive, size, lastModified, parsed);
            } catch (IOException | RuntimeException exception) {
                Railroad.LOGGER.warn("Failed to cache library stubs for {}", archive, exception);
            }
        }

        return toScannedClasses(archive, parsed, cancelled);
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
            scanned.add(new ScannedClass(archiveClass.stub(), archive, archiveEntry(prefix, archiveClass.entryName())));
        }

        return scanned;
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

    /** Characters a URI path keeps as they are; class entries rarely contain anything else. */
    private static boolean isUnreservedPathCharacter(char character) {
        return character >= 'a' && character <= 'z'
            || character >= 'A' && character <= 'Z'
            || character >= '0' && character <= '9'
            || "/-._~$!&'()*+,;=:@".indexOf(character) >= 0;
    }

    private static boolean isDescriptorClass(String name) {
        return name.endsWith("module-info.class") || name.endsWith("package-info.class");
    }

    private record ScannedClass(ClassStub stub, Path origin, DocumentUri location) {
    }

    /** Reads the declaration modifiers a library requests for its compile environment. */
    private static final class AccessDeclarationVisitor extends ClassStubVisitor {
        private static final Pattern RULE = Pattern.compile(
            "(public|protected|default|private)([+-]f)?\\s+(\\S+)(?:\\s+(\\S+))?");
        private static final List<Integer> VISIBILITY = List.of(Opcodes.ACC_PRIVATE, 0, Opcodes.ACC_PROTECTED,
            Opcodes.ACC_PUBLIC);
        private static final int ACCESS_MASK = Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
        private final List<Rule> rules = new ArrayList<>();

        private static Map<String, AccessDeclarationVisitor> read(Path archive, Runnable check) throws IOException {
            Map<String, AccessDeclarationVisitor> visitors = new HashMap<>();
            try (var jar = new JarFile(archive.toFile())) {
                var entry = jar.getJarEntry("META-INF/accesstransformer.cfg");
                if (entry == null)
                    return Map.of();
                try (var reader = new BufferedReader(
                    new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        check.run();
                        int comment = line.indexOf('#');
                        var match = RULE.matcher((comment < 0 ? line : line.substring(0, comment)).strip());
                        if (!match.matches())
                            continue;
                        int visibility = switch (match.group(1)) {
                            case "public" -> Opcodes.ACC_PUBLIC;
                            case "protected" -> Opcodes.ACC_PROTECTED;
                            case "private" -> Opcodes.ACC_PRIVATE;
                            default -> 0;
                        };
                        String target = match.group(4) == null ? "" : match.group(4).replace('.', '/');
                        visitors.computeIfAbsent(match.group(3), _ -> new AccessDeclarationVisitor()).rules
                            .add(new Rule(target, visibility, match.group(2)));
                    }
                }
            }
            return visitors;
        }

        @Override
        public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces
        ) {
            super.visit(version, modifiers(access, "", ""), name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return super.visitField(modifiers(access, name, "*"), name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
        ) {
            return super.visitMethod(modifiers(access, name + descriptor, "*()"), name, descriptor, signature,
                exceptions);
        }

        private int modifiers(int access, String target, String wildcard) {
            int visibility = VISIBILITY.indexOf(access & ACCESS_MASK);
            boolean addFinal = false;
            boolean removeFinal = false;
            for (Rule rule : rules) {
                if (rule.target().equals(target) || rule.target().equals(wildcard)) {
                    visibility = Math.max(visibility, VISIBILITY.indexOf(rule.visibility()));
                    addFinal |= "+f".equals(rule.finalChange());
                    removeFinal |= "-f".equals(rule.finalChange());
                }
            }
            int result = (access & ~ACCESS_MASK) | VISIBILITY.get(visibility);
            if (addFinal == removeFinal)
                return result;
            return addFinal ? result | Opcodes.ACC_FINAL : result & ~Opcodes.ACC_FINAL;
        }

        private record Rule(String target, int visibility, String finalChange) {
        }
    }
}
