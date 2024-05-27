package io.github.railroad.plugin;

import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.ui.defaults.RRVBox;

public abstract class Plugin {
    private PluginManager pluginManager;
    private PluginStates state = PluginStates.NOT_LOADED;
    private PluginHealthChecker healthChecker;
    private String pluginLogoUrl;
    private String pluiginName;

    public Plugin() {
        this.healthChecker = new PluginHealthChecker(this);
        this.healthChecker.start();
    }

    public abstract PluginPhaseResult initPlugin();

    public abstract PluginPhaseResult loadPlugin();

    public abstract PluginPhaseResult unloadPlugin();

    public abstract PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes);

    public abstract PluginPhaseResult reloadPlugin();

    public abstract RRVBox showSettings();

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

    public String getName() {
        return this.getClass().getName().toString();
    }

    public String getPluiginName() {
        return pluiginName;
    }

    public void setPluiginName(String pluiginName) {
        this.pluiginName = pluiginName;
    }

    public String getPluginLogoUrl() {
        return pluginLogoUrl;
    }

    public void setPluginLogoUrl(String pluginLogoUrl) {
        this.pluginLogoUrl = pluginLogoUrl;
    }
}
