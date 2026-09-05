package dev.railroadide.railroad.plugin.defaults;

import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A document backed by an existing regular file, with content read from disk on demand.
 * Its display name and path can be rebound after the file is moved.
 */
public class FileSystemDocument implements Document {
    private String name;
    private Path path;
    private final String languageId;
    @Setter
    private boolean dirty = false;

    /**
     * Creates a file-backed document with an explicit display name and language.
     *
     * @param name nonblank document display name
     * @param path path to an existing regular file
     * @param languageId nonblank language identifier
     * @throws IllegalArgumentException if the name or language is null or blank, or the path is invalid
     */
    public FileSystemDocument(String name, Path path, String languageId) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Document name cannot be null or empty");
        if (path == null || Files.notExists(path) || !Files.isRegularFile(path))
            throw new IllegalArgumentException("Invalid document path: " + path);
        if (languageId == null || languageId.isBlank())
            throw new IllegalArgumentException("Language ID cannot be null or empty");

        this.name = name;
        this.path = path;
        this.languageId = languageId;
    }

    /**
     * Creates a document using the file name and the language resolved from its path.
     *
     * @param path path to an existing regular file
     */
    public FileSystemDocument(Path path) {
        this(path.getFileName().toString(), path, LanguageSupportRegistry.resolveLanguageId(path));
    }

    /**
     * Creates a document using the file name and an explicit language.
     *
     * @param path path to an existing regular file
     * @param languageId nonblank language identifier
     */
    public FileSystemDocument(Path path, String languageId) {
        this(path.getFileName().toString(), path, languageId);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Path getPath() {
        return this.path;
    }

    @Override
    public byte[] getContent() {
        try {
            return Files.readAllBytes(this.path);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read document content from: " + this.path, exception);
        }
    }

    @Override
    public long getLineCount() {
        try (Stream<String> lines = Files.lines(this.path)) {
            return lines.count();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read document content from: " + this.path, exception);
        }
    }

    @Override
    public String getLanguageId() {
        return this.languageId;
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Updates this document's location and display name after the backing file has been moved.
     *
     * @param path new location, stored as an absolute normalized path
     * @throws IllegalArgumentException if the path is null or does not refer to an existing regular file
     */
    public void rebind(Path path) {
        if (path == null || Files.notExists(path) || !Files.isRegularFile(path))
            throw new IllegalArgumentException("Invalid document path: " + path);

        this.path = path.toAbsolutePath().normalize();
        this.name = this.path.getFileName().toString();
    }
}
