package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A labeled {@link RRHBox} that contains a label and a primary component.
 *
 * @param <T> The type of the primary component.
 */
@Getter
public abstract class LabeledHBox<T extends Node> extends RRHBox {
    private final LocalizedLabel label;
    private final T primaryComponent;
    private final boolean required;

    /**
     * Creates a new labeled HBox.
     *
     * @param labelKey The key of the label.
     * @param required Whether the field is required.
     * @param params   The parameters to pass to the primary component.
     */
    public LabeledHBox(@NotNull String labelKey, boolean required, Map<String, Object> params) {
        super(10);
        setAlignment(Pos.CENTER_LEFT);

        this.label = createLabel(this, labelKey, required);
        this.primaryComponent = createPrimaryComponent(params);
        this.label.setLabelFor(primaryComponent);
        getChildren().add(primaryComponent);

        this.required = required;
    }

    /**
     * Creates the primary component.
     *
     * @param params The parameters to pass to the primary component.
     * @return The primary component.
     */
    public abstract T createPrimaryComponent(Map<String, Object> params);

    /**
     * Creates a label.
     *
     * @param hBox     The HBox to add the label to.
     * @param label    The label text.
     * @param required Whether the field is required.
     * @return The created label.
     */
    protected static LocalizedLabel createLabel(RRHBox hBox, @NotNull String label, boolean required) {
        var labelNode = new LocalizedLabel(label);
        hBox.getChildren().add(labelNode);
        if (required) {
            hBox.getChildren().add(createAsterisk());
        }

        return labelNode;
    }

    /**
     * Creates an asterisk with a tooltip.
     *
     * @return The created asterisk.
     */
    protected static Text createAsterisk() {
        var asterisk = new Text("*");
        asterisk.setFill(Color.RED);
        Tooltip.install(asterisk, new Tooltip("Required"));
        return asterisk;
    }
}
