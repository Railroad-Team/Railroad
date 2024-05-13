package io.github.railroad.PluginManager;

import io.github.railroad.discord.activity.RailroadActivities;

public abstract class Plugin {
    private PluginManager pluginManager;

    public Plugin() {
        this.healthChecker = new PluginHealthChecker(this);
        this.healthChecker.start();
    }

    private PluginStates state = PluginStates.NOTLOADED;
    private PluginHealthChecker healthChecker;

    public abstract PluginPhaseResult InitPlugin();

    public abstract PluginPhaseResult LoadPlugin();

    public abstract PluginPhaseResult UnloadPlugin();

    public abstract PluginPhaseResult RaildraodActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);

    public abstract PluginPhaseResult ReloadPlugin();

    public void UpdateStatus(PluginStates state) {
        getPluginManager().showLog(this, "Change state from: " + this.state + " to: " + state);
        this.state = state;
    }

    public PluginStates getState() {
        return this.state;
    }

    public PluginPhaseResult getNewPhase() {
        return new PluginPhaseResult();
    }


    public PluginHealthChecker getHealthChecker() {
        return healthChecker;
    }

    public void setHealthChecker(PluginHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
}
