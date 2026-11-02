package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.project.*;
import java.nio.file.Path;
import java.util.*;

/** Builds reusable Java analysis inputs exclusively from captured documents and configuration. */
public final class JavaWorkspaceAnalysisProvider {
    private static final Path RUNNING_JDK = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
    private final Map<WorkspaceSnapshot, JavaWorkspaceAnalysis> workspaces = new WeakHashMap<>();
    private final Map<TextDocumentSnapshot, JavaProjectSemanticIndex.SourceFileIndex> files = new WeakHashMap<>();
    private final Map<WorkspaceConfigurationSnapshot, JavaSymbolIndex> binaries = new WeakHashMap<>();
    private final Map<Path, JavaJdkSymbolIndex> jdks = new HashMap<>();

    /** Creates a cache whose weak keys allow released workspace and document captures to be reclaimed. */
    public JavaWorkspaceAnalysisProvider() {
    }

    /**
     * Pins Java source declarations, source bodies, and binary symbols for a request workspace.
     * This method runs on a language worker; it never consults project models or the live source index.
     * Binary artifacts are read when their captured configuration is first analyzed and remain pinned
     * while that configuration is retained. External artifact replacement requires configuration refresh.
     *
     * @param workspace immutable workspace captured for the feature request
     * @return immutable analysis inputs shared by requests using that capture
     */
    public synchronized JavaWorkspaceAnalysis capture(WorkspaceSnapshot workspace) {
        Objects.requireNonNull(workspace);
        return workspaces.computeIfAbsent(workspace, this::build);
    }

    private JavaWorkspaceAnalysis build(WorkspaceSnapshot workspace) {
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        Map<Path, String> texts = new HashMap<>();
        var indexer = new JavaProjectSemanticIndexer();
        for (DocumentSnapshot document : workspace.documents().values()) {
            if (!(document instanceof TextDocumentSnapshot text) || !"java".equals(text.languageId()))
                continue;
            Path path = text.uri().filePath().orElseGet(() -> Path.of(".railroad", "virtual", text.id().toString(),
                text.uri().fileName().orElse("Snapshot.java")).toAbsolutePath().normalize());
            JavaProjectSemanticIndex.SourceFileIndex indexed = files.computeIfAbsent(text,
                _ -> indexer.indexFile(path, text.text()));
            builder.putFile(indexed);
            texts.put(indexed.path(), text.text());
        }
        JavaProjectSemanticIndex sources = builder.build();
        WorkspaceConfigurationSnapshot configuration = workspace.configuration();
        List<Path> classpath = classpath(configuration);
        Path jdk = jdkHome(configuration);
        JavaSymbolIndex binary = binaries.computeIfAbsent(configuration, _ -> new CompositeJavaSymbolIndex(List.of(
            JavaLibrarySymbolIndex.build(classpath), jdks.computeIfAbsent(jdk, JavaJdkSymbolIndex::build))));
        return new JavaWorkspaceAnalysis(sources, new CompositeJavaSymbolIndex(List.of(sources, binary)), texts,
            classpath, jdk);
    }

    private static List<Path> classpath(WorkspaceConfigurationSnapshot configuration) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        Set<Path> outputs = new HashSet<>();
        for (String module : configuration.moduleIds()) {
            for (String kind : List.of("output", "testOutput")) {
                String output = configuration.settings().get("module." + module + "." + kind);
                if (output != null && !output.isBlank()) {
                    outputs.add(Path.of(output).toAbsolutePath().normalize());
                }
            }
        }
        for (String module : configuration.moduleIds().stream().sorted().toList()) {
            for (String kind : List.of("classpath", "modulePath", "dependencies")) {
                String value = configuration.settings().get("module." + module + "." + kind);
                if (value != null) {
                    value.lines().filter(line -> !line.isBlank()).map(Path::of)
                        .map(path -> path.toAbsolutePath().normalize()).filter(path -> !outputs.contains(path))
                        .forEach(roots::add);
                }
            }
        }
        return List.copyOf(roots);
    }

    private static Path jdkHome(WorkspaceConfigurationSnapshot configuration) {
        return configuration.moduleIds().stream().sorted()
            .map(module -> configuration.settings().get("module." + module + ".jdkHome"))
            .filter(Objects::nonNull).filter(home -> !home.isBlank()).findFirst()
            .map(Path::of).map(path -> path.toAbsolutePath().normalize()).orElse(RUNNING_JDK);
    }
}
