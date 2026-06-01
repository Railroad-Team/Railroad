package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.index.DefaultProjectIndexContextResolver;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JavaAnalysisContextProvider {
    private final DefaultProjectIndexContextResolver resolver = new DefaultProjectIndexContextResolver();
    private final ConcurrentMap<LibraryIndexKey, JavaLibrarySymbolIndex> libraryIndexes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, JavaJdkSymbolIndex> jdkIndexes = new ConcurrentHashMap<>();

    public @Nullable JavaSymbolIndex index(Project project) {
        return index(resolver.resolve(project));
    }

    public @Nullable JavaSymbolIndex index(ProjectIndexContext context) {
        Objects.requireNonNull(context, "context");

        JavaProjectSemanticIndex projectIndex =
            Services.PROJECT_LANGUAGE_INDEX_SERVICE.indexTyped(context, JavaLanguageSupport.LANGUAGE_ID);
        JavaLanguageIndexContext javaContext = context.language(JavaLanguageSupport.LANGUAGE_ID, JavaLanguageIndexContext.class);

        List<JavaSymbolIndex> delegates = new ArrayList<>();
        if (projectIndex != null)
            delegates.add(projectIndex);

        if (javaContext != null) {
            JavaLibrarySymbolIndex libraryIndex = libraryIndex(javaContext);
            if (!libraryIndex.declaredQualifiedNames().isEmpty())
                delegates.add(libraryIndex);

            JavaJdkSymbolIndex jdkIndex = jdkIndex(javaContext);
            if (!jdkIndex.declaredQualifiedNames().isEmpty())
                delegates.add(jdkIndex);

            logIndexSummary(javaContext, projectIndex, libraryIndex, jdkIndex);
        }

        if (delegates.isEmpty())
            return null;
        if (delegates.size() == 1)
            return delegates.getFirst();
        return new CompositeJavaSymbolIndex(delegates);
    }

    private JavaLibrarySymbolIndex libraryIndex(JavaLanguageIndexContext context) {
        Set<Path> roots = new LinkedHashSet<>();
        roots.addAll(context.dependencyRoots());
        roots.addAll(context.classpathRoots());
        roots.addAll(context.modulePathRoots());

        List<Path> normalizedRoots = FileUtils.normalizePaths(List.copyOf(roots));
        LibraryIndexKey key = new LibraryIndexKey(normalizedRoots);
        return libraryIndexes.computeIfAbsent(key, $ -> {
            Railroad.LOGGER.warn(
                "Building Java library index: roots={}, javafxBaseRoots={}, javafxGraphicsRoots={}, sampleRoots={}",
                normalizedRoots.size(),
                countRootsContaining(normalizedRoots, "javafx-base"),
                countRootsContaining(normalizedRoots, "javafx-graphics"),
                sampleRoots(normalizedRoots)
            );
            JavaLibrarySymbolIndex index = JavaLibrarySymbolIndex.build(normalizedRoots);
            Railroad.LOGGER.warn(
                "Built Java library index: classes={}, hasObservableList={}, hasReadOnlyDoubleProperty={}, hasNode={}, hasScene={}",
                index.declaredQualifiedNames().size(),
                index.classStubsByQualifiedName().containsKey("javafx.collections.ObservableList"),
                index.classStubsByQualifiedName().containsKey("javafx.beans.property.ReadOnlyDoubleProperty"),
                index.classStubsByQualifiedName().containsKey("javafx.scene.Node"),
                index.classStubsByQualifiedName().containsKey("javafx.scene.Scene")
            );
            return index;
        });
    }

    private JavaJdkSymbolIndex jdkIndex(JavaLanguageIndexContext context) {
        Path jdkHome = context.jdkHome();
        Path cacheKey = jdkHome == null ? Path.of(System.getProperty("java.home")).toAbsolutePath().normalize() : FileUtils.normalizePath(jdkHome);
        return jdkIndexes.computeIfAbsent(cacheKey, $ -> {
            Railroad.LOGGER.warn("Building Java JDK index: jdkHome={}, cacheKey={}", jdkHome, cacheKey);
            JavaJdkSymbolIndex index = JavaJdkSymbolIndex.build(jdkHome);
            Railroad.LOGGER.warn(
                "Built Java JDK index: classes={}, hasJavaUtilList={}, hasJavaLangObject={}, hasJavaUtilCollection={}",
                index.declaredQualifiedNames().size(),
                index.classStubsByQualifiedName().containsKey("java.util.List"),
                index.classStubsByQualifiedName().containsKey("java.lang.Object"),
                index.classStubsByQualifiedName().containsKey("java.util.Collection")
            );
            return index;
        });
    }

    private static void logIndexSummary(
        JavaLanguageIndexContext context,
        @Nullable JavaProjectSemanticIndex projectIndex,
        JavaLibrarySymbolIndex libraryIndex,
        JavaJdkSymbolIndex jdkIndex
    ) {
        Railroad.LOGGER.warn(
            "Java analysis index context: sourceRoots={}, generatedRoots={}, dependencyRoots={}, classpathRoots={}, modulePathRoots={}, jdkHome={}",
            context.sourceRoots().size(),
            context.generatedRoots().size(),
            context.dependencyRoots().size(),
            context.classpathRoots().size(),
            context.modulePathRoots().size(),
            context.jdkHome()
        );
        Railroad.LOGGER.warn(
            "Java analysis index availability: projectClasses={}, libraryClasses={}, jdkClasses={}, hasObservableList={}, hasList={}, hasNode={}, hasReadOnlyDoubleProperty={}",
            projectIndex == null ? 0 : projectIndex.declaredQualifiedNames().size(),
            libraryIndex.declaredQualifiedNames().size(),
            jdkIndex.declaredQualifiedNames().size(),
            libraryIndex.classStubsByQualifiedName().containsKey("javafx.collections.ObservableList"),
            jdkIndex.classStubsByQualifiedName().containsKey("java.util.List"),
            libraryIndex.classStubsByQualifiedName().containsKey("javafx.scene.Node"),
            libraryIndex.classStubsByQualifiedName().containsKey("javafx.beans.property.ReadOnlyDoubleProperty")
        );
    }

    private static long countRootsContaining(List<Path> roots, String text) {
        return roots.stream()
            .map(Path::toString)
            .filter(path -> path.toLowerCase().contains(text.toLowerCase()))
            .count();
    }

    private static List<String> sampleRoots(List<Path> roots) {
        return Stream.concat(
                roots.stream().filter(path -> path.toString().toLowerCase().contains("javafx")).limit(8),
                roots.stream().limit(4)
            )
            .map(Path::toString)
            .distinct()
            .limit(12)
            .toList();
    }

    private record LibraryIndexKey(List<Path> roots) {
        private LibraryIndexKey(List<Path> roots) {
            this.roots = List.copyOf(roots);
        }
    }
}
