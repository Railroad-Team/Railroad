package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.core.utility.ServiceLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public record Library(Download artifact, String name, Optional<List<DownloadRule>> rules) {
    public static List<Library> fromJsonArray(JsonArray json) {
        List<Library> libraries = new ArrayList<>();
        for (JsonElement jsonElement : json) {
            if (!jsonElement.isJsonObject())
                continue;

            libraries.add(fromJson(jsonElement.getAsJsonObject()));
        }

        return libraries;
    }

    public static Library fromJson(JsonObject json) {
        Download artifact;
        if (json.has("downloads")) {
            JsonObject downloadsJson = json.getAsJsonObject("downloads");
            if (downloadsJson.has("artifact")) {
                JsonObject artifactJson = downloadsJson.getAsJsonObject("artifact");
                artifact = Download.fromJson(artifactJson);
            } else {
                artifact = null;
            }
        } else {
            artifact = null;
        }

        String name = json.get("name").getAsString();

        Optional<List<DownloadRule>> rules;
        if (json.has("rules")) {
            JsonArray rulesJson = json.getAsJsonArray("rules");
            rules = Optional.of(DownloadRule.fromJsonArray(rulesJson));
        } else {
            rules = Optional.empty();
        }

        return new Library(artifact, name, rules);
    }

    public static void readLibraries(Map<String, Path> libraryJars, Path librariesJsonPath) throws IOException {
        String librariesJson = Files.readString(librariesJsonPath);
        JsonObject librariesObject = ServiceLocator.getService(Gson.class).fromJson(librariesJson, JsonObject.class);
        for (Map.Entry<String, JsonElement> entry : librariesObject.entrySet()) {
            String name = entry.getKey();
            String path = entry.getValue().getAsString();
            libraryJars.put(name, Path.of(path));
        }
    }

    public record DownloadRule(Action action, OperatingSystem os) {
        public static DownloadRule fromJson(JsonObject json) {
            Action action;
            if (json.has("action")) {
                action = Action.valueOf(json.get("action").getAsString().toUpperCase(Locale.ROOT));
            } else {
                action = null;
            }

            OperatingSystem os;
            if (json.has("os")) {
                JsonObject osJson = json.getAsJsonObject("os");
                os = OperatingSystem.fromJson(osJson);
            } else {
                os = null;
            }

            return new DownloadRule(action, os);
        }

        public static List<DownloadRule> fromJsonArray(JsonArray array) {
            List<DownloadRule> rules = new ArrayList<>();
            for (JsonElement jsonElement : array) {
                if (!jsonElement.isJsonObject())
                    continue;

                rules.add(fromJson(jsonElement.getAsJsonObject()));
            }

            return rules;
        }

        public enum Action {
            ALLOW, DISALLOW;
        }

        public record OperatingSystem(String name) {
            public static OperatingSystem fromJson(JsonObject json) {
                return ServiceLocator.getService(Gson.class).fromJson(json, OperatingSystem.class);
            }
        }
    }
}
