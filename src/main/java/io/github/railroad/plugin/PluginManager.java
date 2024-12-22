package io.github.railroad.plugin;

import io.github.railroad.Railroad;
import io.github.railroad.discord.activity.RailroadActivities;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.Optional;

public class PluginManager extends Thread {
    @Getter
    private final ObservableList<Plugin> pluginList = FXCollections.observableArrayList();
    private PluginManagerErrorEventListener listener;

    public static Plugin createPlugin(String pluginName) throws Exception {
        return (Plugin) Class.forName("io.github.railroad.plugin.defaults." + pluginName)
                .getDeclaredConstructor().newInstance();
    }

    public void addCustomEventListener(PluginManagerErrorEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
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
                }
            }

            PluginPhaseResult result = plugin.unload(); // TODO: Handle result
            showLog(plugin, "Unloaded");
        }
    }

    public void addPlugin(Plugin plugin) {
        this.pluginList.add(plugin);
        showLog(plugin, "Added plugin");
    }

    public void notifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes railroadActivityTypes, Object... data) {
        for (Plugin plugin : this.pluginList) {
            PluginPhaseResult phaseResult = plugin.railroadActivityChange(railroadActivityTypes, data);
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

    public void addPlugin(String name) {
        try {
            addPlugin(createPlugin(name));
        } catch (Exception exception) {
            var phase = new PluginPhaseResult();
            phase.addError(new Error(exception.getMessage()));
            showError(null, phase, "Error finding class and create new " + name);
        }
    }
}
