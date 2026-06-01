package dev.railroadide.railroad.maven;

import org.apache.maven.model.Model;

import java.nio.file.Path;
import java.util.Optional;

public interface MavenModelService {
    Optional<Model> loadEffectiveModel(Path projectRoot);
}
