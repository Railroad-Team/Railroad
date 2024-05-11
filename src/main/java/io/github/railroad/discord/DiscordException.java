package io.github.railroad.discord;

import io.github.railroad.discord.data.DiscordResult;

/**
 * Exception which is thrown when a {@link DiscordResult} that is not {@link DiscordResult#OK} occurs.
 */
public class DiscordException extends RuntimeException {
    private final DiscordResult result;

    public DiscordException(DiscordResult result) {
        super("Discord error: " + result);
        this.result = result;
    }

    /**
     * Non-{@link DiscordResult#OK} result that occurred.
     *
     * @return Occurred {@link DiscordResult}
     */
    public DiscordResult getResult() {
        return result;
    }
}
