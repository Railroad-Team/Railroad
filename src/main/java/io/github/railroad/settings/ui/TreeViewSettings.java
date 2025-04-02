package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.handler.SearchHandler;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TreeViewSettings {
    public final SearchHandler SEARCH_HANDLER = new SearchHandler();
    private Stage stage;

    //TODO make it, well, not look like this - fix button positioning, treeview border etc
    public TreeViewSettings() {
        stage = new Stage();

        stage.setTitle("Settings");

        var vbox = new VBox();

        var hbox = new HBox();

        var tree = Railroad.SETTINGS_HANDLER.createCategoryTree();
        var treContent = new ScrollPane(Railroad.SETTINGS_HANDLER.createSettingsSection(null));

        tree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                var selected = newValue.getValue();
                if (selected != null) {
                    treContent.setContent(Railroad.SETTINGS_HANDLER.createSettingsSection(((LocalizedLabel) selected).getText()));
                }
            }
        });

        hbox.setMaxWidth(Double.MAX_VALUE);
        hbox.setPrefWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(hbox, Priority.ALWAYS);
        HBox.setHgrow(treContent, Priority.ALWAYS);
        treContent.setFitToWidth(true);
        treContent.setFitToHeight(true);

        vbox.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(vbox, Priority.ALWAYS);
        vbox.setSpacing(10);

        hbox.getChildren().addAll(tree, treContent);

        var searchBar = new TextField();
        searchBar.setPromptText("Search settings");
        searchBar.textProperty().addListener(event -> {
            var searchText = searchBar.getText();
            SEARCH_HANDLER.setQuery(searchText);
            if (!searchText.isEmpty()) {
                var res = SEARCH_HANDLER.mostRelevantFolder(searchText);
                //If currently selected folder is the res
//                if (tree.getSelectionModel().getSelectedItem().getValue().equals(res)) {
//                    //Refresh right side pane
//                    //TODO refresh right side pane properly
//                    tree.getSelectionModel().clearSelection();
//                    tree.getSelectionModel().select(tree.getSelectionModel().getSelectedIndex());
//                }

                if (res != null) {
                    String[] parts = res.split("[.]");
                    String lastPart = parts[parts.length - 1];
                    var currentPart = tree.getRoot();
                    TreeItem lastPartNode = null;

                    Railroad.LOGGER.debug("Last part {}, parts: {}", lastPart, parts);

                    for (String part : parts) {
                        var node = currentPart.getChildren().stream()
                                .filter(item -> ((LocalizedLabel) item.getValue()).getText().equalsIgnoreCase(part))
                                .findFirst()
                                .orElse(null);
                        if (node != null) {
                            if (part.equals(lastPart)){
                                lastPartNode = node;
                                break;
                            }

                            currentPart = node;
                        } else {
                            Railroad.LOGGER.warn("Folder TreeNode not found when searching for {} folder in parts {}", part, res);
                            break;
                        }
                    }

                    tree.getSelectionModel().clearSelection();
                    tree.getSelectionModel().select(lastPartNode);
                }
            }
        });

        vbox.getChildren().addAll(searchBar, hbox);

        var scene = new Scene(vbox, 600, 700);

        Railroad.handleStyles(scene);

        stage.setScene(scene);
        stage.show();
        Railroad.SETTINGS_HANDLER.loadSettingsFromFile();

        //Reset to settings saved when closed without applying
        stage.setOnCloseRequest(event -> {
            Railroad.LOGGER.info("Settings window closed - Reloading settings");
            Railroad.SETTINGS_HANDLER.loadSettingsFromFile();
            Railroad.SETTINGS_HANDLER.getSettings().reloadSettings();
        });
    }

    public void close() {
        stage.close();
    }
}
