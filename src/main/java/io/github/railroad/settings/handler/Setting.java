package io.github.railroad.settings.handler;

import javafx.event.EventHandler;
import javafx.event.EventType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
public class Setting<T> {
    private final String id;
    private final String treeId;
    private final String codecId;
    private final T defaultValue;
    private final Map<EventType, EventHandler> eventHandlers;

    @Setter
    private T value;

    /**
     * Constructor for the Setting class
     * @param id The id of the setting, used to retrieve it.
     * @param treeId The id of where the setting should be placed in context of the tree.
     * @param codecId The id of the codec to use for the setting.
     * @param defaultValue The default value of the setting.
     * @param eventHandlers The event handlers for the setting.
     */
    public Setting(String id, String treeId, String codecId, T defaultValue, Map<EventType, EventHandler> eventHandlers) {
        this.id = id;
        this.treeId = treeId;
        this.codecId = codecId;
        this.defaultValue = defaultValue;
        this.eventHandlers = eventHandlers;
    }
}
