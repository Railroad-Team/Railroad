package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Launch, runtime, asset, and library metadata published for a Minecraft version.
 *
 * @param arguments the game and JVM arguments
 * @param assetIndex the asset index download metadata
 * @param assets the asset collection identifier
 * @param complianceLevel the metadata compliance level
 * @param downloads the executable and mapping downloads
 * @param id the Minecraft version identifier
 * @param javaVersion the Java runtime requirements
 * @param libraries the library dependencies
 * @param logging the client logging configuration
 * @param mainClass the game entry point class name
 * @param minimumLauncherVersion the minimum launcher version declared by the metadata
 * @param releaseTime the published release timestamp
 * @param time the metadata update timestamp
 * @param type the release category, such as release or snapshot
 */
public record VersionPackage(
    Arguments arguments,
    AssetIndex assetIndex,
    String assets,
    int complianceLevel,
    Downloads downloads,
    String id,
    JavaVersion javaVersion,
    List<Library> libraries,
    Logging logging,
    String mainClass,
    int minimumLauncherVersion,
    String releaseTime,
    String time,
    String type
) {
    /**
     * Parses version metadata and its nested download and launch configuration.
     * Game arguments are read from {@code arguments.game}; JVM arguments are read from the top-level {@code jvm} array.
     *
     * @param json the version metadata object
     * @return the parsed version package
     */
    public static VersionPackage fromJson(JsonObject json) {
        JsonObject argumentsJson = json.getAsJsonObject("arguments");
        JsonArray gameJson = argumentsJson.getAsJsonArray("game");
        JsonArray jvmJson = json.getAsJsonArray("jvm");
        CLIArguments gameArguments = CLIArguments.fromJsonArray(gameJson);
        CLIArguments jvmArguments = CLIArguments.fromJsonArray(jvmJson);
        var arguments = new Arguments(gameArguments, jvmArguments);

        JsonObject assetIndexJson = json.getAsJsonObject("assetIndex");
        AssetIndex assetIndex = AssetIndex.fromJson(assetIndexJson);

        String assets = json.get("assets").getAsString();
        int complianceLevel = json.get("complianceLevel").getAsInt();

        JsonObject downloadsJson = json.getAsJsonObject("downloads");
        Downloads downloads = Downloads.fromJson(downloadsJson);

        String id = json.get("id").getAsString();

        JsonObject javaVersionJson = json.getAsJsonObject("javaVersion");
        JavaVersion javaVersion = JavaVersion.fromJson(javaVersionJson);

        JsonArray librariesJson = json.getAsJsonArray("libraries");
        List<Library> libraries = Library.fromJsonArray(librariesJson);

        JsonObject loggingJson = json.getAsJsonObject("logging");
        Logging logging = Logging.fromJson(loggingJson);

        String mainClass = json.get("mainClass").getAsString();
        int minimumLauncherVersion = json.get("minimumLauncherVersion").getAsInt();
        String releaseTime = json.get("releaseTime").getAsString();
        String time = json.get("time").getAsString();
        String type = json.get("type").getAsString();

        return new VersionPackage(arguments, assetIndex, assets, complianceLevel, downloads, id, javaVersion,
            libraries, logging, mainClass, minimumLauncherVersion, releaseTime, time, type);
    }

    /**
     * Reads and parses a Minecraft version metadata file.
     *
     * @param pistonMetaPath the metadata JSON file
     * @return the parsed version package
     * @throws RuntimeException if the file cannot be read
     */
    public static VersionPackage fromFile(Path pistonMetaPath) {
        try {
            return fromJsonString(Files.readString(pistonMetaPath));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Parses a JSON string containing Minecraft version metadata.
     *
     * @param jsonString the metadata JSON text
     * @return the parsed version package
     */
    public static VersionPackage fromJsonString(String jsonString) {
        return fromJson(Railroad.GSON.fromJson(jsonString, JsonObject.class));
    }
}
