package io.github.railroad.project.minecraft;

import io.github.railroad.utility.XMLParser;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Optional;
import java.util.stream.IntStream;

public record NeoForgeVersion(MinecraftVersion minecraftVersion, String id,
                              boolean recommended) implements RecommendableVersion {
    private static final String MAVEN_METADATA_URL = "https://maven.neoforged.net/net/neoforged/neoforge/maven-metadata.xml";
    private static final ObservableMap<MinecraftVersion, ObservableList<NeoForgeVersion>> NEOFORGE_VERSIONS = FXCollections.observableHashMap();
    private static final ObjectProperty<NeoForgeVersion> LATEST = new SimpleObjectProperty<>();

    public static void load() {
        NeoForgeVersion.requestNeoForgeVersions();
    }

    private static void requestNeoForgeVersions() {
        Document document = XMLParser.parseFromURL(MAVEN_METADATA_URL);

        NodeList versionsNodeList = document.getElementsByTagName("version");
        for (int i = 0; i < versionsNodeList.getLength(); i++) {
            Node versionNode = versionsNodeList.item(i);
            String version = versionNode.getTextContent();
            String[] parts = version.split("\\.");
            String majorMinecraftVersion = parts[0];
            String minorMinecraftVersion;
            if (parts.length > 2) {
                minorMinecraftVersion = parts[1];
            } else {
                minorMinecraftVersion = "0";
            }

            String neoforgeVersion = parts[parts.length - 1];
            Optional<MinecraftVersion> parsedMinecraftVersion = MinecraftVersion.fromId("1." + majorMinecraftVersion +
                    (!"0".equals(majorMinecraftVersion) ? "." + minorMinecraftVersion : ""));
            if (parsedMinecraftVersion.isEmpty())
                continue;

            MinecraftVersion minecraftVersionObj = parsedMinecraftVersion.get();
            var neoForgeVersion = new NeoForgeVersion(minecraftVersionObj, version, !neoforgeVersion.endsWith("-beta"));
            NEOFORGE_VERSIONS.computeIfAbsent(minecraftVersionObj, k -> FXCollections.observableArrayList()).add(neoForgeVersion);

            if (LATEST.get() == null && document.getElementsByTagName("latest").item(0).getTextContent().equals(neoforgeVersion))
                LATEST.set(neoForgeVersion);
        }

        // reverse the list so that the latest version is at the top
        NEOFORGE_VERSIONS.replaceAll((k, v) -> {
            ObservableList<NeoForgeVersion> reversed = FXCollections.observableArrayList();
            IntStream.iterate(v.size() - 1, i -> i >= 0, i -> i - 1)
                    .mapToObj(v::get)
                    .forEach(reversed::add);
            return reversed;
        });
    }

    public static ObservableList<NeoForgeVersion> getVersions(MinecraftVersion minecraftVersion) {
        return NEOFORGE_VERSIONS.getOrDefault(minecraftVersion, FXCollections.emptyObservableList());
    }

    public static ObservableValue<NeoForgeVersion> latestProperty() {
        return LATEST;
    }

    public static NeoForgeVersion getLatestVersion(MinecraftVersion minecraftVersion) {
        return NEOFORGE_VERSIONS.getOrDefault(minecraftVersion, FXCollections.emptyObservableList()).stream().max((v1, v2) -> {
                    String[] v1Parts = v1.id().split("\\.");
                    String[] v2Parts = v2.id().split("\\.");
                    for (int i = 0; i < Math.min(v1Parts.length, v2Parts.length); i++) {
                        int v1Part = Integer.parseInt(v1Parts[i].split("-")[0]);
                        int v2Part = Integer.parseInt(v2Parts[i].split("-")[0]);
                        if (v1Part != v2Part) {
                            return Integer.compare(v1Part, v2Part);
                        }
                    }

                    return Integer.compare(v1Parts.length, v2Parts.length);
                })
                .orElse(null);
    }

    public static NeoForgeVersion getRecommendedVersion(MinecraftVersion minecraftVersion) {
        return NEOFORGE_VERSIONS.getOrDefault(minecraftVersion, FXCollections.emptyObservableList()).stream()
                .filter(NeoForgeVersion::isRecommended)
                .findFirst()
                .orElse(null);
    }

    public static Optional<NeoForgeVersion> fromId(String id) {
        for (ObservableList<NeoForgeVersion> versions : NEOFORGE_VERSIONS.values()) {
            for (NeoForgeVersion version : versions) {
                if (version.id().equals(id)) {
                    return Optional.of(version);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean isRecommended() {
        return recommended;
    }
}
