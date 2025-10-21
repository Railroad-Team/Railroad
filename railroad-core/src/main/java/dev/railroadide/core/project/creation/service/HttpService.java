package dev.railroadide.core.project.creation.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public interface HttpService {
    /**
     * Downloads a resource to a destination file.
     */
    void download(URI uri, Path dest) throws IOException;

    /**
     * Checks if the URL returns 404 (or not found).
     */
    boolean isNotFound(URI uri) throws IOException;
}
