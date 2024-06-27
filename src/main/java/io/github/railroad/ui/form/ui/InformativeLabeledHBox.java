package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Getter
public abstract class InformativeLabeledHBox<T extends Node> extends RRVBox {
    private static final BooleanBinding TRUE_BINDING = new BooleanBinding() {
        @Override
        protected boolean computeValue() {
            return true;
        }
    };

    private final LabeledHBox<T> labeledHBox;
    private final List<InformationLabel> informationLabels = new ArrayList<>();

    public InformativeLabeledHBox(String labelKey, boolean required, Map<String, Object> params) {
        super(5);

        this.labeledHBox = new LabeledHBox<>(labelKey, required, params) {
            @Override
            public T createPrimaryComponent(Map<String, Object> params) {
                return InformativeLabeledHBox.this.createPrimaryComponent(params);
            }
        };

        getChildren().addAll(labeledHBox);
    }

    public void addInformationLabel(@NotNull String informativeText, @NotNull InformationType informationType, @Nullable StringProperty bindTo, Object... args) {
        addInformationLabel(informativeText, t -> TRUE_BINDING, informationType, bindTo, args);
    }

    public void addInformationLabel(@NotNull String informativeText, @NotNull InformationType informationType, Object... args) {
        addInformationLabel(informativeText, informationType, null, args);
    }

    public void addInformationLabel(@NotNull String informativeText, Object... args) {
        addInformationLabel(informativeText, InformationType.INFO, args);
    }

    public void addInformationLabel(@NotNull String informativeText, @Nullable StringProperty bindTo, Object... args) {
        addInformationLabel(informativeText, InformationType.INFO, bindTo, args);
    }

    public void addInformationLabel(@NotNull String informativeText, @NotNull Function<T, BooleanBinding> informativeTextVisibleBinding, @NotNull InformationType informationType, @Nullable StringProperty bindTo, Object... args) {
        var informationLabel = new InformationLabel(informativeText, informationType, args);
        informationLabel.visibleProperty().bind(informativeTextVisibleBinding.apply(labeledHBox.getPrimaryComponent()));
        informationLabels.add(informationLabel);
        getChildren().add(informationLabel);

        if(bindTo != null) {
            bindTo.bindBidirectional(informationLabel.textProperty());
        }
    }

    public void addInformationLabel(@NotNull String informativeText, @NotNull Function<T, BooleanBinding> informativeTextVisibleBinding, @NotNull InformationType informationType, Object... args) {
        addInformationLabel(informativeText, informativeTextVisibleBinding, informationType, null);
    }

    public abstract T createPrimaryComponent(Map<String, Object> params);

    public T getPrimaryComponent() {
        return labeledHBox.getPrimaryComponent();
    }

    @Getter
    public static class InformationLabel extends LocalizedLabel {
        private final InformationType informationType;

        public InformationLabel(String key, InformationType informationType, Object... args) {
            super(key, args);
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
