package io.github.railroad.core.form.ui;

import io.github.railroad.core.ui.RRHBox;
import io.github.railroad.core.ui.RRVBox;
import io.github.railroad.core.ui.localized.LocalizedLabel;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A labeled container that can switch between horizontal and vertical layout based on available space.
 * Uses horizontal layout by default, but switches to vertical when labels are truncated.
 *
 * @param <T> The type of the primary component.
 */
@Getter
public abstract class LabeledHBox<T extends Node> extends RRHBox {
    private final LocalizedLabel label;
    private final T primaryComponent;
    private final boolean required;
    private final VBox verticalLayout;
    private boolean isVerticalLayout = false;

    /**
     * Creates a new labeled container.
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

        this.verticalLayout = new RRVBox(8);
        this.verticalLayout.setAlignment(Pos.CENTER_LEFT);
        
        if (primaryComponent instanceof Region) {
            HBox.setHgrow(primaryComponent, Priority.ALWAYS);
        }

        this.required = required;
        
        setupHorizontalLayout();
        setupResponsiveLayout();
    }

    private void setupHorizontalLayout() {
        getChildren().clear();
        getChildren().addAll(label, primaryComponent);
        if (required) {
            getChildren().add(createAsterisk());
        }
    }

    private void setupVerticalLayout() {
        getChildren().clear();
        verticalLayout.getChildren().clear();
        verticalLayout.getChildren().addAll(label, primaryComponent);
        if (required) {
            verticalLayout.getChildren().add(createAsterisk());
        }
        getChildren().add(verticalLayout);
        HBox.setHgrow(verticalLayout, Priority.ALWAYS);
    }

    private void setupResponsiveLayout() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getWindow().widthProperty().addListener((obs2, oldWidth, newWidth) -> {
                    checkAndSwitchLayout();
                });
            }
        });
        
        layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (newBounds.getWidth() > 0) {
                checkAndSwitchLayout();
            }
        });
    }

    private void checkAndSwitchLayout() {
        if (label.getText().isEmpty()) return;
        
        // Get the actual width needed for the label text
        double textWidth = label.getFont().getSize() * label.getText().length() * 0.6; // Rough estimate
        double availableWidth = getWidth() - 30; // Account for padding and spacing
        double componentMinWidth = primaryComponent instanceof Region ? 
            ((Region) primaryComponent).getMinWidth() : 100;
        
        // Check if we need vertical layout (not enough space for horizontal)
        boolean needsVertical = availableWidth < (textWidth + componentMinWidth + 20); // 20px buffer
        
        if (needsVertical && !isVerticalLayout) {
            isVerticalLayout = true;
            setupVerticalLayout();
            getStyleClass().add("vertical-layout");
        } else if (!needsVertical && isVerticalLayout) {
            isVerticalLayout = false;
            setupHorizontalLayout();
            getStyleClass().remove("vertical-layout");
        }
    }

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
        labelNode.getStyleClass().add("field-label");
        labelNode.setMinWidth(Region.USE_PREF_SIZE);
        labelNode.setPrefWidth(Region.USE_COMPUTED_SIZE);
        labelNode.setWrapText(true);
        HBox.setHgrow(labelNode, Priority.ALWAYS);
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
        asterisk.getStyleClass().add("field-required");
        Tooltip.install(asterisk, new Tooltip("Required"));
        return asterisk;
    }

    /**
     * Creates the primary component.
     *
     * @param params The parameters to pass to the primary component.
     * @return The primary component.
     */
    public abstract T createPrimaryComponent(Map<String, Object> params);
}
