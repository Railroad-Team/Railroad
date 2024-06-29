package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Form {
    private final BiConsumer<Form, FormData> onSubmit;
    private final List<FormSection> formSections;
    private final int spacing;
    private final Insets padding;
    private final @Nullable Button submitButton;
    private final @Nullable Button resetButton;

    private final FormData formData = new FormData();

    private Form(Builder builder) {
        this.onSubmit = builder.onSubmit;
        this.formSections = builder.formSections;
        this.spacing = builder.spacing;
        this.padding = builder.padding;

        this.submitButton = builder.submitButtonFactory != null ? builder.submitButtonFactory.apply(this) : null;
        this.resetButton = builder.resetButtonFactory != null ? builder.resetButtonFactory.apply(this) : null;
    }

    public static Builder create() {
        return new Builder();
    }

    public Node createUI() {
        var vbox = new RRVBox(spacing);
        vbox.setPadding(padding);

        for (FormSection section : formSections) {
            vbox.getChildren().add(section.createUI());
            section.bindFormData(formData);
        }

        var buttonBox = new RRHBox(10);
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

    public boolean validate() {
        return formSections.stream().allMatch(FormSection::validate);
    }

    public void runValidation() {
        formSections.forEach(FormSection::runValidation);
    }

    public static class Builder {
        protected final List<FormSection> formSections = new ArrayList<>();
        private BiConsumer<Form, FormData> onSubmit;
        private int spacing;
        private Insets padding = new Insets(25);
        private @Nullable Function<Form, Button> submitButtonFactory = form -> new Button("Submit");
        private @Nullable Function<Form, Button> resetButtonFactory = form -> new Button("Reset");

        public Builder onSubmit(BiConsumer<Form, FormData> onSubmit) {
            this.onSubmit = onSubmit;
            return this;
        }

        public Builder appendSection(FormSection formSection) {
            formSections.add(formSection);
            return this;
        }

        public Builder appendSection(FormSection.Builder formSectionBuilder) {
            formSectionBuilder.build(this);
            return this;
        }

        public Builder appendSection(String title, FormComponent<?, ?, ?, ?>... formComponents) {
            var builder = new FormSection.Builder().title(title);
            Arrays.stream(formComponents).forEach(builder::appendComponent);
            return appendSection(builder);
        }

        public Builder spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        public Builder padding(Insets padding) {
            this.padding = padding;
            return this;
        }

        public Builder padding(int top, int right, int bottom, int left) {
            return padding(new Insets(top, right, bottom, left));
        }

        public Builder padding(int padding) {
            return padding(padding, padding, padding, padding);
        }

        public Builder submitButtonFactory(@Nullable Function<Form, Button> submitButtonFactory) {
            this.submitButtonFactory = submitButtonFactory;
            return this;
        }

        public Builder resetButtonFactory(@Nullable Function<Form, Button> resetButtonFactory) {
            this.resetButtonFactory = resetButtonFactory;
            return this;
        }

        public Builder disableSubmitButton() {
            return submitButtonFactory(null);
        }

        public Builder disableResetButton() {
            return resetButtonFactory(null);
        }

        public Form build() {
            return new Form(this);
        }
    }
}
