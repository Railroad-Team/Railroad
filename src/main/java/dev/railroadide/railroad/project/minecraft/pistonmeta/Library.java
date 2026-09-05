package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A Minecraft library dependency and its optional download eligibility rules.
 *
 * @param artifact the library artifact download, or null when none is declared
 * @param name the library Maven coordinates
 * @param rules the declared download rules, or empty when the rules field is absent
 */
public record Library(Download artifact, String name, Optional<List<DownloadRule>> rules) {
    /**
     * Parses library entries in encounter order, skipping elements that are not JSON objects.
     *
     * @param json the library metadata array
     * @return a mutable list of parsed libraries
     */
    public static List<Library> fromJsonArray(JsonArray json) {
        List<Library> libraries = new ArrayList<>();
        for (JsonElement jsonElement : json) {
            if (!jsonElement.isJsonObject())
                continue;

            libraries.add(fromJson(jsonElement.getAsJsonObject()));
        }

        return libraries;
    }

    /**
     * Parses a library name, optional artifact download, and optional rules.
     *
     * @param json the library metadata object
     * @return the parsed library
     */
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

    /**
     * Loads a persisted mapping of library names to local JAR paths into an existing map.
     * Entries with matching names replace existing values; other entries are retained.
     *
     * @param libraryJars the destination map to update
     * @param librariesJsonPath the JSON file containing library names and path strings
     * @throws IOException if the file cannot be read
     */
    public static void readLibraries(Map<String, Path> libraryJars, Path librariesJsonPath) throws IOException {
        String librariesJson = Files.readString(librariesJsonPath);
        JsonObject librariesObject = Railroad.GSON.fromJson(librariesJson, JsonObject.class);
        for (Map.Entry<String, JsonElement> entry : librariesObject.entrySet()) {
            String name = entry.getKey();
            String path = entry.getValue().getAsString();
            libraryJars.put(name, Path.of(path));
        }
    }

    /**
     * A library eligibility action with an optional operating system restriction.
     *
     * @param action the eligibility action, or null when absent from the metadata
     * @param os the operating system restriction, or null when none is declared
     */
    public record DownloadRule(Action action, OperatingSystem os) {
        /**
         * Parses an optional action and operating system restriction.
         *
         * @param json the rule metadata object
         * @return the parsed download rule
         * @throws IllegalArgumentException if a supplied action is not recognized
         */
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

        /**
         * Parses download rules in encounter order, skipping elements that are not JSON objects.
         *
         * @param array the rule metadata array
         * @return a mutable list of parsed rules
         */
        public static List<DownloadRule> fromJsonArray(JsonArray array) {
            List<DownloadRule> rules = new ArrayList<>();
            for (JsonElement jsonElement : array) {
                if (!jsonElement.isJsonObject())
                    continue;

                rules.add(fromJson(jsonElement.getAsJsonObject()));
            }

            return rules;
        }

        /**
         * Specifies whether a matching library download rule permits or rejects the dependency.
         */
        public enum Action {
            /**
             * Permits the library when the rule matches.
             */
            ALLOW,
            /**
             * Rejects the library when the rule matches.
             */
            DISALLOW;
        }

        /**
         * Operating system name used to restrict a library download rule.
         *
         * @param name the operating system identifier from the metadata
         */
        public record OperatingSystem(String name) {
            /**
             * Parses the operating system restriction of a library rule.
             *
             * @param json the operating system metadata object
             * @return the parsed restriction, or null if the JSON object is null
             */
            public static OperatingSystem fromJson(JsonObject json) {
                return Railroad.GSON.fromJson(json, OperatingSystem.class);
            }
        }
    }
}
