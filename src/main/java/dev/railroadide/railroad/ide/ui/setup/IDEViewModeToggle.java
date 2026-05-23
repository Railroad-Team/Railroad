package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRToggleButton;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleGroup;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.EnumMap;

public final class IDEViewModeToggle extends RRHBox {
    public IDEViewModeToggle(ObjectProperty<IDEViewMode> viewModeProperty) {
        super(0);
        getStyleClass().addAll("ide-view-mode-toggle", "segmented-switch");
        setAlignment(Pos.CENTER_LEFT);

        var toggleGroup = new ToggleGroup();
        var buttonsByMode = new EnumMap<IDEViewMode, RRToggleButton>(IDEViewMode.class);
        IDEViewMode[] modes = IDEViewMode.values();

        for (int index = 0; index < modes.length; index++) {
            IDEViewMode mode = modes[index];
            var button = createButton(mode, toggleGroup);
            button.getStyleClass().add(pillStyleClass(index, modes.length));
            button.setOnAction(event -> viewModeProperty.set(mode));
            buttonsByMode.put(mode, button);
            getChildren().add(button);
        }

        viewModeProperty.addListener((observable, oldValue, newValue) ->
            selectButton(buttonsByMode, toggleGroup, newValue));

        IDEViewMode initialMode = viewModeProperty.get() == null ? IDEViewMode.CODE : viewModeProperty.get();
        selectButton(buttonsByMode, toggleGroup, initialMode);
    }

    private static RRToggleButton createButton(IDEViewMode mode, ToggleGroup toggleGroup) {
        var button = new RRToggleButton(mode.getLocalizationKey(), iconFor(mode));
        button.setToggleGroup(toggleGroup);
        button.setVariant(ButtonVariant.SECONDARY);
        button.setButtonSize(ButtonSize.SMALL);
        button.setMinWidth(USE_PREF_SIZE);
        button.getStyleClass().add("segmented-switch-button");
        return button;
    }

    private static void selectButton(EnumMap<IDEViewMode, RRToggleButton> buttonsByMode, ToggleGroup toggleGroup, IDEViewMode selectedMode) {
        IDEViewMode resolvedMode = selectedMode == null ? IDEViewMode.CODE : selectedMode;
        RRToggleButton button = buttonsByMode.get(resolvedMode);
        if (button != null) {
            button.setSelected(true);
            toggleGroup.selectToggle(button);
        }
    }

    private static String pillStyleClass(int index, int size) {
        if (size == 1) {
            return "left-pill";
        }

        if (index == 0) {
            return "left-pill";
        }

        if (index == size - 1) {
            return "right-pill";
        }

        return "center-pill";
    }

    private static Ikon iconFor(IDEViewMode mode) {
        return switch (mode) {
            case CODE -> FontAwesomeSolid.CODE;
            case GIT -> FontAwesomeBrands.GIT_ALT;
        };
    }
}
