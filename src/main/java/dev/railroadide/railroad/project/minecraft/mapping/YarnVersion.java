package dev.railroadide.railroad.project.minecraft.mapping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.utility.XMLParser;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class YarnVersion extends MappingVersion {
    private static final String YARN_MAVEN = "https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml";
    private static final Pattern YARN_IGNORE_PATTERN = Pattern.compile("\\d+w\\d+\\w\\.\\d+");
    private static final Pattern YARN_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(\\.\\d+)*\\+build\\.\\d+");

    private static final Map<MinecraftVersion, List<YarnVersion>> VERSIONS = getYarnVersions();

    public YarnVersion(MinecraftVersion minecraftVersion, String id, boolean isLatest) {
        super(minecraftVersion, id, isLatest);
    }

    public static Map<MinecraftVersion, List<YarnVersion>> getYarnVersions() {
        final Map<MinecraftVersion, List<YarnVersion>> versions = new HashMap<>();

        try (Response response = Railroad.HTTP_CLIENT.newCall(new Request.Builder().url(YARN_MAVEN).get().build()).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Failed to read Yarn versions!");

            ResponseBody body = response.body();
            if (body == null)
                throw new IOException("Failed to read Yarn versions!");

            String xml = body.string();
            if (xml.isBlank())
                throw new IOException("Failed to read Yarn versions!");

            final JsonObject xmlJson = XMLParser.xmlToJson(xml);
            final JsonObject versioning = xmlJson.getAsJsonObject("metadata").getAsJsonObject("versioning");
            final JsonArray versionsArray = versioning.getAsJsonObject("versions").getAsJsonArray("version");

            Map<MinecraftVersion, List<String>> versionMap = new HashMap<>();
            for (final JsonElement element : versionsArray) {
                final String version = element.getAsString();
                if (YARN_IGNORE_PATTERN.matcher(version).matches())
                    continue;

                final String[] split = version.split("\\+");
                if (split.length != 2)
                    continue;

                final String minecraftVersion = split[0];
                final String yarnVersion = split[1];

                if (!YARN_VERSION_PATTERN.matcher(version).matches())
                    continue;

                MinecraftVersion.fromId(minecraftVersion).ifPresent(mcVersion -> {
                    List<String> versionsList = versionMap.computeIfAbsent(mcVersion, k -> new ArrayList<>());
                    versionsList.add(yarnVersion);
                });
            }

            for (Map.Entry<MinecraftVersion, List<String>> entry : versionMap.entrySet()) {
                List<YarnVersion> yarnVersions = versions.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                for (int index = 0; index < entry.getValue().size(); index++) {
                    String version = entry.getValue().get(index);
                    yarnVersions.add(new YarnVersion(entry.getKey(), version, index == entry.getValue().size() - 1));
                }

                versions.put(entry.getKey(), yarnVersions.reversed());
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to read Yarn versions!", exception);
        }

        return versions;
    }

    public static List<YarnVersion> getYarnVersions(MinecraftVersion minecraftVersion) {
        return VERSIONS.containsKey(minecraftVersion) ? VERSIONS.get(minecraftVersion) : new ArrayList<>();
    }
}
