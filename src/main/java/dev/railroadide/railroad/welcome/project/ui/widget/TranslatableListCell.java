package dev.railroadide.railroad.welcome.project.ui.widget;

import dev.railroadide.railroad.localization.L18n;
import javafx.scene.control.ListCell;

// TODO: Move to core and support args(?)
public class TranslatableListCell extends ListCell<String> {
    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
        } else {
            setText(L18n.localize(item));
        }
    }
}
