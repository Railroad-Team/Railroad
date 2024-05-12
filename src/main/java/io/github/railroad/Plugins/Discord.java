package io.github.railroad.Plugins;

import io.github.railroad.PluginManager.Plugin;
import io.github.railroad.PluginManager.PluginStates;
import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.activity.DiscordActivity;
import io.github.railroad.discord.activity.RailroadActivities;

import java.time.Instant;

public class Discord extends Plugin {
    private DiscordCore DISCORD;

    @Override
    public boolean InitPlugin() {
        UpdateStatus(PluginStates.loading);
        System.out.println("Staring Init Discord Plugin");
        try {
            var discord = new DiscordCore("853387211897700394");

            Runtime.getRuntime().addShutdownHook(new Thread(discord::close));
            this.DISCORD = discord;

        } catch (Exception e) {
            System.out.println("Error starting Discord Plugin");
        }
        UpdateStatus(PluginStates.loaded);
        return false;
    }

    @Override
    public boolean LoadPlugin() {
        return false;
    }

    @Override
    public boolean UnloadPlugin() {
        return false;
    }

    @Override
    public boolean RaildraodActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        if (this.getState() != PluginStates.loaded) {
            System.out.println("Plugin not loaded, unable to send the update");
            return false;
        }
        var activity = new DiscordActivity();
        switch(railroadActivityTypes) {
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
        return true;

    }

    @Override
    public boolean ReloadPlugin() {
        return false;
    }
}
