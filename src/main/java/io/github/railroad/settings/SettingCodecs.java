package io.github.railroad.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.railroad.core.localization.Language;
import io.github.railroad.core.settings.SettingCodec;
import io.github.railroad.localization.Languages;
import io.github.railroad.plugin.PluginManager;
import io.github.railroad.plugin.ui.PluginsPane;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import io.github.railroad.settings.ui.themes.ThemeSettingsSection;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;

import java.util.Map;

public class SettingCodecs {
    public static final SettingCodec<Language, ComboBox<Language>> LANGUAGE =
            SettingCodec.<Language, ComboBox<Language>>builder()
                    .id("railroad.language")
                    .nodeToValue(ComboBoxBase::getValue)
                    .valueToNode((lang, comboBox) ->
                            comboBox.setValue(lang == null ? Languages.EN_US : lang))
                    .jsonDecoder(json -> Language.fromCode(json.getAsString()).orElse(Languages.EN_US))
                    .jsonEncoder(lang ->
                            new JsonPrimitive((lang == null ? Languages.EN_US : lang).getFullCode()))
                    .createNode(lang -> {
                        var comboBox = new ComboBox<Language>();
                        comboBox.getItems().addAll(Language.REGISTRY.values());
                        comboBox.setValue(lang == null ? Languages.EN_US : lang);
                        return comboBox;
                    })
                    .build();

    public static final SettingCodec<String, ThemeSettingsSection> THEME =
            SettingCodec.builder("railroad.theme", String.class, ThemeSettingsSection.class)
                    .nodeToValue(ThemeSettingsSection::getSelectedTheme)
                    .valueToNode((theme, section) -> section.setSelectedTheme(theme))
                    .jsonDecoder(JsonElement::getAsString)
                    .jsonEncoder(JsonPrimitive::new)
                    .createNode(theme -> {
                        var section = new ThemeSettingsSection();
                        section.setSelectedTheme(theme);
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
