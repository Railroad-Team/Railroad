package io.github.railroad.settings.handler;

import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import lombok.Getter;

@Getter
public class Setting<T> {
    private final Class<T> type;
    private final String id;
    private final T defaultValue;

    private final EventType eventType;
    private final EventHandler eventHandler;

    private SimpleObjectProperty<T> value = new SimpleObjectProperty<>();
    //TODO allow multiple event handlers
    public Setting(String id, Class<T> type, T defaultValue, EventType eventType, EventHandler<?> eventHandler) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
        this.eventType = eventType;
        this.eventHandler = eventHandler;

        setValue(defaultValue);
    }

    public T getValue() {
        return value.getValue();
    }

    public void setValue(T value) {
        this.value.setValue(value);
    }
}