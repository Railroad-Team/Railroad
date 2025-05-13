package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.github.palexdev.materialfx.factories.InsetsFactory;
import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.localization.L18n;
import io.github.railroad.localization.Language;
import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.ui.TreeViewSettings;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import io.github.railroad.settings.ui.themes.ThemeDownloadPane;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SettingsHandler {
    private final ObservableMap<String, Decoration<?>> decorations = FXCollections.observableHashMap();
    private final ObservableMap<String, SettingCodec<?, ?, ?>> codecs = FXCollections.observableHashMap();
    @Getter
    private final Settings settings = new Settings();
    private final Path configPath;

    public SettingsHandler() {
        //TODO register plugin settings
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

    public TreeView<LocalizedLabel> createCategoryTree() {
        TreeView<LocalizedLabel> view = new TreeView<>(new TreeItem<>(null));
        view.setShowRoot(false);

        for (Setting<?> setting : settings.getSettings().values()) {
            String[] parts = setting.getTreeId().split("[:.]");
            String settingPart = parts[parts.length - 1];

            var currNode = view.getRoot();
            int index = 0;

            for (String part : parts) {
                index++;
                if (part.equals(settingPart)) {
                    break;
                }

                //If it is the last folder
                int finalIndex = index;
                if (index == (parts.length - 1)) {
                    break;
                }

                //Check if the folder already exists
                var folder = currNode.getChildren().stream().filter(i -> i.getValue() instanceof LocalizedLabel l && l.getKey().equals(StringUtils.join(parts, ".", 0, finalIndex)))
                        .findFirst();

                if (folder.isEmpty()) {
                    //If it does not exist, create it
                    TreeItem<LocalizedLabel> newFolder = new TreeItem<>(new LocalizedLabel( StringUtils.join(parts, ".", 0, index)));
                    currNode.getChildren().add(newFolder);
                    currNode = newFolder;
                } else {
                    //If it exists, set the current node to the folder
                    currNode = folder.get();
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
        final Map<String, VBox> folderBoxes = new HashMap<>();
        final VBox vbox = new VBox();

        if (parent == null) {
            //TODO If no node is selected - Possibly make impossible? Maybe force onto railroad/general or last used node
            var title = new LocalizedLabel("railroad.home.settings.title");
            title.setStyle("-fx-font-size: 24px;");

            var desc = new LocalizedLabel("railroad.home.settings.description");
            desc.setStyle("-fx-font-size: 14px; -fx-wrap-text: true;");
            vbox.getChildren().addAll(title, desc);

            return vbox;
        }
        parent = parent.toLowerCase();

        for (Setting<?> setting : settings.getSettings().values()) {
            String[] parts = setting.getTreeId().split("[.:]");
            //Get the last folder present
            String parentId = parts[parts.length - 3];

            if (parentId.equals(parent)) {
                String innerFolder = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));
                if (!folderBoxes.containsKey(innerFolder)) {
                    var folderBox = new VBox();
                    folderBoxes.put(innerFolder, folderBox);
                    VBox.setMargin(folderBox, InsetsFactory.of(5, 10, 0, 10));
                }
                var folderBox = folderBoxes.get(innerFolder);

                var settingBundle = new VBox();
                VBox.setMargin(settingBundle, InsetsFactory.all(5));

                var settingNode = getCodec(setting.getCodecId()).createNode().apply(setting.getValue());

                settingNode.addEventHandler(
                        ActionEvent.ACTION, e -> {
                            setting.setValue(getCodec(setting.getCodecId()).nodeToValue().apply(settingNode));
                        });
                settingNode.addEventHandler(ActionEvent.ACTION, e -> {
                    setting.getApplySetting().accept(null); //TODO fix generics?
                });

                if (setting.getEventHandlers() != null)
                    setting.getEventHandlers().forEach(settingNode::addEventHandler);

                var settingTitleLabel = new LocalizedLabel(setting.getTreeId().replace(":", ".") + ".title");
                settingTitleLabel.setStyle("-fx-font-size: 16px;");

                VBox.setMargin(settingTitleLabel, InsetsFactory.bottom(5));

                settingBundle.getChildren().addAll(TreeViewSettings.SEARCH_HANDLER.styleNodes(settingTitleLabel, settingNode));

                var descKey = setting.getTreeId().replace(":", ".") + ".description";
                if (L18n.isKeyValid(descKey)) {
                    var settingDescLabel = new LocalizedLabel(descKey);
                    settingDescLabel.setStyle("-fx-font-size: 14px; -fx-wrap-text: true;");
                    settingBundle.getChildren().add(TreeViewSettings.SEARCH_HANDLER.styleNode(settingDescLabel));
                }

                folderBox.getChildren().add(settingBundle);
            }
        }

        for (Decoration<?> decoration : decorations.values()) {
            String[] parts = decoration.treeId().split("[.:]");
            //Get the last folder present
            String parentId = parts[parts.length - 3];

            if (parentId.equals(parent)) {
                String innerFolder = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));

                if (!folderBoxes.containsKey(innerFolder)) {
                    var folderBox = new VBox();
                    folderBoxes.put(innerFolder, folderBox);
                    VBox.setMargin(folderBox, InsetsFactory.of(5, 10, 0, 10));
                }
                var folderBox = folderBoxes.get(innerFolder);

                var decorBundle = new VBox();
                VBox.setMargin(decorBundle, InsetsFactory.all(5));

                var decorationNode = decoration.nodeCreator().get();
                var decorationLabelKey = decoration.treeId().replace(":", ".") + ".title";
                var decorationDescKey =  decoration.treeId().replace(":", ".") + ".description";

                if (L18n.isKeyValid(decorationLabelKey)) {
                    var decorationLabel = new LocalizedLabel(decorationLabelKey);
                    decorationLabel.setStyle("-fx-font-size: 16px;");
                    decorBundle.getChildren().add(TreeViewSettings.SEARCH_HANDLER.styleNode(decorationLabel));
                }

                decorBundle.getChildren().add(TreeViewSettings.SEARCH_HANDLER.styleNode(decorationNode));

                if (L18n.isKeyValid(decorationDescKey)) {
                    var decorationDesc = new LocalizedLabel(decorationDescKey);
                    decorationDesc.setStyle("-fx-font-size: 14px; -fx-wrap-text: true;");
                    decorBundle.getChildren().add(TreeViewSettings.SEARCH_HANDLER.styleNode(decorationDesc));
                }

                folderBox.getChildren().add(decorBundle);
            }
        }

        for (Map.Entry<String, VBox> entry : folderBoxes.entrySet()) {
            var sepBox = new HBox();
            sepBox.setSpacing(10);
            sepBox.setPadding(new Insets(5, 10, 0, 10));

            var sectionLabel = new LocalizedLabel(entry.getKey());
            sectionLabel.setStyle("-fx-font-size: 18px;");

            var sep = new Separator();
            sep.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(sep, Priority.ALWAYS);
            vbox.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(sep, Priority.ALWAYS);

            sepBox.getChildren().addAll(sectionLabel, sep);
            vbox.getChildren().addAll(sepBox, entry.getValue());
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
    }

    private void registerDefaultSettings() {
        registerSetting(new Setting<>(
                    "railroad:language",
                    "railroad:general.language",
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
    }

    private void registerDefaultDecorations() {
        registerDecoration(new Decoration<>("railroad:appearance.themes.download", "railroad:appearance.themes.download", () -> {
            var button = new LocalizedButton("railroad.home.settings.appearance.downloadtheme");
            button.setOnAction(e -> new ThemeDownloadPane());
            return button;
        }));
    }
}