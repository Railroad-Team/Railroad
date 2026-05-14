package dev.railroadide.railroad.plugin.spi.deps;

import org.jspecify.annotations.NonNull;

/**
 * Represents a Maven dependency with its group ID, artifact ID, and version.
 * This class provides methods to create a Maven dependency from its full name
 * and to retrieve its full name in the standard Maven format.
 */
public record MavenDep(String groupId, String artifactId, String version) {
    /**
     * Constructs a Maven dependency from its full name in the format "groupId:artifactId:version".
     *
     * @param fullName the full name of the dependency
     * @return a new MavenDep instance
     * @throws IllegalArgumentException if the full name is not in the correct format
     */
    public static MavenDep fromFullName(String fullName) {
        String[] parts = fullName.split(":");
        if (parts.length != 3)
            throw new IllegalArgumentException("Invalid Maven dependency format: " + fullName);

        return new MavenDep(parts[0], parts[1], parts[2]);
    }

    /**
     * Constructs a Maven dependency from its group ID, artifact ID, and version.
     *
     * @param groupId    the group ID of the dependency
     * @param artifactId the artifact ID of the dependency
     * @param version    the version of the dependency
     * @throws IllegalArgumentException if any of the parameters are null or empty,
     */
    public MavenDep {
        if (groupId == null || artifactId == null || version == null)
            throw new IllegalArgumentException("Group ID, artifact ID, and version must not be null");

        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty())
            throw new IllegalArgumentException("Group ID, artifact ID, and version must not be empty");

        if (!groupId.matches("[a-zA-Z0-9._-]+") || !artifactId.matches("[a-zA-Z0-9._-]+") || !version.matches("[a-zA-Z0-9._-]+"))
            throw new IllegalArgumentException("Group ID, artifact ID, and version must match the Maven naming conventions");
    }

    /**
     * Returns the full name of the Maven dependency in the format "groupId:artifactId:version".
     *
     * @return the full name of the dependency
     */
    public String getFullName() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public @NonNull String toString() {
        return getFullName();
    }
}
