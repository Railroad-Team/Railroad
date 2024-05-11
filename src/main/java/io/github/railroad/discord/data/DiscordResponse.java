package io.github.railroad.discord.data;

public record DiscordResponse(DiscordConnectionState connectionState, String payload) {
}
