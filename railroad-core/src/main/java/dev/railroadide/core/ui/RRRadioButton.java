package dev.railroadide.core.ui;

import org.kordamp.ikonli.Ikon;

import dev.railroadide.core.ui.skin.RRRadioButtonSkin;
import javafx.beans.value.WritableValue;
import javafx.css.StyleableProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;

public class RRRadioButton extends RRToggleButton {

    public static final String[] DEFAULT_STYLE_CLASSES = { "rr-radio-button", "radio-button" };

    //#region Constructor

    public RRRadioButton() {
        this("");
    }

	public RRRadioButton(String localizationKey, Ikon icon, Object... args) {
        super(localizationKey, icon, args);
    }

	public RRRadioButton(String localizationKey, Node graphic, Object... args) {
		super(localizationKey, graphic, args);
    }

    public RRRadioButton(String localizationKey, Object... args) {
		super(localizationKey, args);
    }

    @Override
    protected void initialize() {
        super.initialize();
        
        getStyleClass().setAll(RRRadioButton.DEFAULT_STYLE_CLASSES);

        setAccessibleRole(AccessibleRole.RADIO_BUTTON);

        ((StyleableProperty<Pos>)(WritableValue<Pos>)alignmentProperty()).applyStyle(null, Pos.CENTER_LEFT);
    }

    /**
     * Toggles the state of the radio button if and only if the RadioButton
     * has not already selected or is not part of a {@link ToggleGroup}.
     */
    @Override
    public void fire() {
        // we don't toggle from selected to not selected if part of a group
        if (getToggleGroup() == null || !isSelected()) {
            super.fire();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RRRadioButtonSkin(this);
    }

    @Override
    protected Pos getInitialAlignment() {
        return Pos.CENTER_LEFT;
    }

    //#endregion
}
