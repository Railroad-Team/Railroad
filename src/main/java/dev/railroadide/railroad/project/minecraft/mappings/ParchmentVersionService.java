package dev.railroadide.railroad.project.minecraft.mappings;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.VersionService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// TODO: Put in an intermediary metadata server that will clone the github repository, fetch the tags and serve them as JSON
// This will be better than doing it per client, and will allow for more advanced features like caching and filtering
public class ParchmentVersionService extends VersionService<String> {
    private static final String MAVEN_METADATA_URL = "https://ldtteam.jfrog.io/artifactory/parchmentmc-public/org/parchmentmc/data/parchment-%s/maven-metadata.xml";

    private static final ObjectProperty<String> LATEST_VERSION = new SimpleObjectProperty<>();
    private static final ObjectProperty<String> LATEST_RELEASE_VERSION = new SimpleObjectProperty<>();

    public static final ParchmentVersionService INSTANCE = new ParchmentVersionService();

    public ParchmentVersionService() {
        super("Parchment");
    }

    public ParchmentVersionService(Duration ttl) {
        super("Parchment", ttl);
    }

    public ParchmentVersionService(Duration ttl, String userAgent, HttpClient httpClient) {
        super("Parchment", ttl, userAgent, httpClient);
    }

    public ParchmentVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        super("Parchment", ttl, userAgent, httpTimeout);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return versions().stream()
            .filter(v -> v.startsWith(minecraftVersion.id()))
            .filter(v -> includePrereleases || !isPrerelease(v))
            .max(this::compareVersions);
    }

    @Override
    public List<String> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        return versions().stream()
            .filter(v -> includePrereleases || !isPrerelease(v))
            .sorted(this::compareVersions)
            .toList();
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return versions().stream()
            .filter(v -> v.startsWith(minecraftVersion.id()))
            .filter(v -> includePrereleases || !isPrerelease(v))
            .sorted(this::compareVersions)
            .toList();
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        try {
            Metadata metadata = fetchAllVersionsFromMaven();

            List<String> fresh = metadata.allVersions();
            cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));

            LATEST_VERSION.set(metadata.latestVersion());
            LATEST_RELEASE_VERSION.set(metadata.latestReleaseVersion());
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to refresh Parchment versions", exception);
        }
    }

    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("[.-]");
        String[] parts2 = version2.split("[.-]");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            String part1 = i < parts1.length ? parts1[i] : "0";
            String part2 = i < parts2.length ? parts2[i] : "0";

            int cmp;
            try {
                Integer int1 = Integer.parseInt(part1);
                Integer int2 = Integer.parseInt(part2);
                cmp = int1.compareTo(int2);
            } catch (NumberFormatException e) {
                cmp = part1.compareTo(part2);
            }

            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static boolean isPrerelease(String version) {
        return version.toLowerCase(Locale.ROOT).contains("snapshot") || version.toLowerCase(Locale.ROOT).contains("nightly");
    }

    private List<String> versions() {
        CacheEntry<List<String>> entry = cache.get("all");
        if(entry != null && entry.isActive())
            return entry.value();

        Metadata metadata = fetchAllVersionsFromMaven();

        List<String> fresh = metadata.allVersions();
        cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));

        LATEST_VERSION.set(metadata.latestVersion());
        LATEST_RELEASE_VERSION.set(metadata.latestReleaseVersion());

        return fresh;
    }

    private Metadata fetchAllVersionsFromMaven() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MAVEN_METADATA_URL))
                .header("User-Agent", userAgent)
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();

            HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
                throw new RuntimeException("Maven metadata HTTP " + response.statusCode());

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setExpandEntityReferences(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            byte[] xml = response.body();
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList nodes = doc.getElementsByTagName("version");

            List<String> out = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                String version = nodes.item(i).getTextContent();
                if (version != null && !version.isBlank()) {
                    out.add(version.trim());
                }
            }

            Node latest = doc.getElementsByTagName("latest").item(0);
            Node release = doc.getElementsByTagName("release").item(0);

            String latestVersion = latest != null ? latest.getTextContent() : null;
            String latestReleaseVersion = release != null ? release.getTextContent() : null;

            return new Metadata(out, latestVersion, latestReleaseVersion);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch/parse Parchment Maven metadata", exception);
        }
    }

    public record Metadata(List<String> allVersions, String latestVersion, String latestReleaseVersion) {
    }
}
