package io.github.railroad.discord.activity;

import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.data.DiscordResult;
import io.github.railroad.discord.event.DiscordCommand;

import java.util.function.Consumer;

/**
 * Manager to control the player's current activity.
 *
 * @see <a href="https://discordapp.com/developers/docs/game-sdk/activities">
 * https://discordapp.com/developers/docs/game-sdk/activities</a>
 */
public class DiscordActivityManager {
    private final DiscordCore core;

    public DiscordActivityManager(DiscordCore core) {
        this.core = core;
    }

    /**
     * <p>Updates the user's current presence to a new activity.</p>
     * <p>The {@link DiscordCore#DEFAULT_CALLBACK} is used to handle the returned {@link DiscordResult}.</p>
     *
     * @param activity New activity for the user.
     * @see <a href="https://discordapp.com/developers/docs/game-sdk/activities#updateactivity">
     * https://discordapp.com/developers/docs/game-sdk/activities#updateactivity</a>
     */
    public void updateActivity(DiscordActivity activity) {
        updateActivity(activity, DiscordCore.DEFAULT_CALLBACK);
    }

    /**
     * <p>Updates the user's current presence to a new activity.</p>
     * <p>A custom callback is used to handle the returned {@link DiscordResult}.</p>
     *
     * @param activity New activity for the user.
     * @param callback Callback to process the returned {@link DiscordResult}.
     * @see <a href="https://discordapp.com/developers/docs/game-sdk/activities#updateactivity">
     * https://discordapp.com/developers/docs/game-sdk/activities#updateactivity</a>
     */
    public void updateActivity(DiscordActivity activity, Consumer<DiscordResult> callback) {
        this.core.sendCommand(DiscordCommand.Type.SET_ACTIVITY, new DiscordSetActivity.Args(this.core.getPid(), activity), response -> {
            callback.accept(this.core.checkError(response));
        });
    }

    public void clearActivity() {
        clearActivity(DiscordCore.DEFAULT_CALLBACK);
    }

    /**
     * <p>Clears the user's current presence.</p>
     * <p>A custom callback is used to handle the returned {@link DiscordResult}.</p>
     *
     * @param callback Callback to process the returned {@link DiscordResult}.
     * @see <a href="https://discordapp.com/developers/docs/game-sdk/activities#clearactivity">
     * https://discordapp.com/developers/docs/game-sdk/activities#clearactivity</a>
     */
    public void clearActivity(Consumer<DiscordResult> callback) {
        updateActivity(null);
    }
}
