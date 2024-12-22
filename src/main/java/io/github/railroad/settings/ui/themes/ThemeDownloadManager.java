package io.github.railroad.settings.ui.themes;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.utility.FileHandler;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static io.github.railroad.Railroad.LOGGER;

/**
 * A class of functions to enable the downloading and management of themes.
 */
public class ThemeDownloadManager {
    /**
     * The last time the themes were refreshed, must be at least 60 seconds ago otherwise rate limiting could occur
     */
    private static final AtomicLong LAST_REFRESHED = new AtomicLong(0);
    private static final List<Theme> THEMES_CACHE = new ArrayList<>();

    private ThemeDownloadManager() {
    }

    /**
     * Downloads the provided theme using the FileHandler utility
     * @param theme {@link Theme} The theme to download
     * @return Whether the theme was downloaded successfully
     */
    public static boolean downloadTheme(@NotNull Theme theme) {
        if (theme.getDownloadUrl() == null) {
            LOGGER.error("Theme download URL is null");
            return false;
        } else {
            String url = theme.getDownloadUrl();
            String[] split = url.split("[/\\s]");
            String fileName = split[split.length - 1];

            LOGGER.info("Downloading theme: {}", fileName);
            try {
                FileHandler.copyUrlToFile(url, Paths.get(getThemesDirectory().toString(), fileName));
                LOGGER.info("Completed theme download");
            } catch (RuntimeException exception) {
                LOGGER.error("Exception downloading theme", exception);
            }

            if (Files.exists(Path.of(getThemesDirectory().toString() + '\\' + fileName))) {
                LOGGER.info("Downloaded theme: {} to {}", fileName, Path.of(getThemesDirectory().toString() + '\\' + fileName));
                return true;
            } else {
                LOGGER.error("Error Downloading theme: {} to {}", fileName, Path.of(getThemesDirectory().toString() + '\\' + fileName));
                return false;
            }
        }
    }

    /**
     * Checks if the provided theme is downloaded
     * @param theme {@link Theme} The theme to check
     * @return bool - Whether the theme is downloaded
     */
    public static boolean isDownloaded(final Theme theme) {
        return getDownloaded().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .anyMatch(t -> t.equals(theme.getName().replace("\"", "")));
    }

    /**
     * Fetches all downloaded themes
     * If the themes directory does not exist, it will be created
     * @return {@link List<Path>} - A list of all downloaded themes
     */
    public static List<Path> getDownloaded() {
        Path dir = getThemesDirectory();

        if (Files.notExists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException exception) {
                LOGGER.warn("Could not create themes directory", exception);
            }
        }

        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(file -> file.toString().endsWith(".css")).toList();
        } catch (IOException exception) {
            LOGGER.warn("Could not fetch installed themes", exception);
            return List.of();
        }
    }

    /**
     * Fetches all themes from the provided URL
     * Themes are then parsed into a list of {@link Theme} objects
     * @param url {@link String} The URL to fetch themes from
     * @return {@link List<Theme>} - A list of all themes fetched
     */
    public static List<Theme> fetchThemes(final String url) {
        if (LAST_REFRESHED.get() + 60_000 > System.currentTimeMillis())
            return ImmutableList.copyOf(THEMES_CACHE);

        List<Theme> itemList = new ArrayList<>();
        JsonArray themesArray;

        LOGGER.info("Fetching themes from: {}", url);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = Railroad.HTTP_CLIENT.newCall(request).execute()) {
            int resCode = response.code();
            if (resCode != 200) {
                LOGGER.error("There was an issue downloading themes. Response Code: {}", resCode);
            }

            ResponseBody body = response.body();
            String bodyStr = body.string();
            if (bodyStr.isBlank()) {
                LOGGER.error("While fetching themes, the body was empty");
                return itemList;
            }

            themesArray = Railroad.GSON.fromJson(bodyStr, JsonArray.class);
        } catch (IOException exception) {
            LOGGER.warn("Error fetching themes", exception);
            return ImmutableList.copyOf(THEMES_CACHE);
        }

        if (themesArray != null && !themesArray.isEmpty()) {
            for (JsonElement element : themesArray) {
                if (!element.isJsonObject())
                    continue;

                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("name") || !obj.get("name").isJsonPrimitive())
                    continue;

                JsonPrimitive name = obj.getAsJsonPrimitive("name");
                if (!name.isString() || !name.getAsString().endsWith(".css"))
                    continue;

                itemList.add(Railroad.GSON.fromJson(element.getAsJsonObject(), Theme.class));
            }
        }

        THEMES_CACHE.clear();
        THEMES_CACHE.addAll(itemList);
        LAST_REFRESHED.set(System.currentTimeMillis());
        return ImmutableList.copyOf(THEMES_CACHE);
    }

    public static Path getThemesDirectory() {
        return ConfigHandler.getConfigDirectory().resolve("themes");
    }
}