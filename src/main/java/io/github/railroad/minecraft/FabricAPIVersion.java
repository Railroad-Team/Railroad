package io.github.railroad.minecraft;

import io.github.railroad.utility.XMLParser;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.util.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Optional;

public record FabricAPIVersion(String version, MinecraftVersion minecraftVersion, String fullVersion) {
    private static final ObservableMap<MinecraftVersion, ObservableList<FabricAPIVersion>> VERSIONS = FXCollections.observableHashMap();
    private static final ObjectProperty<FabricAPIVersion> LATEST = new SimpleObjectProperty<>();
    private static final String MAVEN_METADATA_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml";
    private static final MinecraftVersion ONE_FOURTEEN = MinecraftVersion.fromId("1.14").orElseThrow();

    public static void load() {
        requestFabricVersions();
    }

    private static void requestFabricVersions() {
        Document document = XMLParser.parseFromURL(MAVEN_METADATA_URL);

        NodeList versionsNodeList = document.getElementsByTagName("version");
        for (int index = 0; index < versionsNodeList.getLength(); index++) {
            String version = versionsNodeList.item(index).getTextContent();
            Optional<Pair<MinecraftVersion, String>> parsedVersion = parseVersions(version);
            if (parsedVersion.isEmpty())
                continue;

            Pair<MinecraftVersion, String> pair = parsedVersion.get();
            MinecraftVersion minecraftVersionObj = pair.getKey();
            String fabricVersion = pair.getValue();
            VERSIONS.computeIfAbsent(minecraftVersionObj, k -> FXCollections.observableArrayList())
                    .add(new FabricAPIVersion(fabricVersion, minecraftVersionObj, version));
        }

        Node latestNode = document.getElementsByTagName("latest").item(0);
        String latestVersion = latestNode.getTextContent();
        Optional<Pair<MinecraftVersion, String>> parsedMinecraftVersion = parseVersions(latestVersion);
        if (parsedMinecraftVersion.isEmpty())
            return;

        Pair<MinecraftVersion, String> pair = parsedMinecraftVersion.get();
        LATEST.set(new FabricAPIVersion(pair.getValue(), pair.getKey(), latestVersion));
    }

    private static Optional<Pair<MinecraftVersion, String>> parseVersions(String version) {
        String[] parts = version.split("[-+]");
        if (parts.length < 2 || version.contains("pre") || (parts.length == 2 && parts[1].startsWith("build")))
            return Optional.of(new Pair<>(ONE_FOURTEEN, version));

        Optional<MinecraftVersion> minecraftVersion = MinecraftVersion.fromId(parts[1].startsWith("build") ? parts[2] : parts[1]);
        return minecraftVersion.map(value -> new Pair<>(value, parts[0]));
    }

    public static FabricAPIVersion getLatest() {
        return LATEST.get();
    }

    public static List<FabricAPIVersion> getVersions(MinecraftVersion mcVersion) {
        return VERSIONS.computeIfAbsent(mcVersion, k -> FXCollections.observableArrayList());
    }
}