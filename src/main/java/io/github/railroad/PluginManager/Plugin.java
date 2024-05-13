package io.github.railroad.PluginManager;

import io.github.railroad.discord.activity.RailroadActivities;

public abstract class Plugin {
    private PluginManager pluginManager;

    public Plugin() {
        this.healthChecker = new PluginHealthChecker(this);
        this.healthChecker.start();
    }

    private PluginStates state = PluginStates.NOT_LOADED;
    private PluginHealthChecker healthChecker;

    public abstract pluginPhaseResult initPlugin();

    public abstract pluginPhaseResult loadPlugin();

    public abstract pluginPhaseResult unloadPlugin();

    public abstract pluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);

    public abstract pluginPhaseResult reloadPlugin();

    public void updateStatus(PluginStates state) {
        getPluginManager().showLog(this, "Change state from: " + this.state + " to: " + state);
        this.state = state;
    }

    public PluginStates getState() {
        return this.state;
    }

    public pluginPhaseResult getNewPhase() {
        return new pluginPhaseResult();
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
