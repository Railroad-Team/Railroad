package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRVBox;
import javafx.scene.Node;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Form {
    private final Consumer<FormData> onSubmit;
    private final FormData formData = new FormData();
    private final List<FormSection> formSections;

    private Form(Builder builder) {
        this.onSubmit = builder.onSubmit;
        this.formSections = builder.formSections;
    }

    public static Builder create() {
        return new Builder();
    }

    public Node createUI() {
        var vbox = new RRVBox(25);
        for (FormSection section : formSections) {
            vbox.getChildren().add(section.createUI());
        }

        return vbox;
    }

    public static class Builder {
        Consumer<FormData> onSubmit;
        List<FormSection> formSections = new ArrayList<>();

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

        public Form build() {
            return new Form(this);
        }
    }
}
