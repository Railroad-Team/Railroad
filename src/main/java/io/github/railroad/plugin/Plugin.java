package io.github.railroad.plugin;

import com.google.gson.JsonPrimitive;
import io.github.railroad.config.PluginSettings;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.*;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

public abstract class Plugin implements JsonSerializable<JsonPrimitive> {
    private final ObjectProperty<PluginSettings> pluginSettings = new ReadOnlyObjectWrapper<>();
    private final ObjectProperty<PluginState> state = new SimpleObjectProperty<>(PluginState.NOT_LOADED);

    @Setter
    private PluginManager pluginManager;
    private final PluginHealthChecker healthChecker;
    private final StringProperty pluginLogoUrl = new SimpleStringProperty();
    private final StringProperty pluginName = new SimpleStringProperty();

    public Plugin() {
        this.healthChecker = new PluginHealthChecker(this);
        this.healthChecker.start();
        this.pluginSettings.set(createSettings());
    }

    public abstract PluginPhaseResult init();

    public abstract PluginPhaseResult load();

    public abstract PluginPhaseResult unload();

    public abstract PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);

    public abstract PluginPhaseResult reload();

    public abstract RRVBox showSettings(); // TODO: Replace with automatic generation using form builder

    public abstract PluginSettings createSettings();

    public void updateStatus(PluginState state) {
        this.pluginManager.showLog(this, "Change state from: " + this.state + " to: " + state);
        this.state.set(state);
    }

    public PluginPhaseResult getNewPhase() {
        return new PluginPhaseResult();
    }

    public String getName() {
        return getClass().getName();
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
}
