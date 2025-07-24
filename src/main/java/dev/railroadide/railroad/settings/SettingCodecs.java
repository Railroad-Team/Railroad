package dev.railroadide.railroad.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.railroadide.core.localization.Language;
import dev.railroadide.core.settings.SettingCodec;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.railroad.localization.Languages;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.ui.PluginsPane;
import dev.railroadide.railroad.settings.keybinds.KeybindsList;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import dev.railroadide.railroad.settings.ui.themes.ThemeSettingsSection;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;

import java.util.List;
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

    public static final SettingCodec<Map<String, List<KeybindData>>, KeybindsList> KEYBINDS =
            SettingCodec.<Map<String, List<KeybindData>>, KeybindsList>builder()
                    .id("railroad:keybinds")
                    .createNode(KeybindsList::new)
                    .nodeToValue(KeybindsList::getKeybinds)
                    .valueToNode((map, kl) -> kl.loadKeybinds(map))
                    .jsonEncoder(KeybindsList::toJson)
                    .jsonDecoder(KeybindsList::fromJson)
                    .build();
}
