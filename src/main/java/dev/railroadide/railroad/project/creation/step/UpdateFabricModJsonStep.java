package dev.railroadide.railroad.project.creation.step;

import com.google.gson.JsonObject;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.creation.modjson.*;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;

import java.nio.file.Path;
import java.util.*;

public record UpdateFabricModJsonStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:update_fabric_mod_json";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.update_fabric_mod_json";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Deleting assets directory...");
        Path projectDir = ctx.projectDir();
        Path resourcesDir = projectDir.resolve("src/main/resources");
        files.deleteDirectory(resourcesDir.resolve("assets"));

        reporter.info("Updating fabric.mod.json...");
        Path fabricModJson = resourcesDir.resolve("fabric.mod.json");
        String content = files.readString(fabricModJson);
        JsonObject json = Railroad.GSON.fromJson(content, JsonObject.class);
        if (!json.has("schemaVersion")) {
            // We're in version 0, we use 'https://github.com/craftson/spec' as the schema
        } else {
            int schemaVersion = json.get("schemaVersion").getAsInt();
            if (schemaVersion == 1) {
                // We're in version 1, using 'https://wiki.fabricmc.net/documentation:fabric_mod_json_spec#version_1_current'
                FabricModJson modJson = Railroad.GSON.fromJson(content, FabricModJson.class);

                String modId = ctx.data().getAsString(MinecraftProjectKeys.MOD_ID);
                String modName = ctx.data().getAsString(MinecraftProjectKeys.MOD_NAME);
                modJson.setId(modId);
                modJson.setName(modName);
                if (ctx.data().contains(ProjectData.DefaultKeys.DESCRIPTION))
                    modJson.setDescription(ctx.data().getAsString(ProjectData.DefaultKeys.DESCRIPTION));

                Optional<String> authorOpt = ctx.data().contains(ProjectData.DefaultKeys.AUTHOR) ?
                    Optional.of(ctx.data().getAsString(ProjectData.DefaultKeys.AUTHOR)) :
                    Optional.empty();

                authorOpt.ifPresent(author -> {
                    String[] split = author.split(",\\s*");
                    modJson.setAuthors(Arrays.stream(split).map(String::trim).map(Person::fromName).toList());
                });

                License license = ctx.data().get(ProjectData.DefaultKeys.LICENSE, License.class);
                String licenseStr = license == LicenseRegistry.CUSTOM ? ctx.data().getAsString(ProjectData.DefaultKeys.LICENSE_CUSTOM) : license.getSpdxId();
                modJson.setLicense(Collections.singletonList(licenseStr));

                String groupId = ctx.data().getAsString(MavenProjectKeys.GROUP_ID);
                String mainClass = ctx.data().getAsString(MinecraftProjectKeys.MAIN_CLASS);
                var entrypoints = new EntrypointContainer();
                entrypoints.put("main", Collections.singletonList(Entrypoint.of(groupId + "." + modId + "." + mainClass)));
                if (ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES)) {
                    entrypoints.put("client", Collections.singletonList(Entrypoint.of(groupId + "." + modId + "." + mainClass + "Client")));
                }
                modJson.setEntrypoints(entrypoints);

                List<MixinEnvironment> mixinConfigs = new ArrayList<>();
                mixinConfigs.add(MixinEnvironment.of(modId + ".mixins.json"));
                if (ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES)) {
                    mixinConfigs.add(new MixinEnvironment(modId + ".client.mixins.json", "client"));
                }
                modJson.setMixins(mixinConfigs);

                Map<String, VersionRange> depends = modJson.getDepends();
                if (depends == null) depends = new HashMap<>();

                FabricLoaderVersion loaderVersion = ctx.data().get(FabricProjectKeys.FABRIC_LOADER_VERSION, FabricLoaderVersion.class);
                if (loaderVersion == null) {
                    reporter.info("No fabric loader version found in context, cannot update fabric.mod.json.");
                    Railroad.LOGGER.warn("No fabric loader version found in context during project creation, cannot update fabric.mod.json.");
                    return;
                }

                MinecraftVersion mcVersion = ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION, MinecraftVersion.class);
                if (mcVersion == null) {
                    reporter.info("No minecraft version found in context, cannot update fabric.mod.json.");
                    Railroad.LOGGER.warn("No minecraft version found in context during project creation, cannot update fabric.mod.json.");
                    return;
                }

                depends.put("fabricloader", VersionRange.gte(loaderVersion.version()));
                depends.put("minecraft", VersionRange.gteMinor(mcVersion.id()));
                if (ctx.data().contains(FabricProjectKeys.FABRIC_API_VERSION)) {
                    String fabricApiVersion = ctx.data().getAsString(FabricProjectKeys.FABRIC_API_VERSION);
                    depends.put("fabric", VersionRange.gte(fabricApiVersion));
                } else {
                    depends.put("fabric", VersionRange.any());
                    Railroad.LOGGER.warn("No fabric api version found in context during project creation, setting fabric dependency to '*'.");
                }
                modJson.setDepends(depends);

                modJson.setSuggests(null); // Clear out example suggests

                if (ctx.data().getAsBoolean(FabricProjectKeys.ACCESS_WIDENER_PATH)) {
                    // TODO: Use FabricProjectKeys.ACCESS_WIDENER_PATH
                    String accessWidenerPath = modId + ".accesswidener";
                    modJson.setAccessWidener(accessWidenerPath);

                    files.writeString(resourcesDir.resolve(accessWidenerPath), """
                        accessWidener v2 named


                        """.stripIndent());
                } else {
                    modJson.setAccessWidener(null);
                }

                ContactInformation contact = modJson.getContact();
                if (ctx.data().contains(ProjectData.DefaultKeys.ISSUES_URL))
                    contact.setIssues(ctx.data().getAsString(ProjectData.DefaultKeys.ISSUES_URL));
                if (ctx.data().contains(ProjectData.DefaultKeys.HOMEPAGE_URL))
                    contact.setHomepage(ctx.data().getAsString(ProjectData.DefaultKeys.HOMEPAGE_URL));
                if (ctx.data().contains(ProjectData.DefaultKeys.SOURCES_URL))
                    contact.setSources(ctx.data().getAsString(ProjectData.DefaultKeys.SOURCES_URL));
                modJson.setContact(contact);

                files.writeString(fabricModJson, Railroad.GSON.toJson(modJson));

                ctx.put(ProjectContextKeys.FABRIC_MOD_JSON, modJson);
            } else {
                // TODO: Maybe try and parse? Unsure whats the best way to handle this.
                reporter.info("Unknown fabric.mod.json schema version: " + schemaVersion + ", skipping update.");
                Railroad.LOGGER.warn("Unknown fabric.mod.json schema version '{}' during project creation, skipping update.", schemaVersion);
                Thread.sleep(1000); // Sleep a bit so the user can see the message
            }
        }
    }
}
