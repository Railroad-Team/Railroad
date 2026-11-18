package dev.railroadide.railroad.ide.edit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Filesystem operations performed by workspace edits. Each call is a single, non-transactional filesystem request;
 * implementations other than {@link #NIO} exist to observe or inject failures.
 */
public interface WorkspaceFiles {
    /** Direct NIO operations. */
    WorkspaceFiles NIO = new WorkspaceFiles() {
        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return Files.isDirectory(path);
        }

        @Override
        public boolean isWritable(Path path) {
            return Files.isWritable(path);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public void overwrite(Path path, byte[] content) throws IOException {
            Files.write(path, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        @Override
        public void createFile(Path path, byte[] content) throws IOException {
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        @Override
        public void createDirectory(Path path) throws IOException {
            Files.createDirectory(path);
        }

        @Override
        public void delete(Path path) throws IOException {
            Files.delete(path);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            Files.move(source, target);
        }
    };

    /**
     * Reports whether a file or directory exists.
     *
     * @param path location to check
     * @return whether it exists
     */
    boolean exists(Path path);

    /**
     * Reports whether a location is an existing directory.
     *
     * @param path location to check
     * @return whether it is a directory
     */
    boolean isDirectory(Path path);

    /**
     * Reports whether a file or directory is writable.
     *
     * @param path location to check
     * @return whether it may be written
     */
    boolean isWritable(Path path);

    /**
     * Reads a file's content.
     *
     * @param path file to read
     * @return file bytes
     * @throws IOException if reading fails
     */
    byte[] read(Path path) throws IOException;

    /**
     * Replaces an existing file's content in place. This is not crash-atomic.
     *
     * @param path existing file
     * @param content new content
     * @throws IOException if writing fails
     */
    void overwrite(Path path, byte[] content) throws IOException;

    /**
     * Creates a file that must not already exist.
     *
     * @param path new file
     * @param content initial content
     * @throws IOException if the file exists or writing fails
     */
    void createFile(Path path, byte[] content) throws IOException;

    /**
     * Creates one directory whose parent exists.
     *
     * @param path new directory
     * @throws IOException if creation fails
     */
    void createDirectory(Path path) throws IOException;

    /**
     * Deletes a file or empty directory.
     *
     * @param path location to delete
     * @throws IOException if deletion fails
     */
    void delete(Path path) throws IOException;

    /**
     * Moves a file without replacing an existing target.
     *
     * @param source existing file
     * @param target destination that must not exist
     * @throws IOException if moving fails
     */
    void move(Path source, Path target) throws IOException;
}
