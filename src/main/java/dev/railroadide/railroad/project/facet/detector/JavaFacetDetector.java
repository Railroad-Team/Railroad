package dev.railroadide.railroad.project.facet.detector;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.JavaFacetData;
import dev.railroadide.railroad.utility.JavaVersion;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.*;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.gradle.tooling.model.java.InstalledJdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Detects the presence of Java support in a project directory by searching for Java source files and determining the Java version.
 * This detector is used by the facet system to identify Java projects and extract relevant configuration data.
 */
public class JavaFacetDetector implements FacetDetector<JavaFacetData> {
    /**
     * Attempts to determine the most reliable Java version for the given project path.
     * Checks Gradle, Maven, compiled class files, and system properties in order.
     *
     * @param project the project
     * @return the detected JavaVersion, or an invalid version if not found
     */
    private static JavaVersion findMostReliableJavaVersion(@NotNull Project project) {
        JavaVersion gradleVersion = getJavaVersionFromGradle(project);
        if (gradleVersion.major() != -1)
            return gradleVersion;

        Path path = project.getPath();
        if (path == null) {
            Railroad.LOGGER.warn("Project path is null for project: {}", project.getAlias());
            return JavaVersion.fromMajor(-1);
        }

        JavaVersion mavenVersion = getJavaVersionFromMaven(path);
        if (mavenVersion.major() != -1)
            return mavenVersion;

        String systemVersionStr = System.getProperty("java.version");
        JavaVersion systemVersion = JavaVersion.fromReleaseString(systemVersionStr);
        if (systemVersion.major() != -1)
            return systemVersion;

        Railroad.LOGGER.warn("No reliable Java version found for path: {}", path);
        return JavaVersion.fromMajor(-1);
    }

    /**
     * Attempts to extract the Java version from a Gradle project by connecting to the build and reading configuration.
     *
     * @param project the project
     * @return the JavaVersion specified in the Gradle build, or an invalid version if not found
     */
    private static JavaVersion getJavaVersionFromGradle(@NotNull Project project) {
        if (!project.getGradleManager().isGradleProject())
            return JavaVersion.fromMajor(-1);

        return project.getGradleManager().getGradleModelService().getCachedModel()
            .map(gradleBuildModel -> {
                try {
                    InstalledJdk jdk = gradleBuildModel.project().javaLanguageSettings().getJdk();
                    return JavaVersion.fromMajor(Integer.parseInt(jdk.getJavaVersion().getMajorVersion()));
                } catch (NumberFormatException exception) {
                    Railroad.LOGGER.error("Error parsing Java version from Gradle model for project: {}", project.getAlias(), exception);
                    return JavaVersion.fromMajor(-1);
                }
            })
            .orElse(JavaVersion.fromMajor(-1));
    }

    /**
     * Attempts to extract the Java version from a Maven project by reading the pom.xml and plugins.
     *
     * @param projectDir the project directory
     * @return the JavaVersion specified in the Maven build, or an invalid version if not found
     */
    private static JavaVersion getJavaVersionFromMaven(Path projectDir) {
        Path pom = projectDir.resolve("pom.xml");
        if (!Files.isReadable(pom))
            return JavaVersion.fromMajor(-1);

        try {
            // TODO: Look into replacement of these as they are deprecated(?)
            ModelBuildingRequest req = new DefaultModelBuildingRequest()
                .setProcessPlugins(false)
                .setPomFile(pom.toFile());

            ModelBuildingResult res = new DefaultModelBuilderFactory()
                .newInstance()
                .build(req);

            Model model = res.getEffectiveModel();

            // 1) look in <properties>
            String src = model.getProperties().getProperty("maven.compiler.source");
            String tgt = model.getProperties().getProperty("maven.compiler.target");

            // 2) if still null, look at <build><plugin> entries
            if ((src == null || tgt == null) && model.getBuild() != null) {
                Build build = model.getBuild();
                for (Plugin plugin : build.getPlugins()) {
                    if ("org.apache.maven.plugins:maven-compiler-plugin"
                        .equals(plugin.getGroupId() + ":" + plugin.getArtifactId())) {
                        var cfg = (XmlPlexusConfiguration)
                            plugin.getConfiguration();
                        if (src == null && cfg.getChild("source") != null) {
                            src = cfg.getChild("source").getValue();
                        }
                        if (tgt == null && cfg.getChild("target") != null) {
                            tgt = cfg.getChild("target").getValue();
                        }

                        break;
                    }
                }
            }

            // fall back defaults
            if (src == null) src = "1.8";
            if (tgt == null) tgt = src;

            JavaVersion sourceVer = JavaVersion.fromReleaseString(src);
            JavaVersion targetVer = JavaVersion.fromReleaseString(tgt);

            return sourceVer.compareTo(targetVer) >= 0 ? sourceVer : targetVer;
        } catch (ModelBuildingException | PlexusConfigurationException exception) {
            Railroad.LOGGER.error("Error reading POM for Java version: {}", projectDir, exception);
            return JavaVersion.fromMajor(-1);
        }
    }

    /**
     * Detects a Java facet in the given path by searching for .java files and determining the Java version.
     *
     * @param project the project
     * @return an Optional containing the Java facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<JavaFacetData>> detect(@UnknownNullability Project project) {
        boolean hasJavaFiles = false;
        try {
            try (Stream<Path> javaFiles = Files.find(project.getPath(), 10,
                (p, attrs) -> p.toString().endsWith(".java"))) {
                hasJavaFiles = javaFiles.findAny().isPresent();
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error while detecting Java files in path: {}", project.getPath(), exception);
        }

        JavaFacetData data = null;
        if (hasJavaFiles) {
            data = new JavaFacetData();
            JavaVersion highestJavaVersion = findMostReliableJavaVersion(project);
            data.setVersion(highestJavaVersion);
        }

        return hasJavaFiles ?
            Optional.of(new Facet<>(FacetManager.JAVA, data)) :
            Optional.empty();
    }
}
