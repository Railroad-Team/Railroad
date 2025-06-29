package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.handler.SearchHandler;
import io.github.railroad.ui.nodes.RRButton;
import io.github.railroad.ui.nodes.RRTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public class SettingsPane extends VBox {
    public static final SearchHandler SEARCH_HANDLER = new SearchHandler();

    public SettingsPane() {
        setPadding(new Insets(24));
        setSpacing(10);
        setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(this, Priority.ALWAYS);

        var splitPane = new SplitPane();
        splitPane.setMaxWidth(Double.MAX_VALUE);
        splitPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(splitPane, Priority.ALWAYS);

        var leftVbox = new VBox();
        leftVbox.setSpacing(8);
        leftVbox.setPadding(new Insets(0, 12, 0, 0));
        leftVbox.getStyleClass().add("settings-left-vbox");
        var searchBar = new RRTextField("railroad.home.settings.search");
        searchBar.setPromptText("Search settings...");
        searchBar.setMaxWidth(Double.MAX_VALUE);
        searchBar.setPrefHeight(75);
        searchBar.setId("settings-search-bar");
        VBox.setMargin(searchBar, new Insets(0, 0, 16, 0));
        TreeView<LocalizedLabel> tree = Railroad.SETTINGS_HANDLER.createCategoryTree();
        tree.getStyleClass().addAll("settings-tree", "rr-sidebar-tree");
        VBox.setVgrow(tree, Priority.ALWAYS);
        leftVbox.getChildren().addAll(searchBar, tree);
        VBox.setVgrow(leftVbox, Priority.ALWAYS);

        var rightVbox = new VBox();
        rightVbox.setSpacing(0);
        rightVbox.setPadding(new Insets(0, 0, 0, 24));
        rightVbox.getStyleClass().add("settings-right-vbox");
        var pathLabel = new LocalizedLabel("");
        pathLabel.getStyleClass().add("settings-path-title");
        VBox.setMargin(pathLabel, new Insets(0, 0, 16, 0));
        var settingsContentBox = new VBox();
        settingsContentBox.setSpacing(20);
        settingsContentBox.setFillWidth(true);
        settingsContentBox.getStyleClass().add("settings-content-box");
        var settingsContent = new ScrollPane(settingsContentBox);
        settingsContent.setFitToWidth(true);
        settingsContent.setFitToHeight(true);
        settingsContent.getStyleClass().add("settings-content");
        VBox.setVgrow(settingsContent, Priority.ALWAYS);
        rightVbox.getChildren().addAll(pathLabel, settingsContent);
        VBox.setVgrow(rightVbox, Priority.ALWAYS);

        tree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                var selected = newValue.getValue();
                if (selected != null) {
                    var parts = selected.getKey().split("[.]");
                    pathLabel.setText(String.join(" > ", Arrays.stream(parts).map((s) -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).toList()));
                    settingsContentBox.getChildren().setAll(Railroad.SETTINGS_HANDLER.createSettingsSection(parts[parts.length - 1]));
                }
            }
        });

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

        splitPane.getStyleClass().add("settings-split-pane");
        splitPane.setDividerPositions(0.28);
        splitPane.getItems().setAll(leftVbox, rightVbox);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        var borderContainer = new VBox(splitPane);
        borderContainer.getStyleClass().add("settings-border-container");
        VBox.setVgrow(borderContainer, Priority.ALWAYS);

        var buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        var apply = new RRButton("railroad.generic.apply");
        var cancel = new RRButton("railroad.generic.cancel");
        HBox.setMargin(apply, new Insets(5));
        HBox.setMargin(cancel, new Insets(5));
        buttonBar.getChildren().addAll(apply, cancel);
        VBox.setMargin(buttonBar, new Insets(24, 0, 0, 0));

        apply.setOnAction(event -> Railroad.SETTINGS_HANDLER.saveSettingsFile());
        cancel.setOnAction(event -> {
            Scene scene = getScene();
            if (scene != null && scene.getWindow() != null) {
                scene.getWindow().hide();
            }
        });

        getChildren().addAll(borderContainer, buttonBar);
        VBox.setVgrow(buttonBar, Priority.NEVER);

        // Automatically select and open the first category
        TreeItem<LocalizedLabel> root = tree.getRoot();
        if (root != null && !root.getChildren().isEmpty()) {
            TreeItem<LocalizedLabel> firstCategory = root.getChildren().getFirst();
            tree.getSelectionModel().select(firstCategory);
            tree.scrollTo(tree.getRow(firstCategory));
        }
    }
} 