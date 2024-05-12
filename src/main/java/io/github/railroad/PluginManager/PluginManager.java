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

    public void run() {
        PrepareluginsFromConfig();
        LoadAllPlugins();
    }
    private void LoadAllPlugins() {
        for (Plugin plugin: this.pluginList) {
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
        JsonArray plugins = object.getAsJsonObject("railroadsettings").getAsJsonArray("plugins");
        for (JsonElement s: plugins) {
            try {
                Object o = Class.forName("io.github.railroad.Plugins."+s.getAsString()).newInstance();
                this.AddPlugin((Plugin) o);
            } catch (Exception e) {
                print("ConfigLoad","Error finding class and create new " + s.getAsString());
            }

        }
    }

    public static void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult, String phase) {
        System.out.println("[PluginManager]["+plugin.getClass().getName()+"] Phase: "+phase+" State: "+plugin.getState()+" Errors: " + pluginPhaseResult.getErrors());
    }

    public void unLoadAllPlugins() {
        for (Plugin plugin: this.pluginList) {
            while (plugin.getHealthChecker().isAlive()) {
                try {
                    plugin.getHealthChecker().join(50);
                } catch (InterruptedException e) {
                    print("UnloadPlugins","Error join of healthchecker of "+ plugin.getClass().getName());
                }
            }
            plugin.UnloadPlugin();
            print("UnloadPlugin", plugin.getClass().getName()+ " unloaded");
        }
    }
    private void print(String subalias, String message) {
        System.out.println("[PluginManager]["+subalias+"] "+ message);
    }
    public boolean AddPlugin(Plugin plugin) {
        this.pluginList.add(plugin);
        print("AddPlugin", "New Plugin: "+ plugin.getClass().getName());
        return true;
    }
    public void NotifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        for (Plugin plugin: this.pluginList) {
            PluginPhaseResult phaseResult = plugin.RaildraodActivityChange(railroadActivityTypes);
            if (plugin.getState() == PluginStates.ACTIVITY_UPDATE_ERROR) {
                showError(plugin, phaseResult, "Update Activity");
            }
        }
    }
}
