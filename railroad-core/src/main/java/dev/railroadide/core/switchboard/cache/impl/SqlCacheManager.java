package dev.railroadide.core.switchboard.cache.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.core.logger.LoggerServiceLocator;
import dev.railroadide.core.switchboard.cache.CacheEntryWrapper;
import dev.railroadide.core.switchboard.cache.MetadataCacheEntry;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SqlCacheManager implements IterableCacheManager {
    private final Connection connection;
    private final Map<String, MetadataCacheEntry<?>> memoryCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqlCacheManager(String uri) throws SQLException {
        this.connection = DriverManager.getConnection(uri);
        initSchema();
    }

    public SqlCacheManager(Path dbFile) throws SQLException {
        this("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cache_entries (
                        key TEXT PRIMARY KEY,
                        value BLOB NOT NULL,
                        type TEXT,
                        last_fetched INTEGER NOT NULL,
                        ttl_seconds INTEGER NOT NULL,
                        etag TEXT
                    )
                """);
        }
    }

    @Override
    public <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, TypeToken<@NotNull T> typeToken) {
        // first check in-memory cache
        @SuppressWarnings("unchecked")
        MetadataCacheEntry<T> memEntry = (MetadataCacheEntry<T>) memoryCache.get(key);
        if (memEntry != null && !memEntry.isExpired())
            return CompletableFuture.completedFuture(Optional.of(memEntry));

        CompletableFuture<Optional<MetadataCacheEntry<T>>> future = new CompletableFuture<>();

        // then check SQLite cache
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT value, type, last_fetched, ttl_seconds, etag FROM cache_entries WHERE key = ?")) {
                stmt.setString(1, key);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    byte[] jsonBytes = rs.getBytes("value");

                    T data = objectMapper.readValue(jsonBytes, objectMapper.getTypeFactory().constructType(typeToken.getType()));

                    Instant lastFetched = Instant.ofEpochMilli(rs.getLong("last_fetched"));
                    Duration ttl = Duration.ofSeconds(rs.getLong("ttl_seconds"));
                    String etag = rs.getString("etag");

                    var entry = new MetadataCacheEntry<T>(data, lastFetched, typeToken, ttl, etag);
                    if (!entry.isExpired()) {
                        memoryCache.put(key, entry);
                        future.complete(Optional.of(entry));
                    }
                } else {
                    future.complete(Optional.empty());
                }
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });

        return future;
    }

    @Override
    public <T> T put(String key, MetadataCacheEntry<T> entry) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] jsonBytes = objectMapper.writeValueAsBytes(entry.data());

                try (PreparedStatement stmt = connection.prepareStatement("""
                        INSERT INTO cache_entries (key, value, type, last_fetched, ttl_seconds, etag)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(key) DO UPDATE SET
                            value = excluded.value,
                            type = excluded.type,
                            last_fetched = excluded.last_fetched,
                            ttl_seconds = excluded.ttl_seconds,
                            etag = excluded.etag
                    """)) {
                    stmt.setString(1, key);
                    stmt.setBytes(2, jsonBytes);
                    stmt.setString(3, entry.dataClass().getType().getTypeName());
                    stmt.setLong(4, entry.lastFetched().toEpochMilli());
                    stmt.setLong(5, entry.ttl().toSeconds());
                    stmt.setString(6, entry.etag());
                    stmt.executeUpdate();
                }

                memoryCache.put(key, entry);
            } catch (Exception exception) {
                LoggerServiceLocator.getInstance().getLogger().error("Failed to put cache entry for key: {}", key, exception);
            }
        });

        return entry.data();
    }

    @Override
    public void invalidate(String key) {
        memoryCache.remove(key);
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM cache_entries WHERE key = ?")) {
            stmt.setString(1, key);
            stmt.executeUpdate();
        } catch (SQLException exception) {
            LoggerServiceLocator.getInstance().getLogger().error("Failed to invalidate cache entry for key: {}", key, exception);
        }
    }

    @Override
    public Iterable<CacheEntryWrapper> entries() {
        List<CacheEntryWrapper> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT key, value, type, last_fetched, ttl_seconds, etag FROM cache_entries");
            while (rs.next()) {
                String key = rs.getString("key");
                String typeName = rs.getString("type");
                byte[] jsonBytes = rs.getBytes("value");

                try {
                    Class<?> clazz = Class.forName(typeName);
                    TypeToken<?> typeToken = TypeToken.get(clazz);

                    MetadataCacheEntry<?> entry = objectMapper.readValue(
                        jsonBytes,
                        objectMapper.getTypeFactory().constructParametricType(MetadataCacheEntry.class, clazz)
                    );

                    results.add(new CacheEntryWrapper(key, entry, typeToken));
                } catch (Exception exception) {
                    LoggerServiceLocator.getInstance().getLogger().error("Failed to deserialize cache entry: {}", key, exception);
                }
            }
        } catch (SQLException exception) {
            LoggerServiceLocator.getInstance().getLogger().error("Failed to iterate cache entries", exception);
        }

        return results;
    }
}
