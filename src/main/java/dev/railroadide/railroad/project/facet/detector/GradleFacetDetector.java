package dev.railroadide.railroad.project.facet.detector;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.GradleFacetData;

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
     * @param project the project to inspect
     * @return an Optional containing the Gradle facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<GradleFacetData>> detect(RailroadProject project) {
        for (String buildFile : BUILD_FILES) {
            Path buildFilePath = project.getPath().resolve(buildFile);
            if (Files.exists(buildFilePath)) {
                var data = new GradleFacetData(); // TODO: Come back to this and figure out what data it should store
                return Optional.of(new Facet<>(FacetManager.GRADLE, data));
            }
        }

        return Optional.empty();
    }
}
