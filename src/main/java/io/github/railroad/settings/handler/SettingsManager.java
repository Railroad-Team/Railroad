package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.localization.L18n;
import io.github.railroad.localization.Language;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public class SettingsManager {
    private final ObservableMap<Class<?>, SettingCodec<?, ?, ?>> codecs = FXCollections.observableHashMap();
    @Getter
    private final Settings settings = new Settings();

    public SettingsManager() {
        defaultCodecs();
        defaultSettings();
    }

    public void initSettings() {
        if (Files.exists(ConfigHandler.getConfigDirectory().resolve("settings.json"))) {
            loadFromFile();
        } else {
            createSettingsFile();
            loadFromFile();
        }
    }

    public void createSettingsFile() {
        try {
            Files.createDirectories(ConfigHandler.getConfigDirectory());
            Files.createFile(ConfigHandler.getConfigDirectory().resolve("settings.json"));
            Files.writeString(ConfigHandler.getConfigDirectory().resolve("settings.json"), Railroad.GSON.toJson(settings.toJson()));
        } catch (IOException e) {
            Railroad.LOGGER.error("Error creating settings file", e);
        }
    }

    /**
     * Save the settings to the config file
     */
    public void saveSettings() {
        try {
            Files.writeString(ConfigHandler.getConfigDirectory().resolve("settings.json"), settings.toJson().toString());
        } catch (IOException e) {
            Railroad.LOGGER.error("Error saving settings to file", e);
        }
    }

    /**
     * Load the settings from the config file into the settings object
     */
    public void loadFromFile() {
        try {
            String fs = Files.readString(ConfigHandler.getConfigDirectory().resolve("settings.json"));
            JsonObject t = Railroad.GSON.fromJson(fs, JsonObject.class);
            settings.fromJson(t);
        } catch (IOException e) {
            Railroad.LOGGER.error("Error loading settings from file", e);
            throw new RuntimeException("Unable to load config file! That isn't good!");
        }
    }

    public void registerCodec(SettingCodec<?, ?, ?> codec) {
        Railroad.LOGGER.info("Registering codec for type: {}", codec.getType());
        var res = codecs.put(codec.getType(), codec);
        if (res != null) {
            Railroad.LOGGER.warn("WARNING: SettingCodec for type: {} has been overwritten! This may cause problems!", codec.getType());
        }
    }

    public Setting<?> getSetting(String id) {
        return settings.getSetting(id);
    }

    public <T> SettingCodec<T, ?, ?> getCodec(Class<?> type) {
        return (SettingCodec<T, ?, ?>) codecs.get(type);
    }

    public TreeView createTree() {
        var tree = new TreeView(new TreeItem<Node>(new Label("Settings")));
        tree.getRoot().setExpanded(true);

        //For each part in the setting string, e.g "railroad", "appearance" and then "theme" or language e.g "railroad:appearance.theme"
        //create a tree item for it, and add it to the tree
        //The final tree item for each setting will be the node that the setting is represented by

        for (Setting setting : settings.getSettingsMap().values()) {
            var parts = setting.getId().split("[.:]");
            var current = tree.getRoot();

            for (String part : parts) {
                //Todo localize the part - then can change to label.getKey()
                if (current.getChildren().stream().anyMatch(treeItem -> ((TreeItem)treeItem).getValue() instanceof Label label && label.getText().equals(part))) {
                    current = (TreeItem) current.getChildren().stream().filter(treeItem -> ((TreeItem)treeItem).getValue() instanceof Label label && label.getText().equals(part)).findFirst().get();
                } else if (Arrays.stream(parts).toList().indexOf(part) == parts.length - 1) {
                    SettingCodec codec = getCodec(setting.getType());

                    if (codec == null) {
                        Railroad.LOGGER.warn("No codec found for setting: {}", setting.getId());
                        continue;
                    }

                    Node item = (Node) codec.getNodeCreator().apply(setting.getValue() == null ? setting.getDefaultValue() : setting.getValue());
                    item.addEventHandler(ActionEvent.ACTION, event -> {
                        setting.setValue(codec.getNodeToValFunction().apply(item));
                    });

                    if (setting.getEventHandlers() != null)
                        setting.getEventHandlers().forEach((key, value) -> item.addEventHandler((EventType)key, (EventHandler<? super Event>) value));

                    current.getChildren().add(new TreeItem<>(item));
                } else {
                    var partItem = new TreeItem<>(new Label(part));
                    partItem.setExpanded(true);
                    current.getChildren().add(partItem);
                    current = (TreeItem) current.getChildren().get(current.getChildren().size() - 1);
                }
            }
        }

        return tree;
    }

    private void defaultCodecs() {
        Railroad.LOGGER.info("Registering default codecs");
        registerCodec(new SettingCodec<>(Language.class, ComboBox.class, JsonPrimitive.class,
                node -> {
                    if (node instanceof ComboBox<?> comboBox) {
                        return Language.fromName((String) comboBox.getValue());
                    }

                    return null;
                },
                (comboBox, language) -> comboBox.setValue(language.getName()),
                language -> {
                    var nc = new ComboBox<>();

                    var longest = "";
                    for (Language value : Language.values()) {
                        nc.getItems().add(value.getName());
                        if (value.getName().length() > longest.length()) {
                            longest = value.getName();
                        }
                    }
                    nc.setPrefSize(longest.length() * 10, 30);
                    nc.setValue(language.getName());
                    return nc;
                },
                language -> new JsonPrimitive(language.getName()),
                json -> Language.fromName(json.getAsString())
                ));

        registerCodec(new SettingCodec<>(
                String.class,
                ComboBox.class,
                JsonElement.class,
                node -> ((ComboBox<String>) node).getValue(),
                (comboBox, value) -> comboBox.setValue(value),
                value -> {
                    var nc = new ComboBox<String>();
                    nc.getItems().addAll("default-dark", "default-light");
                    for (Path file : ThemeDownloadManager.getDownloaded()) {
                        nc.getItems().add(file.getFileName().toString().replace(".css", ""));
                    }

                    nc.setValue(value);
                    return nc;
                },
                value -> new JsonPrimitive(value),
                json -> json.getAsString()
        ));
    }

    private void defaultSettings() {
        Railroad.LOGGER.info("Registering default settings");
        settings.registerSetting(
                new Setting<>("railroad:appearance.language", Language.class, Language.EN_US, Map.of(ActionEvent.ACTION, e -> {
                    L18n.loadLanguage();
                })));

        settings.registerSetting(
                new Setting<>("railroad:appearance.theme", String.class, "default-dark", Map.of(ActionEvent.ACTION, e -> {
                    Railroad.updateTheme(Railroad.SETTINGS_MANAGER.getSetting("railroad:appearance.theme").getValue().toString());
                }))
        );
    }
}