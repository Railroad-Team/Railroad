package dev.railroadide.railroad.project.minecraft;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public abstract class VersionService<T> {
    protected static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(20);

    protected final HttpClient httpClient;
    protected final Duration ttl;
    protected final String userAgent;

    protected final Map<String, CacheEntry<List<T>>> cache = new ConcurrentHashMap<>();

    public VersionService(String serviceName) {
        this(serviceName, Duration.ofHours(6), serviceName + "VersionService/1.0 (+https://railroadide.dev)", DEFAULT_HTTP_TIMEOUT);
    }

    public VersionService(String serviceName, Duration ttl) {
        this(serviceName, ttl, serviceName + "VersionService/1.0 (+https://railroadide.dev)", DEFAULT_HTTP_TIMEOUT);
    }

    public VersionService(String serviceName, Duration ttl, String userAgent, HttpClient httpClient) {
        this.ttl = Objects.requireNonNullElse(ttl, Duration.ofHours(6));
        this.userAgent = Objects.requireNonNullElse(userAgent, serviceName + "VersionService/1.0");
        this.httpClient = Objects.requireNonNullElse(httpClient, HttpClient.newBuilder()
                .connectTimeout(DEFAULT_HTTP_TIMEOUT)
                .build());
    }

    public VersionService(String serviceName, Duration ttl, String userAgent, Duration httpTimeout) {
        this.ttl = Objects.requireNonNullElse(ttl, Duration.ofHours(6));
        this.userAgent = Objects.requireNonNullElse(userAgent, serviceName + "VersionService/1.0");

        HttpClient.Builder builder = HttpClient.newBuilder();
        if (httpTimeout != null)
            builder.connectTimeout(httpTimeout);

        this.httpClient = builder.build();
    }

    public abstract Optional<String> latestFor(String minecraftVersion);

    public abstract Optional<String> latestFor(String minecraftVersion, boolean includePrereleases);

    public abstract List<String> listAllVersions();

    public abstract List<String> listAllVersions(boolean includePrereleases);

    public abstract List<String> listVersionsFor(String minecraftVersion);

    public abstract List<String> listVersionsFor(String minecraftVersion, boolean includePrereleases);

    public void clearCache() {
        cache.clear();
    }

    public abstract void forceRefresh(boolean includePrereleases);

    public record CacheEntry<T>(T value, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
