package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.localization.L18n;
import io.github.railroad.localization.Language;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class SettingsHandler {
    private final ObservableMap<String, SettingCodec> codecs = FXCollections.observableHashMap();
    private final Settings settings = new Settings();
    private final Path configPath;

    public SettingsHandler() {
        registerDefaultSettings();
        registerDefaultCodecs();

        configPath = ConfigHandler.getConfigDirectory().resolve("settings.json");
    }

    //File methods
    public void initSettingsFile() {
        try {
            if(!Files.exists(configPath)) {
                createSettingsFile();
            }

            loadSettingsFromFile();
        } catch (Exception e) {
            Railroad.LOGGER.error("Failed to initialize settings file", e);
        }
    }

    public void createSettingsFile() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.createFile(configPath);

            saveSettingsFile();
        } catch (IOException e) {
            Railroad.LOGGER.error("Failed to create settings file", e);
        }
    }

    public void loadSettingsFromFile() {
        try {
            settings.fromJson(Railroad.GSON.fromJson(Files.readString(configPath), JsonObject.class));
        } catch (IOException e) {
            Railroad.LOGGER.error("Failed to load settings file", e);
        }
    }

    public void saveSettingsFile() {
        try {
            Files.writeString(configPath, settings.toJson().toString());
        } catch (IOException e) {
            Railroad.LOGGER.error("Failed to save settings file", e);
        }
    }


    //Settings methods
    public void registerSetting(Setting setting) {
        settings.registerSetting(setting);
    }

    public void registerCodec(SettingCodec codec) {
        codecs.put(codec.getId(), codec);
    }

    public Setting getSetting(String id) {
        return settings.getSetting(id);
    }

    public <N extends Node, J extends JsonElement> SettingCodec<Object, N, J> getCodec(String id) {
        return codecs.get(id);
    }


    //GUI methods
    public TreeView createTree() {
        var tv = new TreeView(new TreeItem(new LocalizedLabel("railroad.home.welcome.settings")));
        tv.getRoot().setExpanded(true);

        for (Setting setting : settings.getSettings().values()) {
            var parts = setting.getId().split(("[.:]"));
            var current = tv.getRoot();

            for (String part : parts) {
                //TODO localize
                Optional treeItem = current.getChildren().stream()
                        .filter(TreeItem.class::isInstance)
                        .map(TreeItem.class::cast)
                        .filter(ttreeItem -> ((TreeItem)ttreeItem).getValue() instanceof Label label && label.getText().equals(part)).findFirst();

                if (treeItem.isPresent()) {
                    current = (TreeItem) treeItem.get();
                } else if (Arrays.asList(parts).indexOf(part) == parts.length - 1) {
                    var codec = getCodec(setting.getCodecId());

                    if (codec == null) {
                        Railroad.LOGGER.error("Codec not found with codec ID: {}", setting.getCodecId());
                        continue;
                    }

                    var node = codec.getCreateNode().apply(setting.getValue() == null ? setting.getDefaultValue() : setting.getValue());
                    node.addEventHandler(ActionEvent.ACTION, event -> {
                        setting.setValue(codec.getNodeToValue().apply(node));
                    });

                    if (setting.getEventHandlers() != null)
                        setting.getEventHandlers().forEach((type, handler) -> node.addEventHandler((EventType)type, (EventHandler) handler));

                    current.getChildren().add(new TreeItem<>(node));
                } else {
                    var item = new TreeItem<>(new Label(part));
                    item.setExpanded(true);
                    current.getChildren().add(item);
                    current = (TreeItem) current.getChildren().get(current.getChildren().size() - 1);
                }
            }
        }
        return tv;
    }


    //Default settings & codecs
    private void registerDefaultCodecs() {
        registerCodec(
                new SettingCodec<Language, ComboBox<String>, JsonElement>("railroad:language",
                        n -> Language.fromName(n.getValue()),
                        (t, n) -> n.setValue(t.getName()),
                        j -> Language.valueOf(j.getAsString()),
                        t -> new JsonPrimitive(t.name()),
                        t -> {
                            var combo = new ComboBox<String>();
                            for (Language language : Language.values()) {
                                combo.getItems().add(language.getName());
                            }
                            combo.setValue(t.getName());
                            return combo;
                        }
                        )
        );

        registerCodec(
                new SettingCodec<String, ComboBox<String>, JsonElement>("railroad:theme.select",
                        ComboBoxBase::getValue,
                        (t, n) -> n.setValue(t),
                        JsonElement::getAsString,
                        JsonPrimitive::new,
                        t -> {
                            var combo = new ComboBox<String>();
                            for (Path file : ThemeDownloadManager.getDownloaded()) {
                                combo.getItems().add(file.getFileName().toString().replace(".css", ""));
                            }

                            combo.getItems().addAll("default-dark", "default-light");
                            combo.setValue(t);
                            return combo;
                        })
        );
    }

    private void registerDefaultSettings() {
        registerSetting(
                new Setting<>("railroad:appearance.language", "railroad:language", Language.EN_US,
                        Map.of(ActionEvent.ACTION,e -> L18n.loadLanguage()
                        )));

        registerSetting(
                new Setting<>("railroad:appearance.theme", "railroad:theme.select", "default-dark",
                        Map.of(ActionEvent.ACTION, e -> Railroad.updateTheme(Railroad.SETTINGS_HANDLER.getSetting("railroad:appearance.theme").getValue().toString())
                        )));
    }
}
