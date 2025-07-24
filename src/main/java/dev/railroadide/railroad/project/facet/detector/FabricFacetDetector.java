package dev.railroadide.railroad.project.facet.detector;

import com.google.gson.JsonObject;
import dev.railroadide.fabricExtractorPlugin.model.FabricExtractorModel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.FabricFacetData;
import org.gradle.api.GradleException;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * Detects the presence of Fabric modding platform support in a project directory by searching for fabric.mod.json and extracting metadata.
 * This detector is used by the facet system to identify Fabric mod projects and extract relevant configuration data.
 */
public class FabricFacetDetector implements FacetDetector<FabricFacetData> {
    /**
     * Detects a Fabric facet in the given path by searching for fabric.mod.json and extracting mod metadata and build info.
     *
     * @param path the project directory to analyze
     * @return an Optional containing the Fabric facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<FabricFacetData>> detect(@NotNull Path path) {
        Path fabricModJson = path.resolve("src").resolve("main").resolve("resources").resolve("fabric.mod.json");
        if (Files.notExists(fabricModJson) || !Files.isRegularFile(fabricModJson) || !Files.isReadable(fabricModJson))
            return Optional.empty();

        try {
            JsonObject json = Railroad.GSON.fromJson(Files.readString(fabricModJson), JsonObject.class);
            var data = new FabricFacetData();
            data.setModId(json.get("id").getAsString());
            data.setVersion(json.get("version").getAsString());
            data.setDisplayName(json.has("name") ? json.get("name").getAsString() : data.getModId());
            data.setDescription(json.has("description") ? json.get("description").getAsString() : "");
            data.setAuthors(json.has("authors") ? json.get("authors").getAsString() : "");
            data.setContributors(json.has("contributors") ? json.get("contributors").getAsString() : "");
            data.setLicense(json.has("license") ? json.get("license").getAsString() : "");
            data.setIconPath(json.has("icon") ? json.get("icon").getAsString() : "");
            data.setLogoPath(json.has("logo") ? json.get("logo").getAsString() : "");
            JsonObject contact = json.has("contact") ? json.getAsJsonObject("contact") : new JsonObject();
            data.setWebsiteUrl(contact.has("website") ? contact.get("website").getAsString() : "");
            data.setSourceUrl(contact.has("source") ? contact.get("source").getAsString() : "");
            data.setIssuesUrl(contact.has("issues") ? contact.get("issues").getAsString() : "");
            data.setChangelogUrl(json.has("changelog") ? json.get("changelog").getAsString() : "");

            Path buildFilePath = null;
            for (String buildFile : GradleFacetDetector.BUILD_FILES) {
                Path tryBuildFilePath = path.resolve(buildFile);
                if (Files.exists(tryBuildFilePath) && Files.isRegularFile(tryBuildFilePath) && Files.isReadable(tryBuildFilePath)) {
                    buildFilePath = tryBuildFilePath;
                    break;
                }
            }

            data.setBuildFilePath(Objects.toString(buildFilePath));

            String minecraftVersion, fabricLoaderVersion, fabricApiVersion, yarnMappingsVersion, loomVersion;
            try (ProjectConnection connection = GradleConnector.newConnector()
                    .forProjectDirectory(path.toFile())
                    .connect()) {
                Path initScriptPath = extractInitScript();

                OutputStream outputStream = OutputStream.nullOutputStream();
                FabricExtractorModel model = connection.model(FabricExtractorModel.class)
                        .withArguments("--init-script", initScriptPath.toAbsolutePath().toString())
                        .setStandardOutput(outputStream)
                        .setStandardError(outputStream)
                        .get();

                minecraftVersion = model.minecraftVersion();
                fabricLoaderVersion = model.loaderVersion();
                fabricApiVersion = model.fabricApiVersion();
                yarnMappingsVersion = model.mappingsVersion();
                loomVersion = model.loomVersion();
            }

            data.setMinecraftVersion(minecraftVersion);
            data.setFabricLoaderVersion(fabricLoaderVersion);
            data.setFabricApiVersion(fabricApiVersion);
            data.setYarnMappingsVersion(yarnMappingsVersion);
            data.setLoomVersion(loomVersion);

            return Optional.of(new Facet<>(FacetManager.FABRIC, data));
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read fabric.mod.json at {}", fabricModJson, exception);
            return Optional.empty();
        } catch (GradleException | BuildException ignored) {}

        return Optional.empty();
    }

    /**
     * Extracts the Gradle init script for Fabric metadata extraction to a temporary file.
     *
     * @return the path to the extracted init script
     * @throws IOException if the script cannot be extracted
     */
    private static Path extractInitScript() throws IOException {
        try (InputStream inputStream = Railroad.getResourceAsStream("scripts/init-fabric-extractor.gradle")) {
            if (inputStream == null)
                throw new IllegalStateException("init script resource missing");

            Path tempFile = Files.createTempFile("init-fabric-extractor", ".gradle");
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile;
        }
    }
}
