package io.github.railroad.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.config.ConfigHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PluginManager extends Thread {
    @Getter
    private final ObservableList<Plugin> pluginList = FXCollections.observableArrayList();
    private PluginManagerErrorEventListener listener;

    public void addCustomEventListener(PluginManagerErrorEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        preparePluginsFromConfig();
        loadAllPlugins();
    }

    private void loadAllPlugins() {
        for (Plugin plugin : this.pluginList) {
            if (plugin.getState() == PluginState.LOADED)
                continue;

            PluginPhaseResult initPhaseResult = plugin.init();
            if (initPhaseResult != null) {
                if (plugin.getState() == PluginState.FINISHED_INIT) {
                    PluginPhaseResult loadPhaseResult = plugin.load();
                    if (loadPhaseResult != null) {
                        if (plugin.getState() == PluginState.LOADED) {
                            showLog(plugin, "Loaded");
                        } else {
                            showError(plugin, loadPhaseResult, "LoadPlugin");
                        }
                    } else showError(plugin, null, "No Phase result");
                } else {
                    showError(plugin, initPhaseResult, "InitPlugin");
                }
            } else showError(plugin, null, "No Phase result");
        }
    }

    private void preparePluginsFromConfig() {
        JsonObject object = ConfigHandler.getConfigJson();
        JsonArray plugins = object.getAsJsonObject("settings").getAsJsonArray("plugins");
        for (JsonElement element : plugins) {
            try {
                // TODO: Move plugins to external jar files
                addPlugin(createPlugin(element.getAsString()));
            } catch (Exception exception) {
                PluginPhaseResult phase = new PluginPhaseResult();
                phase.addError(new Error(exception.getMessage()));
                showError(null, phase, "Error finding class and create new " + element.getAsString());
            }
        }
    }

    private static Plugin createPlugin(String pluginName) throws Exception {
        return (Plugin) Class.forName("io.github.railroad.plugin.defaults." + pluginName)
                .getDeclaredConstructor().newInstance();
    }

    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String message) {
        showError(plugin, pluginPhaseResult, message, "PluginManager");
    }

    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String message, String topic) {
        String phaseErrors;
        if (pluginPhaseResult != null) {
            phaseErrors = pluginPhaseResult.getErrors().toString();
        } else {
            phaseErrors = "Missing phase";
            pluginPhaseResult = new PluginPhaseResult();
        }

        if (plugin != null) {
            Railroad.LOGGER.error("[{}][{}] Phase: {} State: {} Errors: {}", topic, plugin.getClass().getName(), message, plugin.getState(), phaseErrors);
        } else {
            Railroad.LOGGER.error("[{}][Missing] Phase: {} State: Missing Errors: {}", topic, message, phaseErrors);
        }

        if (listener != null) {
            PluginManagerErrorEvent event = new PluginManagerErrorEvent(this, plugin, message, pluginPhaseResult);
            listener.onPluginManagerError(event);
        }
    }

    public void showLog(Plugin plugin, String message) {
        showLog(plugin, message, "PluginManager");
    }

    public void showLog(Plugin plugin, String message, String topic) {
        Railroad.LOGGER.info("[{}][{}]{}", topic, plugin.getClass().getName(), message);
    }

    public void unloadPlugins() {
        for (Plugin plugin : this.pluginList) {
            while (plugin.getHealthChecker().isAlive()) {
                try {
                    plugin.getHealthChecker().join(50);
                } catch (InterruptedException ignored) {
                    //throw new RuntimeException(e);
                }
            }

            plugin.unload();
            showLog(plugin, "Unloaded");
        }
    }

    public boolean addPlugin(Plugin plugin) {
        plugin.setPluginManager(this);
        this.pluginList.add(plugin);
        showLog(plugin, "Added plugin");
        return true;
    }

    public void notifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        for (Plugin plugin : this.pluginList) {
            PluginPhaseResult phaseResult = plugin.railroadActivityChange(railroadActivityTypes);
            if (plugin.getState() == PluginState.ACTIVITY_UPDATE_ERROR) {
                showError(plugin, phaseResult, "Update Activity");
            }
        }
    }

    // TODO: Don't do things by name, because other plugins can't have the same name, we should use a unique identifier
    public Optional<Plugin> byName(String asString) {
        for (Plugin plugin : this.pluginList) {
            if (plugin.getClass().getName().equals(asString)) {
                return Optional.of(plugin);
            }
        }

        return Optional.empty();
    }
}
