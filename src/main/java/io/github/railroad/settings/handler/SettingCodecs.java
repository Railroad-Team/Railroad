package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.railroad.core.settings.SettingCodec;
import io.github.railroad.localization.Languages;
import io.github.railroad.plugin.PluginManager;
import io.github.railroad.plugin.ui.PluginsPane;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import io.github.railroad.settings.ui.themes.ThemeSettingsSection;
import javafx.scene.control.ComboBox;

import java.util.Map;

public class SettingCodecs {
    public static final SettingCodec<Languages, ComboBox<String>> LANGUAGE =
            SettingCodec.<Languages, ComboBox<String>>builder()
                    .id("railroad.language")
                    .nodeToValue(comboBox -> comboBox.getValue() == null ? Languages.EN_US : Languages.fromName(comboBox.getValue()))
                    .valueToNode((lang, comboBox) -> {
                        if (lang == null) {
                            lang = Languages.EN_US; // Default to English if null
                        }

                        comboBox.setValue(lang.getName());
                    })
                    .jsonDecoder(json -> Languages.valueOf(json.getAsString()))
                    .jsonEncoder(lang -> new JsonPrimitive(lang.name()))
                    .createNode(lang -> {
                        var combo = new ComboBox<String>();
                        for (Languages language : Languages.values()) {
                            combo.getItems().add(language.getName());
                        }

                        combo.setValue(lang.getName());
                        return combo;
                    })
                    .build();

    public static final SettingCodec<String, ThemeSettingsSection> THEME =
            SettingCodec.builder("railroad.theme", String.class, ThemeSettingsSection.class)
                    .nodeToValue(ThemeSettingsSection::getSelectedTheme)
                    .valueToNode((theme, section) -> section.setSelectedTheme(theme))
                    .jsonDecoder(JsonElement::getAsString)
                    .jsonEncoder(JsonPrimitive::new)
                    .createNode(t -> {
                        var section = new ThemeSettingsSection();
                        section.setSelectedTheme(t);
                        return section;
                    })
                    .build();

    public static final SettingCodec<Map<PluginDescriptor, Boolean>, PluginsPane> ENABLED_PLUGINS =
            SettingCodec.<Map<PluginDescriptor, Boolean>, PluginsPane>builder()
                    .id("railroad.enabled_plugins")
                    .nodeToValue(PluginsPane::getEnabledPlugins)
                    .valueToNode((plugins, pane) -> pane.setEnabledPlugins(plugins))
                    .jsonDecoder(PluginManager::decodeEnabledPlugins)
                    .jsonEncoder(PluginManager::encodeEnabledPlugins)
                    .createNode(PluginsPane::new)
                    .build();
}
