package io.github.railroad.Plugins;

import io.github.railroad.PluginManager.Plugin;
import io.github.railroad.PluginManager.PluginPhaseResult;
import io.github.railroad.PluginManager.PluginStates;
import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.activity.DiscordActivity;
import io.github.railroad.discord.activity.RailroadActivities;

import java.time.Instant;

public class Discord extends Plugin {
    private DiscordCore DISCORD;

    @Override
    public PluginPhaseResult initPlugin() {
        updateStatus(PluginStates.STARTING_INIT);
        PluginPhaseResult phaseResult = this.getNewPhase();
        try {
            var discord = new DiscordCore("853387211897700394");

            Runtime.getRuntime().addShutdownHook(new Thread(discord::close));
            this.DISCORD = discord;

        } catch (Exception exception) {
            this.updateStatus(PluginStates.ERROR_INIT);
            phaseResult.AddError(new Error(exception.getMessage()));
            return phaseResult;
        }
        updateStatus(PluginStates.FINSIHED_INIT);
        return phaseResult;
    }

    @Override
    public PluginPhaseResult loadPlugin() {
        updateStatus(PluginStates.LOADED);
        return this.getNewPhase();
    }

    @Override
    public PluginPhaseResult unloadPlugin() {
        return this.getNewPhase();
    }

    @Override
    public PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        if (this.getState() != PluginStates.LOADED) {
            System.out.println("Plugin not loaded, unable to send the update");
            return this.getNewPhase();
        }
        try {
            this.updateStatus(PluginStates.ACTIVITY_UPDATE_START);
            var activity = new DiscordActivity();
            switch (railroadActivityTypes) {
                case RAILROAD_DEFAULT:
                    activity.setDetails("Modding Minecraft");
                    activity.setType(DiscordActivity.ActivityType.PLAYING);
                    activity.setState("v0.1.0");
                    activity.getTimestamps().setStart(Instant.now());
                    activity.getAssets().setLargeImage("logo");
                    break;
                case EDIT_FILE:
                    activity.setDetails("Editing FILE");
                    activity.setType(DiscordActivity.ActivityType.PLAYING);
                    activity.setState("v0.1.0");
                    activity.getTimestamps().setStart(Instant.now());
                    activity.getAssets().setLargeImage("logo");
                    break;
                default:
                    activity.setDetails("Modding Minecraft");
                    activity.setType(DiscordActivity.ActivityType.PLAYING);
                    activity.setState("v0.1.0");
                    activity.getTimestamps().setStart(Instant.now());
                    activity.getAssets().setLargeImage("logo");
                    break;
            }
            this.DISCORD.getActivityManager().updateActivity(activity);
        } catch (Exception e) {
            this.updateStatus(PluginStates.ACTIVITY_UPDATE_ERROR);
            PluginPhaseResult phaseResult = new PluginPhaseResult();
            phaseResult.AddError(new Error(e.getMessage()));
        }
        this.updateStatus(PluginStates.ACTIVITY_UPDATE_FINSIHED);
        this.updateStatus(PluginStates.LOADED);
        return new PluginPhaseResult();
    }

    @Override
    public PluginPhaseResult reloadPlugin() {
        return this.getNewPhase();
    }
}
