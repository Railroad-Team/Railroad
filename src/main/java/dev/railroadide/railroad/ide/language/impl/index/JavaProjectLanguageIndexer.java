package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexer;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/**
 * Builds and incrementally updates semantic indexes for Java project source files.
 */
public final class JavaProjectLanguageIndexer
    implements
        ProjectLanguageIndexer<JavaProjectSemanticIndex, JavaProjectSemanticIndex.SourceFileIndex> {
    private final JavaProjectSemanticIndexer indexer = new JavaProjectSemanticIndexer();

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java");
    }

    @Override
    public JavaProjectSemanticIndex build(ProjectIndexContext context, Collection<Path> sourceFiles) {
        return indexer.build(sourceFiles.stream()
            .filter(this::supports)
            .sorted(Comparator.naturalOrder())
            .toList());
    }

    @Override
    public JavaProjectSemanticIndex.SourceFileIndex indexFile(
        ProjectIndexContext context,
        Path sourceFile,
        String sourceContent
    ) {
        return indexer.indexFile(sourceFile, sourceContent);
    }

    @Override
    public JavaProjectSemanticIndex withUpdatedFile(
        JavaProjectSemanticIndex index,
        Path sourceFile,
        JavaProjectSemanticIndex.SourceFileIndex indexedFile
    ) {
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        index.files().forEach((path, fileIndex) -> {
            if (!path.equals(sourceFile)) {
                builder.putFile(fileIndex);
            }
        });

        builder.putFile(indexedFile);
        return builder.build();
    }

    @Override
    public JavaProjectSemanticIndex withRemovedFile(JavaProjectSemanticIndex index, Path sourceFile) {
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        index.files().forEach((path, fileIndex) -> {
            if (!path.equals(sourceFile)) {
                builder.putFile(fileIndex);
            }
        });

        return builder.build();
    }
}
