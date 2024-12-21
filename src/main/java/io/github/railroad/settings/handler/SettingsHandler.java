package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.localization.L18n;
import io.github.railroad.localization.Language;
import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import io.github.railroad.settings.ui.themes.ThemeDownloadPane;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class SettingsHandler {
    private final ObservableMap<String, Decoration> decorations = FXCollections.observableHashMap();
    private final ObservableMap<String, SettingCodec> codecs = FXCollections.observableHashMap();
    private final Settings settings = new Settings();
    private final Path configPath;

    public SettingsHandler() {
        registerDefaultSettings();
        registerDefaultCodecs();
        registerDefaultDecorations();

        configPath = ConfigHandler.getConfigDirectory().resolve("settings.json");
    }

    /**
     * If the settings file does not exist, create it
     * also loads the settings from the file
     */
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

    /**
     * Creates the settings file and applies the default settings to it.
     */
    public void createSettingsFile() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.createFile(configPath);

            saveSettingsFile();
        } catch (IOException e) {
            Railroad.LOGGER.error("Failed to create settings file", e);
        }
    }

    /**
     * Loads the settings from the file.
     * If the file is empty, or not a valid json object, it will reset the file to the default settings.
     */
    public void loadSettingsFromFile() {
        try {
            String fileInput = Files.readString(configPath);

            if (fileInput.isEmpty()) {
                Railroad.LOGGER.error("Settings file is empty!");
                throw new JsonSyntaxException("Settings file is empty!");
            }

            JsonObject json = Railroad.GSON.fromJson(fileInput, JsonObject.class);
            settings.fromJson(json);
        } catch (JsonSyntaxException e) {
            Railroad.LOGGER.error("Failed to parse settings file {}, resetting json file from: {} to defaults.", e, settings.toJson().toString());
            saveSettingsFile();
            loadSettingsFromFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveSettingsFile() {
        try {
            Files.writeString(configPath, settings.toJson().toString());
        } catch (IOException e) {
            Railroad.LOGGER.error("Failed to save settings file", e);
        }
    }

    public void registerSetting(Setting setting) {
        settings.registerSetting(setting);
    }

    public void registerCodec(SettingCodec codec) {
        codecs.put(codec.getId(), codec);
    }

    public void registerDecoration(Decoration decoration) {
        decorations.put(decoration.getId(), decoration);
    }

    public Setting getSetting(String id) {
        return settings.getSetting(id);
    }

    public <N extends Node, J extends JsonElement> SettingCodec<Object, N, J> getCodec(String id) {
        return codecs.get(id);
    }

    public Decoration getDecoration(String id) {
        return decorations.get(id);
    }

    /**
     * Loops through settings, using the same algorithm as {@link Settings#toJson()} to create a tree view.
     * Also does the same for decorations.
     * @return {@link TreeView} The tree view to be displayed.
     */
    public TreeView createTree() {
        var tv = new TreeView(new TreeItem(null));
        tv.setShowRoot(false);

        for (Setting setting : settings.getSettings().values()) {
            var parts = setting.getTreeId().split("[.:]");
            var currentPart = tv.getRoot();

            for (int i = 0; i < parts.length; i++) {
                if (i == parts.length - 1) {
                    Node node = getCodec(setting.getCodecId()).getCreateNode().apply(setting.getValue());
                    node.addEventHandler(ActionEvent.ACTION, e -> {
                        setting.setValue(getCodec(setting.getCodecId()).getNodeToValue().apply(node));
                    });

                    if (setting.getEventHandlers() != null) {
                        setting.getEventHandlers().forEach((type, handler) -> {
                            node.addEventHandler((EventType) type, (EventHandler) handler);
                        });
                    }

                    TreeItem item = new TreeItem(node);
                    currentPart.getChildren().add(item);
                } else {
                    int finalI = i;

                    Optional stringPart = currentPart.getChildren().stream()
                            .filter(TreeItem.class::isInstance)
                            .map(TreeItem.class::cast)
                            .filter(a -> ((TreeItem)a).getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                            .findFirst();

                    if (stringPart.isPresent()) {
                        currentPart = (TreeItem) stringPart.get();
                    } else {
                        TreeItem item = new TreeItem(new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")));
                        Railroad.LOGGER.debug("Localizing label: {}", "settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), "."));
                        item.setExpanded(true);
                        currentPart.getChildren().add(item);

                        Optional currPart = currentPart.getChildren().stream()
                                .filter(TreeItem.class::isInstance)
                                .map(TreeItem.class::cast)
                                .filter(a -> ((TreeItem)a).getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                                .findFirst();

                        if (currPart.isPresent()) {
                            currentPart = (TreeItem) currPart.get();
                        } else {
                            Railroad.LOGGER.error("Failed to find part that was just created: {} from {}", parts[i], setting.getTreeId());
                        }
                    }
                }
            }
        }

        for (String decId : decorations.keySet()) {
            var parts = decId.split("[.:]");
            var currentPart = tv.getRoot();

            for (int i = 0; i < parts.length; i++) {
                int finalI = i;

                if (i == parts.length - 1) {
                    Node node = (Node) decorations.get(decId).getNodeCreator().get();
                    TreeItem item = new TreeItem(node);
                    currentPart.getChildren().add(item);
                    break;
                }

                Optional part = currentPart.getChildren().stream()
                        .filter(TreeItem.class::isInstance)
                        .map(TreeItem.class::cast)
                        .filter(a -> ((TreeItem)a).getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                        .findFirst();

                if (part.isPresent()) {
                    currentPart = (TreeItem) part.get();
                } else {
                    TreeItem item = new TreeItem(new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")));
                    currentPart.getChildren().add(item);

                    Optional currPart = currentPart.getChildren().stream()
                            .filter(TreeItem.class::isInstance)
                            .map(TreeItem.class::cast)
                            .filter(a -> ((TreeItem)a).getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                            .findFirst();

                    if (currPart.isPresent()) {
                        currentPart = (TreeItem) currPart.get();
                    } else {
                        Railroad.LOGGER.error("Failed to find part that was just created: {} from {}", parts[i], decId);
                    }
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
                        })
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
                new Setting<>("railroad:language", "railroad:appearance.language.language", "railroad:language", Language.EN_US,
                        Map.of(ActionEvent.ACTION,e -> L18n.loadLanguage()
                        )));

        registerSetting(
                new Setting<>("railroad:theme","railroad:appearance.themes.select", "railroad:theme.select", "default-dark",
                        Map.of(ActionEvent.ACTION, e -> Railroad.updateTheme(Railroad.SETTINGS_HANDLER.getSetting("railroad:theme").getValue().toString())
                        )));
    }

    private void registerDefaultDecorations() {
        registerDecoration(new Decoration<>("railroad:appearance.themes.download", "railroad:theme.download", () -> {
            var button = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");
            button.setOnAction(e -> new ThemeDownloadPane());
            return button;
        }));
    }
}
