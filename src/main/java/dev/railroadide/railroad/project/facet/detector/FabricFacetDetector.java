package dev.railroadide.railroad.project.facet.detector;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetDetector;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.FabricFacetData;
import dev.railroadide.railroadplugin.dto.FabricDataModel;
import org.gradle.api.GradleException;
import org.gradle.tooling.BuildException;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects the presence of Fabric modding platform support in a project directory by searching for fabric.mod.json and extracting metadata.
 * This detector is used by the facet system to identify Fabric mod projects and extract relevant configuration data.
 */
public class FabricFacetDetector implements FacetDetector<FabricFacetData> {
    /**
     * Detects a Fabric facet in the given path by searching for fabric.mod.json and extracting mod metadata and build info.
     *
     * @param project the project context for detection
     * @return an Optional containing the Fabric facet if detected, or empty if not found
     */
    @Override
    public Optional<Facet<FabricFacetData>> detect(@UnknownNullability Project project) {
        Path path = project.getPath();
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

            GradleModelService gradleModelService = project.getGradleManager().getGradleModelService();
            gradleModelService.getCachedModel().ifPresent(gradleBuildModel -> {
                FabricDataModel fabricDataModel = gradleBuildModel.fabricData();
                data.setMinecraftVersion(fabricDataModel.minecraftVersion());
                data.setYarnMappingsVersion(fabricDataModel.mappingsVersion());
                data.setFabricLoaderVersion(fabricDataModel.loaderVersion());
                data.setFabricApiVersion(fabricDataModel.fabricApiVersion());
                if (fabricDataModel.loomVersion() != null) {
                    data.setLoomVersion(fabricDataModel.loomVersion().version());
                    data.setArchitecturyLoom(fabricDataModel.loomVersion().isArchitecturyLoom());
                }

                // TODO: Set source file
            });

            return Optional.of(new Facet<>(FacetManager.FABRIC, data));
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read fabric.mod.json at {}", fabricModJson, exception);
            return Optional.empty();
        } catch (GradleException | BuildException ignored) {
        }

        return Optional.empty();
    }
}
