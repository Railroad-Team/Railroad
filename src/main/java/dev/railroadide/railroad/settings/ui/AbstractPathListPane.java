package dev.railroadide.railroad.settings.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.styling.ButtonSize;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.localization.L18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

    protected AbstractPathListPane(Collection<Path> initialPaths,
                                   String placeholderKey,
                                   String addTooltipKey,
                                   String removeTooltipKey) {
        setSpacing(12);
        setFillWidth(true);
        getStyleClass().add("path-list-pane");

        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setPlaceholder(new LocalizedLabel(placeholderKey));
        listView.setMinHeight(200);
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        VBox.setVgrow(listView, Priority.NEVER);

        var controls = new RRHBox(8);

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

        addButton.setOnAction($ -> {
            Path selected = choosePath();
            if (selected == null) {
                return;
            }

            addPath(selected);
        });

        removeButton.setOnAction($ -> {
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

    protected AbstractPathListPane(String placeholderKey,
                                   String addTooltipKey,
                                   String removeTooltipKey) {
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
     */
    public List<Path> getPaths() {
        return new ArrayList<>(paths);
    }

    /**
     * Replaces the current list of paths with the given collection.
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
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    public static JsonElement toJson(List<Path> paths) {
        var array = new JsonArray();
        for (Path path : paths) {
            array.add(path.toString());
        }

        return array;
    }

    public static List<Path> fromJson(JsonElement jsonElement) {
        var paths = new ArrayList<Path>();
        if (jsonElement == null || !jsonElement.isJsonArray())
            return paths;

        var jsonArray = jsonElement.getAsJsonArray();
        for (JsonElement element : jsonArray) {
            try {
                Path path = Path.of(element.getAsString());
                paths.add(path);
            } catch (InvalidPathException ignored) {
            }
        }

        return paths;
    }
}
