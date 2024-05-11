package io.github.railroad.discord.event;

import io.github.railroad.discord.DiscordCore;

public abstract class DiscordEventHandler<Data> {
    protected final DiscordCore core;

    protected DiscordEventHandler(DiscordCore core) {
        this.core = core;
    }

    public abstract void handle(DiscordCommand command, Data data);

    public final void handleObject(DiscordCommand command, Object object) {
        if (object == null)
            throw new IllegalArgumentException("Data is null");

        if (!getDataClass().isInstance(object))
            throw new IllegalArgumentException("Data is not an instance of " + getDataClass().getName());

        handle(command, getDataClass().cast(object));
    }

    public abstract Class<Data> getDataClass();

    public boolean shouldRegister() {
        return true;
    }

    public Object getRegistrationArgs() {
        return null;
    }
}
