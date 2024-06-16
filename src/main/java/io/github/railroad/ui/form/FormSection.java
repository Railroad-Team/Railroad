package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedText;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
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

    public static Builder create(String titleKey) {
        return new Builder().title(titleKey);
    }

    public void reset() {
        fields.forEach(FormComponent::reset);
    }

    public void disable(boolean disable) {
        fields.forEach(field -> field.disable(disable));
    }

    public Node createUI() {
        var vbox = new RRVBox(10);
        vbox.setBorder(new Border(new BorderStroke(Color.AQUAMARINE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
        vbox.setPadding(new Insets(10));

        var title = new LocalizedText(titleKey);
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        vbox.getChildren().add(title);

        for (FormComponent<?, ?, ?, ?> field : fields) {
            vbox.getChildren().add(field.getComponent());
        }

        return vbox;
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
