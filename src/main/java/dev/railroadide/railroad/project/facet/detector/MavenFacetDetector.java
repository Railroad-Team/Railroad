package dev.railroadide.railroad.project.facet.detector;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.maven.DefaultMavenModelService;
import dev.railroadide.railroad.maven.MavenModelService;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.MavenFacetData;
import org.apache.maven.model.Model;
import org.jetbrains.annotations.UnknownNullability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects the presence of Maven build system support in a project directory by searching for pom.xml and extracting
 * Maven coordinates.
 * This detector is used by the facet system to identify Maven projects and extract relevant configuration data.
 */
public class MavenFacetDetector implements FacetDetector<MavenFacetData> {
    private static final MavenModelService MAVEN_MODELS = new DefaultMavenModelService();

    /**
     * Detects a Maven facet in the given path by searching for pom.xml and extracting Maven coordinates.
     *
     * @param project the project to inspect
     * @return an Optional containing the Maven facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<MavenFacetData>> detect(@UnknownNullability Project project) {
        Path pomFile = project.path().resolve("pom.xml");
        if (Files.notExists(pomFile) || !Files.isRegularFile(pomFile) || !Files.isReadable(pomFile))
            return Optional.empty();

        try {
            Optional<Model> maybeModel = MAVEN_MODELS.loadEffectiveModel(project.path());
            if (maybeModel.isEmpty())
                return Optional.empty();

            Model effectiveModel = maybeModel.get();

            String groupId = effectiveModel.getGroupId();
            String artifactId = effectiveModel.getArtifactId();
            String version = effectiveModel.getVersion();

            var data = new MavenFacetData();
            data.setPomFilePath(pomFile.toString());
            data.setGroupId(groupId);
            data.setArtifactId(artifactId);
            data.setVersion(version);

            return Optional.of(new Facet<>(FacetManager.MAVEN, data));
        } catch (Exception exception) {
            Railroad.LOGGER.error("Unexpected error while detecting Maven facet", exception);
        }

        return Optional.empty();
    }
}
