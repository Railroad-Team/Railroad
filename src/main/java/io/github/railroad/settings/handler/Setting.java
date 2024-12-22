package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import javafx.event.EventHandler;
import javafx.event.EventType;
import lombok.Getter;

import java.util.Map;
import java.util.function.Consumer;

@Getter
@SuppressWarnings("rawtypes")
public class Setting<T> {
    private final String id;
    private final String treeId;
    private final String codecId;

    private final T defaultValue;
    // The event handler to be applied to on action, should call a method that applies the setting.
    private final Consumer applySetting;
    private final Map<EventType, EventHandler> eventHandlers;

    private T value;

    /**
     * Constructor for the Setting class
     * @param id The id of the setting, used to retrieve it.
     * @param treeId The id of where the setting should be placed in context of the tree.
     * @param codecId The id of the codec to use for the setting.
     * @param defaultValue The default value of the setting.
     * @param eventHandlers The event handlers for the setting.
     */
    public Setting(String id, String treeId, String codecId, T defaultValue, Consumer applySetting, Map<EventType, EventHandler> eventHandlers) {
        this.id = id;
        this.treeId = treeId;
        this.codecId = codecId;
        this.defaultValue = defaultValue;
        this.applySetting = applySetting;
        this.eventHandlers = eventHandlers;
    }

    /**
     * Sets the value of the setting.
     * @param value The value to set the setting to.
     */
    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        try {
            this.value = (T) value;
        } catch (ClassCastException exception) {
            Railroad.LOGGER.error("Error setting value for setting: {}", this.id, exception);
        }
    }
}
