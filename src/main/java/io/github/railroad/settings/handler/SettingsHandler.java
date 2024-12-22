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
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class SettingsHandler {
    private final ObservableMap<String, Decoration<?>> decorations = FXCollections.observableHashMap();
    private final ObservableMap<String, SettingCodec<?, ?, ?>> codecs = FXCollections.observableHashMap();
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
            if (!Files.exists(configPath)) {
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

    public void registerSetting(Setting<?> setting) {
        settings.registerSetting(setting);
    }

    public void registerCodec(SettingCodec<?, ?, ?> codec) {
        codecs.put(codec.id(), codec);
    }

    public void registerDecoration(Decoration<?> decoration) {
        decorations.put(decoration.id(), decoration);
    }

    public Setting<?> getSetting(String id) {
        return settings.getSetting(id);
    }

    @SuppressWarnings("unchecked")
    public <T> SettingCodec<T, Node, ?> getCodec(String id) {
        return (SettingCodec<T, Node, ?>) codecs.get(id);
    }

    public Decoration<?> getDecoration(String id) {
        return decorations.get(id);
    }

    /**
     * Loops through settings, using the same algorithm as {@link Settings#toJson()} to create a tree view.
     * Also does the same for decorations.
     *
     * @return {@link TreeView} The tree view to be displayed.
     */
    public TreeView<?> createTree() {
        TreeView<Node> view = new TreeView<>(new TreeItem<>(null));
        view.setShowRoot(false);

        for (Setting<?> setting : settings.getSettings().values()) {
            String[] parts = setting.getTreeId().split("[.:]");
            TreeItem<Node> currentPart = view.getRoot();

            for (int i = 0; i < parts.length; i++) {
                if (i == parts.length - 1) {
                    addSettingToTree(setting, currentPart);
                } else {
                    int finalI = i;

                    Optional<TreeItem<Node>> stringPart = currentPart.getChildren().stream()
                            .filter(a -> a.getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                            .findFirst();

                    if (stringPart.isPresent()) {
                        currentPart = stringPart.get();
                    } else {
                        TreeItem<Node> item = new TreeItem<>(new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")));
                        Railroad.LOGGER.debug("Localizing label: {}", "settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), "."));
                        item.setExpanded(true);
                        currentPart.getChildren().add(item);

                        Optional<TreeItem<Node>> currPart = currentPart.getChildren().stream()
                                .filter(a -> a.getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                                .findFirst();

                        if (currPart.isPresent()) {
                            currentPart = currPart.get();
                        } else {
                            Railroad.LOGGER.error("Failed to find part that was just created: {} from {}", parts[i], setting.getTreeId());
                        }
                    }
                }
            }
        }

        for (String decId : decorations.keySet()) {
            var parts = decId.split("[.:]");
            var currentPart = view.getRoot();

            for (int i = 0; i < parts.length; i++) {
                int finalI = i;

                if (i == parts.length - 1) {
                    Node node = decorations.get(decId).nodeCreator().get();
                    TreeItem<Node> item = new TreeItem<>(node);
                    currentPart.getChildren().add(item);
                    break;
                }

                Optional<TreeItem<Node>> part = currentPart.getChildren().stream()
                        .filter(a -> a.getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                        .findFirst();

                if (part.isPresent()) {
                    currentPart = part.get();
                } else {
                    TreeItem<Node> item = new TreeItem<>(new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")));
                    currentPart.getChildren().add(item);

                    Optional<TreeItem<Node>> currPart = currentPart.getChildren().stream()
                            .filter(a -> a.getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, finalI + 1), ".")))
                            .findFirst();

                    if (currPart.isPresent()) {
                        currentPart = currPart.get();
                    } else {
                        Railroad.LOGGER.error("Failed to find part that was just created: {} from {}", parts[i], decId);
                    }
                }
            }
        }

        return view;
    }

    private <T> void addSettingToTree(Setting<T> setting, TreeItem<Node> currentPart) {
        SettingCodec<T, Node, ?> codec = getCodec(setting.getCodecId());
        Node node = codec.createNode().apply(setting.getValue());
        node.addEventHandler(ActionEvent.ACTION, e -> setting.setValue(codec.nodeToValue().apply(node)));

        if (setting.getEventHandlers() != null)
            setting.getEventHandlers().forEach(node::addEventHandler);

        TreeItem<Node> item = new TreeItem<>(node);
        currentPart.getChildren().add(item);
    }

    // Default settings & codecs
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
        registerSetting(new Setting<>(
                "railroad:language",
                "railroad:appearance.language.language",
                "railroad:language",
                Language.EN_US,
                Map.of(ActionEvent.ACTION, e -> L18n.loadLanguage())));

        registerSetting(
                new Setting<>(
                        "railroad:theme",
                        "railroad:appearance.themes.select",
                        "railroad:theme.select",
                        "default-dark",
                        Map.of(ActionEvent.ACTION, e -> Railroad.updateTheme(Railroad.SETTINGS_HANDLER.getSetting("railroad:theme").getValue().toString()))));
    }

    private void registerDefaultDecorations() {
        registerDecoration(new Decoration<>("railroad:appearance.themes.download", "railroad:theme.download", () -> {
            var button = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");
            button.setOnAction(e -> new ThemeDownloadPane());
            return button;
        }));
    }
}
