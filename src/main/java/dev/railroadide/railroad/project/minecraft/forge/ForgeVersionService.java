package dev.railroadide.railroad.project.minecraft.forge;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.VersionService;
import lombok.Data;
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
import java.util.*;
import java.util.stream.Collectors;

public class ForgeVersionService extends VersionService<String> {
    private static final String MAVEN_METADATA_URL =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    public static final ForgeVersionService INSTANCE = new ForgeVersionService();

    private CacheEntry<Promotions> promotionsCache = null;

    public ForgeVersionService() {
        super("Forge");
    }

    public ForgeVersionService(Duration ttl) {
        super("Forge", ttl);
    }

    public ForgeVersionService(Duration ttl, String userAgent, HttpClient httpClient) {
        super("Forge", ttl, userAgent, httpClient);
    }

    public ForgeVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        super("Forge", ttl, userAgent, httpTimeout);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        List<String> list = listVersionsFor(minecraftVersion);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return latestFor(minecraftVersion);
    }

    public Optional<String> recommendedFor(MinecraftVersion minecraftVersion) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Promotions promos = promotions();
        if (promos == null || promos.promos == null)
            return Optional.empty();

        String key = minecraftVersion.id() + "-recommended";
        String build = promos.promos.get(key);
        if (build == null || build.isBlank())
            return Optional.empty();

        // promotions map returns just the Forge build (e.g., "58.0.10"), compose full coordinate
        return Optional.of(minecraftVersion.id() + "-" + build);
    }

    public boolean isRecommended(String forgeVersion) {
        Objects.requireNonNull(forgeVersion, "forgeVersion");
        Optional<MinecraftVersion> mcVersion = toMinecraftVersion(forgeVersion);
        if (mcVersion.isEmpty())
            return false;

        Promotions promos = promotions();
        if (promos == null || promos.promos == null)
            return false;

        String key = mcVersion.get().id() + "-recommended";
        String build = promos.promos.get(key);
        if (build == null || build.isBlank())
            return false;

        String expected = mcVersion.get().id() + "-" + build;
        return forgeVersion.equals(expected);
    }

    @Override
    public List<String> listAllVersions() {
        return versions();
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        return listAllVersions();
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        return versions().stream()
                .filter(v -> toMinecraftVersion(v).map(minecraftVersion::equals).orElse(false))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return listVersionsFor(minecraftVersion);
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        try {
            List<String> fresh = fetchAllVersionsFromMaven();
            cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
            Promotions promotions = fetchPromotions();
            promotionsCache = new CacheEntry<>(promotions, Instant.now().plus(ttl));
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to refresh Forge versions", exception);
        }
    }

    private List<String> versions() {
        CacheEntry<List<String>> entry = cache.get("all");
        if (entry != null && entry.isActive())
            return entry.value();

        List<String> fresh = fetchAllVersionsFromMaven();
        cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
        return fresh;
    }

    private Promotions promotions() {
        if (promotionsCache != null && promotionsCache.isActive())
            return promotionsCache.value();

        Promotions promotions = fetchPromotions();
        promotionsCache = new CacheEntry<>(promotions, Instant.now().plus(ttl));
        return promotions;
    }

    private List<String> fetchAllVersionsFromMaven() {
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

            return List.copyOf(out);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch/parse Forge Maven metadata", exception);
        }
    }

    private Promotions fetchPromotions() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(PROMOTIONS_URL))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .timeout(DEFAULT_HTTP_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new RuntimeException("Promotions HTTP " + response.statusCode() + ": " + response.body());

            return Railroad.GSON.fromJson(response.body(), Promotions.class);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch/parse Forge promotions JSON", exception);
        }
    }

    // Extract minecraft version from a forge version like "1.21.8-58.0.10".
    public static Optional<MinecraftVersion> toMinecraftVersion(String forgeVersion) {
        if (forgeVersion == null)
            return Optional.empty();

        int dash = forgeVersion.indexOf('-');
        if (dash <= 0)
            return Optional.empty();

        return MinecraftVersion.fromId(forgeVersion.substring(0, dash));
    }

    @Data
    private static final class Promotions {
        String homepage;
        Map<String, String> promos;
    }
}
