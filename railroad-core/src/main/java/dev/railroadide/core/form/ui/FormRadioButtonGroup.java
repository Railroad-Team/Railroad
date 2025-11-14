package dev.railroadide.core.form.ui;

import dev.railroadide.core.form.HasSetValue;
import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A form component wrapper that renders a horizontal group of radio buttons with a label.
 *
 * @param <E> the enum backing the radio buttons
 */
public class FormRadioButtonGroup<E extends Enum<E>> extends InformativeLabeledHBox<HBox> implements HasSetValue {
    private ToggleGroup toggleGroup;
    private final ObjectProperty<E> value = new SimpleObjectProperty<>();
    private Map<E, RadioButton> radioButtons;

    public FormRadioButtonGroup(String labelKey, boolean required, List<E> values, Function<E, String> optionLabelProvider, boolean translateOptions, double spacing) {
        super(labelKey, required, Map.of(
            "values", values,
            "labelProvider", optionLabelProvider,
            "translateOptions", translateOptions,
            "spacing", spacing
        ));

        value.addListener((observable, oldValue, newValue) -> {
            if (newValue == null && toggleGroup != null && toggleGroup.getSelectedToggle() != null) {
                toggleGroup.selectToggle(null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public HBox createPrimaryComponent(Map<String, Object> params) {
        List<E> values = (List<E>) params.get("values");
        Function<E, String> labelProvider = (Function<E, String>) params.get("labelProvider");
        boolean translate = Boolean.TRUE.equals(params.getOrDefault("translateOptions", Boolean.TRUE));
        double spacing = params.get("spacing") instanceof Number number ? number.doubleValue() : 12d;

        var container = new RRHBox(spacing);
        container.getStyleClass().add("form-radio-button-group");

        this.toggleGroup = new ToggleGroup();
        if (radioButtons == null) {
            radioButtons = new LinkedHashMap<>();
        } else {
            radioButtons.clear();
        }

        for (E enumValue : values) {
            var radioButton = new RadioButton();
            radioButton.setToggleGroup(toggleGroup);
            radioButton.setUserData(enumValue);
            radioButton.setMnemonicParsing(false);
            radioButton.getStyleClass().add("rr-radio-button");

            String label = labelProvider.apply(enumValue);
            radioButton.setText(translate ?
                ServiceLocator.getService(LocalizationService.class).get(label) :
                label);

            container.getChildren().add(radioButton);
            radioButtons.put(enumValue, radioButton);
        }

        toggleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                value.set(null);
            } else {
                value.set((E) newToggle.getUserData());
            }
        });

        return container;
    }

    public ObjectProperty<E> valueProperty() {
        return value;
    }

    public E getValue() {
        return value.get();
    }

    public void setValue(E newValue) {
        if (newValue == null) {
            Platform.runLater(() -> {
                if (toggleGroup != null) {
                    toggleGroup.selectToggle(null);
                }
                value.set(null);
            });
            return;
        }

        RadioButton radioButton = radioButtons.get(newValue);
        if (radioButton == null)
            return;

        Platform.runLater(() -> radioButton.setSelected(true));
    }

    @Override
    public void setValue(Object newValue) {
        if (newValue == null) {
            setValue(null);
            return;
        }

        @SuppressWarnings("unchecked")
        E enumValue = (E) newValue;
        setValue(enumValue);
    }

    /**
     * Exposes the selected toggle for validation or accessibility.
     */
    public Toggle selectedToggle() {
        return toggleGroup == null ? null : toggleGroup.getSelectedToggle();
    }
}
