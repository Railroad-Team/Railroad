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
