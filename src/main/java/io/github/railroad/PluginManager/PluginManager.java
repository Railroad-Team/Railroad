package io.github.railroad.PluginManager;

import io.github.railroad.discord.activity.RailroadActivities;

import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private List<Plugin> pluginList;
    public PluginManager() {
        this.pluginList = new ArrayList<Plugin>();
    }

    public void LoadAllPlugins() {
        for (Plugin plugin: this.pluginList) {
            plugin.InitPlugin();
            plugin.LoadPlugin();
        }
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
