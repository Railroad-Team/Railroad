package dev.railroadide.railroad.plugin.ui;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import dev.railroadide.railroadpluginapi.deps.MavenDep;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class PluginsPane extends SplitPane {
    @Getter
    private final Map<PluginDescriptor, Boolean> enabledPlugins = new HashMap<>();

    private final ObservableList<PluginModel> items = FXCollections.observableArrayList();
    private final FilteredList<PluginModel> filtered = new FilteredList<>(items);

    private final ListView<PluginModel> listView = new ListView<>();
    private final VBox detailsBox = new RRVBox(24);

    public PluginsPane(Map<PluginDescriptor, Boolean> defaultEnabledPlugins) {
        getStyleClass().add("plugins-pane");

        var left = new RRVBox(12);
        left.getStyleClass().add("plugins-left-pane");
        left.setPadding(new Insets(16, 16, 16, 16));

        var search = new RRTextField("railroad.plugins.search.placeholder");
        search.setPrefHeight(38);

        SortedList<PluginModel> sorted = new SortedList<>(filtered, Comparator.comparing(vm -> vm.descriptor().getName().toLowerCase()));
        listView.setItems(sorted);
        listView.setFocusTraversable(true);
        listView.getStyleClass().add("plugins-list");
        listView.setFixedCellSize(-1);
        VBox.setVgrow(listView, Priority.ALWAYS);

        search.textProperty().addListener((observable, oldValue, newValue) -> {
            final String query = newValue == null ? "" : newValue.toLowerCase();
            filtered.setPredicate(vm ->
                query.isBlank()
                    || vm.nameLower().contains(query)
                    || vm.authorLower().contains(query)
                    || vm.descriptionLower().contains(query));
        });

        listView.setCellFactory($ -> new PluginCell());
        left.getChildren().addAll(search, listView);

        detailsBox.setPadding(new Insets(24, 28, 24, 28));
        detailsBox.setFillWidth(true);

        var detailsScroll = new ScrollPane();
        detailsScroll.setFitToWidth(true);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailsScroll.setContent(detailsBox);

        showPlaceholder();

        listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                showPlaceholder();
                return;
            }

            showDetails(newValue.descriptor());
        });

        setEnabledPlugins(defaultEnabledPlugins);

        getItems().addAll(left, detailsScroll);
        setDividerPositions(0.38);
    }

    public void setEnabledPlugins(Map<PluginDescriptor, Boolean> state) {
        enabledPlugins.clear();
        enabledPlugins.putAll(state);

        items.setAll(
            state.entrySet().stream()
                .map(entry -> new PluginModel(entry.getKey(), entry.getValue()))
                .toList()
        );

        if (!items.isEmpty() && listView.getSelectionModel().getSelectedItem() == null) {
            listView.getSelectionModel().selectFirst();
        } else if (items.isEmpty()) {
            showPlaceholder();
        }
    }

    private void showPlaceholder() {
        detailsBox.getChildren().setAll(createPlaceholderCard());
    }

    private void showDetails(PluginDescriptor descriptor) {
        var card = new RRCard();
        card.getStyleClass().add("plugin-details-card");
        card.setPadding(new Insets(20));
        card.setSpacing(16);

        var header = new RRHBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        var icon = new FontIcon(FontAwesomeSolid.PUZZLE_PIECE);
        icon.setIconSize(28);
        icon.getStyleClass().add("plugin-icon");

        var title = new RRVBox(4);
        var name = new Label(descriptor.getName());
        name.getStyleClass().add("plugin-details-name");
        var version = new LocalizedLabel("railroad.plugins.details.version", descriptor.getVersion());
        version.getStyleClass().add("plugin-details-version");
        title.getChildren().addAll(name, version);
        header.getChildren().addAll(icon, title);

        var info = new RRVBox(12);
        info.getStyleClass().add("plugin-info-box");

        if (isNotBlankOrNull(descriptor.getDescription())) {
            var desc = new LocalizedLabel(resolveText(descriptor.getDescription()));
            desc.getStyleClass().add("plugin-description");
            desc.setWrapText(true);
            info.getChildren().add(desc);
        }

        if (isNotBlankOrNull(descriptor.getAuthor()))
            info.getChildren().add(infoRow("railroad.plugins.details.author", descriptor.getAuthor()));

        if (isNotBlankOrNull(descriptor.getWebsite()))
            info.getChildren().add(infoRow("railroad.plugins.details.website", descriptor.getWebsite()));

        if (isNotBlankOrNull(descriptor.getLicense()))
            info.getChildren().add(infoRow("railroad.plugins.details.license", descriptor.getLicense()));

        info.getChildren().add(infoRow("railroad.plugins.details.id", descriptor.getId()));
        info.getChildren().add(infoRow("railroad.plugins.details.main_class", descriptor.getMainClass()));

        if (descriptor.getDependencies() != null && !descriptor.getDependencies().artifacts().isEmpty()) {
            var depsLabel = new LocalizedLabel("railroad.plugins.details.dependencies");
            depsLabel.getStyleClass().add("plugin-info-label");
            var deps = new RRVBox(4);
            for (MavenDep dependency : descriptor.getDependencies().artifacts()) {
                var label = new Label("• " + dependency.getFullName());
                label.getStyleClass().add("plugin-dependency");
                deps.getChildren().add(label);
            }
            info.getChildren().addAll(depsLabel, deps);
        }

        card.addContent(header, info);
        detailsBox.getChildren().setAll(card);
    }

    private HBox infoRow(String key, String value) {
        var row = new RRHBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        var keyLabel = new LocalizedLabel(key);
        keyLabel.getStyleClass().add("plugin-info-label");
        var valueLabel = new Label(value);
        valueLabel.getStyleClass().add("plugin-info-value");
        valueLabel.setWrapText(true);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        row.getChildren().addAll(keyLabel, valueLabel);
        return row;
    }

    private Node createPlaceholderCard() {
        var card = new RRCard(18, new Insets(36));
        card.getStyleClass().add("plugin-details-placeholder-card");
        var icon = new FontIcon(FontAwesomeSolid.PUZZLE_PIECE);
        icon.setIconSize(56);
        icon.getStyleClass().add("plugin-placeholder-icon");
        var title = new LocalizedLabel("railroad.plugins.placeholder.title");
        title.getStyleClass().add("plugin-placeholder-title");
        var sub = new LocalizedLabel("railroad.plugins.placeholder.subtitle");
        sub.getStyleClass().add("plugin-placeholder-subtitle");
        sub.setWrapText(true);
        sub.setMaxWidth(420);

        var box = new RRVBox(12);
        box.setAlignment(Pos.CENTER);
        box.getChildren().addAll(icon, title, sub);
        card.addContent(box);

        var wrap = new RRVBox(card);
        wrap.setAlignment(Pos.CENTER);
        VBox.setVgrow(wrap, Priority.ALWAYS);
        return wrap;
    }

    private static boolean isNotBlankOrNull(String str) {
        return str != null && !str.isBlank();
    }

    private static String resolveText(String maybeKey) {
        var localizationService = ServiceLocator.getService(LocalizationService.class);
        if (localizationService != null && localizationService.isKeyValid(maybeKey))
            return localizationService.get(maybeKey);

        return maybeKey;
    }

    private record PluginModel(PluginDescriptor descriptor, boolean enabled) {
        String nameLower() {
            return descriptor.getName().toLowerCase();
        }

        String authorLower() {
            return isNotBlankOrNull(descriptor.getAuthor()) ? descriptor.getAuthor().toLowerCase() : "";
        }

        String descriptionLower() {
            var description = descriptor.getDescription();
            var localizationService = ServiceLocator.getService(LocalizationService.class);
            String txt = (localizationService != null && localizationService.isKeyValid(description)) ?
                localizationService.get(description) :
                description;
            return isNotBlankOrNull(txt) ? txt.toLowerCase() : "";
        }
    }

    private class PluginCell extends ListCell<PluginModel> {
        private final CheckBox check = new CheckBox();
        private final Label name = new Label();
        private final Label meta = new Label();
        private final Label summary = new Label();
        private final HBox root = new RRHBox(12);

        PluginCell() {
            getStyleClass().add("plugin-item");
            name.getStyleClass().add("plugin-name");
            meta.getStyleClass().add("plugin-version");
            summary.getStyleClass().add("plugin-summary");
            summary.setWrapText(true);

            var info = new RRVBox(4);
            info.getChildren().addAll(name, meta, summary);
            HBox.setHgrow(info, Priority.ALWAYS);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getChildren().addAll(check, info);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            root.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(PluginModel vm, boolean empty) {
            super.updateItem(vm, empty);
            if (empty || vm == null) {
                setGraphic(null);
                return;
            }

            var descriptor = vm.descriptor();
            name.setText(descriptor.getName());
            meta.setText(ServiceLocator.getService(LocalizationService.class) != null
                ? ServiceLocator.getService(LocalizationService.class).get("railroad.plugins.list.version", descriptor.getVersion())
                : "v" + descriptor.getVersion());

            var description = resolveText(descriptor.getDescription());
            if (isNotBlankOrNull(description)) {
                summary.setManaged(true);
                summary.setVisible(true);
                summary.setText(description.length() > 160 ? description.substring(0, 157) + "…" : description);
            } else {
                summary.setManaged(false);
                summary.setVisible(false);
            }

            check.setSelected(enabledPlugins.getOrDefault(descriptor, false));
            check.selectedProperty().addListener((observable, oldValue, newValue) ->
                enabledPlugins.put(descriptor, newValue));

            setGraphic(root);
        }
    }
}
