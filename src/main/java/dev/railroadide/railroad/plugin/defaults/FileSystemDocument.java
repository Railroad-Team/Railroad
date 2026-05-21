package dev.railroadide.railroad.plugin.defaults;

import dev.railroadide.railroad.plugin.spi.dto.Document;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileSystemDocument implements Document {
    private final String name;
    private final Path path;
    private final String languageId;
    @Setter
    private boolean dirty = false;

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
}
