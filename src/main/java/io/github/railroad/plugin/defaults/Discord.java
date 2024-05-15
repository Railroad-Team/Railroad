package io.github.railroad.plugin.defaults;

import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.activity.DiscordActivity;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.plugin.PluginPhaseResult;
import io.github.railroad.plugin.PluginStates;

import java.time.Instant;

public class Discord extends Plugin {
    private DiscordCore discord;

    @Override
    public PluginPhaseResult initPlugin() {
        updateStatus(PluginStates.STARTING_INIT);
        PluginPhaseResult phaseResult = getNewPhase();

        try {
            var discord = new DiscordCore("853387211897700394");
            Runtime.getRuntime().addShutdownHook(new Thread(discord::close));
            this.discord = discord;
        } catch (Exception exception) {
            updateStatus(PluginStates.ERROR_INIT);
            phaseResult.addError(new Error(exception.getMessage()));
            return phaseResult;
        }

        updateStatus(PluginStates.FINISHED_INIT);
        return phaseResult;
    }

    @Override
    public PluginPhaseResult loadPlugin() {
        updateStatus(PluginStates.LOADED);
        return getNewPhase();
    }

    @Override
    public PluginPhaseResult unloadPlugin() {
        return getNewPhase();
    }

    @Override
    public PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        if (getState() != PluginStates.LOADED) {
            System.out.println("Plugin not loaded, unable to send the update");
            return getNewPhase();
        }

        try {
            updateStatus(PluginStates.ACTIVITY_UPDATE_START);
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

            discord.getActivityManager().updateActivity(activity);
        } catch (Exception exception) {
            updateStatus(PluginStates.ACTIVITY_UPDATE_ERROR);
            var phaseResult = new PluginPhaseResult();
            phaseResult.addError(new Error(exception.getMessage()));
        }

        this.updateStatus(PluginStates.ACTIVITY_UPDATE_FINISHED);
        this.updateStatus(PluginStates.LOADED);
        return new PluginPhaseResult();
    }

    @Override
    public PluginPhaseResult reloadPlugin() {
        return getNewPhase();
    }
}
