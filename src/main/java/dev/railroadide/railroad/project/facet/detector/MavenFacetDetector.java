package dev.railroadide.railroad.project.facet.detector;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.MavenFacetData;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects the presence of Maven build system support in a project directory by searching for pom.xml and extracting Maven coordinates.
 * This detector is used by the facet system to identify Maven projects and extract relevant configuration data.
 */
public class MavenFacetDetector implements FacetDetector<MavenFacetData> {
    private static final ModelBuilder BUILDER = new DefaultModelBuilderFactory().newInstance();

    /**
     * Detects a Maven facet in the given path by searching for pom.xml and extracting Maven coordinates.
     *
     * @param path the project directory to analyze
     * @return an Optional containing the Maven facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<MavenFacetData>> detect(@NotNull Path path) {
        Path pomFile = path.resolve("pom.xml");
        if (Files.notExists(pomFile) || !Files.isRegularFile(pomFile) || !Files.isReadable(pomFile))
            return Optional.empty();

        try {
            ModelBuildingRequest req = new DefaultModelBuildingRequest()
                    .setProcessPlugins(false)
                    .setPomFile(pomFile.toFile())
                    .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                    .setTwoPhaseBuilding(false);

            ModelBuildingResult result = BUILDER.build(req);
            Model effectiveModel = result.getEffectiveModel();
            if (effectiveModel == null)
                return Optional.empty();

            String groupId = effectiveModel.getGroupId();
            String artifactId = effectiveModel.getArtifactId();
            String version = effectiveModel.getVersion();

            var data = new MavenFacetData();
            data.setPomFilePath(pomFile.toString());
            data.setGroupId(groupId);
            data.setArtifactId(artifactId);
            data.setVersion(version);

            return Optional.of(new Facet<>(FacetManager.MAVEN, data));
        } catch (ModelBuildingException exception) {
            Railroad.LOGGER.error("Error building Maven model from pom.xml", exception);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Unexpected error while detecting Maven facet", exception);
        }

        return Optional.empty();
    }
}
