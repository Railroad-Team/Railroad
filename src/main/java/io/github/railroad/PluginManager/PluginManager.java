package io.github.railroad.PluginManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.utility.ConfigHandler;

import java.util.ArrayList;
import java.util.List;


public class PluginManager extends Thread {
    private List<Plugin> pluginList;

    public PluginManager() {
        this.pluginList = new ArrayList<Plugin>();
    }

    private PluginManagerErrorEventListener listener;

    // Method to register listener
    public void addCustomEventListener(PluginManagerErrorEventListener listener) {
        this.listener = listener;
    }

    public void run() {
        PrepareluginsFromConfig();
        LoadAllPlugins();
    }

    private void LoadAllPlugins() {
        for (Plugin plugin : this.pluginList) {
            if (plugin.getState() == PluginStates.LOADED) continue;
            PluginPhaseResult initphaseResult = plugin.InitPlugin();
            if (plugin.getState() == PluginStates.FINSIHED_INIT) {
                PluginPhaseResult loadphaseResult = plugin.LoadPlugin();
                if (plugin.getState() == PluginStates.LOADED) {
                } else {
                    showError(plugin, loadphaseResult, "LoadPlugin");
                }
            } else {
                showError(plugin, initphaseResult, "InitPlugin");
            }

        }
    }

    private void PrepareluginsFromConfig() {
        JsonObject object = ConfigHandler.getConfigJson();
        JsonArray plugins = object.getAsJsonObject("settings").getAsJsonArray("plugins");
        for (JsonElement s : plugins) {
            try {
                Object o = Class.forName("io.github.railroad.Plugins." + s.getAsString()).newInstance();
                this.AddPlugin((Plugin) o);
            } catch (Exception e) {
                PluginPhaseResult phase = new PluginPhaseResult();
                phase.AddError(new Error(e.getMessage()));
                showError(null, phase, "Error finding class and create new " + s.getAsString());
            }

        }
    }


    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String message) {
        showError(plugin, pluginPhaseResult, message, "PluginManager");
    }

    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String message, String topic) {
        if (plugin != null) {
            System.out.println("[Error][" + topic + "][" + plugin.getClass().getName() + "] Phase: " + message + " State: " + plugin.getState() + " Errors: " + pluginPhaseResult.getErrors());
        } else {
            System.out.println("[Error][" + topic + "][Missing] Phase: " + message + " State: Missing Errors: " + pluginPhaseResult.getErrors());
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
        System.out.println("[Log][" + topic + "][" + plugin.getClass().getName() + "]" + message);
    }

    public void unLoadAllPlugins() {
        for (Plugin plugin : this.pluginList) {
            while (plugin.getHealthChecker().isAlive()) {
                try {
                    plugin.getHealthChecker().join(50);
                } catch (InterruptedException e) {
                    //throw new RuntimeException(e);
                }
            }
            plugin.UnloadPlugin();
            showLog(plugin, "Unloaded");
        }
    }


    public boolean AddPlugin(Plugin plugin) {
        plugin.setPluginManager(this);
        this.pluginList.add(plugin);
        showLog(plugin, "Added plugin");
        return true;
    }

    public void NotifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        for (Plugin plugin : this.pluginList) {
            PluginPhaseResult phaseResult = plugin.RaildraodActivityChange(railroadActivityTypes);
            if (plugin.getState() == PluginStates.ACTIVITY_UPDATE_ERROR) {
                showError(plugin, phaseResult, "Update Activity");
            }
        }
    }
}
