package io.github.railroad.plugin;

import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.config.PluginSettings;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.*;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public abstract class Plugin implements JsonSerializable<JsonPrimitive> {
    private final ObjectProperty<PluginSettings> pluginSettings = new ReadOnlyObjectWrapper<>();
    private final ObjectProperty<PluginState> state = new SimpleObjectProperty<>(PluginState.NOT_LOADED);

    @Getter
    private final PluginHealthChecker healthChecker;
    private final StringProperty logoUrl = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();

    public Plugin() {
        this.healthChecker = new PluginHealthChecker(this);
        this.healthChecker.start(); // TODO: Create start method so this doesn't run in the constructor
        this.pluginSettings.set(createSettings());
    }

    public abstract PluginPhaseResult init();

    public abstract PluginPhaseResult load();

    public abstract PluginPhaseResult unload();

    public abstract PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes, Object... data);

    public abstract PluginPhaseResult reload();

    public abstract RRVBox showSettings(); // TODO: Replace with automatic generation using form builder

    public abstract PluginSettings createSettings();

    public void updateStatus(PluginState state) {
        Railroad.PLUGIN_MANAGER.showLog(this, "Change state from: " + this.state + " to: " + state);
        this.state.set(state);
    }

    public PluginPhaseResult getNewPhase() {
        return new PluginPhaseResult();
    }

    public String getName() {
        return getClass().getSimpleName();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public @Nullable PluginSettings getPluginSettings() {
        return pluginSettings.get();
    }

    @Override
    public JsonPrimitive toJson() {
        return new JsonPrimitive(getName());
    }

    @Override
    public void fromJson(JsonPrimitive json) {
        // This is a no-op because we don't need to deserialize the plugin name
    }

    public PluginState getState() {
        return state.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty logoUrlProperty() {
        return logoUrl;
    }
}
