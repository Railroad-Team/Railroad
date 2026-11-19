package dev.railroadide.railroad.ide.sst.project;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.io.IOException;
import java.util.Optional;

/** A Java symbol index whose source declarations come from captured document contents. */
public interface JavaSourceSnapshotIndex extends JavaSymbolIndex {
    /**
     * Returns captured source content without reading a live source file.
     *
     * @param path source path recorded by a symbol descriptor
     * @return captured text, or empty when this workspace contains no such source
     */
    Optional<String> sourceText(Path path);

    /**
     * Reads a dependency from its captured revision, or from disk for a legacy index.
     * A missing captured source never falls back to a newer filesystem revision.
     *
     * @param index symbol index owning the dependency
     * @param path source path recorded by its declaration
     * @return source text belonging to the index
     * @throws IOException if the source is unavailable
     */
    static String readSource(JavaSymbolIndex index, Path path) throws IOException {
        return index instanceof JavaSourceSnapshotIndex captured
            ? captured.sourceText(path).orElseThrow(() -> new NoSuchFileException(path.toString()))
            : Files.readString(path);
    }
}
