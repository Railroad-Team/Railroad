package io.github.railroad.minecraft.mapping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.RecommendableVersion;

import java.io.InputStreamReader;
import java.util.*;

public class MCPVersion extends MappingVersion implements RecommendableVersion {
    private static final Map<MinecraftVersion, List<MCPVersion>> VERSIONS = getMCPVersions();

    private final boolean isRecommended;

    public MCPVersion(MinecraftVersion minecraftVersion, String id, boolean isLatest, boolean isRecommended) {
        super(minecraftVersion, id, isLatest);
        this.isRecommended = isRecommended;
    }

    public static Map<MinecraftVersion, List<MCPVersion>> getMCPVersions() {
        final Map<MinecraftVersion, List<MCPVersion>> versions = new HashMap<>();

        var reader = new InputStreamReader(Railroad.getResourceAsStream("mcp_mappings.json"));
        final JsonObject response = Railroad.GSON.fromJson(reader, JsonObject.class);
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            String mcVersionStr = entry.getKey();
            JsonObject versionsObj = entry.getValue().getAsJsonObject();
            JsonArray snapshots = versionsObj.getAsJsonArray("snapshot");
            JsonArray stables = versionsObj.getAsJsonArray("stable");

            MinecraftVersion.fromId(mcVersionStr).ifPresent(minecraftVersion -> {
                List<MCPVersion> versionsList = versions.computeIfAbsent(minecraftVersion, k -> new ArrayList<>());

                List<Integer> snapshotVersions = new ArrayList<>();
                List<Integer> stableVersions = new ArrayList<>();

                for (JsonElement snapshot : snapshots) {
                    snapshotVersions.add(snapshot.getAsInt());
                }

                for (JsonElement stable : stables) {
                    stableVersions.add(stable.getAsInt());
                }

                for (int index = 0; index < stableVersions.size(); index++) {
                    int version = stableVersions.get(index);
                    versionsList.add(new MCPVersion(minecraftVersion, String.valueOf(version), index == 0, true));
                }

                for (int index = 0; index < snapshotVersions.size(); index++) {
                    int version = snapshotVersions.get(index);
                    versionsList.add(new MCPVersion(minecraftVersion, String.valueOf(version), index == 0, stableVersions.isEmpty()));
                }
            });
        }

        return versions;
    }

    public static List<MCPVersion> getMCPVersions(MinecraftVersion minecraftVersion) {
        if (VERSIONS.containsKey(minecraftVersion))
            return VERSIONS.get(minecraftVersion);

        Optional<MinecraftVersion> majorVersion = MinecraftVersion.getMajorVersion(minecraftVersion);
        if (majorVersion.isPresent() && VERSIONS.containsKey(majorVersion.get()))
            return VERSIONS.get(majorVersion.get());

        return new ArrayList<>();
    }

    public static List<MCPVersion> getLatestMCPVersions(MinecraftVersion minecraftVersion) {
        return getMCPVersions(minecraftVersion).stream().filter(MCPVersion::isLatest).toList();
    }

    public static List<MCPVersion> getRecommendedMCPVersions(MinecraftVersion minecraftVersion) {
        return getMCPVersions(minecraftVersion).stream().filter(MCPVersion::isRecommended).toList();
    }

    @Override
    public boolean isRecommended() {
        return isRecommended;
    }
}