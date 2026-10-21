package dev.railroadide.railroad.project.creation.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Downloads and checks remote resources required to create a project.
 */
public interface HttpService {
    /**
     * Downloads a resource to a destination file.
     *
     * @param uri the remote resource URI
     * @param dest the file that will receive the resource
     * @throws IOException if the request or writing the destination fails
     */
    void download(URI uri, Path dest) throws IOException;

    /**
     * Checks if the URL returns 404 (or not found).
     *
     * @param uri the remote resource URI to check
     * @return {@code true} if the resource responds with HTTP 404
     * @throws IOException if the request fails
     */
    boolean isNotFound(URI uri) throws IOException;
}
