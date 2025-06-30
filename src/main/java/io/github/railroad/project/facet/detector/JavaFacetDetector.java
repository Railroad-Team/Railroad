package io.github.railroad.project.facet.detector;

import io.github.railroad.Railroad;
import io.github.railroad.javaversion.model.JavaVersionModel;
import io.github.railroad.project.facet.Facet;
import io.github.railroad.project.facet.FacetDetector;
import io.github.railroad.project.facet.FacetManager;
import io.github.railroad.project.facet.data.JavaFacetData;
import io.github.railroad.utility.JavaVersion;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.*;
import org.apache.maven.shared.utils.xml.pull.XmlPullParserException;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.gradle.api.GradleException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class JavaFacetDetector implements FacetDetector<JavaFacetData> {
    @Override
    public Optional<Facet<JavaFacetData>> detect(@NotNull Path path) {
        long javaFileCount = 0;
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> javaFiles = Files.find(path, 10,
                        (p, attrs) -> p.toString().endsWith(".java"))) {
                    javaFileCount = javaFiles.count();
                }
            } else if (path.toString().endsWith(".java")) {
                javaFileCount = 1;
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error while detecting Java files in path: {}", path, exception);
        }

        JavaFacetData data = null;
        if (javaFileCount > 0) {
            data = new JavaFacetData();
            JavaVersion highestJavaVersion = findMostReliableJavaVersion(path);
            data.setVersion(highestJavaVersion);
        }

        return javaFileCount > 0 ?
                Optional.of(new Facet<>(FacetManager.JAVA, data)) :
                Optional.empty();
    }

    private static JavaVersion findMostReliableJavaVersion(@NotNull Path path) {
        JavaVersion gradleVersion = getJavaVersionFromGradle(path);
        if (gradleVersion.major() != -1)
            return gradleVersion;

        JavaVersion mavenVersion = getJavaVersionFromMaven(path);
        if (mavenVersion.major() != -1)
            return mavenVersion;

        JavaVersion classVersion = findHighestJavaVersionForClasses(path);
        if (classVersion.major() != -1)
            return classVersion;

        String systemVersionStr = System.getProperty("java.version");
        JavaVersion systemVersion = JavaVersion.fromReleaseString(systemVersionStr); // TODO: Parsing doesn't work here
        if (systemVersion.major() != -1)
            return systemVersion;

        Railroad.LOGGER.warn("No reliable Java version found for path: {}", path);
        return JavaVersion.fromMajor(-1); // Fallback to an invalid version
    }

    private static JavaVersion findHighestJavaVersionForClasses(@NotNull Path path) {
        List<Path> classFiles = findAllClassFiles(path);
        if (classFiles.isEmpty())
            return JavaVersion.fromMajor(-1); // No class files found

        return classFiles.stream()
                .map(JavaFacetDetector::parseJavaVersionFromClassFile)
                .filter(version -> version.major() != -1)
                .max(Comparator.naturalOrder())
                .orElse(JavaVersion.fromMajor(-1));
    }

    private static List<Path> findAllClassFiles(@NotNull Path path) {
        try (Stream<Path> classFiles = Files.walk(path)) {
            return classFiles.filter(p -> p.toString().endsWith(".class"))
                    .toList();
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error while finding class files in path: {}", path, exception);
            return List.of();
        }
    }

    private static JavaVersion parseJavaVersionFromClassFile(@NotNull Path classFile) {
        try (var inputStream = new DataInputStream(Files.newInputStream(classFile))) {
            int magic = inputStream.readInt();
            if (magic != 0xCAFEBABE)
                return JavaVersion.fromMajor(-1); // Not a valid class file

            int minorVersion = inputStream.readUnsignedShort();
            int majorVersion = inputStream.readUnsignedShort();
            return JavaVersion.fromMajorMinor(majorVersion, minorVersion);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error reading class file: {}", classFile, exception);
            return JavaVersion.fromMajor(-1);
        }
    }

    private static JavaVersion getJavaVersionFromGradle(@NotNull Path path) {
        boolean hasBuildFile = false;
        for (String buildFile : GradleFacetDetector.BUILD_FILES) {
            Path tryBuildFilePath = path.resolve(buildFile);
            if (Files.exists(tryBuildFilePath) && Files.isRegularFile(tryBuildFilePath) && Files.isReadable(tryBuildFilePath)) {
                hasBuildFile = true;
                break;
            }
        }

        if (!hasBuildFile)
            return JavaVersion.fromMajor(-1); // No Gradle build file found

        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(path.toFile())
                .connect()) {
            if (connection == null)
                return JavaVersion.fromMajor(-1); // No Gradle connection

            Path initScriptPath = extractInitScript();

            OutputStream outputStream = OutputStream.nullOutputStream();
            JavaVersionModel model = connection.model(JavaVersionModel.class)
                    .withArguments("--init-script", initScriptPath.toAbsolutePath().toString())
                    .setStandardOutput(outputStream)
                    .setStandardError(outputStream)
                    .get();
            if(model == null) {
                Railroad.LOGGER.warn("No Java version model found in Gradle project at path: {}", path);
                return JavaVersion.fromMajor(-1);
            }

            JavaVersion sourceVersion = JavaVersion.fromReleaseString(model.sourceCompatibility());
            JavaVersion targetVersion = JavaVersion.fromReleaseString(model.targetCompatibility());
            System.out.println("Source compatibility: " + sourceVersion);
            System.out.println("Target compatibility: " + targetVersion);

            return sourceVersion.compareTo(targetVersion) >= 0 ?
                    sourceVersion :
                    targetVersion;
        } catch (IOException exception) {
            Railroad.LOGGER.error("IO exception while detecting Java version in path: {}", path, exception);
        } catch (GradleException exception) {
            Railroad.LOGGER.error("Gradle exception while detecting Java version in path: {}", path, exception);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Unexpected error while detecting Java version in path: {}", path, exception);
        }

        return JavaVersion.fromMajor(-1);
    }

    private static JavaVersion getJavaVersionFromMaven(Path projectDir) {
        Path pom = projectDir.resolve("pom.xml");
        if (!Files.isReadable(pom))
            return JavaVersion.fromMajor(-1);

        try {
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
            if ( (src == null || tgt == null) && model.getBuild() != null ) {
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

            return sourceVer.compareTo(targetVer) >= 0
                    ? sourceVer
                    : targetVer;
        }
        catch (XmlPullParserException | ModelBuildingException | PlexusConfigurationException exception) {
            Railroad.LOGGER.error("Error reading POM for Java version: {}", projectDir, exception);
            return JavaVersion.fromMajor(-1);
        }
    }

    private static Path extractInitScript() throws IOException {
        try (InputStream inputStream = Railroad.getResourceAsStream("scripts/init-java-version.gradle")) {
            if (inputStream == null)
                throw new IllegalStateException("init script resource missing");

            Path tempFile = Files.createTempFile("init-java-version", ".gradle");
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile;
        }
    }
}