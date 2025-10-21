package dev.railroadide.railroad.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.defaults.DefaultPluginDescriptor;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import dev.railroadide.railroadpluginapi.deps.MavenDep;
import dev.railroadide.railroadpluginapi.deps.MavenDeps;
import dev.railroadide.railroadpluginapi.deps.MavenRepo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * PluginLoader is responsible for loading plugins from JAR files.
 * It reads the plugin descriptor from the JAR and validates its contents.
 */
public class PluginLoader {
    /**
     * Loads a plugin from the specified JAR file path.
     *
     * @param pluginPath the path to the plugin JAR file
     * @return a PluginLoadResult containing the loaded plugin descriptor and path
     * @throws IllegalArgumentException if the plugin path is invalid
     * @throws RuntimeException         if there is an error reading the plugin JAR or descriptor
     */
    public static PluginLoadResult loadPlugin(Path pluginPath) {
        if (pluginPath == null || !pluginPath.toString().endsWith(".jar"))
            throw new IllegalArgumentException("Invalid plugin path: " + pluginPath);

        try (var jarFile = new JarFile(pluginPath.toFile())) {
            PluginDescriptor descriptor = readDescriptor(jarFile);
            if (descriptor == null)
                throw new IOException("Failed to read plugin descriptor from: " + pluginPath);

            return new PluginLoadResult(pluginPath, descriptor);
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid plugin path: " + pluginPath, exception);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read plugin JAR: " + pluginPath, exception);
        }
    }

    private static PluginDescriptor readDescriptor(JarFile jarFile) throws IOException {
        JarEntry jarEntry = jarFile.getJarEntry("META-INF/plugin.json");
        if (jarEntry == null)
            throw new IOException("plugin.json not found in JAR");

        try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
            if (inputStream == null)
                throw new IOException("plugin.json not found in JAR");

            JsonObject json = Railroad.GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);
            if (json == null)
                throw new IOException("Failed to parse plugin.json");

            if (!json.has("id"))
                throw new IOException("plugin.json does not contain 'id' field");

            if (!json.has("name"))
                throw new IOException("plugin.json does not contain 'name' field");

            if (!json.has("version"))
                throw new IOException("plugin.json does not contain 'version' field");

            if (!json.has("mainClass"))
                throw new IOException("plugin.json does not contain 'mainClass' field");

            JsonElement idElement = json.get("id");
            if (!idElement.isJsonPrimitive() || !idElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'id' field must be a string");

            JsonElement nameElement = json.get("name");
            if (!nameElement.isJsonPrimitive() || !nameElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'name' field must be a string");

            JsonElement versionElement = json.get("version");
            if (!versionElement.isJsonPrimitive() || !versionElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'version' field must be a string");

            JsonElement mainClassElement = json.get("mainClass");
            if (!mainClassElement.isJsonPrimitive() || !mainClassElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'mainClass' field must be a string");

            String id = idElement.getAsString();
            String name = nameElement.getAsString();
            String version = versionElement.getAsString();
            String mainClass = mainClassElement.getAsString();

            String author = getAsString(json, "author", false);
            String description = getAsString(json, "description", false);
            String website = getAsString(json, "website", false);
            String license = getAsString(json, "license", false);
            String iconPath = getAsString(json, "icon", false);

            List<MavenRepo> repositories = new ArrayList<>();
            List<MavenDep> artifacts = new ArrayList<>();
            if (json.has("dependencies") && json.get("dependencies").isJsonObject()) {
                JsonObject dependenciesJson = json.getAsJsonObject("dependencies");

                if (dependenciesJson.has("repositories") && dependenciesJson.get("repositories").isJsonArray()) {
                    for (JsonElement repoElement : dependenciesJson.getAsJsonArray("repositories")) {
                        if (repoElement.isJsonObject()) {
                            JsonObject repoObject = repoElement.getAsJsonObject();
                            String idRepo = getAsString(repoObject, "id", true);
                            String url = getAsString(repoObject, "url", true);
                            repositories.add(new MavenRepo(idRepo, url));
                        } else if (repoElement.isJsonPrimitive() && repoElement.getAsJsonPrimitive().isString()) {
                            String url = repoElement.getAsString();
                            repositories.add(new MavenRepo("unknown", url));
                        } else {
                            throw new IOException("plugin.json 'dependencies.repositories' must be an array of objects or strings");
                        }
                    }
                }

                if (dependenciesJson.has("artifacts") && dependenciesJson.get("artifacts").isJsonArray()) {
                    for (JsonElement artifactElement : dependenciesJson.getAsJsonArray("artifacts")) {
                        if (artifactElement.isJsonObject()) {
                            JsonObject artifactObject = artifactElement.getAsJsonObject();
                            String groupId = getAsString(artifactObject, "groupId", true);
                            String artifactId = getAsString(artifactObject, "artifactId", true);
                            String versionArtifact = getAsString(artifactObject, "version", true);

                            artifacts.add(new MavenDep(groupId, artifactId, versionArtifact));
                        } else if (artifactElement.isJsonPrimitive() && artifactElement.getAsJsonPrimitive().isString()) {
                            String artifact = artifactElement.getAsString();
                            artifacts.add(MavenDep.fromFullName(artifact));
                        } else {
                            throw new IOException("plugin.json 'dependencies.artifacts' must be an array of objects or strings");
                        }
                    }
                }
            }

            return DefaultPluginDescriptor.builder(id)
                .name(name)
                .version(version)
                .author(author)
                .description(description)
                .website(website)
                .license(license)
                .iconPath(iconPath)
                .mainClass(mainClass)
                .dependencies(new MavenDeps(repositories, artifacts))
                .build();
        }
    }

    private static @Nullable String getAsString(JsonObject json, String key, boolean required) throws IOException {
        if (json.has(key)) {
            JsonElement element = json.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            } else throw new IOException("plugin.json '" + key + "' field must be a string");
        } else if (required) throw new IOException("plugin.json does not contain '" + key + "' field");

        return null;
    }
}
