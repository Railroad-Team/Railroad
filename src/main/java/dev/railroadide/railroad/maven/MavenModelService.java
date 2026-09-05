package dev.railroadide.railroad.maven;

import org.apache.maven.model.Model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Service for loading Maven models.
 */
public interface MavenModelService {
    /**
     * Loads the effective Maven model for the given project root.
     *
     * @param projectRoot the root path of the Maven project
     * @return an Optional containing the effective Model if it could be loaded, or an empty Optional if not
     */
    Optional<Model> loadEffectiveModel(Path projectRoot);
}
