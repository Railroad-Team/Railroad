package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexer;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticExtractor;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

public final class JavaProjectLanguageIndexer implements ProjectLanguageIndexer<JavaProjectSemanticIndex, JavaProjectSemanticIndex.SourceFileIndex> {
    private final JavaProjectSemanticExtractor extractor = new JavaProjectSemanticExtractor();

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
    public JavaProjectSemanticIndex build(Path projectRoot, Collection<Path> sourceFiles) {
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();

        sourceFiles.stream()
            .filter(this::supports)
            .sorted(Comparator.naturalOrder())
            .forEach(path -> builder.putFile(indexFile(path, read(path))));

        return builder.build();
    }

    @Override
    public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, String sourceContent) {
        return extractor.extract(sourceFile, sourceContent);
    }

    @Override
    public JavaProjectSemanticIndex withUpdatedFile(JavaProjectSemanticIndex index, Path sourceFile, JavaProjectSemanticIndex.SourceFileIndex indexedFile) {
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

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read " + path, exception);
        }
    }
}
