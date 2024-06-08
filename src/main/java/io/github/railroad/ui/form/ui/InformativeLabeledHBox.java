package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Getter
public abstract class InformativeLabeledHBox<T extends Node> extends RRVBox {
    private final LabeledHBox<T> labeledHBox;
    private final List<InformationLabel> informationLabels = new ArrayList<>();

    public InformativeLabeledHBox(String labelKey, boolean required) {
        super(10);

        this.labeledHBox = new LabeledHBox<>(labelKey, required) {
            @Override
            public T createPrimaryComponent() {
                return InformativeLabeledHBox.this.createPrimaryComponent();
            }
        };

        getChildren().addAll(labeledHBox);
    }

    public void addInformationLabel(@NotNull String informativeText, @NotNull Function<T, BooleanBinding> informativeTextVisibleBinding, @NotNull InformationType informationType, @Nullable StringProperty bindTo) {
        var informationLabel = new InformationLabel(informativeText, informationType);
        informationLabel.visibleProperty().bind(informativeTextVisibleBinding.apply(labeledHBox.getPrimaryComponent()));
        informationLabels.add(informationLabel);
        getChildren().add(informationLabel);

        if(bindTo != null) {
            bindTo.bind(informationLabel.textProperty());
        }
    }

    public void addInformationLabel(@NotNull String informativeText, @NotNull Function<T, BooleanBinding> informativeTextVisibleBinding, @NotNull InformationType informationType) {
        addInformationLabel(informativeText, informativeTextVisibleBinding, informationType, null);
    }

    public abstract T createPrimaryComponent();

    public T getPrimaryComponent() {
        return labeledHBox.getPrimaryComponent();
    }

    @Getter
    public static class InformationLabel extends LocalizedLabel {
        private final InformationType informationType;

        public InformationLabel(String key, InformationType informationType) {
            super(key);
            this.informationType = informationType;
            if(informationType == InformationType.ERROR) {
                setStyle("-fx-font-weight: bold;");
            }

            if(informationType == InformationType.NOTE) {
                setTextFill(Color.SLATEGRAY);
            }

            var fontIcon = new FontIcon(switch (informationType) {
                case INFO -> FontAwesomeSolid.INFO_CIRCLE;
                case NOTE -> FontAwesomeSolid.LIGHTBULB;
                case WARNING -> FontAwesomeSolid.EXCLAMATION_TRIANGLE;
                case ERROR -> FontAwesomeSolid.TIMES_CIRCLE;
            });
            fontIcon.setIconSize(16);
            fontIcon.setIconColor(switch (informationType) {
                case INFO -> Color.BLUE;
                case NOTE -> Color.GREEN;
                case WARNING -> Color.ORANGE;
                case ERROR -> Color.RED;
            });

            setGraphic(fontIcon);
        }
    }

    public enum InformationType {
        INFO,
        NOTE,
        WARNING,
        ERROR
    }
}
