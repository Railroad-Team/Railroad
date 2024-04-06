package io.github.railroad.utility;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.JsonObject;
import org.json.*;
public class ConfigHandler {

    public ConfigHandler() {}
    public void CreateDefaultConfigs() {
        CreateDirectory(getConfigPath());
        createProjectsJsonIfNotExists();
    }


    private void createProjectsJsonIfNotExists() {
        Path projectsJsonPath = getConfigPath().resolve("config.json");
        if (!Files.exists(projectsJsonPath)) {
            try {
                JSONObject initialData = new JSONObject();
                JSONArray projectsArray = new JSONArray();
                initialData.put("projects", projectsArray);
                writeJsonToFile(projectsJsonPath, initialData.toString());
            } catch (IOException e) {
                throw new RuntimeException("Error creating config.json", e);
            }
        }
    }
    public JSONObject getConfigJson() {
        Path projectsJsonPath = getConfigPath().resolve("config.json");
        if (!Files.exists(projectsJsonPath)) {
            createProjectsJsonIfNotExists();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(projectsJsonPath.toFile()))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            return new JSONObject(jsonContent.toString());
            //return jsonObject.getJSONArray("projects");
        } catch (IOException e) {
            throw new RuntimeException("Error reading config.json", e);
        }
    }

    public JSONArray getProjectsConfig() {
        return getConfigJson().getJSONArray("projects");
    }
    public Path getConfigPath() {
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
    private void CreateDirectory(Path path) {
        try {
            Files.createDirectory(path);
        }
        catch (FileAlreadyExistsException a) {

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void updateConfig(JSONObject obj) {
        Path projectsJsonPath = getConfigPath().resolve("config.json");
        if (!Files.exists(projectsJsonPath)) {
            createProjectsJsonIfNotExists();
        }
        try {
            writeJsonToFile(projectsJsonPath, obj.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    private void writeJsonToFile(Path filePath, String jsonContent) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
            writer.write(jsonContent);
        }
    }
}
