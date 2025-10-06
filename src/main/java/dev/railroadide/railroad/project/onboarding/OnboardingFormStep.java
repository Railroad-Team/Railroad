package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.core.form.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generic onboarding step backed by a {@link Form}.
 */
public final class OnboardingFormStep implements OnboardingStep {
    private final String id;
    private final String title;
    private final String description;
    private final OnboardingSection section;
    private final ReadOnlyBooleanProperty validProperty;
    private final Consumer<OnboardingContext> onEnter;
    private final Consumer<OnboardingContext> onExit;
    private final Consumer<OnboardingContext> onDispose;
    private final Function<OnboardingContext, CompletableFuture<Void>> beforeNext;

    private OnboardingFormStep(Builder builder, OnboardingSection section, ReadOnlyBooleanProperty validProperty) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.description = Objects.requireNonNull(builder.description, "description");
        this.section = Objects.requireNonNull(section, "section");
        this.validProperty = Objects.requireNonNull(validProperty, "validProperty");
        this.onEnter = Objects.requireNonNull(builder.onEnter, "onEnter");
        this.onExit = Objects.requireNonNull(builder.onExit, "onExit");
        this.onDispose = Objects.requireNonNull(builder.onDispose, "onDispose");
        this.beforeNext = Objects.requireNonNull(builder.beforeNext, "beforeNext");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public OnboardingSection section() {
        return section;
    }

    @Override
    public ReadOnlyBooleanProperty validProperty() {
        return validProperty;
    }

    @Override
    public void onEnter(OnboardingContext ctx) {
        onEnter.accept(ctx);
    }

    @Override
    public void onExit(OnboardingContext ctx) {
        onExit.accept(ctx);
    }

    @Override
    public void dispose(OnboardingContext ctx) {
        onDispose.accept(ctx);
    }

    @Override
    public CompletableFuture<Void> beforeNext(OnboardingContext ctx) {
        return beforeNext.apply(ctx);
    }

    public static final class Builder {
        private String id;
        private String title;
        private String description;
        private ReadOnlyBooleanProperty validProperty = new SimpleBooleanProperty(false);
        private Consumer<OnboardingContext> onEnter = ctx -> {
        };
        private Consumer<OnboardingContext> onExit = ctx -> {
        };
        private Consumer<OnboardingContext> onDispose = ctx -> {
        };
        private Function<OnboardingContext, CompletableFuture<Void>> beforeNext = ctx -> CompletableFuture.completedFuture(null);
        private final List<FormSection> formSections = new ArrayList<>();
        private final List<Consumer<FormSection.Builder>> sectionConfigurators = new ArrayList<>();
        private final List<Consumer<Form.Builder>> formCustomizers = new ArrayList<>();
        private final List<FormComponent<?, ?, ?, ?>> trackedComponents = new ArrayList<>();
        private final Map<String, FormComponent<?, ?, ?, ?>> componentsByDataKey = new HashMap<>();
        private final Map<String, DataMapping> dataMappings = new HashMap<>();
        private int spacing = 15;
        private Insets padding = new Insets(10);
        private boolean disableSubmitButton = true;
        private boolean disableResetButton = true;
        private Form form;
        private OnboardingSection section;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder validProperty(ReadOnlyBooleanProperty validProperty) {
            this.validProperty = validProperty;
            return this;
        }

        public Builder form(Form form) {
            this.form = form;
            this.section = null;
            return this;
        }

        public Builder form(Consumer<Form.Builder> formConfigurer) {
            this.formCustomizers.add(formConfigurer);
            return this;
        }

        public Builder section(OnboardingSection section) {
            this.section = section;
            this.form = null;
            return this;
        }

        public Builder appendSection(FormSection formSection) {
            this.formSections.add(formSection);
            return this;
        }

        public Builder appendSection(Consumer<FormSection.Builder> sectionConfigurer) {
            this.sectionConfigurators.add(sectionConfigurer);
            return this;
        }

        public Builder appendSection(String titleKey, FormComponent<?, ?, ?, ?>... components) {
            return appendSection(titleKey, builder -> {
            }, components);
        }

        public Builder appendSection(String titleKey, Consumer<FormSection.Builder> customizer, FormComponent<?, ?, ?, ?>... components) {
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(customizer, "customizer");

            return appendSection(sectionBuilder -> {
                sectionBuilder.title(titleKey).borderColor(Color.DARKGRAY);
                customizer.accept(sectionBuilder);
                Arrays.stream(components).forEach(component -> {
                    sectionBuilder.appendComponent(component);
                    trackComponent(component);
                });
            });
        }

        public Builder appendSection(String titleKey, FormComponentBuilder<?, ?, ?, ?>... componentBuilders) {
            return appendSection(titleKey, builder -> {
            }, componentBuilders);
        }

        public Builder appendSection(String titleKey, Consumer<FormSection.Builder> customizer, FormComponentBuilder<?, ?, ?, ?>... componentBuilders) {
            Objects.requireNonNull(componentBuilders, "componentBuilders");

            return appendSection(titleKey, customizer,
                Arrays.stream(componentBuilders)
                    .map(this::buildAndTrack)
                    .toArray(FormComponent[]::new));
        }

        public Builder mapData(String dataKey) {
            return mapData(dataKey, dataKey, Function.identity());
        }

        public Builder mapData(String dataKey, Function<Object, Object> transformer) {
            return mapData(dataKey, dataKey, transformer);
        }

        public Builder mapData(String dataKey, String contextKey) {
            return mapData(dataKey, contextKey, Function.identity());
        }

        public Builder mapData(String dataKey, String contextKey, Function<Object, Object> transformer) {
            Objects.requireNonNull(dataKey, "dataKey");
            Objects.requireNonNull(contextKey, "contextKey");

            dataMappings.put(dataKey, new DataMapping(contextKey, transformer != null ? transformer : Function.identity()));
            return this;
        }

        public Builder spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        public Builder padding(Insets padding) {
            this.padding = padding;
            return this;
        }

        public Builder padding(int padding) {
            return padding(new Insets(padding));
        }

        public Builder padding(int top, int right, int bottom, int left) {
            return padding(new Insets(top, right, bottom, left));
        }

        public Builder disableSubmitButton() {
            this.disableSubmitButton = true;
            return this;
        }

        public Builder enableSubmitButton() {
            this.disableSubmitButton = false;
            return this;
        }

        public Builder disableResetButton() {
            this.disableResetButton = true;
            return this;
        }

        public Builder enableResetButton() {
            this.disableResetButton = false;
            return this;
        }

        public Builder customizeForm(Consumer<Form.Builder> customizer) {
            this.formCustomizers.add(customizer);
            return this;
        }

        public Builder onEnter(Consumer<OnboardingContext> onEnter) {
            this.onEnter = onEnter;
            return this;
        }

        public Builder onExit(Consumer<OnboardingContext> onExit) {
            this.onExit = onExit;
            return this;
        }

        public Builder onDispose(Consumer<OnboardingContext> onDispose) {
            this.onDispose = onDispose;
            return this;
        }

        public Builder beforeNext(Function<OnboardingContext, CompletableFuture<Void>> beforeNext) {
            this.beforeNext = beforeNext;
            return this;
        }

        public OnboardingFormStep build() {
            BooleanProperty managedValid = null;
            ReadOnlyBooleanProperty valid;
            if (this.validProperty != null) {
                valid = this.validProperty;
                if (this.validProperty instanceof BooleanProperty booleanProperty) {
                    managedValid = booleanProperty;
                }
            } else {
                var booleanProperty = new SimpleBooleanProperty(true);
                valid = booleanProperty;
                managedValid = booleanProperty;
            }

            OnboardingSection section = this.section;
            if (section == null) {
                Form resolvedForm = this.form;
                if (resolvedForm == null) {
                    Form.Builder builder = Form.create();
                    builder.spacing(spacing);
                    builder.padding(padding);

                    if (disableSubmitButton)
                        builder.disableSubmitButton();
                    if (disableResetButton)
                        builder.disableResetButton();

                    formSections.forEach(builder::appendSection);

                    for (Consumer<FormSection.Builder> configurator : sectionConfigurators) {
                        var sectionBuilder = new FormSection.Builder();
                        configurator.accept(sectionBuilder);
                        sectionBuilder.build(builder);
                    }

                    formCustomizers.forEach(customizer -> customizer.accept(builder));

                    if (formSections.isEmpty() && sectionConfigurators.isEmpty() && formCustomizers.isEmpty())
                        throw new IllegalStateException("No form configuration provided");

                    resolvedForm = builder.build();
                }

                section = new FormOnboardingSection(resolvedForm);
            }

            if (!dataMappings.isEmpty()) {
                Consumer<OnboardingContext> existingOnExit = this.onExit;
                this.onExit = ctx -> {
                    saveDataToContext(ctx);
                    existingOnExit.accept(ctx);
                };
            }

            if (managedValid != null && !trackedComponents.isEmpty()) {
                setupAutoValidation(managedValid);
            }

            return new OnboardingFormStep(this, section, valid);
        }

        private FormComponent<?, ?, ?, ?> buildAndTrack(FormComponentBuilder<?, ?, ?, ?> builder) {
            FormComponent<?, ?, ?, ?> component = builder.build();
            trackComponent(component);
            return component;
        }

        private void trackComponent(FormComponent<?, ?, ?, ?> component) {
            if (component == null)
                return;

            if (!componentsByDataKey.containsKey(component.dataKey())) {
                trackedComponents.add(component);
                componentsByDataKey.put(component.dataKey(), component);
            }

            dataMappings.putIfAbsent(component.dataKey(), new DataMapping(component.dataKey(), Function.identity()));
        }

        private void setupAutoValidation(BooleanProperty validProperty) {
            Runnable recompute = () -> validProperty.set(trackedComponents.stream()
                .allMatch(component -> component.validate().status() != ValidationResult.Status.ERROR));

            trackedComponents.forEach(component -> {
                component.componentProperty().addUpdateListener(recompute);
                attachNodeListener(component, recompute);
            });

            recompute.run();
        }

        private void attachNodeListener(FormComponent<?, ?, ?, ?> component, Runnable recompute) {
            ObservableValue<?> validationNode = component.getValidationNode();
            attachToNode(validationNode.getValue(), recompute);
            validationNode.addListener((observable, oldValue, newValue) -> {
                attachToNode(newValue, recompute);
                recompute.run();
            });
        }

        private void attachToNode(Object node, Runnable recompute) {
            if (!(node instanceof Node fxNode))
                return;

            switch (fxNode) {
                case TextInputControl textInput ->
                    textInput.textProperty().addListener((obs, oldVal, newVal) -> recompute.run());
                case ComboBox<?> comboBox ->
                    comboBox.valueProperty().addListener((obs, oldVal, newVal) -> recompute.run());
                case CheckBox checkBox ->
                    checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> recompute.run());
                default -> fxNode.focusedProperty().addListener((obs, oldVal, newVal) -> recompute.run());
            }
        }

        private void saveDataToContext(OnboardingContext ctx) {
            dataMappings.forEach((dataKey, mapping) -> {
                FormComponent<?, ?, ?, ?> component = componentsByDataKey.get(dataKey);
                if (component == null)
                    return;

                Object rawValue = extractValue(component);
                Object transformed = mapping.transformer().apply(rawValue);
                ctx.put(mapping.contextKey(), transformed);
            });
        }

        private Object extractValue(FormComponent<?, ?, ?, ?> component) {
            Object node = component.getValidationNode().getValue();
            if (node instanceof TextInputControl textInput)
                return textInput.getText();
            if (node instanceof ComboBox<?> comboBox)
                return comboBox.getValue();
            if (node instanceof CheckBox checkBox)
                return checkBox.isSelected();
            if (node instanceof Node)
                return node;
            return null;
        }

        private record DataMapping(String contextKey, Function<Object, Object> transformer) {
        }
    }
}
