package dev.railroadide.railroad.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import dev.railroadide.core.localization.Language;
import dev.railroadide.core.settings.SettingCodec;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.utility.ComboBoxConverter;
import dev.railroadide.railroad.localization.Languages;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.ui.PluginsPane;
import dev.railroadide.railroad.settings.keybinds.KeybindsList;
import dev.railroadide.railroad.settings.ui.AbstractPathListPane;
import dev.railroadide.railroad.settings.ui.DirectoryListPane;
import dev.railroadide.railroad.settings.ui.FileListPane;
import dev.railroadide.railroad.settings.ui.GitExecutablePathPane;
import dev.railroadide.railroad.theme.ui.ThemeSettingsSection;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
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
}
