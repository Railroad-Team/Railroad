package io.github.railroad.PluginManager;

import io.github.railroad.discord.activity.RailroadActivities;

public abstract class Plugin {
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
        print("Change state from: "+this.state+" to: " +state);
        this.state = state;
    }
    public PluginStates getState() {
        return this.state;
    }
    public PluginPhaseResult getNewPhase() {
        return new PluginPhaseResult();
    }

    public void print(String message) {
        System.out.println("[Plugin]["+this.getClass().getName()+"] "+ message);
    }


    public PluginHealthChecker getHealthChecker() {
        return healthChecker;
    }

    public void setHealthChecker(PluginHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }
}
