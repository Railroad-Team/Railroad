package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.railroad.utility.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

public class NioZipService implements ZipService {
    @Override
    public void unzip(Path zipFile, Path targetDir) throws IOException {
        FileUtils.unzipFile(zipFile, targetDir);
    }
}
