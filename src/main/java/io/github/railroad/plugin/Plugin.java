package io.github.railroad.plugin;

import io.github.railroad.discord.activity.RailroadActivities;

public abstract class Plugin {
    private PluginManager pluginManager;
    private PluginStates state = PluginStates.NOT_LOADED;
    private PluginHealthChecker healthChecker;

    public Plugin() {
        this.healthChecker = new PluginHealthChecker(this);
        this.healthChecker.start();
    }

    public abstract PluginPhaseResult initPlugin();

    public abstract PluginPhaseResult loadPlugin();

    public abstract PluginPhaseResult unloadPlugin();

    public abstract PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);

    public abstract PluginPhaseResult reloadPlugin();

    public void updateStatus(PluginStates state) {
        this.pluginManager.showLog(this, "Change state from: " + this.state + " to: " + state);
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
