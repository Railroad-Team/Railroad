package io.github.railroad.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.plugin.defaults.DefaultPluginDescriptor;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginLoader {
    public static PluginLoadResult loadPlugin(Path pluginPath) {
        if (pluginPath == null || !pluginPath.toString().endsWith(".jar"))
            throw new IllegalArgumentException("Invalid plugin path: " + pluginPath);

        try(var jarFile = new JarFile(pluginPath.toFile())) {
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

    private static String readMainClass(URLClassLoader classLoader, String mainClass) throws Exception {
        if (mainClass == null || mainClass.isBlank())
            throw new IllegalArgumentException("Main class cannot be null or empty");

        try(var inputStream = classLoader.getResourceAsStream(mainClass.replace('.', '/') + ".class")) {
            if (inputStream == null)
                throw new IOException("Main class " + mainClass + " not found in JAR");
        }

        return mainClass;
    }

    private static PluginDescriptor readDescriptor(JarFile jarFile) throws IOException {
        JarEntry jarEntry = jarFile.getJarEntry("META-INF/plugin.json");
        if (jarEntry == null)
            throw new IOException("plugin.json not found in JAR");

        try(InputStream inputStream = jarFile.getInputStream(jarEntry)) {
            if (inputStream == null)
                throw new IOException("plugin.json not found in JAR");

            JsonObject json = Railroad.GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);
            if (json == null)
                throw new IOException("Failed to parse plugin.json");

            if(!json.has("id"))
                throw new IOException("plugin.json does not contain 'id' field");

            if(!json.has("name"))
                throw new IOException("plugin.json does not contain 'name' field");

            if(!json.has("version"))
                throw new IOException("plugin.json does not contain 'version' field");

            if(!json.has("mainClass"))
                throw new IOException("plugin.json does not contain 'mainClass' field");

            JsonElement idElement = json.get("id");
            if(!idElement.isJsonPrimitive() || !idElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'id' field must be a string");

            JsonElement nameElement = json.get("name");
            if(!nameElement.isJsonPrimitive() || !nameElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'name' field must be a string");

            JsonElement versionElement = json.get("version");
            if(!versionElement.isJsonPrimitive() || !versionElement.getAsJsonPrimitive().isString())
                throw new IOException("plugin.json 'version' field must be a string");

            JsonElement mainClassElement = json.get("mainClass");
            if(!mainClassElement.isJsonPrimitive() || !mainClassElement.getAsJsonPrimitive().isString())
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

            Map<String, String> dependencies = new HashMap<>();
            if (json.has("dependencies") && json.get("dependencies").isJsonObject()) {
                JsonObject dependenciesJson = json.getAsJsonObject("dependencies");
                for (Map.Entry<String, JsonElement> entry : dependenciesJson.entrySet()) {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                        dependencies.put(entry.getKey(), entry.getValue().getAsString());
                    } else {
                        throw new IOException("plugin.json 'dependencies' field must be a string map");
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
                    .dependencies(dependencies)
                    .build();
        }
    }

    private static @Nullable String getAsString(JsonObject json, String key, boolean required) throws IOException {
        if (json.has(key)) {
            JsonElement element = json.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            } else throw new IOException("plugin.json '" + key + "' field must be a string");
        } else if(required) throw new IOException("plugin.json does not contain '" + key + "' field");

        return null;
    }
}
