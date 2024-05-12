package io.github.railroad.PluginManager;

import io.github.railroad.discord.activity.RailroadActivities;

public abstract class Plugin {
    private PluginStates state = PluginStates.notloaded;
    public abstract boolean InitPlugin();
    public abstract boolean LoadPlugin();
    public abstract boolean UnloadPlugin();
    public abstract boolean RaildraodActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);
    public abstract boolean ReloadPlugin();
    public void UpdateStatus(PluginStates states) {
        this.state = states;
    }
    public PluginStates getState() {
        return this.state;
    }
}
