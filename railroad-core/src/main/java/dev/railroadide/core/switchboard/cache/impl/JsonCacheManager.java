package dev.railroadide.core.switchboard.cache.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.core.logger.LoggerServiceLocator;
import dev.railroadide.core.switchboard.cache.CacheEntryWrapper;
import dev.railroadide.core.switchboard.cache.MetadataCacheEntry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class JsonCacheManager implements IterableCacheManager {
    private final Path baseDir;
    private final Gson gson;
    private final Map<String, MetadataCacheEntry<?>> memoryCache = new ConcurrentHashMap<>();

    public JsonCacheManager(Path baseDir, Gson gson) throws UncheckedIOException {
        this.baseDir = baseDir;
        this.gson = gson;

        try {
            Files.createDirectories(baseDir);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create cache directory: " + baseDir, exception);
        }
    }

    @Override
    public <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, TypeToken<@NotNull T> typeToken) {
        return CompletableFuture.supplyAsync(() -> {
            // First check in-memory cache
            @SuppressWarnings("unchecked")
            MetadataCacheEntry<T> memEntry = (MetadataCacheEntry<T>) memoryCache.get(key);
            if (memEntry != null && !memEntry.isExpired())
                return Optional.of(memEntry);

            // Fallback to disk
            Path file = toPath(key);
            if (Files.notExists(file))
                return Optional.empty();

            try (Reader reader = Files.newBufferedReader(file)) {
                Type wrapperType = TypeToken.getParameterized(MetadataCacheEntry.class, typeToken.getType()).getType();
                MetadataCacheEntry<T> entry = gson.fromJson(reader, wrapperType);

                if (entry == null)
                    return Optional.empty();

                if (!entry.isExpired()) {
                    memoryCache.put(key, entry);
                    return Optional.of(entry);
                }
            } catch (IOException exception) {
                LoggerServiceLocator.getInstance().getLogger().warn("Failed to read cache file: {}", file, exception);
                invalidate(key);
            }
            return Optional.empty();
        });
    }

    @Override
    public <T> T put(String key, MetadataCacheEntry<T> entry) {
        memoryCache.put(key, entry);

        Path file = toPath(key);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(entry, writer);
        } catch (IOException exception) {
            LoggerServiceLocator.getInstance().getLogger().warn("Failed to write cache file: {}", file, exception);
        }

        return entry.data();
    }

    @Override
    public void invalidate(String key) {
        memoryCache.remove(key);
        Path file = toPath(key);
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            LoggerServiceLocator.getInstance().getLogger().warn("Failed to delete cache file: {}", file, exception);
        }
    }

    private Path toPath(String key) {
        String safeName = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return baseDir.resolve(safeName + ".json");
    }

    private String fromPath(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".json".length()).replace('_', ':');
    }

    @Override
    public Iterable<CacheEntryWrapper> entries() {
        try (Stream<Path> stream = Files.list(baseDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> {
                    String key = fromPath(path);
                    try (Reader reader = Files.newBufferedReader(path)) {
                        JsonObject root = gson.fromJson(reader, JsonObject.class);
                        if (root == null)
                            return null;

                        // Read the entry without knowing the data type
                        MetadataCacheEntry<?> entry = gson.fromJson(root, MetadataCacheEntry.class);

                        // Try to determine the actual data type
                        TypeToken<?> typeToken = TypeToken.get(Object.class); // fallback
                        if (root.has("dataClass")) {
                            try {
                                String className = root.get("dataClass").getAsString();
                                typeToken = TypeToken.get(Class.forName(className));
                            } catch (Exception ignored) {}
                        }

                        return new CacheEntryWrapper(key, entry, typeToken);
                    } catch (Exception exception) {
                        LoggerServiceLocator.getInstance().getLogger().error("Failed to read cache entry: {}", path, exception);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException exception) {
            LoggerServiceLocator.getInstance().getLogger().error("Failed to list cache directory: {}", baseDir, exception);
            return List.of();
        }
    }
}
