package io.github.railroad.project.facet.detector;

import io.github.railroad.Railroad;
import io.github.railroad.project.facet.Facet;
import io.github.railroad.project.facet.FacetDetector;
import io.github.railroad.project.facet.FacetManager;
import io.github.railroad.project.facet.data.GradleFacetData;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class GradleFacetDetector implements FacetDetector<GradleFacetData> {
    public static final List<String> BUILD_FILES = List.of("build.gradle", "build.gradle.kts");

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

    private Optional<Path> findWrapperProperties(@NotNull Path path) {
        Path wrapperProperties = path.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (Files.exists(wrapperProperties)) {
            return Optional.of(wrapperProperties);
        }

        return Optional.empty();
    }

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
