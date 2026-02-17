package dev.railroadide.railroad.vcs.git.util;

import java.nio.file.Path;

/**
 * Represents a git repository by its normalized root path.
 *
 * @param root repository root path
 */
public record GitRepository(Path root) {
}
