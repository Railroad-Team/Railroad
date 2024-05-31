package io.github.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.project.data.Project;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

@Getter
public class Config implements JsonSerializable<JsonObject> {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private final ObservableList<Plugin> plugins = FXCollections.observableArrayList();
    private final ObjectProperty<Settings> settings = new ReadOnlyObjectWrapper<>(new Settings());

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();

        var projects = new JsonArray();
        for (Project project : this.projects) {
            projects.add(project.toJson());
        }

        json.add("Projects", projects);

        var plugins = new JsonArray();
        for (Plugin plugin : this.plugins) {
            plugins.add(plugin.toJson());
        }

        json.add("Plugins", plugins);
        json.add("Settings", this.settings.get().toJson());

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {

    }
}
