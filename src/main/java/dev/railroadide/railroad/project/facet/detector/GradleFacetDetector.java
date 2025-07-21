package dev.railroadide.railroad.project.facet.detector;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.GradleFacetData;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Detects the presence of Gradle build system support in a project directory by searching for build.gradle or build.gradle.kts files.
 * This detector is used by the facet system to identify Gradle projects and extract relevant configuration data.
 */
public class GradleFacetDetector implements FacetDetector<GradleFacetData> {
    public static final List<String> BUILD_FILES = List.of("build.gradle", "build.gradle.kts");

    /**
     * Detects a Gradle facet in the given path by searching for build.gradle or build.gradle.kts files and reading Gradle version info.
     *
     * @param path the project directory to analyze
     * @return an Optional containing the Gradle facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<GradleFacetData>> detect(@NotNull Path path) {
        for (String buildFile : BUILD_FILES) {
            Path buildFilePath = path.resolve(buildFile);
            if (Files.exists(buildFilePath)) {
                var data = new GradleFacetData();
                String buildFilePathStr = buildFilePath.toString();
                boolean isKts = buildFile.endsWith(".kts");
                String gradleVersion;
                Optional<Path> wrapperProperties = findWrapperProperties(path);
                if (wrapperProperties.isPresent()) {
                    try {
                        List<String> lines = Files.readAllLines(wrapperProperties.get());
                        gradleVersion = parseGradleVersion(lines);
                    } catch (Exception exception) {
                        Railroad.LOGGER.error("Error reading gradle-wrapper.properties", exception);
                        gradleVersion = null;
                    }
                } else {
                    gradleVersion = null;
                }

                data.setGradleVersion(gradleVersion);
                data.setBuildFilePath(buildFilePathStr);
                data.setKts(isKts);

                return Optional.of(new Facet<>(FacetManager.GRADLE, data));
            }
        }

        return Optional.empty();
    }

    /**
     * Finds the gradle-wrapper.properties file in the project directory, if present.
     *
     * @param path the project directory
     * @return an Optional containing the path to gradle-wrapper.properties, or empty if not found
     */
    private Optional<Path> findWrapperProperties(@NotNull Path path) {
        Path wrapperProperties = path.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (Files.exists(wrapperProperties)) {
            return Optional.of(wrapperProperties);
        }

        return Optional.empty();
    }

    /**
     * Parses the Gradle version from the lines of a gradle-wrapper.properties file.
     *
     * @param lines the lines of the properties file
     * @return the Gradle version string, or null if not found
     */
    private String parseGradleVersion(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("distributionUrl=")) {
                String url = line.substring("distributionUrl=".length());
                String[] parts = url.split("/");
                if (parts.length > 1) {
                    return parts[parts.length - 1].replace("gradle-", "").replace(".zip", "");
                }
            }
        }

        return null;
    }
}
