package dev.railroadide.railroad.project.minecraft.fabric;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.VersionService;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FabricApiGithubReleaseVersionService extends VersionService<FabricApiGithubReleaseVersionService.GithubRelease> {
    private static final String OWNER = "FabricMC";
    private static final String REPO = "fabric";
    private static final String BASE = "https://api.github.com";

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("^\\[(?<mc>[^\\]]+)]\\s*Fabric API\\s+(?<api>\\S+)");

    public static final FabricApiGithubReleaseVersionService INSTANCE = new FabricApiGithubReleaseVersionService();

    private final String githubToken;

    public FabricApiGithubReleaseVersionService(String githubToken) {
        super("FabricApi");
        this.githubToken = githubToken;
    }

    public FabricApiGithubReleaseVersionService(Duration ttl, String githubToken) {
        super("FabricApi", ttl);
        this.githubToken = githubToken;
    }

    public FabricApiGithubReleaseVersionService(Duration ttl, String userAgent, HttpClient httpClient, String githubToken) {
        super("FabricApi", ttl, userAgent, httpClient);
        this.githubToken = githubToken;
    }

    public FabricApiGithubReleaseVersionService(Duration ttl, String userAgent, Duration httpTimeout, String githubToken) {
        super("FabricApi", ttl, userAgent, httpTimeout);
        this.githubToken = githubToken;
    }

    public FabricApiGithubReleaseVersionService() {
        this((String) null);
    }

    public FabricApiGithubReleaseVersionService(Duration ttl) {
        this(ttl, null);
    }

    public FabricApiGithubReleaseVersionService(Duration ttl, String userAgent, HttpClient httpClient) {
        this(ttl, userAgent, httpClient, null);
    }

    public FabricApiGithubReleaseVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        this(ttl, userAgent, httpTimeout, null);
    }

    @Override
    public Optional<String> latestFor(String minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<String> latestFor(String minecraftVersion, boolean includePrereleases) {
        return releases(includePrereleases).stream()
                .map(FabricApiGithubReleaseVersionService::parseReleaseTitle)
                .filter(Objects::nonNull)
                .filter(release -> release.mcVersion.equalsIgnoreCase(minecraftVersion))
                .sorted(Comparator.comparing(release -> release.publishedAt, Comparator.reverseOrder()))
                .map(release -> release.apiVersion)
                .findFirst();
    }

    @Override
    public List<String> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        return releases(includePrereleases).stream()
                .map(FabricApiGithubReleaseVersionService::parseReleaseTitle)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(release -> release.publishedAt, Comparator.reverseOrder()))
                .map(release -> release.apiVersion)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listVersionsFor(String minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    @Override
    public List<String> listVersionsFor(String minecraftVersion, boolean includePrereleases) {
        return releases(includePrereleases).stream()
                .map(FabricApiGithubReleaseVersionService::parseReleaseTitle)
                .filter(Objects::nonNull)
                .filter(release -> release.mcVersion.equalsIgnoreCase(minecraftVersion))
                .sorted(Comparator.comparing(r -> r.publishedAt, Comparator.reverseOrder()))
                .map(release -> release.apiVersion)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        List<GithubRelease> fresh = fetchAllReleases(includePrereleases);
        String key = includePrereleases ? "all:+pre" : "all:-pre";
        cache.put(key, new CacheEntry<>(fresh, Instant.now().plus(ttl)));
    }

    private List<GithubRelease> releases(boolean includePrereleases) {
        String key = includePrereleases ? "all:+pre" : "all:-pre";
        CacheEntry<List<GithubRelease>> entry = cache.get(key);
        if (entry != null && !entry.isExpired())
            return entry.value();

        List<GithubRelease> fresh = fetchAllReleases(includePrereleases);
        cache.put(key, new CacheEntry<>(fresh, Instant.now().plus(ttl)));
        return fresh;
    }

    private List<GithubRelease> fetchAllReleases(boolean includePrereleases) {
        try {
            List<GithubRelease> result = new ArrayList<>();
            int page = 1;
            while (true) {
                String url = BASE + "/repos/" + OWNER + "/" + REPO + "/releases?per_page=100&page=" + page;
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(DEFAULT_HTTP_TIMEOUT)
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .GET();
                if (githubToken != null && !githubToken.isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + githubToken);
                }

                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                if (response.statusCode() == 422 && body != null && body.contains("Only the first 1000 results are available"))
                    break;

                if (response.statusCode() != 200)
                    throw new IOException("GitHub API " + response.statusCode() + ": " + body);

                Type listType = TypeToken.getParameterized(List.class, GithubRelease.class).getType();
                List<GithubRelease> pageReleases = Railroad.GSON.fromJson(body, listType);
                if (pageReleases == null || pageReleases.isEmpty())
                    break;

                for (GithubRelease release : pageReleases) {
                    if (Boolean.TRUE.equals(release.draft))
                        continue;

                    if (!includePrereleases && Boolean.TRUE.equals(release.prerelease))
                        continue;

                    result.add(release);
                }

                Optional<String> link = response.headers().firstValue("Link");
                if (link.isEmpty() || !hasRelNext(link.get()))
                    break;

                page++;
                if (page > 10)
                    break; // 10 * 100 = 1000 releases max
            }

            result.sort(Comparator.comparing(release -> release.publishedAt, Comparator.reverseOrder()));
            return List.copyOf(result);
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch GitHub releases", exception);
        }
    }

    private static ParsedRelease parseReleaseTitle(GithubRelease release) {
        if (release.name == null)
            return null;

        Matcher matcher = TITLE_PATTERN.matcher(release.name.trim());
        if (!matcher.find())
            return null;

        String mc = matcher.group("mc");
        String api = matcher.group("api");
        Instant pub = (release.publishedAt == null || release.publishedAt.isBlank())
                ? Instant.EPOCH
                : Instant.parse(release.publishedAt);
        return new ParsedRelease(mc, api, pub);
    }

    /**
     * Looks for RFC5988 Link header to see if it contains rel="next".
     * <p>
     * See <a href="https://docs.github.com/en/rest/guides/traversing-with-pagination">here</a>
     *
     * @param linkHeader the Link header value
     * @return true if rel="next" is present, false otherwise
     */
    private static boolean hasRelNext(String linkHeader) {
        for (String part : linkHeader.split(",")) {
            if (part.contains("rel=\"next\""))
                return true;
        }

        return false;
    }

    public static final class GithubRelease {
        String name;
        Boolean draft;
        Boolean prerelease;
        @SerializedName("published_at")
        String publishedAt;
    }

    protected record ParsedRelease(String mcVersion, String apiVersion, Instant publishedAt) {
    }
}
