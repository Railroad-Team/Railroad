package io.github.railroad.plugin.ui;

import io.github.railroad.core.ui.RRCard;
import io.github.railroad.core.ui.RRHBox;
import io.github.railroad.core.ui.RRTextField;
import io.github.railroad.core.ui.RRVBox;
import io.github.railroad.core.ui.localized.LocalizedLabel;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PluginsPane extends SplitPane {
    private final Map<PluginDescriptor, Boolean> enabledPlugins = new HashMap<>();

    private final VBox pluginsList;
    private final VBox pluginDetails;
    private PluginItem selectedPluginItem;

    public PluginsPane(Map<PluginDescriptor, Boolean> defaultEnabledPlugins) {
        var leftPane = new RRVBox();
        var searchField = new RRTextField();
        searchField.setPromptText("Search plugins...");
        var pluginsListScroll = new ScrollPane();
        pluginsList = new RRVBox();
        var pluginDetailsScroll = new ScrollPane();
        pluginDetails = new RRVBox();

        leftPane.getChildren().addAll(searchField, pluginsListScroll);
        pluginsListScroll.setContent(pluginsList);
        pluginsListScroll.setFitToWidth(true);

        pluginDetailsScroll.setContent(pluginDetails);
        pluginDetailsScroll.setFitToWidth(true);
        pluginDetails.setPadding(new Insets(16));
        pluginDetails.setSpacing(16);

        getItems().addAll(leftPane, pluginDetailsScroll);
        setDividerPositions(0.4);

        getStyleClass().add("plugins-pane");

        refreshPluginsList(defaultEnabledPlugins);
        this.enabledPlugins.putAll(defaultEnabledPlugins);

        searchField.textProperty().addListener((obs, oldText, newText) ->
                filterPlugins(newText));
    }

    public void setEnabledPlugins(Map<PluginDescriptor, Boolean> enabledPlugins) {
        this.enabledPlugins.clear();
        this.enabledPlugins.putAll(enabledPlugins);
        refreshPluginsList(enabledPlugins);
    }

    private void refreshPluginsList(Map<PluginDescriptor, Boolean> enabledPlugins) {
        pluginsList.getChildren().clear();
        pluginsList.setSpacing(8);
        pluginsList.setPadding(new Insets(8));

        for (Map.Entry<PluginDescriptor, Boolean> entry : enabledPlugins.entrySet()) {
            PluginDescriptor descriptor = entry.getKey();
            boolean isEnabled = entry.getValue();
            var pluginItem = new PluginItem(descriptor, isEnabled);
            pluginItem.setOnMouseClicked(e -> selectPlugin(pluginItem));
            pluginItem.setOnEnableChange(enabled -> enabledPlugins.put(descriptor, enabled));
            pluginsList.getChildren().add(pluginItem);
        }

        // Clear selection if the selected plugin is no longer available
        if (selectedPluginItem != null) {
            boolean stillExists = enabledPlugins.entrySet().stream()
                    .anyMatch(entry -> entry.getKey().equals(selectedPluginItem.getDescriptor()));
            if (!stillExists) {
                selectedPluginItem = null;
                pluginDetails.getChildren().clear();
            }
        }
    }

    private void filterPlugins(String searchText) {
        for (Node child : pluginsList.getChildren()) {
            if (child instanceof PluginItem pluginItem) {
                String name = pluginItem.getDescriptor().getName().toLowerCase();
                String description = pluginItem.getDescriptor().getDescription() != null ?
                        pluginItem.getDescriptor().getDescription().toLowerCase() : "";
                String author = pluginItem.getDescriptor().getAuthor() != null ?
                        pluginItem.getDescriptor().getAuthor().toLowerCase() : "";

                boolean matches = searchText.isEmpty() ||
                        name.contains(searchText.toLowerCase()) ||
                        description.contains(searchText.toLowerCase()) ||
                        author.contains(searchText.toLowerCase());

                pluginItem.setVisible(matches);
                pluginItem.setManaged(matches);
            }
        }
    }

    private void selectPlugin(PluginItem pluginItem) {
        if (selectedPluginItem != null) {
            selectedPluginItem.setSelected(false);
        }

        selectedPluginItem = pluginItem;
        pluginItem.setSelected(true);

        showPluginDetails(pluginItem.getDescriptor());
    }

    private void showPluginDetails(PluginDescriptor descriptor) {
        pluginDetails.getChildren().clear();

        var detailsCard = new RRCard();
        detailsCard.setPadding(new Insets(20));
        detailsCard.setSpacing(16);
        detailsCard.getStyleClass().add("plugin-details-card");

        var headerBox = new RRHBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        var pluginIcon = new FontIcon(FontAwesomeSolid.PUZZLE_PIECE);
        pluginIcon.setIconSize(32);
        pluginIcon.getStyleClass().add("plugin-icon");

        var titleBox = new RRVBox(4);
        var nameLabel = new Label(descriptor.getName());
        nameLabel.getStyleClass().add("plugin-details-name");
        var versionLabel = new Label("Version " + descriptor.getVersion());
        versionLabel.getStyleClass().add("plugin-details-version");
        titleBox.getChildren().addAll(nameLabel, versionLabel);

        headerBox.getChildren().addAll(pluginIcon, titleBox);

        var infoBox = new RRVBox(12);
        infoBox.getStyleClass().add("plugin-info-box");

        if (descriptor.getDescription() != null && !descriptor.getDescription().isEmpty()) {
            var descriptionLabel = new LocalizedLabel(descriptor.getDescription());
            descriptionLabel.setWrapText(true);
            descriptionLabel.getStyleClass().add("plugin-description");
            infoBox.getChildren().add(descriptionLabel);
        }

        if (descriptor.getAuthor() != null && !descriptor.getAuthor().isEmpty()) {
            var authorBox = createInfoRow("Author", descriptor.getAuthor());
            infoBox.getChildren().add(authorBox);
        }

        if (descriptor.getWebsite() != null && !descriptor.getWebsite().isEmpty()) {
            var websiteBox = createInfoRow("Website", descriptor.getWebsite());
            infoBox.getChildren().add(websiteBox);
        }

        if (descriptor.getLicense() != null && !descriptor.getLicense().isEmpty()) {
            var licenseBox = createInfoRow("License", descriptor.getLicense());
            infoBox.getChildren().add(licenseBox);
        }

        var idBox = createInfoRow("Plugin ID", descriptor.getId());
        infoBox.getChildren().add(idBox);

        var mainClassBox = createInfoRow("Main Class", descriptor.getMainClass());
        infoBox.getChildren().add(mainClassBox);

        if (descriptor.getDependencies() != null && !descriptor.getDependencies().isEmpty()) {
            var dependenciesBox = createDependenciesSection(descriptor.getDependencies());
            infoBox.getChildren().add(dependenciesBox);
        }

        detailsCard.addContent(headerBox, infoBox);
        pluginDetails.getChildren().add(detailsCard);
    }

    private HBox createInfoRow(String label, String value) {
        var row = new RRHBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        var labelNode = new Label(label + ":");
        labelNode.getStyleClass().add("plugin-info-label");
        labelNode.setMinWidth(80);

        var valueNode = new Label(value);
        valueNode.getStyleClass().add("plugin-info-value");
        valueNode.setWrapText(true);
        HBox.setHgrow(valueNode, Priority.ALWAYS);

        row.getChildren().addAll(labelNode, valueNode);
        return row;
    }

    private VBox createDependenciesSection(Map<String, String> dependencies) {
        var section = new RRVBox(8);

        var sectionLabel = new Label("Dependencies:");
        sectionLabel.getStyleClass().add("plugin-info-label");

        var dependenciesList = new RRVBox(4);
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            var depLabel = new Label("• " + entry.getKey() + ": " + entry.getValue());
            depLabel.getStyleClass().add("plugin-dependency");
            dependenciesList.getChildren().add(depLabel);
        }

        section.getChildren().addAll(sectionLabel, dependenciesList);
        return section;
    }

    public Map<PluginDescriptor, Boolean> getEnabledPlugins() {
        return this.enabledPlugins;
    }

    public class PluginItem extends RRCard {
        @Getter
        private final PluginDescriptor descriptor;
        private final CheckBox enableCheckBox;
        @Getter
        private boolean selected = false;
        @Setter
        private Consumer<Boolean> onEnableChange;

        public PluginItem(PluginDescriptor descriptor, boolean enabled) {
            this.descriptor = descriptor;
            setPadding(new Insets(12));
            setSpacing(8);
            getStyleClass().add("plugin-item");
            setInteractive(true);

            enableCheckBox = new CheckBox();
            enableCheckBox.setSelected(enabled);
            enableCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                PluginsPane.this.enabledPlugins.put(descriptor, newValue);
            });

            var infoBox = new RRVBox(4);
            var nameLabel = new Label(descriptor.getName());
            nameLabel.getStyleClass().add("plugin-name");

            var versionLabel = new Label("Version: " + descriptor.getVersion());
            versionLabel.getStyleClass().add("plugin-version");

            infoBox.getChildren().addAll(nameLabel, versionLabel);
            VBox.setVgrow(infoBox, Priority.ALWAYS);

            var contentBox = new RRHBox(12);
            contentBox.setAlignment(Pos.CENTER_LEFT);
            contentBox.getChildren().addAll(enableCheckBox, infoBox);

            addContent(contentBox);
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            if (selected) {
                getStyleClass().add("selected");
            } else {
                getStyleClass().remove("selected");
            }
        }
    }
}
