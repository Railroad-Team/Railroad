package io.github.railroad.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.utility.ConfigHandler;

import java.util.ArrayList;
import java.util.List;

public class PluginManager extends Thread {
    private final List<Plugin> pluginList = new ArrayList<>();
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
            if (plugin.getState() == PluginStates.LOADED)
                continue;

            PluginPhaseResult initPhaseResult = plugin.initPlugin();
            if (initPhaseResult != null) {
                if (plugin.getState() == PluginStates.FINISHED_INIT) {
                    PluginPhaseResult loadPhaseResult = plugin.loadPlugin();
                    if (loadPhaseResult != null) {
                        if (plugin.getState() == PluginStates.LOADED) {
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
                // TODO: Don't do this because it's really fucking jank
                var plugin = (Plugin) Class.forName("io.github.railroad.plugin.defaults." + element.getAsString())
                        .getDeclaredConstructor().newInstance();
                addPlugin(plugin);
            } catch (Exception exception) {
                PluginPhaseResult phase = new PluginPhaseResult();
                phase.addError(new Error(exception.getMessage()));
                showError(null, phase, "Error finding class and create new " + element.getAsString());
            }
        }
    }

    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String message) {
        showError(plugin, pluginPhaseResult, message, "PluginManager");
    }

    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String message, String topic) {
        String phaseErrors = "";
        if (pluginPhaseResult != null) {
            phaseErrors = pluginPhaseResult.getErrors().toString();
        } else {
            phaseErrors = "Missing phase";
            pluginPhaseResult = new PluginPhaseResult();
        }
        if (plugin != null) {
            Railroad.LOGGER.error("[" + topic + "][" + plugin.getClass().getName() + "] Phase: " + message + " State: " + plugin.getState() + " Errors: " + phaseErrors);
        } else {
            Railroad.LOGGER.error("[" + topic + "][Missing] Phase: " + message + " State: Missing Errors: " + phaseErrors);
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
        Railroad.LOGGER.info("[" + topic + "][" + plugin.getClass().getName() + "]" + message);
    }

    public void unLoadAllPlugins() {
        for (Plugin plugin : this.pluginList) {
            while (plugin.getHealthChecker().isAlive()) {
                try {
                    plugin.getHealthChecker().join(50);
                } catch (InterruptedException ignored) {
                    //throw new RuntimeException(e);
                }
            }

            plugin.unloadPlugin();
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
            if (plugin.getState() == PluginStates.ACTIVITY_UPDATE_ERROR) {
                showError(plugin, phaseResult, "Update Activity");
            }
        }
    }

    public List<Plugin> getPluginList() {
        return this.pluginList;
    }
}
