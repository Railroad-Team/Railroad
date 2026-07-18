package dev.railroadide.railroad.maven;

import dev.railroadide.railroad.Railroad;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class DefaultMavenModelService implements MavenModelService {
    @Override
    @SuppressWarnings("deprecation")
    public Optional<Model> loadEffectiveModel(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");

        Path pomFile = projectRoot.resolve("pom.xml");
        if (Files.notExists(pomFile) || !Files.isRegularFile(pomFile) || !Files.isReadable(pomFile))
            return Optional.empty();

        try {
            ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
            ModelBuildingRequest request = new DefaultModelBuildingRequest()
                .setProcessPlugins(false)
                .setPomFile(pomFile.toFile())
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                .setTwoPhaseBuilding(false);

            ModelBuildingResult result = builder.build(request);
            return Optional.ofNullable(result.getEffectiveModel());
        } catch (ModelBuildingException exception) {
            Railroad.LOGGER.error("Error building Maven model from {}", pomFile, exception);
            return Optional.empty();
        } catch (Exception exception) {
            Railroad.LOGGER.error("Unexpected error while resolving Maven model for {}", projectRoot, exception);
            return Optional.empty();
        }
    }
}
