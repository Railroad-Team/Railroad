package io.github.railroad.settings.handler;

import lombok.Getter;

@Getter
public class Setting<T> {
    private final Class<T> type;
    private final String id;
    private final T defaultValue;

    public Setting(String id, Class<T> type, T defaultValue) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public void setValue(T value) {

    }
}