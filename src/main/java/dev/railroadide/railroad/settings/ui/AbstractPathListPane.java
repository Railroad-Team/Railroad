package dev.railroadide.railroad.settings.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.localization.L18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

/**
 * Base class for path list panes that provide add/remove controls and common styling.
 * Subclasses need only provide the path selection implementation and localization keys.
 */
public abstract class AbstractPathListPane extends RRVBox {
    private final ObservableList<Path> paths = FXCollections.observableArrayList();
    @Getter
    private final ListView<Path> listView = new RRListView<>(paths);

    /**
     * Creates a path list pane initialized with the supplied paths and localization keys.
     *
     * @param initialPaths paths to display initially
     * @param placeholderKey localization key for the empty-list placeholder
     * @param addTooltipKey localization key for the add button tooltip
     * @param removeTooltipKey localization key for the remove button tooltip
     */
    protected AbstractPathListPane(
        Collection<Path> initialPaths,
        String placeholderKey,
        String addTooltipKey,
        String removeTooltipKey
    ) {
        setFillWidth(true);
        getStyleClass().add("path-list-pane");

        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setPlaceholder(new LocalizedLabel(placeholderKey));
        listView.getStyleClass().add("path-list-view");
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        VBox.setVgrow(listView, Priority.NEVER);

        var controls = new RRHBox();
        controls.getStyleClass().add("path-list-controls");

        var addButton = new RRButton(null, FontAwesomeSolid.PLUS);
        addButton.setButtonSize(ButtonSize.SMALL);
        addButton.setVariant(ButtonVariant.SUCCESS);
        addButton.setTooltip(new Tooltip(L18n.localize(addTooltipKey)));

        var removeButton = new RRButton(null, FontAwesomeSolid.MINUS);
        removeButton.setButtonSize(ButtonSize.SMALL);
        removeButton.setVariant(ButtonVariant.DANGER);
        removeButton.setTooltip(new Tooltip(L18n.localize(removeTooltipKey)));
        removeButton.setDisable(true);

        controls.getChildren().addAll(addButton, removeButton);

        addButton.setOnAction(_ -> {
            Path selected = choosePath();
            if (selected == null)
                return;

            addPath(selected);
        });

        removeButton.setOnAction(_ -> {
            Path selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                paths.remove(selected);
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldValue, newValue) -> removeButton.setDisable(newValue == null));

        getChildren().addAll(listView, controls);

        setPaths(initialPaths);
    }

    /**
     * Creates an empty path list pane using the supplied localization keys.
     *
     * @param placeholderKey localization key for the empty-list placeholder
     * @param addTooltipKey localization key for the add button tooltip
     * @param removeTooltipKey localization key for the remove button tooltip
     */
    protected AbstractPathListPane(
        String placeholderKey,
        String addTooltipKey,
        String removeTooltipKey
    ) {
        this(Collections.emptyList(), placeholderKey, addTooltipKey, removeTooltipKey);
    }

    /**
     * Allows subclasses to trigger the same path management behaviour for specific selections.
     *
     * @return The user-selected path, or {@code null} if selection was cancelled.
     */
    protected abstract Path choosePath();

    /**
     * Returns the currently listed paths.
     *
     * @return a copy of the currently listed paths
     */
    public List<Path> getPaths() {
        return new ArrayList<>(paths);
    }

    /**
     * Replaces the current list of paths with the given collection.
     *
     * @param newPaths paths to display; {@code null} is treated as an empty collection
     */
    public void setPaths(Collection<Path> newPaths) {
        paths.setAll(normalizePaths(newPaths));
    }

    private void addPath(Path path) {
        Path normalized = normalize(path);
        if (normalized == null)
            return;

        if (paths.stream().noneMatch(existing -> existing.equals(normalized))) {
            paths.add(normalized);
        }
    }

    private static List<Path> normalizePaths(Collection<Path> source) {
        if (source == null || source.isEmpty())
            return Collections.emptyList();

        Set<Path> normalized = new LinkedHashSet<>();
        for (Path path : source) {
            Path normalizedPath = normalize(path);
            if (normalizedPath != null) {
                normalized.add(normalizedPath);
            }
        }

        return new ArrayList<>(normalized);
    }

    private static Path normalize(Path path) {
        if (path == null)
            return null;

        try {
            return path.toAbsolutePath().normalize();
        } catch (InvalidPathException _) {
            return null;
        }
    }

    /**
     * Converts a list of paths into a JSON array containing their string forms.
     *
     * @param paths paths to serialize
     * @return a JSON array containing the supplied paths
     */
    public static JsonElement toJson(List<Path> paths) {
        var array = new JsonArray();
        for (Path path : paths) {
            array.add(path.toString());
        }

        return array;
    }

    /**
     * Reads paths from a JSON array, ignoring malformed entries and non-array values.
     *
     * @param jsonElement JSON value containing serialized paths
     * @return the paths represented by the value, or an empty list when it is not an array
     */
    public static List<Path> fromJson(JsonElement jsonElement) {
        var paths = new ArrayList<Path>();
        if (jsonElement == null || !jsonElement.isJsonArray())
            return paths;

        var jsonArray = jsonElement.getAsJsonArray();
        for (JsonElement element : jsonArray) {
            try {
                Path path = Path.of(element.getAsString());
                paths.add(path);
            } catch (InvalidPathException _) {
            }
        }

        return paths;
    }
}
