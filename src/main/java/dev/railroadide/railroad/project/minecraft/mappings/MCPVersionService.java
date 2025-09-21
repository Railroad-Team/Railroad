package dev.railroadide.railroad.project.minecraft.mappings;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.VersionService;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class MCPVersionService extends VersionService<String> {
    private static final String STABLE_METADATA_URL =
            "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/maven-metadata.xml";
    private static final String SNAPSHOT_METADATA_URL =
            "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_snapshot/maven-metadata.xml";

    public static final MCPVersionService INSTANCE = new MCPVersionService();

    public MCPVersionService() {
        super("MCP");
    }

    public MCPVersionService(Duration ttl) {
        super("MCP", ttl);
    }

    public MCPVersionService(Duration ttl, String userAgent, java.net.http.HttpClient httpClient) {
        super("MCP", ttl, userAgent, httpClient);
    }

    public MCPVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        super("MCP", ttl, userAgent, httpTimeout);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");

        List<String> stable = listVersionsFor(minecraftVersion, false);
        if (!stable.isEmpty())
            return Optional.of(stable.getLast()); // metadata is ascending

        if (includePrereleases) {
            List<String> snapshot = listVersionsFor(minecraftVersion, true)
                    .stream()
                    .filter(MCPVersionService::isSnapshot)
                    .toList();

            if (!snapshot.isEmpty())
                return Optional.of(snapshot.getLast()); // metadata is ascending
        }

        return Optional.empty();
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        List<String> stable = versionsStable();
        if (!includePrereleases)
            return stable;

        List<String> combined = new ArrayList<>(stable.size() + 64); // rough estimate
        combined.addAll(stable);
        combined.addAll(versionsSnapshot());
        combined.sort(String::compareTo);
        return combined;
    }

    @Override
    public List<String> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");

        List<String> output = versionsStable().stream()
                .filter(v -> toMinecraftVersion(v).map(minecraftVersion::equals).orElse(false))
                .collect(Collectors.toCollection(ArrayList::new)); // ascending order preserved

        if (includePrereleases) {
            versionsSnapshot().stream()
                    .filter(v -> toMinecraftVersion(v).map(minecraftVersion::equals).orElse(false))
                    .forEach(output::add); // ascending order preserved
        }

        return output;
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        try {
            cache.remove("mcp:stable");
            cache.remove("mcp:snapshot");
            Instant expiresAt = Instant.now().plus(ttl);
            cache.put("mcp:stable", new CacheEntry<>(fetchAllVersionsFrom(STABLE_METADATA_URL), expiresAt));
            if (includePrereleases) {
                cache.put("mcp:snapshot", new CacheEntry<>(fetchAllVersionsFrom(SNAPSHOT_METADATA_URL), expiresAt));
            }
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to refresh MCP versions", exception);
        }
    }

    private List<String> versionsStable() {
        CacheEntry<List<String>> cacheEntry = cache.get("mcp:stable");
        if (cacheEntry != null && cacheEntry.isActive())
            return cacheEntry.value();

        List<String> fresh = fetchAllVersionsFrom(STABLE_METADATA_URL);
        cache.put("mcp:stable", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
        return fresh;
    }

    private List<String> versionsSnapshot() {
        CacheEntry<List<String>> cacheEntry = cache.get("mcp:snapshot");
        if (cacheEntry != null && cacheEntry.isActive())
            return cacheEntry.value();

        List<String> fresh = fetchAllVersionsFrom(SNAPSHOT_METADATA_URL);
        cache.put("mcp:snapshot", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
        return fresh;
    }

    private List<String> fetchAllVersionsFrom(String metadataUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(metadataUrl))
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

            List<String> versions = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                String val = nodes.item(i).getTextContent();
                if (val != null && !val.isBlank()) {
                    versions.add(val.trim());
                }
            }

            return List.copyOf(versions); // ascending as given by Maven
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch/parse MCP Maven metadata (" + metadataUrl + ")", exception);
        }
    }

    private static Optional<MinecraftVersion> toMinecraftVersion(String mcpVersion) {
        int index = mcpVersion.lastIndexOf('-');
        return MinecraftVersion.fromId((index >= 0 && index + 1 < mcpVersion.length()) ?
                mcpVersion.substring(index + 1) :
                mcpVersion);
    }

    private static boolean isSnapshot(String version) {
        // Snapshots are fetched from mcp_snapshot (date prefix), but keep a defensive check.
        int index = version.indexOf('-');
        return index == 8 && version.chars().limit(8).allMatch(Character::isDigit);
    }
}
