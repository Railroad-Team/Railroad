package io.github.railroad.minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.XMLParser;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ForgeVersion(MinecraftVersion minecraftVersion, String id,
                           boolean recommended) implements RecommendableVersion {
    private static final String FORGE_PROMOS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_VERSIONS_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final ObservableMap<MinecraftVersion, ObservableList<ForgeVersion>> FORGE_VERSIONS = FXCollections.observableHashMap();
    private static final ObjectProperty<ForgeVersion> LATEST = new SimpleObjectProperty<>();

    public static void load() {
        Promos.requestForgePromos();
        ForgeVersion.requestForgeVersions();
    }

    private static void requestForgeVersions() {
        Document document = XMLParser.parseFromURL(FORGE_VERSIONS_URL);

        NodeList versionsNodeList = document.getElementsByTagName("version");
        for (int i = 0; i < versionsNodeList.getLength(); i++) {
            Node versionNode = versionsNodeList.item(i);
            String version = versionNode.getTextContent();
            String[] parts = version.split("-");
            String minecraftVersion = parts[0];
            String forgeVersion = parts[1];
            Optional<MinecraftVersion> parsedMinecraftVersion = MinecraftVersion.fromId(minecraftVersion);
            if (parsedMinecraftVersion.isEmpty())
                continue;

            MinecraftVersion minecraftVersionObj = parsedMinecraftVersion.get();

            ForgeVersion latest = Promos.getLatest(minecraftVersionObj);
            boolean isLatest = latest != null && latest.id().equals(forgeVersion);
            if (isLatest) {
                LATEST.set(latest);
            }

            ForgeVersion recommended = Promos.getRecommended(minecraftVersionObj);

            boolean isRecommended = recommended != null && recommended.id().equals(forgeVersion);
            if (isRecommended) {
                FORGE_VERSIONS.computeIfAbsent(minecraftVersionObj, k -> FXCollections.observableArrayList()).add(recommended);
            } else if (isLatest) {
                FORGE_VERSIONS.computeIfAbsent(minecraftVersionObj, k -> FXCollections.observableArrayList()).add(latest);
            } else {
                FORGE_VERSIONS.computeIfAbsent(minecraftVersionObj, k -> FXCollections.observableArrayList()).add(new ForgeVersion(minecraftVersionObj, forgeVersion, false));
            }
        }
    }

    public static ObservableList<ForgeVersion> getVersions(MinecraftVersion minecraftVersion) {
        return FORGE_VERSIONS.getOrDefault(minecraftVersion, FXCollections.emptyObservableList());
    }

    public static ObservableValue<ForgeVersion> latestProperty() {
        return LATEST;
    }

    public static ForgeVersion getLatestVersion(MinecraftVersion minecraftVersion) {
        return Promos.getLatest(minecraftVersion);
    }

    public static ForgeVersion getRecommendedVersion(MinecraftVersion minecraftVersion) {
        return Promos.getRecommended(minecraftVersion);
    }

    public static Optional<ForgeVersion> fromId(String id) {
        for (ObservableList<ForgeVersion> versions : FORGE_VERSIONS.values()) {
            for (ForgeVersion version : versions) {
                if (version.id().equals(id)) {
                    return Optional.of(version);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        ForgeVersion that = (ForgeVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean isRecommended() {
        return recommended;
    }

    private static class Promos {
        private static final ObservableMap<MinecraftVersion, ForgeVersion> RECOMMENDED = FXCollections.observableHashMap();
        private static final ObservableMap<MinecraftVersion, ForgeVersion> LATEST = FXCollections.observableHashMap();

        private static void requestForgePromos() {
            try (Response response = Railroad.HTTP_CLIENT.newCall(new Request.Builder().url(FORGE_PROMOS_URL).get().build()).execute()) {
                String json = getString(response);

                JsonObject object = Railroad.GSON.fromJson(json, JsonObject.class);
                JsonObject promosObject = object.getAsJsonObject("promos");
                for (Map.Entry<String, JsonElement> entry : promosObject.entrySet()) {
                    String key = entry.getKey();
                    String forgeVersion = entry.getValue().getAsString();

                    String[] split = key.split("-");
                    String mcVersionStr = split[0];
                    boolean recommended = split.length > 1 && split[1].equals("recommended");

                    Optional<MinecraftVersion> minecraftVersionOpt = MinecraftVersion.fromId(mcVersionStr);
                    if (minecraftVersionOpt.isEmpty())
                        continue;

                    MinecraftVersion minecraftVersion = minecraftVersionOpt.get();
                    ForgeVersion forgeVersionObj = RECOMMENDED.get(minecraftVersion);
                    if (forgeVersionObj == null || !forgeVersionObj.id().equals(forgeVersion)) {
                        forgeVersionObj = new ForgeVersion(minecraftVersion, forgeVersion, recommended);
                    }

                    if (recommended) {
                        RECOMMENDED.put(minecraftVersion, forgeVersionObj);
                    } else {
                        LATEST.put(minecraftVersion, forgeVersionObj);
                    }
                }
            } catch (IOException exception) {
                throw new RuntimeException("Failed to request Forge promos", exception);
            }
        }

        private static ForgeVersion getLatest(MinecraftVersion minecraftVersion) {
            return LATEST.get(minecraftVersion);
        }

        private static ForgeVersion getRecommended(MinecraftVersion minecraftVersion) {
            return RECOMMENDED.get(minecraftVersion);
        }

        @NotNull
        private static String getString(Response response) throws IOException {
            if (!response.isSuccessful())
                throw new RuntimeException("Failed to request Forge promos: " + response.message());

            ResponseBody body = response.body();
            if (body == null)
                throw new RuntimeException("Failed to request Forge promos: Empty response body");

            String bodyStr = body.string();
            if (bodyStr.isBlank())
                throw new RuntimeException("Failed to request Forge promos: Empty response");

            return bodyStr;
        }
    }
}
