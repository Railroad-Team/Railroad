package dev.railroadide.railroad.settings.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.text.Font;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class InstalledFontPane extends ComboBox<String> {
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
