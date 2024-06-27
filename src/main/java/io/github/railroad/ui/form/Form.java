package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class Form {
    private final Consumer<FormData> onSubmit;
    private final List<FormSection> formSections;
    private final int spacing;
    private final Insets padding;

    private Form(Builder builder) {
        this.onSubmit = builder.onSubmit;
        this.formSections = builder.formSections;
        this.spacing = builder.spacing;
        this.padding = builder.padding;
    }

    public static Builder create() {
        return new Builder();
    }

    public Node createUI() {
        var vbox = new RRVBox(spacing);
        vbox.setPadding(padding);

        for (FormSection section : formSections) {
            vbox.getChildren().add(section.createUI());
        }

        return vbox;
    }

    public static class Builder {
        protected final List<FormSection> formSections = new ArrayList<>();
        private Consumer<FormData> onSubmit;
        private int spacing;
        private Insets padding = new Insets(25);

        public Builder onSubmit(Consumer<FormData> onSubmit) {
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

        public Form build() {
            return new Form(this);
        }
    }
}
