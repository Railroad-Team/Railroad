package io.github.railroad.settings.handler;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.ObservableList;

import java.awt.*;
import java.util.List;

public class SettingsHandler {
    private final ObservableList<Setting> settingObservableList = new SimpleListProperty<Setting>();

    public SettingsHandler() {
        var exampleList = List.of("English", "Spanish", "French", "German");
        registerSetting(
                new Setting<List<String>, String>("railroad.settings.general.language", SettingComponentType.DROPDOWN,
                        exampleList, "English", List.of("language", "general")));

        Setting<?, Color> nullWorkaround = new Setting<>("railroad.settings.general.accentColor", SettingComponentType.COLOUR_PICKER,
                            null, Color.BLUE, List.of("color", "general"));

        registerSetting(nullWorkaround);
    }

    public void registerSetting(Setting setting) {
        settingObservableList.add(setting);
    }
}
