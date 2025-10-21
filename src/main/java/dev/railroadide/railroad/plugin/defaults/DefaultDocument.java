package dev.railroadide.railroad.plugin.defaults;

import dev.railroadide.railroadpluginapi.dto.Document;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DefaultDocument implements Document {
    private final String name;
    private final Path path;
    @Setter
    private boolean dirty = false;

    public DefaultDocument(String name, Path path) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Document name cannot be null or empty");
        if (path == null || Files.notExists(path) || !Files.isRegularFile(path))
            throw new IllegalArgumentException("Invalid document path: " + path);

        this.name = name;
        this.path = path;
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
        try(Stream<String> lines = Files.lines(this.path)) {
            return lines.count();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read document content from: " + this.path, exception);
        }
    }

    @Override
    public String getLanguageId() {
        return this.name.contains(".") ?
                this.name.substring(this.name.lastIndexOf('.') + 1) :
                "plaintext";
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }
}
