package io.github.railroad.utility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.*;
public class ConfigHandler {

    public ConfigHandler() {}
    public static void CreateDefaultConfigs() {
        CreateDirectory(getConfigPath());
    }

    public static JSONObject getProjectsConfig() {
        Path projectsJsonPath = getConfigPath().resolve("projects.json");
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(projectsJsonPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading projects.json", e);
        }
        return new JSONObject(jsonContent.toString());
    }
    public static Path getConfigPath() {
        //TODO Implemnet MAC
        var os = System.getProperty("os.name");
        var homePath = System.getProperty("user.home");
        if (os.startsWith("Linux")) {
            return Paths.get(homePath, ".config", "Railroad");
        } else if (os.startsWith("Windows")) {
            return Paths.get(homePath, "AppData", "Roaming", "Railroad");
        } else {
            return Paths.get("");
        }
    }
    private static void CreateDirectory(Path path) {
        try {
            Files.createDirectory(path);
        }
        catch (FileAlreadyExistsException a) {

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
