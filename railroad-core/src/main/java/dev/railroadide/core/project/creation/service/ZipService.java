package dev.railroadide.core.project.creation.service;

import java.io.IOException;
import java.nio.file.Path;

public interface ZipService {
    void unzip(Path zipFile, Path targetDir) throws IOException;
}
