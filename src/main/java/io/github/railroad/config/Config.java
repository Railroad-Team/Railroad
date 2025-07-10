package io.github.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.project.Project;
import io.github.railroad.utility.JsonSerializable;

import java.util.Optional;

public class Config implements JsonSerializable<JsonObject> {
    @Override
    public JsonObject toJson() {
        var json = new JsonObject();

        var projects = new JsonArray();
        for (Project project : Railroad.PROJECT_MANAGER.getProjects()) {
            projects.add(project.toJson());
        }

        json.add("Projects", projects);

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if (json.has("Projects")) {
            JsonElement projects = json.get("Projects");
            if (projects.isJsonArray()) {
                JsonArray projectsArray = projects.getAsJsonArray();
                for (JsonElement project : projectsArray) {
                    if (!project.isJsonObject())
                        continue;

                    Optional<Project> optProject = Project.createFromJson(project.getAsJsonObject());
                    optProject.ifPresent(Railroad.PROJECT_MANAGER::newProject);
                }
            }
        }
    }
}
