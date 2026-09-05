package dev.railroadide.railroad.project.creation.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extracts ZIP archives used as project templates.
 */
public interface ZipService {
    /**
     * Extracts an archive into the target directory.
     *
     * @param zipFile the ZIP archive to extract
     * @param targetDir the destination directory
     * @throws IOException if the archive cannot be read or its contents cannot be written
     */
    void unzip(Path zipFile, Path targetDir) throws IOException;
}
