package io.github.railroad.discord.event;

import io.github.railroad.discord.DiscordCore;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DiscordEvents {
    private final Map<DiscordCommand.Event, DiscordEventHandler<?>> handlers = new HashMap<>();

    private final DiscordCore core;

    public DiscordEvents(DiscordCore core) {
        this.core = core;
        registerEvents();
    }

    private void registerEvents() {
        this.handlers.put(DiscordCommand.Event.READY, new DiscordReadyEvent.Handler(core));
    }

    public DiscordEventHandler<?> getHandler(DiscordCommand.Event event) {
        return this.handlers.get(event);
    }

    public Set<Map.Entry<DiscordCommand.Event, DiscordEventHandler<?>>> getHandlers() {
        return this.handlers.entrySet();
    }
}
