package io.github.railroad.ui.form;

import java.util.ArrayList;
import java.util.List;

public class FormSection {
    private final String title;
    private final List<FormComponent<?, ?>> fields;

    private FormSection(Builder builder) {
        this.title = builder.title;
        this.fields = builder.fields;
    }

    public static class Builder {
        private String title;
        private final List<FormComponent<?, ?>> fields = new ArrayList<>();

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder appendComponent(FormComponent<?, ?> component) {
            this.fields.add(component);
            return this;
        }

        public FormSection build() {
            return new FormSection(this);
        }

        public FormSection build(Form.Builder formBuilder) {
            var formSection = build();
            formBuilder.formSections.add(formSection);
            return formSection;
        }
    }
}
