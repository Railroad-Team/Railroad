package io.github.railroad.settings.handler;

import java.util.List;

/**
 * A setting to be added to the settings menu
 * @param <L> The list type of the default options
 * @param <T> The type of the default values
 * @param settingName The name of the setting
 * @param componentType {@link SettingComponentType} The type of component, for example, a textfield, checkbox, and so on
 * @param defaultOptions The default options for the setting, used for settings with more than one options e.g. a combobox
 * @param defaultValue The default value for the setting, e.g true.
 * @param tags List of strings that can be used to filter/search settings
 */
public record Setting<L, T>(String settingName, SettingComponentType componentType, L defaultOptions, T defaultValue, List<String> tags) {
    public String getSettingName() {
        return settingName;
    }

    public SettingComponentType getComponentType() {
        return componentType;
    }

    public L getDefaultOptions() {
        return defaultOptions;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public List<String> getTags() {
        return tags;
    }

    public Setting {
        if (settingName == null) {
            throw new IllegalArgumentException("Setting name cannot be null");
        }
        if (componentType == null) {
            throw new IllegalArgumentException("Component type cannot be null");
        }
        if (defaultValue == null) {
            throw new IllegalArgumentException("Default value cannot be null");
        }
        if (tags == null) {
            throw new IllegalArgumentException("Tags cannot be null");
        }
    }

    public Setting(String settingName, SettingComponentType componentType, T defaultValue, List<String> tags) {
        this(settingName, componentType, null, defaultValue, tags);
    }
}

