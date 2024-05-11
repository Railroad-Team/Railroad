package io.github.railroad.discord.data;

public enum DiscordConnectionState {
    HANDSHAKE,
    CONNECTED,
    ERROR;

    public static final DiscordConnectionState[] VALUES = values();
}
