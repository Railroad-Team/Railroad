package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.handler.SearchHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TreeViewSettings {
    public static final SearchHandler SEARCH_HANDLER = new SearchHandler();
    private final Stage stage;

    /**
     * Creates a new settings window,
     * contains a tree view of the folders and then a scroll pane with the settings and their parent folders
     */
    public TreeViewSettings() {
        stage = new Stage();

        stage.setTitle("Settings");

        var vbox = new VBox();

        var hbox = new HBox();

        TreeView<LocalizedLabel> tree = Railroad.SETTINGS_HANDLER.createCategoryTree();
        var settingsContent = new ScrollPane(Railroad.SETTINGS_HANDLER.createSettingsSection(null));

        tree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                var selected = newValue.getValue();
                if (selected != null) {
                    var parts = selected.getKey().split("[.]");
                    settingsContent.setContent(Railroad.SETTINGS_HANDLER.createSettingsSection(parts[parts.length - 1]));
                }
            }
        });

        hbox.setMaxWidth(Double.MAX_VALUE);
        hbox.setPrefWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(hbox, Priority.ALWAYS);
        HBox.setHgrow(settingsContent, Priority.ALWAYS);
        settingsContent.setFitToWidth(true);
        settingsContent.setFitToHeight(true);

        vbox.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(vbox, Priority.ALWAYS);
        vbox.setSpacing(10);

        hbox.getChildren().addAll(tree, settingsContent);

        var searchBar = new TextField();
        searchBar.setPromptText("Search settings");
        searchBar.textProperty().addListener(event -> {
            var searchText = searchBar.getText();
            SEARCH_HANDLER.setQuery(searchText);
            if (!searchText.isEmpty()) {
                var res = SEARCH_HANDLER.mostRelevantFolder(searchText);

                if (res != null) {
                    String[] parts = res.split("[.]");
                    String lastPart = parts[parts.length - 2];
                    var currentPart = tree.getRoot();
                    TreeItem<LocalizedLabel> lastPartNode = null;

                    for (String part : parts) {
                        var node = currentPart.getChildren().stream()
                                .filter(item -> item.getValue().getText().equalsIgnoreCase(part))
                                .findFirst()
                                .orElse(null);
                        if (node != null) {
                            if (part.equals(lastPart)){
                                lastPartNode = node;
                                break;
                            }

                            currentPart = node;
                        } else {
                            Railroad.LOGGER.error("Folder Tree Node not found when searching for {} folder in parts {}", part, res);
                            break;
                        }
                    }

                    tree.getSelectionModel().clearSelection();
                    tree.getSelectionModel().select(lastPartNode);
                }
            }
        });

        var apply = new LocalizedButton("railroad.generic.apply");
        apply.setAlignment(Pos.BOTTOM_RIGHT);
        apply.setOnAction(event -> Railroad.SETTINGS_HANDLER.saveSettingsFile());

        vbox.getChildren().addAll(searchBar, hbox, apply);

        var scene = new Scene(vbox, 600, 700);

        Railroad.handleStyles(scene);

        stage.setScene(scene);
        stage.show();
        Railroad.SETTINGS_HANDLER.loadSettingsFromFile();

        //Reset to settings saved when closed without applying
        stage.setOnCloseRequest(event -> {
            Railroad.SETTINGS_HANDLER.loadSettingsFromFile();
            Railroad.SETTINGS_HANDLER.getSettings().reloadSettings();
        });
    }

    public void close() {
        stage.close();
    }
}
