package dev.railroadide.railroad.plugin.ui;

import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.PluginLoadResult;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import dev.railroadide.railroadpluginapi.deps.MavenDeps;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

public class PluginsPane extends SplitPane {
    private static final double DEFAULT_DIVIDER_POSITION = 0.35;

    private final Map<PluginDescriptor, Boolean> enabledPlugins = new LinkedHashMap<>();
    private final ObservableList<PluginDescriptor> pluginItems = FXCollections.observableArrayList();
    private final FilteredList<PluginDescriptor> filteredPlugins = new FilteredList<>(pluginItems, Objects::nonNull);

    private final RRListView<PluginDescriptor> pluginListView = new RRListView<>();
    private final LocalizedLabel listPlaceholderLabel = new LocalizedLabel("railroad.plugins.list.empty");
    private final LocalizedLabel headerLabel = new LocalizedLabel("railroad.settings.plugins.title");
    private final LocalizedLabel placeholderTitle = new LocalizedLabel("railroad.plugins.placeholder.title");
    private final LocalizedLabel placeholderSubtitle = new LocalizedLabel("railroad.plugins.placeholder.subtitle");
    private final VBox placeholderBox = new VBox(6, placeholderTitle, placeholderSubtitle);
    private final VBox detailContainer = new VBox(16);

    private final Label nameLabel = new Label();
    private final Label metaLabel = new Label();
    private final CheckBox detailToggle = new CheckBox();
    private final Label descriptionValue = new Label();
    private final Hyperlink websiteLink = new Hyperlink();

    private final Label idValue = new Label();
    private final Label versionValue = new Label();
    private final Label authorValue = new Label();
    private final Label licenseValue = new Label();
    private final Label mainClassValue = new Label();
    private final Label dependenciesValue = new Label();

    private final HostServices hostServices;
    private PluginDescriptor activeDescriptor;
    private boolean updatingDetailToggle;

    public PluginsPane() {
        this(PluginManager.getEnabledPlugins());
    }

    public PluginsPane(Map<PluginDescriptor, Boolean> defaultEnabledPlugins) {
        HostServices services;
        try {
            services = Services.getService(HostServices.class);
        } catch (Exception exception) {
            services = null;
        }

        this.hostServices = services;

        placeholderTitle.getStyleClass().add("plugins-pane-placeholder-title");
        placeholderSubtitle.getStyleClass().add("plugins-pane-placeholder-subtitle");
        placeholderTitle.setWrapText(true);
        placeholderSubtitle.setWrapText(true);
        placeholderTitle.setMaxWidth(360);
        placeholderSubtitle.setMaxWidth(360);
        placeholderBox.setAlignment(Pos.CENTER);
        placeholderBox.getStyleClass().add("plugins-pane-placeholder");

        listPlaceholderLabel.setWrapText(true);
        listPlaceholderLabel.setMaxWidth(Double.MAX_VALUE);
        listPlaceholderLabel.setAlignment(Pos.CENTER);
        headerLabel.getStyleClass().add("plugins-pane-title");
        headerLabel.setWrapText(true);

        getStyleClass().add("plugins-pane");
        setDividerPositions(DEFAULT_DIVIDER_POSITION);

        setupListPane();
        Node detailPane = setupDetailPane();
        getItems().setAll(createListSection(), detailPane);
        SplitPane.setResizableWithParent(getItems().getFirst(), false);

        PluginManager.getLoadedPluginsList().addListener((ListChangeListener<PluginLoadResult>) change -> {
            boolean needsRefresh = false;
            while (change.next()) {
                if (change.wasAdded()) {
                    for (PluginLoadResult result : change.getAddedSubList()) {
                        if (!enabledPlugins.containsKey(result.descriptor())) {
                            enabledPlugins.put(result.descriptor(), false);
                            needsRefresh = true;
                        }
                    }
                }
            }

            if (needsRefresh) {
                refreshList();
            }
        });

        setEnabledPlugins(defaultEnabledPlugins);

        L18n.currentLanguageProperty().addListener((obs, oldLang, newLang) -> {
            updateLocalizedStaticTexts();
            updateDetails(activeDescriptor);
        });
        updateLocalizedStaticTexts();
    }

    public Map<PluginDescriptor, Boolean> getEnabledPlugins() {
        return new LinkedHashMap<>(enabledPlugins);
    }

    public void setEnabledPlugins(Map<PluginDescriptor, Boolean> state) {
        enabledPlugins.clear();
        if (state != null) {
            enabledPlugins.putAll(state);
        }

        PluginManager.getLoadedPluginsList()
            .stream()
            .map(PluginLoadResult::descriptor)
            .forEach(descriptor -> enabledPlugins.putIfAbsent(descriptor, false));

        refreshList();
    }

    private void setupListPane() {
        pluginListView.setItems(filteredPlugins);
        pluginListView.setPlaceholder(listPlaceholderLabel);
        pluginListView.setPrefWidth(280);
        pluginListView.setBordered(true);
        pluginListView.setDense(true);
        pluginListView.getSelectionModel().selectedItemProperty()
            .addListener((obs, oldSelection, descriptor) -> updateDetails(descriptor));
        pluginListView.setCellFactory(list -> new PluginListCell());

        filteredPlugins.addListener((ListChangeListener<PluginDescriptor>) change -> {
            if (activeDescriptor != null && !filteredPlugins.contains(activeDescriptor)) {
                if (!filteredPlugins.isEmpty()) {
                    pluginListView.getSelectionModel().select(filteredPlugins.getFirst());
                } else {
                    pluginListView.getSelectionModel().clearSelection();
                    updateDetails(null);
                }
            }
        });
    }

    private Node createListSection() {
        var searchField = new RRTextField();
        searchField.setLocalizedPlaceholder("railroad.plugins.search.placeholder");
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter(newText));

        var container = new RRVBox();
        container.setSpacing(12);
        container.setPadding(new Insets(16));
        container.getChildren().addAll(headerLabel, searchField, pluginListView);
        VBox.setVgrow(pluginListView, Priority.ALWAYS);
        return container;
    }

    private Node setupDetailPane() {
        detailContainer.setPadding(new Insets(24));
        detailContainer.getStyleClass().add("plugin-detail-container");

        var iconWrapper = new StackPane();
        iconWrapper.getStyleClass().add("plugin-detail-icon");
        var icon = new FontIcon(FontAwesomeSolid.PUZZLE_PIECE);
        icon.setIconSize(28);
        iconWrapper.getChildren().add(icon);

        nameLabel.getStyleClass().add("plugin-detail-name");
        nameLabel.setWrapText(true);
        metaLabel.getStyleClass().add("plugin-detail-meta");

        detailToggle.selectedProperty().addListener((obs, oldValue, enabled) -> {
            if (updatingDetailToggle) return;
            setPluginEnabled(activeDescriptor, enabled);
        });

        var headerTexts = new VBox(4, nameLabel, metaLabel);
        HBox.setHgrow(headerTexts, Priority.ALWAYS);

        var header = new HBox(16, iconWrapper, headerTexts, detailToggle);
        header.setAlignment(Pos.CENTER_LEFT);

        descriptionValue.setWrapText(true);
        descriptionValue.getStyleClass().add("plugin-detail-description");

        websiteLink.visibleProperty().bind(websiteLink.textProperty().isNotEmpty());
        websiteLink.managedProperty().bind(websiteLink.visibleProperty());
        websiteLink.setOnAction(event -> {
            String url = websiteLink.getText();
            if (url == null || url.isBlank()) return;
            if (hostServices != null) {
                hostServices.showDocument(url);
            } else {
                Railroad.LOGGER.warn("Unable to open plugin website, HostServices unavailable: {}", url);
            }
        });

        GridPane metadataGrid = buildMetadataGrid();

        detailContainer.getChildren().addAll(header, descriptionValue, websiteLink, metadataGrid);

        var detailScroll = new ScrollPane(detailContainer);
        detailScroll.setFitToWidth(true);
        detailScroll.setFitToHeight(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailScroll.getStyleClass().add("plugin-detail-scroll");

        var stack = new StackPane(placeholderBox, detailScroll);
        StackPane.setAlignment(placeholderBox, Pos.CENTER);

        detailContainer.setVisible(false);
        detailContainer.setManaged(false);

        return stack;
    }

    private GridPane buildMetadataGrid() {
        var grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.getStyleClass().add("plugin-detail-grid");

        addMetadataRow(grid, 0, "railroad.plugins.details.label.identifier", idValue);
        addMetadataRow(grid, 1, "railroad.plugins.details.label.version", versionValue);
        addMetadataRow(grid, 2, "railroad.plugins.details.label.author", authorValue);
        addMetadataRow(grid, 3, "railroad.plugins.details.label.license", licenseValue);
        addMetadataRow(grid, 4, "railroad.plugins.details.label.main_class", mainClassValue);
        addMetadataRow(grid, 5, "railroad.plugins.details.label.dependencies", dependenciesValue);

        return grid;
    }

    private void addMetadataRow(GridPane grid, int row, String labelKey, Label valueLabel) {
        var label = new LocalizedLabel(labelKey);
        label.getStyleClass().add("plugin-detail-label");
        valueLabel.getStyleClass().add("plugin-detail-value");
        valueLabel.setWrapText(true);

        grid.add(label, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private void applyFilter(String query) {
        if (query == null || query.isBlank()) {
            filteredPlugins.setPredicate(Objects::nonNull);
            return;
        }

        String needle = query.toLowerCase(Locale.ENGLISH);
        filteredPlugins.setPredicate(descriptor -> {
            if (descriptor == null)
                return false;

            return contains(descriptor.getName(), needle) ||
                contains(descriptor.getId(), needle) ||
                contains(descriptor.getAuthor(), needle);
        });

        if (activeDescriptor != null && !filteredPlugins.contains(activeDescriptor)) {
            if (!filteredPlugins.isEmpty()) {
                pluginListView.getSelectionModel().select(filteredPlugins.getFirst());
            } else {
                pluginListView.getSelectionModel().clearSelection();
                updateDetails(null);
            }
        }
    }

    private boolean contains(String source, String query) {
        return source != null && source.toLowerCase(Locale.ENGLISH).contains(query);
    }

    private void refreshList() {
        PluginDescriptor selected = pluginListView.getSelectionModel().getSelectedItem();

        var ordered = enabledPlugins.keySet().stream()
            .sorted(Comparator.comparing(this::sortKey, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PluginDescriptor::getId, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();

        pluginItems.setAll(ordered);

        if (selected != null && pluginItems.contains(selected)) {
            pluginListView.getSelectionModel().select(selected);
        } else if (!pluginItems.isEmpty()) {
            pluginListView.getSelectionModel().selectFirst();
        } else {
            pluginListView.getSelectionModel().clearSelection();
            updateDetails(null);
        }
    }

    private void updateDetails(PluginDescriptor descriptor) {
        activeDescriptor = descriptor;
        boolean hasSelection = descriptor != null;

        detailContainer.setVisible(hasSelection);
        detailContainer.setManaged(hasSelection);
        placeholderBox.setVisible(!hasSelection);
        placeholderBox.setManaged(!hasSelection);

        if (!hasSelection)
            return;

        String pluginName = textOrFallback(descriptor.getName(), descriptor.getId());
        nameLabel.setText(pluginName);
        metaLabel.setText(buildMetaText(descriptor));
        descriptionValue.setText(textOrFallback(descriptor.getDescription(),
            L18n.localize("railroad.plugins.details.no_description")));
        websiteLink.setText(textOrFallback(descriptor.getWebsite(), ""));

        String unknown = L18n.localize("railroad.generic.unknown");
        idValue.setText(textOrFallback(descriptor.getId(), unknown));
        versionValue.setText(textOrFallback(descriptor.getVersion(), unknown));
        authorValue.setText(textOrFallback(descriptor.getAuthor(), unknown));
        licenseValue.setText(textOrFallback(descriptor.getLicense(), unknown));
        mainClassValue.setText(textOrFallback(descriptor.getMainClass(), unknown));
        dependenciesValue.setText(formatDependencies(descriptor.getDependencies()));

        updatingDetailToggle = true;
        detailToggle.setSelected(enabledPlugins.getOrDefault(descriptor, false));
        updatingDetailToggle = false;
    }

    private String buildMetaText(PluginDescriptor descriptor) {
        String unknown = L18n.localize("railroad.generic.unknown");
        String version = textOrFallback(descriptor.getVersion(), unknown);
        String author = textOrFallback(descriptor.getAuthor(), unknown);
        String versionMeta = L18n.localize("railroad.plugins.list.version", version);
        String authorMeta = L18n.localize("railroad.plugins.list.author", author);
        return versionMeta + "  •  " + authorMeta;
    }

    private String formatDependencies(MavenDeps deps) {
        if (deps == null)
            return L18n.localize("railroad.plugins.details.dependencies.none");

        int repoCount = deps.repositories() == null ? 0 : deps.repositories().size();
        int artifactCount = deps.artifacts() == null ? 0 : deps.artifacts().size();

        if (repoCount == 0 && artifactCount == 0)
            return L18n.localize("railroad.plugins.details.dependencies.none");

        return L18n.localize("railroad.plugins.details.dependencies.count", repoCount, artifactCount);
    }

    private String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void setPluginEnabled(PluginDescriptor descriptor, boolean enabled) {
        if (descriptor == null)
            return;

        boolean current = enabledPlugins.getOrDefault(descriptor, false);
        if (current == enabled)
            return;

        enabledPlugins.put(descriptor, enabled);
        pluginListView.refresh();
    }

    private String sortKey(PluginDescriptor descriptor) {
        if (descriptor == null)
            return "";

        if (descriptor.getName() != null && !descriptor.getName().isBlank())
            return descriptor.getName();

        if (descriptor.getId() != null && !descriptor.getId().isBlank())
            return descriptor.getId();

        return descriptor.getClass().getSimpleName();
    }

    private void updateLocalizedStaticTexts() {
        detailToggle.setText(L18n.localize("railroad.plugins.details.enable"));
    }

    private class PluginListCell extends ListCell<PluginDescriptor> {
        private final Label title = new Label();
        private final Label subtitle = new Label();
        private final FontIcon icon = new FontIcon(FontAwesomeSolid.PUZZLE_PIECE);
        private final CheckBox toggle = new CheckBox();
        private final HBox container = new HBox(12);
        private boolean updating;

        private PluginListCell() {
            container.getStyleClass().add("plugin-cell-container");

            title.getStyleClass().add("plugin-cell-title");
            subtitle.getStyleClass().add("plugin-cell-subtitle");
            subtitle.setWrapText(true);

            icon.setIconSize(18);
            icon.getStyleClass().add("plugin-cell-icon");

            toggle.setFocusTraversable(false);
            toggle.selectedProperty().addListener((obs, oldValue, enabled) -> {
                if (updating)
                    return;

                PluginDescriptor descriptor = getItem();
                if (descriptor != null && !isEmpty()) {
                    setPluginEnabled(descriptor, enabled);
                    if (descriptor.equals(activeDescriptor)) {
                        updatingDetailToggle = true;
                        detailToggle.setSelected(enabled);
                        updatingDetailToggle = false;
                    }
                }
            });

            var textContainer = new VBox(2, title, subtitle);
            HBox.setHgrow(textContainer, Priority.ALWAYS);

            container.setAlignment(Pos.CENTER_LEFT);
            container.getChildren().addAll(icon, textContainer, toggle);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setPrefHeight(Region.USE_COMPUTED_SIZE);

            selectedProperty().addListener(
                (obs, oldSelected, newSelected) -> updateSelectedState(newSelected));
            updateSelectedState(isSelected());
        }

        @Override
        protected void updateItem(PluginDescriptor descriptor, boolean empty) {
            super.updateItem(descriptor, empty);
            if (empty || descriptor == null) {
                setGraphic(null);
                return;
            }

            title.setText(textOrFallback(descriptor.getName(), descriptor.getId()));
            subtitle.setText(buildMetaText(descriptor));

            updating = true;
            toggle.setSelected(enabledPlugins.getOrDefault(descriptor, false));
            updating = false;

            setGraphic(container);
            updateSelectedState(isSelected());
        }

        private void updateSelectedState(boolean selected) {
            if (selected) {
                if (!container.getStyleClass().contains("plugin-cell-selected")) {
                    container.getStyleClass().add("plugin-cell-selected");
                }
            } else {
                container.getStyleClass().remove("plugin-cell-selected");
            }
        }
    }
}
