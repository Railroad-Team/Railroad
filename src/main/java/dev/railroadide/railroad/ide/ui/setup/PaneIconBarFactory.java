package dev.railroadide.railroad.ide.ui.setup;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
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

    public static Node create(
        DetachableTabPane pane,
        SplitPane split,
        Orientation orientation,
        int originalIndex,
        Map<String, String> iconsByName
    ) {
        var bar = orientation == Orientation.HORIZONTAL ? new RRHBox(4) : new RRVBox(4);
        bar.getStyleClass().add("icon-bar-" + orientation.name().toLowerCase(Locale.ROOT));

        Map<Tab, ToggleButton> btnMap = new LinkedHashMap<>();

        Consumer<Tab> addButtonFor = tab -> {
            String name = tab.getText();
            String icon = iconsByName.getOrDefault(name, FontAwesomeSolid.EYE.getDescription());
            var btn = new ToggleButton("", new FontIcon(icon));
            btn.getStyleClass().add("icon-button");

            btn.setOnAction(e -> {
                boolean isVisible = split.getItems().contains(pane);
                Tab selected = pane.getSelectionModel().getSelectedItem();

                if (isVisible && selected == tab) {
                    split.getItems().remove(pane);
                    btnMap.values().forEach(b -> b.setSelected(false));
                } else {
                    if (!isVisible) {
                        split.getItems().add(Math.min(originalIndex, split.getItems().size()), pane);
                    }
                    pane.getSelectionModel().select(tab);
                    btnMap.values().forEach(b -> b.setSelected(b == btn));
                }
            });

            btnMap.put(tab, btn);
            bar.getChildren().add(btn);
        };

        Consumer<Tab> removeButtonFor = tab -> {
            var btn = btnMap.remove(tab);
            if (btn != null) bar.getChildren().remove(btn);
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

        pane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            btnMap.forEach((tab, btn) -> btn.setSelected(tab == newT));
        });

        Tab init = pane.getSelectionModel().getSelectedItem();
        if (init != null) {
            btnMap.get(init).setSelected(true);
        }

        return bar;
    }
}
