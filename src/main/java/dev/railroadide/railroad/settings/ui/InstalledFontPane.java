package dev.railroadide.railroad.settings.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.text.Font;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Combo box containing the fonts installed on the current system.
 */
public class InstalledFontPane extends ComboBox<String> {
    /**
     * Creates a font selector and optionally selects the supplied font.
     * A selected font that is not installed is added to the choices so it remains visible.
     *
     * @param selectedFont initially selected font, or {@code null} for no selection
     */
    public InstalledFontPane(@Nullable String selectedFont) {
        var fonts = FXCollections.observableArrayList(Font.getFamilies());
        fonts.sort(String.CASE_INSENSITIVE_ORDER);
        getStyleClass().add("installed-font-pane");
        setItems(fonts);
        setVisibleRowCount(16);

        if (selectedFont != null && !selectedFont.isBlank()) {
            if (!fonts.contains(selectedFont)) {
                fonts.add(selectedFont);
                fonts.sort(Comparator.comparing(String::toLowerCase));
            }
            setValue(selectedFont);
        }
    }
}
