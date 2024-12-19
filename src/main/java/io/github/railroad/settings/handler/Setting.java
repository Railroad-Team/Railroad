package io.github.railroad.settings.handler;

import javafx.event.EventHandler;
import javafx.event.EventType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
public class Setting<T> {
    private final String id;
    private final String codecId;
    private final T defaultValue;
    private final Map<EventType, EventHandler> eventHandlers;

    @Setter
    private T value;

    public Setting(String id, String codecId, T defaultValue, Map<EventType, EventHandler> eventHandlers) {
        this.id = id;
        this.codecId = codecId;
        this.defaultValue = defaultValue;
        this.eventHandlers = eventHandlers;
    }
}
