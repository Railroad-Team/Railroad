package dev.railroadide.railroad.project.minecraft.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class ParchmentVersionService implements AutoCloseable {
    public static final ParchmentVersionService INSTANCE = new ParchmentVersionService();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final StringProperty baseUrl = new SimpleStringProperty("https://switchboard.railroadide.dev/");
    private final StringProperty userAgent = new SimpleStringProperty("Parchment VersionService/1.0 (+https://railroadide.dev)");
    private final ObjectProperty<Duration> httpTimeout = new SimpleObjectProperty<>(Duration.ofSeconds(20));

    public void setBaseUrl(String url) {
        Objects.requireNonNull(url, "Base URL cannot be null");
        this.baseUrl.set(url.endsWith("/") ? url : url + "/");
    }

    public void setUserAgent(String userAgent) {
        Objects.requireNonNull(userAgent, "User-Agent cannot be null");
        this.userAgent.set(userAgent);
    }

    public void setHttpTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "HTTP timeout cannot be null");
        this.httpTimeout.set(timeout);
    }

    private Optional<HttpResponse<String>> makeRequest(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.get() + endpoint))
                .header("User-Agent", userAgent.get())
                .timeout(httpTimeout.get())
                .GET()
                .build();

            return Optional.ofNullable(this.httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException exception) {
            Railroad.LOGGER.error("I/O error during HTTP request to Parchment service", exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Railroad.LOGGER.error("HTTP request to Parchment service was interrupted", exception);
            return Optional.empty();
        } catch (Exception exception) {
            Railroad.LOGGER.error("Unexpected error during HTTP request to Parchment service", exception);
            return Optional.empty();
        }
    }

    public Optional<String> latestFor(MinecraftVersion minecraftVersion) {
        return makeRequest("parchment/latest/" + minecraftVersion.id())
            .filter(response -> response.statusCode() == 200)
            .map(HttpResponse::body)
            .filter(body -> !body.isBlank())
            .map(bodyStr -> Railroad.GSON.fromJson(bodyStr, JsonObject.class))
            .map(json -> json.get("version").getAsString());
    }

    public List<String> listAllVersions() {
        return makeRequest("parchment/versions")
            .filter(response -> response.statusCode() == 200)
            .map(HttpResponse::body)
            .filter(body -> !body.isBlank())
            .map(bodyStr -> Railroad.GSON.fromJson(bodyStr, JsonArray.class))
            .map(jsonArray -> {
                List<String> versions = new ArrayList<>();
                jsonArray.forEach(element -> versions.add(element.getAsJsonObject().get("version").getAsString()));
                return versions;
            })
            .orElse(Collections.emptyList());
    }

    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        return makeRequest("parchment/versions/" + minecraftVersion.id())
            .filter(response -> response.statusCode() == 200)
            .map(HttpResponse::body)
            .filter(body -> !body.isBlank())
            .map(bodyStr -> Railroad.GSON.fromJson(bodyStr, JsonArray.class))
            .map(jsonArray -> {
                List<String> versions = new ArrayList<>();
                jsonArray.forEach(element -> versions.add(element.getAsJsonObject().get("version").getAsString()));
                return versions;
            })
            .orElse(Collections.emptyList());
    }

    public static int compareVersions(String version1, String version2) {
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

    public static boolean isPrerelease(String version) {
        return version.toLowerCase(Locale.ROOT).contains("snapshot") || version.toLowerCase(Locale.ROOT).contains("nightly");
    }

    @Override
    public void close() throws Exception {
        this.httpClient.close();
    }
}
