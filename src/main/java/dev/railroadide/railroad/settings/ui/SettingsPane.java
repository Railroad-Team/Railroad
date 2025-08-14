package dev.railroadide.railroad.settings.ui;

import dev.railroadide.core.settings.SettingsSearchHandler;
import dev.railroadide.core.settings.SettingsUIHandler;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A comprehensive settings pane that provides a modern, searchable interface for managing application settings.
 * Features a split-pane layout with a searchable category tree on the left and detailed settings content on the right.
 * Supports real-time search functionality and automatic category navigation.
 */
public class SettingsPane extends RRVBox {
    /**
     * Constructs a new SettingsPane with a modern split-pane layout.
     * The left panel contains a search bar and category tree, while the right panel
     * displays the selected category's settings with apply/cancel buttons.
     */
    public SettingsPane() {
        setPadding(new Insets(24));
        setSpacing(10);
        setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(this, Priority.ALWAYS);

        var splitPane = new SplitPane();
        splitPane.setMaxWidth(Double.MAX_VALUE);
        splitPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(splitPane, Priority.ALWAYS);

        var leftVbox = new RRVBox();
        leftVbox.setSpacing(8);
        leftVbox.setPadding(new Insets(0, 12, 0, 0));
        leftVbox.getStyleClass().add("settings-left-vbox");
        var searchBar = new RRTextField("railroad.home.settings.search");
        searchBar.setMaxWidth(Double.MAX_VALUE);
        searchBar.setPrefHeight(75);
        searchBar.setId("settings-search-bar");
        VBox.setMargin(searchBar, new Insets(0, 0, 16, 0));
        TreeView<LocalizedLabel> tree = SettingsUIHandler.createCategoryTree(SettingsHandler.SETTINGS_REGISTRY.values());
        tree.getStyleClass().addAll("settings-tree", "rr-sidebar-tree");
        VBox.setVgrow(tree, Priority.ALWAYS);
        leftVbox.getChildren().addAll(searchBar, tree);
        VBox.setVgrow(leftVbox, Priority.ALWAYS);

        var rightVbox = new RRVBox();
        rightVbox.setSpacing(0);
        rightVbox.setPadding(new Insets(0, 0, 0, 24));
        rightVbox.getStyleClass().add("settings-right-vbox");
        var pathLabel = new LocalizedLabel("");
        pathLabel.getStyleClass().add("settings-path-title");
        VBox.setMargin(pathLabel, new Insets(0, 0, 16, 0));
        var settingsContentBox = new RRVBox();
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

        List<Runnable> applyListeners = new ArrayList<>();

        tree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newItem) -> {
            if (newItem == null) return;

            LocalizedLabel selected = newItem.getValue();
            if (selected == null) return;

            String key = String.valueOf(selected.getUserData());
            if (Objects.equals(key, "null") || key.isBlank()) return;

            String[] parts = key.split("\\.");
            if (parts.length == 0) return;

            var pathBuilder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                String translationKey = "settings.tree." + String.join(".", Arrays.copyOfRange(parts, 0, i + 1));
                String translation = L18n.localize(translationKey);
                if (i > 0) {
                    pathBuilder.append(" > ");
                }

                if (translation == null || translation.isBlank()) {
                    translation = parts[i]; // Fallback to the part itself if no translation is found
                }

                pathBuilder.append(translation);
            }

            pathLabel.setText(pathBuilder.toString());
            settingsContentBox.getChildren().setAll(SettingsUIHandler.createSettingsSection(
                    SettingsHandler.SETTINGS_REGISTRY.values(),
                    parts[parts.length - 1],
                    applyListeners
            ));
        });

        var searchHandler = new SettingsSearchHandler(SettingsHandler.SETTINGS_REGISTRY.values());
        searchBar.textProperty().addListener((observable, oldText, newText) -> {
            searchHandler.setQuery(newText);
            if (newText.isEmpty()) return;

            String matched = searchHandler.mostRelevantFolder(newText);
            if (matched == null) return;

            TreeItem<LocalizedLabel> toSelect = null;
            for (TreeItem<LocalizedLabel> item : tree.getRoot().getChildren()) {
                LocalizedLabel label = item.getValue();
                if (Objects.equals(label.getUserData(), matched)) {
                    toSelect = item;
                    break;
                }
            }

            if (toSelect != null) {
                tree.getSelectionModel().clearSelection();
                tree.getSelectionModel().select(toSelect);
            }
        });

        splitPane.getStyleClass().add("settings-split-pane");
        splitPane.setDividerPositions(0.28);
        splitPane.getItems().setAll(leftVbox, rightVbox);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        var borderContainer = new RRVBox();
        borderContainer.getChildren().add(splitPane);
        borderContainer.getStyleClass().add("settings-border-container");
        VBox.setVgrow(borderContainer, Priority.ALWAYS);

        var buttonBar = new RRHBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        var apply = new RRButton("railroad.generic.apply");
        var cancel = new RRButton("railroad.generic.cancel");
        HBox.setMargin(apply, new Insets(5));
        HBox.setMargin(cancel, new Insets(5));
        buttonBar.getChildren().addAll(apply, cancel);
        VBox.setMargin(buttonBar, new Insets(24, 0, 0, 0));

        apply.setOnAction(event -> {
            for (Runnable listener : applyListeners) {
                listener.run();
            }

            SettingsHandler.saveSettings();
        });
        cancel.setOnAction(event -> {
            SettingsHandler.loadSettings();
            SettingsHandler.getSettingsHolder().updateAll();
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