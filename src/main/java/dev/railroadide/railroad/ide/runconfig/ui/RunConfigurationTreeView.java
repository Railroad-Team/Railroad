package dev.railroadide.railroad.ide.runconfig.ui;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.localization.L18n;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * Presents run configurations grouped by type and optional folder hierarchy.
 */
final class RunConfigurationTreeView extends TreeView<RunConfigurationTreeView.TreeEntry> {
    private final ObservableList<RunConfiguration<?>> configurations;
    private final Map<UUID, TreeItem<TreeEntry>> configurationTreeItems = new HashMap<>();
    private final ObjectProperty<RunConfiguration<?>> selectedConfiguration = new SimpleObjectProperty<>();
    private boolean updatingSelection;
    private UUID pendingSelectionId;

    RunConfigurationTreeView(ObservableList<RunConfiguration<?>> configurations) {
        this.configurations = configurations;
        setShowRoot(false);
        getStyleClass().add("run-configuration-tree-view");
        setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(TreeEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                switch (item) {
                    case TypeTreeEntry(RunConfigurationType<?> type) -> {
                        setText(L18n.localize(type.getLocalizationKey()));
                        setGraphic(createTypeIcon(type));
                    }
                    case FolderTreeEntry(String name) -> {
                        setText(name);
                        setGraphic(new FontIcon(FontAwesomeSolid.FOLDER));
                    }
                    case ConfigurationTreeEntry(RunConfiguration<?> configuration) -> {
                        setText(getConfigurationDisplayName(configuration));
                        setGraphic(createTypeIcon(configuration.type()));
                    }
                    default -> {
                        setText(null);
                        setGraphic(null);
                    }
                }
            }
        });

        configurations.addListener((ListChangeListener<RunConfiguration<?>>) change -> rebuildTree());
        getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (updatingSelection)
                return;

            selectedConfiguration.set(extractConfiguration(newItem));
        });

        selectedConfiguration.addListener((obs, oldValue, newValue) -> handleExternalSelectionChange(newValue));

        rebuildTree();
    }

    ObjectProperty<RunConfiguration<?>> selectedConfigurationProperty() {
        return selectedConfiguration;
    }

    private void rebuildTree() {
        TreeItem<TreeEntry> root = new TreeItem<>(new FolderTreeEntry("root"));
        root.setExpanded(true);
        configurationTreeItems.clear();

        for (RunConfigurationType<?> runConfigurationType : RunConfigurationType.REGISTRY.values()) {
            var typeItem = new TreeItem<TreeEntry>(new TypeTreeEntry(runConfigurationType));
            typeItem.setExpanded(true);
            populateTypeItem(typeItem, runConfigurationType);
            root.getChildren().add(typeItem);
        }

        setRoot(root);
        selectPendingOrCurrentConfiguration();
    }

    private void populateTypeItem(TreeItem<TreeEntry> typeItem, RunConfigurationType<?> type) {
        List<RunConfiguration<?>> typeConfigurations = configurations.stream()
            .filter(configuration -> Objects.equals(configuration.type(), type))
            .sorted(Comparator.comparing(this::getConfigurationDisplayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        Map<String, TreeItem<TreeEntry>> folders = new HashMap<>();
        folders.put("", typeItem);

        for (RunConfiguration<?> configuration : typeConfigurations) {
            TreeItem<TreeEntry> parent = typeItem;
            String folderPath = sanitizeFolderPath(configuration.folderPath());
            if (!folderPath.isBlank()) {
                List<String> segments = extractFolderSegments(folderPath);
                StringBuilder currentPath = new StringBuilder();
                for (String segment : segments) {
                    if (!currentPath.isEmpty())
                        currentPath.append('/');
                    currentPath.append(segment);
                    String pathKey = currentPath.toString();
                    TreeItem<TreeEntry> folderItem = folders.get(pathKey);
                    if (folderItem == null) {
                        folderItem = new TreeItem<>(new FolderTreeEntry(segment));
                        folderItem.setExpanded(true);
                        parent.getChildren().add(folderItem);
                        folders.put(pathKey, folderItem);
                    }
                    parent = folderItem;
                }
            }

            var configurationItem = new TreeItem<TreeEntry>(new ConfigurationTreeEntry(configuration));
            parent.getChildren().add(configurationItem);
            configurationTreeItems.put(configuration.uuid(), configurationItem);
        }
    }

    private RunConfiguration<?> extractConfiguration(TreeItem<TreeEntry> item) {
        if (item == null)
            return null;

        TreeEntry entry = item.getValue();
        if (entry instanceof ConfigurationTreeEntry(RunConfiguration<?> configuration))
            return configuration;

        return null;
    }

    private void handleExternalSelectionChange(RunConfiguration<?> newValue) {
        if (updatingSelection)
            return;

        if (newValue == null) {
            pendingSelectionId = null;
            clearTreeSelection();
            return;
        }

        TreeItem<TreeEntry> treeItem = configurationTreeItems.get(newValue.uuid());
        if (treeItem != null && hasVisibleRows()) {
            selectTreeItem(treeItem);
            pendingSelectionId = null;
        } else {
            pendingSelectionId = newValue.uuid();
        }
    }

    private void selectPendingOrCurrentConfiguration() {
        UUID targetId = pendingSelectionId;
        if (targetId == null && selectedConfiguration.get() != null)
            targetId = selectedConfiguration.get().uuid();

        if (targetId == null) {
            clearTreeSelection();
            return;
        }

        TreeItem<TreeEntry> treeItem = configurationTreeItems.get(targetId);
        if (treeItem == null) {
            pendingSelectionId = targetId;
            return;
        }

        if (hasVisibleRows()) {
            selectTreeItem(treeItem);
            pendingSelectionId = null;
        } else {
            pendingSelectionId = targetId;
        }
    }

    private void selectTreeItem(TreeItem<TreeEntry> treeItem) {
        updatingSelection = true;
        try {
            getSelectionModel().select(treeItem);
            int row = getRow(treeItem);
            if (row >= 0)
                scrollTo(row);
        } finally {
            updatingSelection = false;
        }
    }

    private void clearTreeSelection() {
        updatingSelection = true;
        try {
            getSelectionModel().clearSelection();
        } finally {
            updatingSelection = false;
        }
    }

    private boolean hasVisibleRows() {
        return getExpandedItemCount() > 0;
    }

    private String getConfigurationDisplayName(RunConfiguration<?> configuration) {
        String name = configuration.data().getName();
        if (name == null || name.isBlank())
            return configuration.uuid().toString();

        return name;
    }

    private String sanitizeFolderPath(String folderPath) {
        if (folderPath == null)
            return "";

        return folderPath.trim();
    }

    private List<String> extractFolderSegments(String folderPath) {
        var segments = folderPath.split("[/\\\\]");
        List<String> cleaned = new ArrayList<>();
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty())
                cleaned.add(trimmed);
        }

        return cleaned;
    }

    private FontIcon createTypeIcon(RunConfigurationType<?> type) {
        var icon = new FontIcon(type.getIcon());
        icon.setIconColor(type.getIconColor());
        icon.getStyleClass().add("run-configuration-tree-icon");
        return icon;
    }

    interface TreeEntry {
    }

    private record TypeTreeEntry(RunConfigurationType<?> type) implements TreeEntry {
    }

    private record FolderTreeEntry(String name) implements TreeEntry {
    }

    private record ConfigurationTreeEntry(RunConfiguration<?> configuration) implements TreeEntry {
    }
}
