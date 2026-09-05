package dev.railroadide.railroad.welcome.project.ui.widget;

import dev.railroadide.railroad.localization.L18n;
import javafx.scene.control.ListCell;

// TODO: support args(?)
/**
 * Displays string items as localization keys, translating them when the cell's item is refreshed.
 * The cell does not install a language-change binding.
 */
public class TranslatableListCell extends ListCell<String> {
    /**
     * Translates the current key or clears the text for an empty cell.
     *
     * @param item translation key, or null
     * @param empty whether the cell has no item
     */
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
