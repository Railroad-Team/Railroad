package dev.railroadide.core.form.ui;

import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A labeled {@link RRHBox} that can have information labels attached to it.
 *
 * @param <T> The type of the primary component of the labeled {@link RRHBox}.
 */
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

    /**
     * Creates a new {@link InformativeLabeledHBox} with the given label key and required status.
     *
     * @param labelKey The key of the label.
     * @param required Whether the component is required.
     */
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

    /**
     * Adds an information label to the {@link InformativeLabeledHBox}.
     *
     * @param informativeText The text of the information label.
     * @param informationType The type of the information label.
     * @param bindTo          The property to bind the information label to.
     * @param args            The arguments to format the text with.
     * @see String#format(String, Object...)
     * @see InformationType
     */
    public void addInformationLabel(@NotNull String informativeText, @NotNull InformationType informationType, @Nullable StringProperty bindTo, Object... args) {
        addInformationLabel(informativeText, t -> TRUE_BINDING, informationType, bindTo, args);
    }

    /**
     * Adds an information label to the {@link InformativeLabeledHBox}.
     *
     * @param informativeText The text of the information label.
     * @param informationType The type of the information label.
     * @param args            The arguments to format the text with.
     * @see String#format(String, Object...)
     * @see InformationType
     */
    public void addInformationLabel(@NotNull String informativeText, @NotNull InformationType informationType, Object... args) {
        addInformationLabel(informativeText, informationType, null, args);
    }

    /**
     * Adds an information label to the {@link InformativeLabeledHBox}.
     *
     * @param informativeText The text of the information label.
     * @param args            The arguments to format the text with.
     * @see String#format(String, Object...)
     * @see InformationType
     */
    public void addInformationLabel(@NotNull String informativeText, Object... args) {
        addInformationLabel(informativeText, InformationType.INFO, args);
    }

    /**
     * Adds an information label to the {@link InformativeLabeledHBox}.
     *
     * @param informativeText The text of the information label.
     * @param bindTo          The property to bind the information label to.
     * @param args            The arguments to format the text with.
     * @see String#format(String, Object...)
     * @see InformationType
     */
    public void addInformationLabel(@NotNull String informativeText, @Nullable StringProperty bindTo, Object... args) {
        addInformationLabel(informativeText, InformationType.INFO, bindTo, args);
    }

    /**
     * Adds an information label to the {@link InformativeLabeledHBox}.
     *
     * @param informativeText               The text of the information label.
     * @param informativeTextVisibleBinding The binding that determines whether the information label should be visible.
     * @param informationType               The type of the information label.
     * @param args                          The arguments to format the text with.
     * @see String#format(String, Object...)
     * @see InformationType
     */
    public void addInformationLabel(@NotNull String informativeText, @NotNull Function<T, BooleanBinding> informativeTextVisibleBinding, @NotNull InformationType informationType, Object... args) {
        addInformationLabel(informativeText, informativeTextVisibleBinding, informationType, null, args);
    }

    /**
     * Adds an information label to the {@link InformativeLabeledHBox}.
     *
     * @param informativeText               The text of the information label.
     * @param informativeTextVisibleBinding The binding that determines whether the information label should be visible.
     * @param informationType               The type of the information label.
     * @param bindTo                        The property to bind the information label to.
     * @param args                          The arguments to format the text with.
     * @see String#format(String, Object...)
     * @see InformationType
     */
    public void addInformationLabel(@NotNull String informativeText, @NotNull Function<T, BooleanBinding> informativeTextVisibleBinding, @NotNull InformationType informationType, @Nullable StringProperty bindTo, Object... args) {
        var informationLabel = new InformationLabel(informativeText, informationType, args);
        informationLabel.visibleProperty().bind(informativeTextVisibleBinding.apply(labeledHBox.getPrimaryComponent()));
        informationLabels.add(informationLabel);
        getChildren().add(informationLabel);

        if (bindTo != null) {
            bindTo.bindBidirectional(informationLabel.textProperty());
        }
    }

    /**
     * Creates the primary component of the {@link InformativeLabeledHBox}.
     *
     * @param params The parameters to create the primary component with.
     * @return The primary component of the {@link InformativeLabeledHBox}.
     */
    public abstract T createPrimaryComponent(Map<String, Object> params);

    /**
     * Gets the primary component of the {@link InformativeLabeledHBox}.
     *
     * @return The primary component of the {@link InformativeLabeledHBox}.
     */
    public T getPrimaryComponent() {
        return labeledHBox.getPrimaryComponent();
    }

    /**
     * The type of the information label.
     */
    public enum InformationType {
        INFO,
        NOTE,
        WARNING,
        ERROR
    }

    /**
     * The information label of the {@link InformativeLabeledHBox}.
     */
    @Getter
    public static class InformationLabel extends LocalizedLabel {
        private final InformationType informationType;

        /**
         * Creates a new {@link InformationLabel} with the given key, type, and arguments.
         *
         * @param key             The key of the label.
         * @param informationType The type of the information label.
         * @param args            The arguments to format the text with.
         * @see String#format(String, Object...)
         * @see InformationType
         */
        public InformationLabel(String key, InformationType informationType, Object... args) {
            super(key, args);
            this.informationType = informationType;

            switch (informationType) {
                case ERROR -> {
                    getStyleClass().addAll("field-error", "rr-info-label", "information-label-bold");
                }
                case WARNING -> {
                    getStyleClass().addAll("field-warning", "rr-info-label");
                }
                case NOTE -> {
                    getStyleClass().addAll("field-info", "rr-info-label");
                    setTextFill(Color.SLATEGRAY);
                }
                case INFO -> {
                    getStyleClass().addAll("field-info", "rr-info-label");
                }
            }

            var fontIcon = createFontIcon(informationType);
            setGraphic(fontIcon);
        }
    }

    private static @NotNull FontIcon createFontIcon(InformationType informationType) {
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

        return fontIcon;
    }
}
