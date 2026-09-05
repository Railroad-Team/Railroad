package dev.railroadide.railroad.project.creation.service;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

/**
 * Provides file operations used to prepare and customize a project directory.
 */
public interface FilesService {
    /**
     * Creates a directory and any missing parent directories.
     *
     * @param path the directory to create
     * @throws IOException if the directories cannot be created
     */
    void createDirectories(Path path) throws IOException;

    /**
     * Deletes a directory and its contents recursively.
     *
     * @param dir the directory to delete
     * @throws IOException if deletion fails
     */
    void deleteDirectory(Path dir) throws IOException;

    /**
     * Moves a file or directory to a target path.
     *
     * @param source the existing path to move
     * @param target the destination path
     * @param options the options controlling the move
     * @throws IOException if the path cannot be moved
     */
    void move(Path source, Path target, CopyOption... options) throws IOException;

    /**
     * Copies a file or directory entry to a target path.
     *
     * @param source the existing path to copy
     * @param target the destination path
     * @param options the options controlling the copy
     * @throws IOException if the path cannot be copied
     */
    void copy(Path source, Path target, CopyOption... options) throws IOException;

    /**
     * Writes UTF-8 text to a file.
     *
     * @param file the destination file
     * @param content the text to write
     * @param options the options controlling how the file is opened
     * @throws IOException if the file cannot be written
     */
    void writeString(Path file, String content, OpenOption... options) throws IOException;

    /**
     * Reads a file as UTF-8 text.
     *
     * @param file the file to read
     * @return the complete file contents
     * @throws IOException if the file cannot be read
     */
    String readString(Path file) throws IOException;

    /**
     * Tests whether a path exists.
     *
     * @param path the path to test
     * @return {@code true} if the path is known to exist
     */
    boolean exists(Path path);

    /**
     * Tests whether a directory contains no entries.
     *
     * @param dir the directory to inspect
     * @return {@code true} if the directory is empty
     * @throws IOException if the directory cannot be inspected
     */
    boolean isDirectoryEmpty(Path dir) throws IOException;

    /**
     * Deletes a file or empty directory if it exists.
     *
     * @param path the path to delete
     * @throws IOException if an existing path cannot be deleted
     */
    void delete(Path path) throws IOException;

    /**
     * Updates a property's value in a properties file.
     *
     * @param path the properties file to update
     * @param key the property key
     * @param value the replacement property value
     * @throws IOException if the properties file cannot be read or written
     */
    void updateKeyPairInPropertiesFile(Path path, String key, String value) throws IOException;

    /**
     * Reads the lines of a UTF-8 file without line terminators.
     *
     * @param path the file to read
     * @return the file's lines in their original order
     * @throws IOException if the file cannot be read
     */
    List<String> readLines(Path path) throws IOException;

    /**
     * Creates a new empty file.
     *
     * @param path the file to create
     * @throws IOException if the file already exists or cannot be created
     */
    void createFile(Path path) throws IOException;

    /**
     * Recursively copies a directory's contents into another directory.
     *
     * @param src the directory whose contents are copied
     * @param dst the destination directory
     * @param options the options controlling the copies
     * @throws IOException if the contents cannot be read or copied
     */
    void extractDirectoryContents(Path src, Path dst, CopyOption... options) throws IOException;
}
