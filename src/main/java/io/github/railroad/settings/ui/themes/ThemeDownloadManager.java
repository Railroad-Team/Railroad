package io.github.railroad.settings.ui.themes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railroad.utility.FileHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import static io.github.railroad.Railroad.LOGGER;

public class ThemeDownloadManager {
    private static ZonedDateTime lastRefreshed = ZonedDateTime.now().minusSeconds(60);
    private static List<JsonObject> themesCache = new ArrayList<>();

    private ThemeDownloadManager() {}

    public static boolean downloadTheme(final URL url) {
        if(url == null) {
            LOGGER.error("Theme is null");
            return false;
        } else {
            var split = url.toString().split("[/\\s]");
            var fileName = split[split.length - 1];

            LOGGER.info("Downloading theme: {}", fileName);
            try {
                Thread downloadThread = new Thread(() -> FileHandler.copyUrlToFile(url.toString(), Paths.get(getThemesDir().toString(), fileName)));
                downloadThread.start();
                downloadThread.join();

                LOGGER.info("Completed theme download");
            } catch (Exception e) {
                throw new RuntimeException("Exception downloading theme", e);
            }

            if(Files.exists(Path.of(getThemesDir().toString() + '\\' + fileName))) {
                LOGGER.info("Downloaded theme: {} to {}", fileName, Path.of(getThemesDir().toString() + '\\' + fileName));
                return true;
            } else {
                LOGGER.error("Error Downloading theme: {} to {}", fileName, Path.of(getThemesDir().toString() + '\\' + fileName));
                return false;
            }
        }
    }

    public static boolean isDownloaded(final String theme) {
        return getDownloaded().contains(theme);
    }

    public static ObservableList<String> getDownloaded() {
        Stream<String> themes;
        Path dir = getThemesDir();

        if(Files.notExists(dir)) {
            try {
                Files.createDirectory(dir);
            } catch (IOException e) {
                throw new RuntimeException("Could not create themes directory", e);
            }
        }

        try {
            themes = Files.list(dir).map(e -> e.getFileName().toString());
        } catch (IOException e) {
            throw new RuntimeException("Could not fetch installed themes", e);
        }

        return FXCollections.observableList(themes.toList());
    }

    public static List<JsonObject> fetchThemes(final String url) {
        List<JsonObject> itemList = new ArrayList<>();
        JsonArray jsonRes;

        if(ChronoUnit.SECONDS.between(lastRefreshed, ZonedDateTime.now()) < 60)
            return themesCache;

        try {
            LOGGER.info("FETCHING THEMES");
            URL conUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) conUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int resCode = connection.getResponseCode();

            if(resCode != 200) {
                throw new RuntimeException("THEME DOWNLOADER ERROR " + resCode);
            }

            String inline = "";
            Scanner scanner = new Scanner(conUrl.openStream());

            while (scanner.hasNext()) {
                inline += scanner.nextLine();
            }

            scanner.close();
            connection.disconnect();

            jsonRes = JsonParser.parseString(inline).getAsJsonArray();
            lastRefreshed = ZonedDateTime.now();
        } catch (IOException e) {
            throw new RuntimeException("Could not list themes from github.");
        }

        if(!jsonRes.isEmpty()) {
            jsonRes.forEach(item -> {
                if(item.getAsJsonObject().get("name").toString().replace("\"", "").endsWith("css")) {
                    itemList.add(item.getAsJsonObject());
                }
            });
        }

        themesCache = itemList;
        return itemList;
    }

    public static Path getThemesDir() {
        Path dir;
        var os = System.getProperty("os.name");
        var userHome = System.getProperty("user.home");

        if(os.startsWith("Linux")) {
            dir = Paths.get(userHome, ".config", "Railroad", "themes");
        } else if(os.startsWith("Windows")) {
            dir = Paths.get(userHome, "AppData", "Roaming", "Railroad", "themes");
        } else {
            dir = Paths.get("");
        }

        return dir;
    }
}