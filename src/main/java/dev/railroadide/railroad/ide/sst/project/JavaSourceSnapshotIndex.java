package dev.railroadide.railroad.ide.sst.project;

import java.nio.file.Path;
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
}
