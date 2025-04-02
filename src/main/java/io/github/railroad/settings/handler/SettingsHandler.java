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
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class SettingsHandler {
    public final SearchHandler searchHandler = new SearchHandler();
    private final ObservableMap<String, Decoration<?>> decorations = FXCollections.observableHashMap();
    private final ObservableMap<String, SettingCodec<?, ?, ?>> codecs = FXCollections.observableHashMap();
    @Getter
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
            Railroad.LOGGER.debug("Loaded settings file: {}", json);
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
        Railroad.LOGGER.debug("Saving settings file");
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
     * Creates a tree of setting folders.
     * @return {@link TreeView} The tree of folders
     */

    public TreeView<?> createCategoryTree() {
        TreeView<Node> view = new TreeView<>(new TreeItem<>(null));
        view.setShowRoot(false);

        for (Setting<?> setting : settings.getSettings().values()) {
            String[] parts = setting.getTreeId().split("[.:]");
            String finalPart = parts[parts.length - 1];

            for (String part : parts) {
                if (part.equals(finalPart)) {
                    break;
                }

                Optional<TreeItem<Node>> stringPart = view.getRoot().getChildren().stream()
                        .filter(a -> a.getValue() instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, parts.length - 2), ".")))
                        .findFirst();

                if (stringPart.isEmpty()) {
                    TreeItem<Node> item = new TreeItem<>(new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(parts, parts.length - 2), ".")));
                    item.setExpanded(true);
                    view.getRoot().getChildren().add(item);
                }
            }
        }

        return view;
    }

    /**
     * Creates the pane for the setting folder and adds the relevant settings and decorations to it.
     * @param parent parent folder id (just one part e.g appearance)
     * @return {@link VBox} The pane with the settings and decorations
     */
    public VBox createSettingsSection(String parent) {
        var vbox = new VBox();
        if (parent == null) {
            vbox.getChildren().add(new Label("Wow this is such a cool IDE am I right??"));
            return vbox;
        }
        parent = parent.toLowerCase();


        for (Setting<?> setting : settings.getSettings().values()) {
            String[] parts = setting.getTreeId().split("[.:]");
            //Get the last folder present
            String parentId = parts[parts.length - 3];

            if (parentId.equals(parent)) {
                Optional<Node> stringPart = vbox.getChildren().stream()
                        .filter(a -> a instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(parts, parts.length - 1), ".")))
                        .findFirst();

                if (stringPart.isEmpty()) {
                    var label = new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(parts, parts.length - 1), "."));

                    var codec = getCodec(setting.getCodecId());
                    Node node = codec.createNode().apply(setting.getValue());

                    node.addEventHandler(ActionEvent.ACTION, e -> setting.setValue(codec.nodeToValue().apply(node)));
                    node.addEventHandler(ActionEvent.ACTION, e -> setting.getApplySetting().accept(null));

                    if (setting.getEventHandlers() != null)
                        setting.getEventHandlers().forEach(node::addEventHandler);

                    if (searchHandler.nodeMatches(label)) {
                        label = searchHandler.styleNode(label);
                    }

                    vbox.getChildren().addAll(label, node);
                }
            }
        }

        for (Decoration<?> decoration : decorations.values()) {
            String[] parts = decoration.treeId().split("[.:]");
            //Get the last folder present
            String parentId = parts[parts.length - 3];

            if (parentId.equals(parent)) {
                Optional<Node> stringPart = vbox.getChildren().stream()
                        .filter(a -> a instanceof LocalizedLabel l && l.getKey().equals("settings." + StringUtils.join(Arrays.copyOf(decoration.id().split("[.:]"), decoration.id().split("[.:]").length - 1), ".")))
                        .findFirst();
                if (stringPart.isPresent()) {
                    var node = decoration.nodeCreator().get();
                    vbox.getChildren().add(node);
                } else {
                    var label = new LocalizedLabel("settings." + StringUtils.join(Arrays.copyOf(decoration.id().split("[.:]"), decoration.id().split("[.:]").length - 1), "."));
                    var node = decoration.nodeCreator().get();
                    vbox.getChildren().addAll( label, node);
                }
            }
        }

        return vbox;
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

        registerCodec(
                new SettingCodec<String, TextField, JsonElement>(
                        "railroad:project_folder",
                        TextField::getText,
                        (t, n) -> n.setText(t),
                        JsonElement::getAsString,
                        JsonPrimitive::new,
                        t -> {
                            var tf = new TextField();
                            tf.setText(t);
                            return tf;
                        }

                )
        );
    }

    private void registerDefaultSettings() {
        registerSetting(new Setting<>(
                    "railroad:language",
                    "railroad:appearance.language.language",
                    "railroad:language",
                    Language.EN_US,
                    e -> L18n.loadLanguage(),
                    null
                ));

        registerSetting(
                new Setting<>(
                        "railroad:theme",
                        "railroad:appearance.themes.select",
                        "railroad:theme.select",
                        "default-dark",
                        e -> Railroad.updateTheme(Railroad.SETTINGS_HANDLER.getSetting("railroad:theme").getValue().toString()),
                        null
                ));

        registerSetting(
                new Setting<>(
                        "railroad:project_folder",
                        "railroad:general.project_folder",
                        "railroad:project_folder",
                        System.getProperty("user.home"),
                        e -> {},
                        null
                )
        );
    }

    private void registerDefaultDecorations() {
        registerDecoration(new Decoration<>("railroad:appearance.themes.download", "railroad:appearance.themes.download", () -> {
            var button = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");
            button.setOnAction(e -> new ThemeDownloadPane());
            return button;
        }));
    }
}
