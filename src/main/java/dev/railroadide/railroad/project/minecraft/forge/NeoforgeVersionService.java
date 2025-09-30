package dev.railroadide.railroad.project.minecraft.forge;

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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class NeoforgeVersionService extends VersionService<String> {
    private static final String MAVEN_METADATA_URL =
        "https://maven.neoforged.net/net/neoforged/neoforge/maven-metadata.xml";

    public static final NeoforgeVersionService INSTANCE = new NeoforgeVersionService();

    private static final ObjectProperty<String> LATEST_VERSION = new SimpleObjectProperty<>();
    private static final ObjectProperty<String> LATEST_RELEASE_VERSION = new SimpleObjectProperty<>();

    public NeoforgeVersionService() {
        super("Neoforge");
    }

    public NeoforgeVersionService(Duration ttl) {
        super("Neoforge", ttl);
    }

    public NeoforgeVersionService(Duration ttl, String userAgent, HttpClient httpClient) {
        super("Neoforge", ttl, userAgent, httpClient);
    }

    public NeoforgeVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        super("Neoforge", ttl, userAgent, httpTimeout);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return versions().stream()
            .filter(v -> v.startsWith(minecraftVersion.id().substring(2))) // Remove "1."
            .filter(v -> includePrereleases || !isPrerelease(v))
            .max(this::compareVersions);
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
            } catch (NumberFormatException ignored) { // it's not a number (could be a snapshot or beta or etc.)
                cmp = part1.compareTo(part2);
            }

            if (cmp != 0)
                return cmp;
        }

        return 0;
    }

    public static boolean isPrerelease(String version) {
        String lower = version.toLowerCase();
        return lower.contains("beta") || lower.contains("alpha") || lower.contains("rc") || lower.contains("25w14craftmine");
    }

    @Override
    public List<String> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        return versions().stream()
            .filter(v -> includePrereleases || !isPrerelease(v))
            .toList();
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return versions().stream()
            .filter(v -> v.startsWith(minecraftVersion.id().substring(2))) // Remove "1."
            .filter(v -> includePrereleases || !isPrerelease(v))
            .sorted(Comparator.reverseOrder())
            .toList();
    }

    public String latestVersion() {
        if (LATEST_VERSION.get() == null)
            versions();

        return LATEST_VERSION.get();
    }

    public String latestReleaseVersion() {
        if (LATEST_RELEASE_VERSION.get() == null)
            versions();

        return LATEST_RELEASE_VERSION.get();
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
            Railroad.LOGGER.error("Failed to refresh Neoforge versions", exception);
        }
    }

    private List<String> versions() {
        CacheEntry<List<String>> entry = cache.get("all");
        if (entry != null && entry.isActive())
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
            throw new RuntimeException("Failed to fetch/parse Neoforge Maven metadata", exception);
        }
    }

    public static Optional<MinecraftVersion> toMinecraftVersion(String neoforgeVersion) {
        if (neoforgeVersion == null)
            return Optional.empty();

        if(neoforgeVersion.contains("25w14craftmine"))
            return MinecraftVersion.fromId("25w14craftmine");

        int minus = neoforgeVersion.indexOf('-');
        if(minus == 0)
            return Optional.empty();

        int lastDot = neoforgeVersion.lastIndexOf('.');
        if(lastDot == -1)
            return Optional.empty();

        neoforgeVersion = neoforgeVersion.substring(0, lastDot); // Remove build number

        return MinecraftVersion.fromId("1." + (minus < 0 ? neoforgeVersion : neoforgeVersion.substring(0, minus)));
    }

    public record Metadata(List<String> allVersions, String latestVersion, String latestReleaseVersion) {
    }
}
