package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A form that contains multiple sections, each with multiple components.
 * <p>
 * The form can be validated and the data can be retrieved.
 */
public class Form {
    private final BiConsumer<Form, FormData> onSubmit;
    private final List<FormSection> formSections;
    private final int spacing;
    private final Insets padding;
    private final @Nullable Button submitButton;
    private final @Nullable Button resetButton;
    private final Pos buttonAlignment;

    private final FormData formData = new FormData();

    /**
     * Creates a new form.
     *
     * @param builder The builder to create the form from.
     * @apiNote This constructor is internal and should not be used directly.
     */
    @ApiStatus.Internal
    private Form(Builder builder) {
        this.onSubmit = builder.onSubmit;
        this.formSections = builder.formSections;
        this.spacing = builder.spacing;
        this.padding = builder.padding;

        this.submitButton = builder.submitButtonFactory != null ? builder.submitButtonFactory.apply(this) : null;
        this.resetButton = builder.resetButtonFactory != null ? builder.resetButtonFactory.apply(this) : null;

        this.buttonAlignment = builder.buttonAlignment != null ? builder.buttonAlignment : Pos.CENTER;
    }

    /**
     * Creates a new form builder.
     *
     * @return The new form builder.
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Creates the UI for the form. This method should be called when you want to display the form in your application.
     *
     * @return The root node of the form.
     * @implNote <strong>This method should be called on the JavaFX application thread.</strong>
     * <p>
     * This method will construct all the fields and all the sections and then add them to a {@link javafx.scene.layout.VBox}.
     * <p>
     * The submit button will be added to the bottom of the VBox.
     * The submit button will call the onSubmit consumer when clicked.
     * <p>
     * The reset button will be added to the bottom of the VBox.
     * The reset button will reset all the fields in the form section.
     * <p>
     * The form section will be bound to the formData object.
     */
    public Node createUI() {
        var vbox = new RRVBox(spacing);
        vbox.setPadding(padding);

        for (FormSection section : formSections) {
            vbox.getChildren().add(section.createUI());
            section.bindFormData(formData);
        }

        var buttonBox = new RRHBox(10);
        buttonBox.setAlignment(buttonAlignment);
        if (submitButton != null) {
            submitButton.setOnAction(event -> onSubmit.accept(this, formData));
            buttonBox.getChildren().add(submitButton);
        }

        if (resetButton != null) {
            resetButton.setOnAction(event -> formSections.forEach(FormSection::reset));
            buttonBox.getChildren().add(resetButton);
        }

        vbox.getChildren().add(buttonBox);

        return vbox;
    }

    /**
     * Validates the form.
     *
     * @return {@code true} if the form is valid, {@code false} otherwise.
     */
    public boolean validate() {
        return formSections.stream().allMatch(FormSection::validate);
    }

    /**
     * Runs the validation on the form.
     */
    public void runValidation() {
        formSections.forEach(FormSection::runValidation);
    }

    /**
     * The builder for the form.
     */
    public static class Builder {
        protected final List<FormSection> formSections = new ArrayList<>();
        private BiConsumer<Form, FormData> onSubmit;
        private int spacing;
        private Insets padding = new Insets(25);
        private @Nullable Function<Form, Button> submitButtonFactory = form -> new Button("Submit");
        private @Nullable Function<Form, Button> resetButtonFactory = form -> new Button("Reset");
        private Pos buttonAlignment = Pos.CENTER;

        /**
         * Sets the consumer that will be called when the form is submitted.
         *
         * @param onSubmit The consumer that will be called when the form is submitted.
         * @return This builder.
         */
        public Builder onSubmit(BiConsumer<Form, FormData> onSubmit) {
            this.onSubmit = onSubmit;
            return this;
        }

        /**
         * Appends a section to the form.
         *
         * @param formSection The section to append.
         * @return This builder.
         * @see #appendSection(FormSection.Builder)
         * @see #appendSection(String, FormComponent...)
         */
        public Builder appendSection(FormSection formSection) {
            formSections.add(formSection);
            return this;
        }

        /**
         * Appends a section to the form.
         *
         * @param formSectionBuilder The builder for the section to append.
         * @return This builder.
         * @see #appendSection(FormSection)
         * @see #appendSection(String, FormComponent...)
         */
        public Builder appendSection(FormSection.Builder formSectionBuilder) {
            formSectionBuilder.build(this);
            return this;
        }

        /**
         * Appends a section to the form.
         *
         * @param title          The title of the section.
         * @param formComponents The components to add to the section.
         * @return This builder.
         * @see #appendSection(FormSection)
         * @see #appendSection(FormSection.Builder)
         */
        public Builder appendSection(String title, FormComponent<?, ?, ?, ?>... formComponents) {
            var builder = new FormSection.Builder().title(title);
            Arrays.stream(formComponents).forEach(builder::appendComponent);
            return appendSection(builder);
        }

        /**
         * Sets the spacing between the sections.
         *
         * @param spacing The spacing between the sections.
         * @return This builder.
         */
        public Builder spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        /**
         * Sets the padding of the form.
         *
         * @param padding The padding of the form.
         * @return This builder.
         */
        public Builder padding(Insets padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Sets the padding of the form.
         *
         * @param top    The top padding.
         * @param right  The right padding.
         * @param bottom The bottom padding.
         * @param left   The left padding.
         * @return This builder.
         */
        public Builder padding(int top, int right, int bottom, int left) {
            return padding(new Insets(top, right, bottom, left));
        }

        /**
         * Sets the padding of the form.
         *
         * @param padding The padding of the form.
         * @return This builder.
         */
        public Builder padding(int padding) {
            return padding(padding, padding, padding, padding);
        }

        /**
         * Sets the factory for the submit button.
         *
         * @param submitButtonFactory The factory for the submit button.
         * @return This builder.
         */
        public Builder submitButtonFactory(@Nullable Function<Form, Button> submitButtonFactory) {
            this.submitButtonFactory = submitButtonFactory;
            return this;
        }

        /**
         * Sets the factory for the reset button.
         *
         * @param resetButtonFactory The factory for the reset button.
         * @return This builder.
         */
        public Builder resetButtonFactory(@Nullable Function<Form, Button> resetButtonFactory) {
            this.resetButtonFactory = resetButtonFactory;
            return this;
        }

        /**
         * Disables the submit button.
         *
         * @return This builder.
         */
        public Builder disableSubmitButton() {
            return submitButtonFactory(null);
        }

        /**
         * Disables the reset button.
         *
         * @return This builder.
         */
        public Builder disableResetButton() {
            return resetButtonFactory(null);
        }

        /**
         * Sets the alignment of the buttons.
         *
         * @param buttonAlignment The alignment of the buttons.
         * @return This builder.
         */
        public Builder buttonAlignment(@NotNull Pos buttonAlignment) {
            this.buttonAlignment = buttonAlignment;
            return this;
        }

        /**
         * Builds the form.
         *
         * @return The form.
         */
        public Form build() {
            return new Form(this);
        }
    }
}
