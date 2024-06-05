package io.github.railroad.ui.form;

import java.util.List;
import java.util.function.Consumer;

public class Form {
    private final Consumer<FormData> onSubmit;
    private final FormData formData = new FormData();
    private final List<FormSection> formSections;

    private Form(Builder builder) {
        this.onSubmit = builder.onSubmit;
        this.formSections = builder.formSections;
    }

    public static class Builder {
        Consumer<FormData> onSubmit;
        List<FormSection> formSections;

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

        public Form build() {
            return new Form(this);
        }
    }
}
