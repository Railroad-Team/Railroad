package io.github.railroad.discord.data;

/**
 * Enum for results the Discord IPC can return.
 *
 * @see <a href="https://discordapp.com/developers/docs/game-sdk/discord#data-models-result-enum">
 * https://discordapp.com/developers/docs/game-sdk/discord#data-models-result-enum</a>
 */
public enum DiscordResult {
    OK,
    SERVICE_UNAVAILABLE,
    INVALID_VERSION,
    LOCK_FAILED,
    INTERNAL_ERROR,
    INVALID_PAYLOAD,
    INVALID_COMMAND,
    INVALID_PERMISSIONS,
    NOT_FETCHED,
    NOT_FOUND,
    CONFLICT,
    INVALID_SECRET,
    INVALID_JOIN_SECRET,
    NO_ELIGIBLE_ACTIVITY,
    INVALID_INVITE,
    NOT_AUTHENTICATED,
    INVALID_ACCESS_TOKEN,
    APPLICATION_MISMATCH,
    INVALID_DATA_URL,
    INVALID_BASE64,
    NOT_FILTERED,
    LOBBY_FULL,
    INVALID_LOBBY_SECRET,
    INVALID_FILENAME,
    INVALID_FILE_SIZE,
    INVALID_ENTITLEMENT,
    NOT_INSTALLED,
    NOT_RUNNING,
    INSUFFICIENT_BUFFER,
    PURCHASE_CANCELED,
    INVALID_GUILD,
    INVALID_EVENT,
    INVALID_CHANNEL,
    INVALID_ORIGIN,
    RATE_LIMITED,
    OAUTH2_ERROR,
    SELECT_CHANNEL_TIMEOUT,
    GET_GUILD_TIMEOUT,
    SELECT_VOICE_FORCE_REQUIRED,
    CAPTURE_SHORTCUT_ALREADY_LISTENING,
    UNAUTHORIZED_FOR_ACHIEVEMENT,
    INVALID_GIFT_CODE,
    PURCHASE_ERROR,
    TRANSACTION_ABORTED;

    public static DiscordResult fromCode(int code) {
        return switch (code) {
            case 4000 -> INVALID_PAYLOAD;
            case 4002 -> INVALID_COMMAND;
            case 4010 -> NOT_FOUND;
            case 5006 -> NO_ELIGIBLE_ACTIVITY;
            default -> INTERNAL_ERROR;
        };
    }
}
