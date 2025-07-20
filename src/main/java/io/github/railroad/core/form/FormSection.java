package io.github.railroad.core.form;

import io.github.railroad.core.ui.RRVBox;
import io.github.railroad.core.ui.localized.LocalizedText;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents a section of a form.
 * <p>
 * A form section is a group of form components that are related to each other in some way. For example, a form section
 * could be a group of fields that are all related to a single entity, such as a user. A form section is responsible for
 * creating the UI for the fields it contains, binding the fields to a {@link FormData} object, and validating the fields.
 * </p>
 */
public class FormSection {
    @Getter
    private final String titleKey;
    private final List<FormComponent<?, ?, ?, ?>> fields;
    private final double spacing;
    private final Insets padding;
    private final Border border;
    private final Consumer<LocalizedText> titleConsumer;
    @Getter
    private FormData formData;

    /**
     * Creates a new form section with the given properties.
     *
     * @param builder the builder to create the form section from
     * @implNote This constructor is package-private and should only be called by the {@link Builder} class.
     */
    @ApiStatus.Internal
    private FormSection(Builder builder) {
        this.titleKey = builder.title;
        this.fields = builder.fields;
        this.spacing = builder.spacing;
        this.padding = builder.padding;
        this.border = Objects.requireNonNullElseGet(builder.border,
                () -> new Border(new BorderStroke(builder.borderColor, builder.borderStyle, builder.borderRadii, builder.borderWidths)));
        this.titleConsumer = builder.titleConsumer;
    }

    /**
     * Creates a new form section with the given title key.
     *
     * @param titleKey the key of the title of the form section
     * @return a new form section builder
     */
    public static Builder create(String titleKey) {
        return new Builder().title(titleKey);
    }

    /**
     * Resets all the fields in the form section to their default values.
     */
    public void reset() {
        fields.forEach(FormComponent::reset);
    }

    /**
     * Disables all the fields in the form section.
     *
     * @param disable whether to disable the fields
     */
    public void disable(boolean disable) {
        fields.forEach(field -> field.disable(disable));
    }

    /**
     * Creates the UI for the form section. This method should be called when you want to display the form section in
     * the UI.
     *
     * @return the root node of the form section
     * @implNote This method will construct all the fields in the form section and add them to a VBox. The title of the
     * form section will be displayed at the top of the VBox.
     */
    public Node createUI() {
        var vbox = new RRVBox(spacing);
        vbox.setBorder(border);
        vbox.setPadding(padding);

        var title = new LocalizedText(titleKey);
        title.getStyleClass().add("form-section-title");
        titleConsumer.accept(title);
        vbox.getChildren().add(title);

        for (FormComponent<?, ?, ?, ?> field : fields) {
            Node node = field.getComponent();
            if (node.isVisible()) {
                addChildNode(vbox, field);
            }

            node.visibleProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    if (!vbox.getChildren().contains(node)) {
                        addChildNode(vbox, field);
                    }
                } else {
                    vbox.getChildren().remove(node);
                }
            });
        }

        return vbox;
    }

    /**
     * <strong>Adds the given component to the given vbox at the correct index.</strong>
     * <p>
     * The index is determined by the order of the fields in the fields list. It will find the first field that is
     * before the given component in the list and add the component after that field.
     * </p>
     *
     * @param vbox      the vbox to add the component to
     * @param component the component to add
     */
    private void addChildNode(RRVBox vbox, FormComponent<?, ?, ?, ?> component) {
        int index = fields.indexOf(component);
        if(index == 0) {
            vbox.getChildren().add(1, component.getComponent());
            return;
        }

        for (int i = index - 1; i >= 0; i--) {
            FormComponent<?, ?, ?, ?> field = fields.get(i);
            if (field.getComponent().isVisible()) {
                vbox.getChildren().add(vbox.getChildren().indexOf(field.getComponent()) + 1, component.getComponent());
                return;
            }
        }

        vbox.getChildren().add(1, component.getComponent());
    }

    /**
     * Binds the form data to the form section.
     *
     * @param formData the form data to bind to the form section
     */
    protected void bindFormData(FormData formData) {
        this.formData = formData;
        fields.forEach(field -> field.bindToFormData(formData));
    }

    /**
     * Validates all the fields in the form section.
     *
     * @return whether all the fields in the form section are valid
     */
    public boolean validate() {
        return fields.stream().allMatch(formComponent -> formComponent.validate().status() != ValidationResult.Status.ERROR);
    }

    /**
     * Runs the validation on all the fields in the form section.
     */
    public void runValidation() {
        fields.forEach(FormComponent::runValidation);
    }

    /**
     * A builder class for creating a form section.
     */
    public static class Builder {
        private final List<FormComponent<?, ?, ?, ?>> fields = new ArrayList<>();
        private String title;
        private double spacing = 10;
        private Insets padding = new Insets(10);

        // Border properties
        private Border border = null;
        private Paint borderColor = Color.AQUAMARINE;
        private BorderStrokeStyle borderStyle = BorderStrokeStyle.SOLID;
        private CornerRadii borderRadii = CornerRadii.EMPTY;
        private BorderWidths borderWidths = BorderWidths.DEFAULT;

        private Consumer<LocalizedText> titleConsumer = ignored -> {
        };

        /**
         * Sets the title of the form section.
         *
         * @param title the title of the form section
         * @return this builder
         * @implNote The title is the key of the localized text that will be displayed at the top of the form section.
         */
        public Builder title(@NotNull String title) {
            this.title = title;
            return this;
        }

        /**
         * Appends a form component to the form section.
         *
         * @param component the form component to append
         * @return this builder
         */
        public Builder appendComponent(@NotNull FormComponent<?, ?, ?, ?> component) {
            this.fields.add(component);
            return this;
        }

        /**
         * Sets the spacing between the components in the form section.
         *
         * @param spacing the spacing between the components
         * @return this builder
         */
        public Builder spacing(double spacing) {
            this.spacing = spacing;
            return this;
        }

        /**
         * Sets the padding of the form section.
         *
         * @param padding the padding of the form section
         * @return this builder
         */
        public Builder padding(@NotNull Insets padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Sets the padding of the form section.
         *
         * @param top    the top padding
         * @param right  the right padding
         * @param bottom the bottom padding
         * @param left   the left padding
         * @return this builder
         */
        public Builder padding(double top, double right, double bottom, double left) {
            this.padding = new Insets(top, right, bottom, left);
            return this;
        }

        /**
         * Sets the padding of the form section.
         *
         * @param padding the padding of the form section
         * @return this builder
         */
        public Builder padding(double padding) {
            this.padding = new Insets(padding);
            return this;
        }

        /**
         * Sets the border of the form section.
         *
         * @param border the border of the form section
         * @return this builder
         */
        public Builder border(@Nullable Border border) {
            this.border = border;
            return this;
        }

        /**
         * Sets the border color of the form section.
         *
         * @param color the border color of the form section
         * @return this builder
         */
        public Builder borderColor(@NotNull Color color) {
            this.borderColor = color;
            return this;
        }

        /**
         * Sets the border style of the form section.
         *
         * @param style the border style of the form section
         * @return this builder
         */
        public Builder borderStyle(@NotNull BorderStrokeStyle style) {
            this.borderStyle = style;
            return this;
        }

        /**
         * Sets the border radii of the form section.
         *
         * @param radii the border radii of the form section
         * @return this builder
         */
        public Builder borderRadii(@NotNull CornerRadii radii) {
            this.borderRadii = radii;
            return this;
        }

        /**
         * Sets the border widths of the form section.
         *
         * @param widths the border widths of the form section
         * @return this builder
         */
        public Builder borderWidths(@NotNull BorderWidths widths) {
            this.borderWidths = widths;
            return this;
        }

        /**
         * Sets the consumer for the title of the form section. This can be used to customize the title of the form
         * section, such as changing the style or adding a tooltip.
         *
         * @param titleConsumer the consumer for the title of the form section
         * @return this builder
         */
        public Builder titleConsumer(@NotNull Consumer<LocalizedText> titleConsumer) {
            this.titleConsumer = titleConsumer;
            return this;
        }

        /**
         * Builds the form section.
         *
         * @return the form section
         */
        public FormSection build() {
            return new FormSection(this);
        }

        /**
         * Builds the form section and adds it to the given form builder.
         *
         * @param formBuilder the form builder to add the form section to
         * @return the form section
         */
        public FormSection build(Form.Builder formBuilder) {
            var formSection = build();
            formBuilder.formSections.add(formSection);
            return formSection;
        }
    }
}
