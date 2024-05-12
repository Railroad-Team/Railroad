package io.github.railroad.PluginManager;

import io.github.railroad.discord.activity.RailroadActivities;

public abstract class Plugin {
    private PluginStates state = PluginStates.NOTLOADED;
    public abstract PluginPhaseResult InitPlugin();
    public abstract PluginPhaseResult LoadPlugin();
    public abstract PluginPhaseResult UnloadPlugin();
    public abstract PluginPhaseResult RaildraodActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);
    public abstract PluginPhaseResult ReloadPlugin();
    public void UpdateStatus(PluginStates state) {
        System.out.println("[PluginManager]["+this.getClass().getName()+"] Change state from: "+this.state+" to: " +state);
        this.state = state;
    }
    public PluginStates getState() {
        return this.state;
    }
    public PluginPhaseResult getNewPhase() {
        return new PluginPhaseResult();
    }
}
