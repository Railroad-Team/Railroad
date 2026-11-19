package dev.railroadide.railroad.ide.sst.provider.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Immutable, bounded recognition input. Matchers never need to open arbitrary files. */
public final class LanguageMatchInput {
    /**
     * Maximum number of leading content bytes retained for language recognition.
     */
    public static final int MAX_PREFIX_BYTES = 8192;
    private final Path path;
    private final byte[] prefix;

    /**
     * Creates recognition input, copying and truncating the content prefix to {@link #MAX_PREFIX_BYTES}.
     *
     * @param path non-null file path to recognize
     * @param prefix non-null leading file bytes, or an empty array when unavailable
     */
    public LanguageMatchInput(Path path, byte[] prefix) {
        this.path = Objects.requireNonNull(path, "path");
        Objects.requireNonNull(prefix, "prefix");
        this.prefix = Arrays.copyOf(prefix, Math.min(prefix.length, MAX_PREFIX_BYTES));
    }

    /**
     * Returns the path supplied for recognition.
     *
     * @return the original file path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the final name component used by filename matchers.
     *
     * @return the file name without parent directories
     */
    public String fileName() {
        return path.getFileName().toString();
    }

    /**
     * Returns the captured content prefix without exposing its backing array.
     *
     * @return a defensive copy of at most {@link #MAX_PREFIX_BYTES} bytes
     */
    public byte[] prefix() {
        return Arrays.copyOf(prefix, prefix.length);
    }

    /**
     * Decodes the bounded content prefix as UTF-8.
     *
     * @return the decoded prefix, with replacement characters for malformed byte sequences
     */
    public String textPrefix() {
        return new String(prefix, StandardCharsets.UTF_8);
    }
}
