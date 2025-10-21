package dev.railroadide.railroad.project.creation.step;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.modjson.*;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;

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
                if (json.has("entrypoints") && json.get("entrypoints").isJsonObject()) {
                    JsonObject entrypointsJson = json.getAsJsonObject("entrypoints");
                    entrypointsJson.entrySet().forEach(entry -> {
                        if (!entry.getValue().isJsonArray())
                            return;

                        JsonArray array = entry.getValue().getAsJsonArray();
                        boolean requiresNormalization = false;
                        for (JsonElement element : array) {
                            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                                requiresNormalization = true;
                                break;
                            }
                        }

                        if (!requiresNormalization)
                            return;

                        JsonArray normalized = new JsonArray();
                        for (JsonElement element : array) {
                            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                                JsonObject obj = new JsonObject();
                                obj.addProperty("value", element.getAsString());
                                obj.addProperty("adapter", "default");
                                normalized.add(obj);
                            } else {
                                normalized.add(element);
                            }
                        }

                        entrypointsJson.add(entry.getKey(), normalized);
                    });
                }

                // We're in version 1, using 'https://wiki.fabricmc.net/documentation:fabric_mod_json_spec#version_1_current'
                FabricModJson modJson = Railroad.GSON.fromJson(json, FabricModJson.class);

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
                if (ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES, false)) {
                    entrypoints.put("client", Collections.singletonList(Entrypoint.of(groupId + "." + modId + "." + mainClass + "Client")));
                }
                modJson.setEntrypoints(entrypoints);

                List<MixinEnvironment> mixinConfigs = new ArrayList<>();
                mixinConfigs.add(MixinEnvironment.of(modId + ".mixins.json"));
                if (ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES, false)) {
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

                if (ctx.data().getAsBoolean(FabricProjectKeys.USE_ACCESS_WIDENER, true)) {
                    String accessWidenerTemplate = ctx.data().contains(FabricProjectKeys.ACCESS_WIDENER_PATH)
                        ? ctx.data().getAsString(FabricProjectKeys.ACCESS_WIDENER_PATH)
                        : "${modid}.accesswidener";

                    String resolvedAccessWidenerPath = Optional.ofNullable(accessWidenerTemplate)
                        .map(String::trim)
                        .filter(path -> !path.isEmpty())
                        .orElse("${modid}.accesswidener")
                        .replace("${modid}", modId);

                    if (resolvedAccessWidenerPath.isBlank()) {
                        resolvedAccessWidenerPath = modId + ".accesswidener";
                    }

                    modJson.setAccessWidener(resolvedAccessWidenerPath);
                    ctx.data().set(FabricProjectKeys.ACCESS_WIDENER_PATH, resolvedAccessWidenerPath);

                    Path accessWidenerFile = resourcesDir.resolve(resolvedAccessWidenerPath);
                    Path parent = accessWidenerFile.getParent();
                    if (parent != null) {
                        files.createDirectories(parent);
                    }

                    files.writeString(accessWidenerFile, """
                        accessWidener v2 named


                        """.stripIndent());
                } else {
                    modJson.setAccessWidener(null);
                    ctx.data().remove(FabricProjectKeys.ACCESS_WIDENER_PATH);
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
