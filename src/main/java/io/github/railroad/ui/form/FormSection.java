package io.github.railroad.ui.form;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class FormSection {
    @Getter
    private final String titleKey;
    private final List<FormComponent<?, ?, ?, ?>> fields;

    private FormSection(Builder builder) {
        this.titleKey = builder.title;
        this.fields = builder.fields;
    }

    public boolean isValid() {
        return fields.stream().allMatch(FormComponent::isValid);
    }

    public void reset() {
        fields.forEach(FormComponent::reset);
    }

    public void disable(boolean disable) {
        fields.forEach(field -> field.disable(disable));
    }

    public static class Builder {
        private String title;
        private final List<FormComponent<?, ?, ?, ?>> fields = new ArrayList<>();

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder appendComponent(FormComponent<?, ?, ?, ?> component) {
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
