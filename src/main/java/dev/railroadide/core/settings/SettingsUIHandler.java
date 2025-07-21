package dev.railroadide.core.settings;

import dev.railroadide.core.logger.LoggerServiceLocator;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A utility class for handling the user interface aspects of settings in the Railroad application.
 * It provides methods to create a categorized TreeView of settings and a VBox containing settings sections.
 */
public class SettingsUIHandler {
    private static TreeItem<LocalizedLabel> createItem(@Nullable String key) {
        if (StringUtils.isBlank(key))
            return new TreeItem<>(null);

        var label = new LocalizedLabel("settings.tree." + key);
        label.setUserData(key);
        return new TreeItem<>(label);
    }

    /**
     * Creates a TreeView representing the categories of settings.
     * Each category is represented as a folder in the tree, with subcategories and settings as nested items.
     *
     * @param settings The collection of settings to be displayed in the tree.
     * @return A TreeView containing the categorized settings.
     */
    public static TreeView<LocalizedLabel> createCategoryTree(Collection<Setting<?>> settings) {
        TreeView<LocalizedLabel> view = new TreeView<>(createItem(null));
        view.setShowRoot(false);

        for (Setting<?> setting : settings) {
            String[] parts = setting.getTreePath().split("\\.");
            TreeItem<LocalizedLabel> currNode = view.getRoot();
            for (int index = 0; index < parts.length; index++) {
                String key = String.join(".", Arrays.copyOfRange(parts, 0, index + 1));
                Optional<TreeItem<LocalizedLabel>> folder = currNode.getChildren()
                        .stream()
                        .filter(l -> l.getValue().getUserData().equals(key))
                        .findFirst();

                if (folder.isEmpty()) {
                    var newFolder = createItem(key);
                    currNode.getChildren().add(newFolder);
                    currNode = newFolder;
                } else {
                    currNode = folder.get();
                }
            }
        }

        return view;
    }

    /**
     * Creates a VBox containing settings grouped by their categories.
     *
     * @param settings The collection of settings to be displayed.
     * @param parent   The parent category to filter settings by.
     * @param applyListeners A list of listeners to be notified when settings are applied.
     * @return A VBox containing the settings organized by category.
     */
    public static VBox createSettingsSection(Collection<Setting<?>> settings, @NotNull String parent, List<Runnable> applyListeners) {
        var searchHandler = new SettingsSearchHandler(settings);
        final Map<String, VBox> folderBoxes = new HashMap<>();
        final Map<String, SettingCategory> categoryMap = new HashMap<>();
        final VBox vbox = new RRVBox();

        for (Setting<?> setting : settings) {
            SettingCategory category = setting.getCategory();
            String categoryId = category.id().toLowerCase(Locale.ROOT);
            String[] split = setting.getTreePath().split("\\.");
            if (split.length == 0 || !split[split.length - 1].equals(parent))
                continue;

            categoryMap.putIfAbsent(categoryId, category);

            if(!folderBoxes.containsKey(categoryId)) {
                var folderBox = new RRVBox();
                folderBoxes.put(categoryId, folderBox);
                VBox.setMargin(folderBox, new Insets(5, 10, 0, 10));
            }

            VBox folderBox = folderBoxes.get(categoryId);

            var settingBox = new RRVBox();
            VBox.setMargin(settingBox, new Insets(5));

            Node settingNode = setting.createNode();
            if (settingNode == null) {
                LoggerServiceLocator.getInstance().getLogger().warn("Setting node for {} is null, skipping.", setting.getId());
                continue;
            }

            applyListeners.add(() -> setting.readValueFromNode(settingNode));

            Node titleNode = null;
            if(setting.isHasTitle()) {
                titleNode = new LocalizedLabel(setting.getTitle());
                titleNode.getStyleClass().add("section-label");
            }

            Node descriptionNode = null;
            if(setting.isHasDescription()) {
                var descriptionLabel = new LocalizedLabel(setting.getDescription());
                descriptionLabel.getStyleClass().add("section-description-label");
                descriptionLabel.setWrapText(true);
                descriptionNode = descriptionLabel;
            }

            List<Node> nodes = new ArrayList<>(3);
            if (titleNode != null) {
                nodes.add(titleNode);
            }

            nodes.add(settingNode);

            if (descriptionNode != null) {
                nodes.add(descriptionNode);
            }

            settingBox.getChildren().addAll(searchHandler.styleNodes(nodes.toArray(new Node[0])));

            folderBox.getChildren().add(settingBox);
        }

        for (Map.Entry<String, VBox> entry : folderBoxes.entrySet()) {
            String categoryId = entry.getKey();
            SettingCategory category = categoryMap.get(categoryId);

            var headerBox = new RRHBox();
            headerBox.setSpacing(10);
            headerBox.setPadding(new Insets(5, 10, 0, 10));

            Node titleNode = null;
            if(category.hasTitle()) {
                titleNode = new LocalizedLabel(category.title());
                titleNode.getStyleClass().add("section-label");;
            }

            var separator = new Separator();
            separator.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(separator, Priority.ALWAYS);

            if(titleNode != null) {
                headerBox.getChildren().add(titleNode);
            }
            headerBox.getChildren().add(separator);
            vbox.getChildren().add(headerBox);

            Node descriptionNode = null;
            if(category.hasDescription()) {
                var descriptionLabel = new LocalizedLabel(category.description());
                descriptionLabel.getStyleClass().add("section-description-label");
                descriptionLabel.setWrapText(true);
                descriptionLabel.setPadding(new Insets(0, 0, 0, 5));
                descriptionNode = descriptionLabel;
            }

            if (descriptionNode != null) {
                vbox.getChildren().add(descriptionNode);
            }

            vbox.getChildren().add(entry.getValue());
        }

        return vbox;
    }
}
