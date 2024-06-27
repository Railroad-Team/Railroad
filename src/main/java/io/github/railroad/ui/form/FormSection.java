package io.github.railroad.ui.form;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedText;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class FormSection {
    @Getter
    private final String titleKey;
    private final List<FormComponent<?, ?, ?, ?>> fields;
    private final double spacing;
    private final Insets padding;
    private final Border border;
    private final Consumer<LocalizedText> titleConsumer;

    private FormSection(Builder builder) {
        this.titleKey = builder.title;
        this.fields = builder.fields;
        this.spacing = builder.spacing;
        this.padding = builder.padding;
        this.border = Objects.requireNonNullElseGet(builder.border,
                () -> new Border(new BorderStroke(builder.borderColor, builder.borderStyle, builder.borderRadii, builder.borderWidths)));
        this.titleConsumer = builder.titleConsumer;
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
        var vbox = new RRVBox(spacing);
        vbox.setBorder(border);
        vbox.setPadding(padding);

        var title = new LocalizedText(titleKey);
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        titleConsumer.accept(title);
        vbox.getChildren().add(title);

        for (FormComponent<?, ?, ?, ?> field : fields) {
            vbox.getChildren().add(field.getComponent());
        }

        return vbox;
    }

    public static class Builder {
        private String title;
        private final List<FormComponent<?, ?, ?, ?>> fields = new ArrayList<>();

        private double spacing = 10;
        private Insets padding = new Insets(10);

        // Border properties
        private Border border = null;
        private Paint borderColor = Color.AQUAMARINE;
        private BorderStrokeStyle borderStyle = BorderStrokeStyle.SOLID;
        private CornerRadii borderRadii = CornerRadii.EMPTY;
        private BorderWidths borderWidths = BorderWidths.DEFAULT;

        private Consumer<LocalizedText> titleConsumer = ignored -> {};

        public Builder title(@NotNull String title) {
            this.title = title;
            return this;
        }

        public Builder appendComponent(@NotNull FormComponent<?, ?, ?, ?> component) {
            this.fields.add(component);
            return this;
        }

        public Builder spacing(double spacing) {
            this.spacing = spacing;
            return this;
        }

        public Builder padding(@NotNull Insets padding) {
            this.padding = padding;
            return this;
        }

        public Builder padding(double top, double right, double bottom, double left) {
            this.padding = new Insets(top, right, bottom, left);
            return this;
        }

        public Builder padding(double padding) {
            this.padding = new Insets(padding);
            return this;
        }

        public Builder border(@Nullable Border border) {
            this.border = border;
            return this;
        }

        public Builder borderColor(@NotNull Color color) {
            this.borderColor = color;
            return this;
        }

        public Builder borderStyle(@NotNull BorderStrokeStyle style) {
            this.borderStyle = style;
            return this;
        }

        public Builder borderRadii(@NotNull CornerRadii radii) {
            this.borderRadii = radii;
            return this;
        }

        public Builder borderWidths(@NotNull BorderWidths widths) {
            this.borderWidths = widths;
            return this;
        }

        public Builder titleConsumer(@NotNull Consumer<LocalizedText> titleConsumer) {
            this.titleConsumer = titleConsumer;
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
