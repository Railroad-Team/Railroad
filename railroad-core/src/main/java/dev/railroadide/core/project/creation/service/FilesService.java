package dev.railroadide.core.project.creation.service;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

public interface FilesService {
    void createDirectories(Path path) throws IOException;

    void deleteDirectory(Path dir) throws IOException;

    void move(Path source, Path target, CopyOption... options) throws IOException;

    void copy(Path source, Path target, CopyOption... options) throws IOException;

    void writeString(Path file, String content, OpenOption... options) throws IOException;

    String readString(Path file) throws IOException;

    boolean exists(Path path);

    boolean isDirectoryEmpty(Path dir) throws IOException;

    void delete(Path path) throws IOException;

    void updateKeyPairInPropertiesFile(Path path, String key, String value) throws IOException;

    List<String> readLines(Path path) throws IOException;

    void createFile(Path path) throws IOException;
}
