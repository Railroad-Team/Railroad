package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.railroad.utility.FileUtils;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

public class NioFilesService implements FilesService {
    @Override
    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    @Override
    public void deleteDirectory(Path dir) throws RuntimeException {
        FileUtils.deleteFolder(dir);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        Files.move(source, target, options);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        Files.copy(source, target, options);
    }

    @Override
    public void writeString(Path file, String content, OpenOption... options) throws IOException {
        Files.writeString(file, content, options);
    }

    @Override
    public String readString(Path file) throws IOException {
        return Files.readString(file);
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public boolean isDirectoryEmpty(Path dir) {
        return FileUtils.isDirectoryEmpty(dir);
    }

    @Override
    public void delete(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    @Override
    public void updateKeyPairInPropertiesFile(Path path, String key, String value) throws IOException {
        FileUtils.updateKeyValuePair(key, value, path);
    }

    @Override
    public List<String> readLines(Path path) throws IOException {
        return Files.readAllLines(path);
    }

    @Override
    public void createFile(Path path) throws IOException {
        Files.createFile(path);
    }

    @Override
    public void extractDirectoryContents(Path src, Path dst, CopyOption... options) throws IOException {
        FileUtils.copyDirectoryContents(src, dst, options);
    }
}
