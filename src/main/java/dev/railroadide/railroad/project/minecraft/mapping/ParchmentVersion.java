package dev.railroadide.railroad.project.minecraft.mapping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.XMLParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ParchmentVersion extends MappingVersion {
    private static final String PARCHMENT_MAVEN = "https://ldtteam.jfrog.io/artifactory/parchmentmc-public/org/parchmentmc/data/parchment-%s/maven-metadata.xml";
    private static final Pattern PARCHMENT_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");

    public ParchmentVersion(MinecraftVersion minecraftVersion, String id, boolean isLatest) {
        super(minecraftVersion, id, isLatest);
    }

    public static List<ParchmentVersion> getParchmentVersions(MinecraftVersion minecraftVersion) {
        List<ParchmentVersion> results = new ArrayList<>();
        var file = Path.of(minecraftVersion.id() + "-parchment.xml");
        if (Files.notExists(file)) {
            FileUtils.copyUrlToFile(PARCHMENT_MAVEN.formatted(minecraftVersion.id()), file);
        }

        final JsonObject xmlJson = XMLParser.xmlToJson(file, JsonObject.class);
        final JsonObject versioning = xmlJson.getAsJsonObject("metadata").getAsJsonObject("versioning");
        final JsonArray versionsArray = versioning.getAsJsonObject("versions").getAsJsonArray("version");

        List<String> versions = new ArrayList<>();
        for (final JsonElement element : versionsArray) {
            final String version = element.getAsString();
            if (!PARCHMENT_VERSION_PATTERN.matcher(version).matches())
                continue;

            versions.add(version);
        }

        for (int index = 0; index < versions.size(); index++) {
            String version = versions.get(index);
            results.add(new ParchmentVersion(minecraftVersion, version, index == versions.size() - 1));
        }

        return results.reversed();
    }
}
