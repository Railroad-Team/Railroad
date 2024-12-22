package io.github.railroad.plugin.defaults;

import io.github.railroad.Railroad;
import io.github.railroad.config.PluginSettings;
import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.activity.DiscordActivity;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.plugin.BlankPluginSettings;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.plugin.PluginPhaseResult;
import io.github.railroad.plugin.PluginState;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ShutdownHooks;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class Discord extends Plugin {
    private DiscordCore discord;

    @Override
    public PluginPhaseResult init() {
        setName("Discord");
        updateStatus(PluginState.STARTING_INIT);
        PluginPhaseResult phaseResult = getNewPhase();

        try {
            var discord = new DiscordCore("853387211897700394");
            ShutdownHooks.addHook(discord::close);
            this.discord = discord;
        } catch (Exception exception) {
            updateStatus(PluginState.ERROR_INIT);
            phaseResult.addError(new Error(exception.getMessage()));
            return phaseResult;
        }

        updateStatus(PluginState.FINISHED_INIT);
        return phaseResult;
    }

    @Override
    public PluginPhaseResult load() {
        updateStatus(PluginState.LOADED);
        return getNewPhase();
    }

    @Override
    public PluginPhaseResult unload() {
        return getNewPhase();
    }

    @Override
    public PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes, Object... data) {
        if (getState() != PluginState.LOADED) {
            Railroad.LOGGER.warn("Plugin not loaded, unable to send the update!");
            return getNewPhase();
        }

        try {
            updateStatus(PluginState.ACTIVITY_UPDATE_START);
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
                    if(data.length == 0) {
                        Railroad.LOGGER.warn("No file path provided for EDIT_FILE activity");
                        return getNewPhase();
                    }

                    if(data[0] instanceof Path path) {
                        if(Files.notExists(path)) {
                            activity.setDetails("Editing Unknown File");
                        } else {
                            activity.setDetails("Editing " + path.getFileName());
                        }
                    } else if (data[0] instanceof File file) {
                        if(!file.exists()) {
                            activity.setDetails("Editing Unknown File");
                        } else {
                            activity.setDetails("Editing " + file.getName());
                        }
                    } else {
                        activity.setDetails("Editing Unknown File");
                    }

                    activity.setType(DiscordActivity.ActivityType.PLAYING);
                    activity.setState("v0.1.0");
                    activity.getTimestamps().setStart(Instant.now());
                    activity.getAssets().setLargeImage("logo"); // TODO: Set the image to the file type

                    break;
                case RAILROAD_PROJECT_OPEN:
                    if(data.length == 0) {
                        Railroad.LOGGER.warn("No project provided for RAILROAD_PROJECT_OPEN activity");
                        return getNewPhase();
                    }

                    if(data[0] instanceof Project project) {
                        activity.setDetails("Working on " + project.getAlias());
                        activity.setType(DiscordActivity.ActivityType.PLAYING);
                        activity.setState("v0.1.0");
                        activity.getTimestamps().setStart(Instant.now());
                        activity.getAssets().setLargeImage("logo");
                    } else {
                        Railroad.LOGGER.warn("Invalid project provided for RAILROAD_PROJECT_OPEN activity");
                        return getNewPhase();
                    }

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
            updateStatus(PluginState.ACTIVITY_UPDATE_ERROR);
            var phaseResult = new PluginPhaseResult();
            phaseResult.addError(new Error(exception.getMessage()));
            return phaseResult;
        }

        updateStatus(PluginState.ACTIVITY_UPDATE_FINISHED);
        updateStatus(PluginState.LOADED);
        return new PluginPhaseResult();
    }

    @Override
    public PluginPhaseResult reload() {
        return getNewPhase();
    }

    @Override
    public RRVBox showSettings() {
        return null;
    }

    @Override
    public PluginSettings createSettings() {
        return BlankPluginSettings.INSTANCE;
    }
}
