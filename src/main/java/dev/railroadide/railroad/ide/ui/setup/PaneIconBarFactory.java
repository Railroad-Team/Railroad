package dev.railroadide.railroad.ide.ui.setup;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.ide.ui.IDEDockTab;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.ToggleButton;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds the vertical/horizontal icon bars that toggle DetachableTabPanes via toolbar buttons.
 */
public final class PaneIconBarFactory {
    private PaneIconBarFactory() {
    }

    /**
     * Creates tool-tab toggle buttons synchronized with pane visibility and selection.
     *
     * @param pane editor or tool tab pane to manage
     * @param split split pane containing the tool pane
     * @param orientation direction in which child panes are arranged
     * @param originalIndex position at which a hidden tool pane should be reinserted
     * @return icon bar arranged in the requested orientation
     */
    public static Node create(
        DetachableTabPane pane,
        SplitPane split,
        Orientation orientation,
        int originalIndex
    ) {
        var bar = orientation == Orientation.HORIZONTAL ? new RRHBox(4) : new RRVBox(4);
        bar.getStyleClass().add("icon-bar-" + orientation.name().toLowerCase(Locale.ROOT));

        Map<Tab, ToggleButton> btnMap = new LinkedHashMap<>();
        Runnable updateButtonStates = () -> {
            boolean paneVisible = split.getItems().contains(pane);
            Tab selectedTab = pane.getSelectionModel().getSelectedItem();
            btnMap.forEach((tab, button) -> button.setSelected(paneVisible && tab == selectedTab));
        };

        Consumer<Tab> addButtonFor = tab -> {
            var icon = tab instanceof IDEDockTab dockTab ? dockTab.getDockItem().icon() : FontAwesomeSolid.EYE;
            var btn = new ToggleButton("", new FontIcon(icon));
            if (tab instanceof IDEDockTab dockTab) {
                btn.setId("dock-item-button:" + dockTab.getDockItem().id());
            }
            btn.getStyleClass().add("icon-button");

            if (tab instanceof IDEDockTab dockTab) {
                CommandButtons.bind(btn, Commands.toggleDockItem(dockTab.getDockItem()), dockTab::commandContext);
            } else {
                btn.setOnAction(e -> {
                    boolean isVisible = split.getItems().contains(pane);
                    Tab selected = pane.getSelectionModel().getSelectedItem();

                    if (isVisible && selected == tab) {
                        split.getItems().remove(pane);
                    } else {
                        if (!isVisible) {
                            split.getItems().add(Math.min(originalIndex, split.getItems().size()), pane);
                        }
                        pane.getSelectionModel().select(tab);
                    }
                    updateButtonStates.run();
                });
            }

            btnMap.put(tab, btn);
            bar.getChildren().add(btn);
            updateButtonStates.run();
        };

        Consumer<Tab> removeButtonFor = tab -> {
            Node btn = btnMap.remove(tab);
            if (btn != null) {
                bar.getChildren().remove(btn);
            }
        };

        pane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(addButtonFor);
                }
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(removeButtonFor);
                }
            }
        });

        pane.getTabs().forEach(addButtonFor);

        pane.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> updateButtonStates.run());
        split.getItems().addListener((ListChangeListener<Node>) _ -> updateButtonStates.run());
        updateButtonStates.run();

        return bar;
    }
}
