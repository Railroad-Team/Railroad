package io.github.railroad.discord.activity;

import io.github.railroad.Railroad;

import java.time.Instant;

public class RailroadActivities {
    public enum RailroadActivityTypes{
        RAILROAD_DEFAULT(),
        EDIT_FILE(),

    }

    public static void setActivity(DiscordActivity activity){
        Railroad.getDiscord().getActivityManager().updateActivity(activity);
    }
    public static void setActivity(RailroadActivityTypes type){
        //TODO add current project name & file name into details
        var activity = new DiscordActivity();
        switch(type){
            case RAILROAD_DEFAULT:
                activity.setDetails("Selecting a project");
                activity.setType(DiscordActivity.ActivityType.PLAYING);
                activity.setState("v0.1.0");
                activity.getTimestamps().setStart(Instant.now());
                activity.getAssets().setLargeImage("logo");
                break;
            case EDIT_FILE:
                //TODO Allow input of current file and/or project being edited
                activity.setDetails("Editing FILE");
                activity.setType(DiscordActivity.ActivityType.PLAYING);
                activity.setState("v0.1.0");
                activity.getTimestamps().setStart(Instant.now());
                activity.getAssets().setLargeImage("logo");
        }
        Railroad.getDiscord().getActivityManager().updateActivity(activity);
    }
}
