package dev.railroadide.railroad.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import dev.railroadide.railroad.localization.Language;
import dev.railroadide.railroad.localization.Languages;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.spi.PluginDescriptor;
import dev.railroadide.railroad.plugin.ui.PluginsPane;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.settings.keybinds.KeybindsList;
import dev.railroadide.railroad.settings.ui.*;
import dev.railroadide.railroad.theme.ui.ThemeSettingsSection;
import dev.railroadide.railroad.utility.javafx.ComboBoxConverter;
import javafx.scene.control.ComboBox;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class SettingCodecs {
    public static final SettingCodec<Language, ComboBox<Language>> LANGUAGE =
        SettingCodec.<Language, ComboBox<Language>>builder("railroad:language")
            .nodeToValue(ComboBox::getValue)
            .valueToNode((lang, comboBox) ->
                comboBox.setValue(lang == null ? Languages.EN_US : lang))
            .jsonDecoder(json -> Language.fromCode(json.getAsString()).orElse(Languages.EN_US))
            .jsonEncoder(lang ->
                new JsonPrimitive((lang == null ? Languages.EN_US : lang).getFullCode()))
            .createNode(lang -> {
                var comboBox = new ComboBox<Language>();
                comboBox.getItems().addAll(Language.REGISTRY.values());
                comboBox.setValue(lang == null ? Languages.EN_US : lang);
                comboBox.setConverter(new ComboBoxConverter<>(Language::name, name ->
                    Language.REGISTRY.values().stream()
                        .filter(language -> language.name().equals(name))
                        .findFirst()
                        .orElse(Languages.EN_US)));
                return comboBox;
            })
            .build();

    public static final SettingCodec<String, ThemeSettingsSection> THEME =
        SettingCodec.<String, ThemeSettingsSection>builder("railroad:theme")
            .nodeToValue(ThemeSettingsSection::getSelectedTheme)
            .valueToNode((theme, section) -> section.setSelectedTheme(theme))
            .jsonDecoder(JsonElement::getAsString)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(ThemeSettingsSection::new)
            .build();

    public static final SettingCodec<TerminalFontMode, ComboBox<TerminalFontMode>> TERMINAL_FONT_MODE =
        SettingCodec.<TerminalFontMode, ComboBox<TerminalFontMode>>builder("railroad:terminal_font_mode")
            .nodeToValue(comboBox -> comboBox.getValue() == null ? TerminalFontMode.AUTO : comboBox.getValue())
            .valueToNode((mode, comboBox) -> comboBox.setValue(mode == null ? TerminalFontMode.AUTO : mode))
            .jsonDecoder(json -> {
                try {
                    return TerminalFontMode.valueOf(json.getAsString());
                } catch (Exception ignored) {
                    return TerminalFontMode.AUTO;
                }
            })
            .jsonEncoder(mode -> new JsonPrimitive((mode == null ? TerminalFontMode.AUTO : mode).name()))
            .createNode(mode -> {
                var comboBox = new ComboBox<TerminalFontMode>();
                comboBox.getItems().addAll(TerminalFontMode.values());
                comboBox.setValue(mode == null ? TerminalFontMode.AUTO : mode);
                comboBox.setConverter(new ComboBoxConverter<>(terminalFontMode -> switch (terminalFontMode) {
                    case AUTO -> "Auto";
                    case INSTALLED_FONT -> "Installed Font";
                    case CUSTOM_FAMILY -> "Custom Family";
                }, name -> switch (name) {
                    case "Installed Font" -> TerminalFontMode.INSTALLED_FONT;
                    case "Custom Family" -> TerminalFontMode.CUSTOM_FAMILY;
                    default -> TerminalFontMode.AUTO;
                }));
                return comboBox;
            })
            .build();

    public static final SettingCodec<String, TerminalInstalledFontPane> TERMINAL_INSTALLED_FONT =
        SettingCodec.<String, TerminalInstalledFontPane>builder("railroad:terminal_installed_font")
            .createNode(TerminalInstalledFontPane::new)
            .nodeToValue(ComboBox::getValue)
            .valueToNode((font, pane) -> pane.setValue(font))
            .jsonDecoder(json -> json == null || json.isJsonNull() ? null : json.getAsString())
            .jsonEncoder(font -> font == null ? JsonNull.INSTANCE : new JsonPrimitive(font))
            .build();

    public static final SettingCodec<String, TerminalCustomFontFamilyPane> TERMINAL_CUSTOM_FONT_FAMILY =
        SettingCodec.<String, TerminalCustomFontFamilyPane>builder("railroad:terminal_custom_font_family")
            .createNode(TerminalCustomFontFamilyPane::new)
            .nodeToValue(TerminalCustomFontFamilyPane::getText)
            .valueToNode((fontFamily, pane) -> pane.setText(fontFamily == null ? "" : fontFamily))
            .jsonDecoder(json -> json == null || json.isJsonNull() ? "" : json.getAsString())
            .jsonEncoder(fontFamily -> fontFamily == null ? JsonNull.INSTANCE : new JsonPrimitive(fontFamily))
            .build();

    public static final SettingCodec<Map<PluginDescriptor, Boolean>, PluginsPane> ENABLED_PLUGINS =
        SettingCodec.<Map<PluginDescriptor, Boolean>, PluginsPane>builder("railroad:enabled_plugins")
            .nodeToValue(PluginsPane::getEnabledPlugins)
            .valueToNode((plugins, pane) -> pane.setEnabledPlugins(plugins))
            .jsonDecoder(PluginManager::decodeEnabledPlugins)
            .jsonEncoder(PluginManager::encodeEnabledPlugins)
            .createNode(PluginsPane::new)
            .build();

    public static final SettingCodec<Map<String, List<KeybindData>>, KeybindsList> KEYBINDS =
        SettingCodec.<Map<String, List<KeybindData>>, KeybindsList>builder("railroad:keybinds")
            .createNode(KeybindsList::new)
            .nodeToValue(KeybindsList::getKeybinds)
            .valueToNode((keybindsMap, keybindsList) -> keybindsList.loadKeybinds(keybindsMap))
            .jsonEncoder(KeybindsList::toJson)
            .jsonDecoder(KeybindsList::fromJson)
            .build();

    public static final SettingCodec<List<Path>, DirectoryListPane> DIRECTORY_PATH_LIST =
        SettingCodec.<List<Path>, DirectoryListPane>builder("railroad:directory_path_list")
            .createNode(DirectoryListPane::new)
            .nodeToValue(DirectoryListPane::getDirectories)
            .valueToNode((directories, pane) -> pane.setDirectories(directories))
            .jsonEncoder(AbstractPathListPane::toJson)
            .jsonDecoder(AbstractPathListPane::fromJson)
            .build();

    public static final SettingCodec<List<Path>, FileListPane> FILE_PATH_LIST =
        SettingCodec.<List<Path>, FileListPane>builder("railroad:file_path_list")
            .createNode(FileListPane::new)
            .nodeToValue(FileListPane::getFiles)
            .valueToNode((files, pane) -> pane.setFiles(files))
            .jsonEncoder(AbstractPathListPane::toJson)
            .jsonDecoder(AbstractPathListPane::fromJson)
            .build();

    public static final SettingCodec<Path, GitExecutablePathPane> GIT_EXECUTABLE_PATH =
        SettingCodec.<Path, GitExecutablePathPane>builder("railroad:git_executable_path")
            .createNode(GitExecutablePathPane::new)
            .nodeToValue(GitExecutablePathPane::getGitExecutablePath)
            .valueToNode((path, pane) -> pane.setGitExecutablePath(path))
            .jsonEncoder(path -> path == null ? JsonNull.INSTANCE : new JsonPrimitive(path.toString()))
            .jsonDecoder(json -> (json == null || json.isJsonNull()) ? null : Path.of(json.getAsString()))
            .build();

    public static final SettingCodec<Path, WindowsTerminalSettingsPathPane> WINDOWS_TERMINAL_SETTINGS_PATH =
        SettingCodec.<Path, WindowsTerminalSettingsPathPane>builder("railroad:windows_terminal_settings_path")
            .createNode(WindowsTerminalSettingsPathPane::new)
            .nodeToValue(WindowsTerminalSettingsPathPane::getSettingsPath)
            .valueToNode((path, pane) -> pane.setSettingsPath(path))
            .jsonEncoder(path -> path == null ? JsonNull.INSTANCE : new JsonPrimitive(path.toString()))
            .jsonDecoder(json -> (json == null || json.isJsonNull()) ? null : Path.of(json.getAsString()))
            .build();

    public static final SettingCodec<Integer, ComboBox<Integer>> UI_SCALE =
        SettingCodec.<Integer, ComboBox<Integer>>builder("railroad:ui_scale")
            .nodeToValue(comboBox -> comboBox.getValue() == null ? 100 : comboBox.getValue())
            .valueToNode((scale, comboBox) -> comboBox.setValue(scale == null ? 100 : scale))
            .jsonDecoder(json -> json == null || json.isJsonNull() ? 100 : json.getAsInt())
            .jsonEncoder(JsonPrimitive::new)
            .createNode(scale -> {
                var comboBox = new ComboBox<Integer>();
                comboBox.getItems().addAll(75, 100, 125, 150, 175, 200, 250, 300);
                comboBox.setValue(scale == null ? 100 : scale);
                comboBox.setConverter(new ComboBoxConverter<>(value -> value + "%", value -> {
                    if (value == null || value.isBlank())
                        return 100;

                    String normalized = value.endsWith("%") ? value.substring(0, value.length() - 1) : value;
                    try {
                        return Integer.parseInt(normalized.trim());
                    } catch (NumberFormatException ignored) {
                        return 100;
                    }
                }));

                return comboBox;
            })
            .build();
}
