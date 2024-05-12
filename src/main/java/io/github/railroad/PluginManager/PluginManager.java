package io.github.railroad.PluginManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.utility.ConfigHandler;

import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private List<Plugin> pluginList;
    public PluginManager() {
        this.pluginList = new ArrayList<Plugin>();
    }

    public void LoadAllPlugins() {
        for (Plugin plugin: this.pluginList) {
            PluginPhaseResult initphaseResult = plugin.InitPlugin();
            if (plugin.getState() == PluginStates.FINSIHED_INIT) {
                PluginPhaseResult loadphaseResult = plugin.LoadPlugin();
                if (plugin.getState() == PluginStates.LOADED) {
                } else {
                    showError(plugin, loadphaseResult);
                }
            } else {
                showError(plugin, initphaseResult);
            }

        }
    }

    public void loadPluginsFromConfig() {
        JsonObject object = ConfigHandler.getConfigJson();
        JsonArray plugins = object.getAsJsonObject("railroadsettings").getAsJsonArray("plugins");
        for (JsonElement s: plugins) {
            System.out.println("Found Plugin in the config: " + s.getAsString());
            try {
                Object o = Class.forName("io.github.railroad.Plugins."+s.getAsString()).newInstance();
                this.AddPlugin((Plugin) o);
            } catch (Exception e) {
                print("ConfigLoad","Error finding class and create new " + s.getAsString());
            }

        }
    }

    public static void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult) {
        System.out.println("[PluginManager]["+plugin.getClass().getName()+"] Errors: " + pluginPhaseResult.getErrors());
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
        return true;
    }
    public void NotifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {

    }
}
