package io.github.railroad.discord.activity;

import io.github.railroad.Railroad;

import java.time.Instant;

public class RailroadActivities {
    public enum RailroadActivityTypes{
        PROJECT_SELECTION(),
        PROJECT_CREATION(),
        EDIT_FILE()
    }

    public static void setActivity(DiscordActivity activity){
        Railroad.getDiscord().getActivityManager().updateActivity(activity);
    }
    public static void setActivity(RailroadActivityTypes type){
        //TODO add current project name & file name into details
        var activity = new DiscordActivity();
        switch(type){
            case PROJECT_SELECTION:
                activity.setDetails("Selecting a project");
                activity.setType(DiscordActivity.ActivityType.PLAYING);
                activity.setState("v0.1.0");
                activity.getTimestamps().setStart(Instant.now());
                activity.getAssets().setLargeImage("logo");
                break;
            case PROJECT_CREATION:
                activity.setDetails("Creating a new project");
                break;
            case EDIT_FILE:
                activity.setDetails("Editing FILE");
        }
        Railroad.getDiscord().getActivityManager().updateActivity(activity);
    }
}
