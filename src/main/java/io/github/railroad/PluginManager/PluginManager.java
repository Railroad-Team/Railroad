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
                    this.showError(plugin, loadphaseResult);
                }
            } else {
                this.showError(plugin, initphaseResult);
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
                System.out.println("[PluginManager][ConfigLoaded] Error finding class and create new " + s.getAsString());
            }

        }
    }

    public void showError(Plugin plugin, PluginPhaseResult pluginPhaseResult) {
        System.out.println("[PluginManager]["+plugin.getClass().getName()+"] Errors: " + pluginPhaseResult.getErrors());
    }
    public void unLoadAllPlugins() {
        for (Plugin plugin: this.pluginList) {
            plugin.UnloadPlugin();
        }
    }

    public boolean AddPlugin(Plugin plugin) {
        this.pluginList.add(plugin);
        return true;
    }
    public void NotifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {

    }
}
