package dev.railroadide.railroad.project.minecraft.fabric;

import dev.railroadide.railroad.project.minecraft.VersionService;
import org.w3c.dom.Document;
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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class FabricApiMavenVersionService extends VersionService<String> {
    private static final String METADATA_URL =
            "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml";

    public static final FabricApiMavenVersionService INSTANCE = new FabricApiMavenVersionService();

    public FabricApiMavenVersionService() {
        super("FabricApi");
    }

    public FabricApiMavenVersionService(Duration ttl) {
        super("FabricApi", ttl);
    }

    public FabricApiMavenVersionService(Duration ttl, String userAgent, HttpClient httpClient) {
        super("FabricApi", ttl, userAgent, httpClient);
    }

    public FabricApiMavenVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        super("FabricApi", ttl, userAgent, httpTimeout);
    }

    @Override
    public Optional<String> latestFor(String minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<String> latestFor(String minecraftVersion, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        List<String> forMc = listVersionsFor(minecraftVersion, includePrereleases);
        return forMc.isEmpty() ? Optional.empty() : Optional.of(forMc.getLast()); // metadata is ascending
    }

    @Override
    public List<String> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        return versions().stream()
                .filter(v -> includePrereleases || isRelease(v))
                .collect(Collectors.toList()); // keep original (ascending) order from metadata
    }

    @Override
    public List<String> listVersionsFor(String minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    @Override
    public List<String> listVersionsFor(String minecraftVersion, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        return versions().stream()
                .filter(version -> includePrereleases || isRelease(version))
                .filter(version -> mcTokenOf(version).map(minecraftVersion::equalsIgnoreCase).orElse(false))
                .collect(Collectors.toList()); // ascending order preserved
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        List<String> fresh = fetchAllVersionsFromMaven();
        cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
    }

    private List<String> versions() {
        CacheEntry<List<String>> entry = cache.get("all");
        if (entry != null && !entry.isExpired())
            return entry.value();

        List<String> fresh = fetchAllVersionsFromMaven();
        cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
        return fresh;
    }

    private List<String> fetchAllVersionsFromMaven() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(METADATA_URL))
                    .header("User-Agent", userAgent)
                    .timeout(DEFAULT_HTTP_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
                throw new RuntimeException("Maven metadata HTTP " + response.statusCode());

            byte[] xml = response.body();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setExpandEntityReferences(false);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList nodes = doc.getElementsByTagName("version");

            List<String> versions = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                String val = nodes.item(i).getTextContent();
                if (val != null && !val.isBlank()) versions.add(val.trim());
            }

            return List.copyOf(versions);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch/parse Maven metadata", exception);
        }
    }

    private static Optional<String> mcTokenOf(String fabricApiVersion) {
        int plus = fabricApiVersion.indexOf('+');
        if (plus < 0 || plus == fabricApiVersion.length() - 1)
            return Optional.empty();

        return Optional.of(fabricApiVersion.substring(plus + 1));
    }

    private static boolean isRelease(String version) {
        return !version.contains("-");
    }
}
