package dev.railroadide.railroad.project.onboarding.step;

import dev.railroadide.core.form.*;
import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.ui.FormOnboardingSection;
import dev.railroadide.railroad.project.onboarding.ui.OnboardingSection;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Generic onboarding step backed by a {@link Form}.
 */
public final class OnboardingFormStep implements OnboardingStep {
    private final String id;
    private final String title;
    private final String description;

    private final Supplier<OnboardingSection> section;
    private OnboardingSection cachedSection;

    private final ReadOnlyBooleanProperty validProperty;
    private final Consumer<OnboardingContext> onEnter;
    private final Consumer<OnboardingContext> onEnterAfterUI;
    private final Consumer<OnboardingContext> onExit;
    private final Consumer<OnboardingContext> onDispose;
    private final Function<OnboardingContext, CompletableFuture<Void>> beforeNext;

    private OnboardingFormStep(Builder builder, Supplier<OnboardingSection> section, ReadOnlyBooleanProperty validProperty) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.description = Objects.requireNonNull(builder.description, "description");
        this.section = Objects.requireNonNull(section, "section");
        this.validProperty = Objects.requireNonNull(validProperty, "validProperty");
        this.onEnter = Objects.requireNonNull(builder.onEnter, "onEnter");
        this.onEnterAfterUI = Objects.requireNonNull(builder.onEnterAfterUI, "onEnterAfterUI");
        this.onExit = Objects.requireNonNull(builder.onExit, "onExit");
        this.onDispose = Objects.requireNonNull(builder.onDispose, "onDispose");
        this.beforeNext = Objects.requireNonNull(builder.beforeNext, "beforeNext");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ComponentSpec component(FormComponentBuilder<?, ?, ?, ?> builder) {
        return component(builder, builder != null ? builder.dataKey() : null, Function.identity(), Function.identity());
    }

    public static ComponentSpec component(FormComponentBuilder<?, ?, ?, ?> builder, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {
        return component(builder, builder != null ? builder.dataKey() : null, transformer, reverseTransformer);
    }

    public static ComponentSpec component(FormComponentBuilder<?, ?, ?, ?> builder, String contextKey) {
        return component(builder, contextKey, Function.identity(), Function.identity());
    }

    public static ComponentSpec component(FormComponentBuilder<?, ?, ?, ?> builder, String contextKey, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {
        return new ComponentSpec(builder, null, contextKey, transformer, reverseTransformer);
    }

    public static ComponentSpec component(FormComponent<?, ?, ?, ?> component) {
        return component(component, component != null ? component.dataKey() : null, Function.identity(), Function.identity());
    }

    public static ComponentSpec component(FormComponent<?, ?, ?, ?> component, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {
        return component(component, component != null ? component.dataKey() : null, transformer, reverseTransformer);
    }

    public static ComponentSpec component(FormComponent<?, ?, ?, ?> component, String contextKey) {
        return component(component, contextKey, Function.identity(), Function.identity());
    }

    public static ComponentSpec component(FormComponent<?, ?, ?, ?> component, String contextKey, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {
        return new ComponentSpec(null, component, contextKey, transformer, reverseTransformer);
    }

    public record ComponentSpec(FormComponentBuilder<?, ?, ?, ?> builder, FormComponent<?, ?, ?, ?> component,
                                String contextKey, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {
        public ComponentSpec(FormComponentBuilder<?, ?, ?, ?> builder, FormComponent<?, ?, ?, ?> component, String contextKey, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {
            if (builder == null && component == null)
                throw new IllegalArgumentException("Component specification must provide a builder or component instance");

            this.builder = builder;
            this.component = component;
            this.contextKey = contextKey;
            this.transformer = transformer != null ? transformer : Function.identity();
            this.reverseTransformer = reverseTransformer != null ? reverseTransformer : Function.identity();
        }
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
        if (cachedSection == null) {
            cachedSection = section.get();
        }

        return cachedSection;
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
    public void onEnterAfterUI(OnboardingContext ctx) {
        onEnterAfterUI.accept(ctx);
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
        private Consumer<OnboardingContext> onEnterAfterUI = ctx -> {
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
        private Supplier<OnboardingSection> section;

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

        public Builder section(Supplier<OnboardingSection> section) {
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
            ComponentSpec[] specs = Arrays.stream(components)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, builder -> {
            }, specs);
        }

        public Builder appendSection(String titleKey, Consumer<FormSection.Builder> customizer, FormComponent<?, ?, ?, ?>... components) {
            ComponentSpec[] specs = Arrays.stream(components)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, customizer, specs);
        }

        public Builder appendSection(String titleKey, FormComponentBuilder<?, ?, ?, ?>... componentBuilders) {
            ComponentSpec[] specs = Arrays.stream(componentBuilders)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, builder -> {
            }, specs);
        }

        public Builder appendSection(String titleKey, Consumer<FormSection.Builder> customizer, FormComponentBuilder<?, ?, ?, ?>... componentBuilders) {
            ComponentSpec[] specs = Arrays.stream(componentBuilders)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, customizer, specs);
        }

        public Builder appendSection(String titleKey, ComponentSpec... components) {
            return appendSection(titleKey, builder -> {
            }, components);
        }

        public Builder appendSection(String titleKey, Consumer<FormSection.Builder> customizer, ComponentSpec... components) {
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(customizer, "customizer");
            Objects.requireNonNull(components, "components");

            return appendSection(sectionBuilder -> {
                sectionBuilder.title(titleKey).borderColor(Color.DARKGRAY);
                customizer.accept(sectionBuilder);
                Arrays.stream(components).forEach(spec -> addComponent(sectionBuilder, spec));
            });
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

        public Builder onEnterAfterUI(Consumer<OnboardingContext> onEnterAfterUI) {
            this.onEnterAfterUI = onEnterAfterUI;
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
            AtomicReference<BooleanProperty> managedValid = new AtomicReference<>(null);
            ReadOnlyBooleanProperty valid;
            if (this.validProperty != null) {
                valid = this.validProperty;
                if (this.validProperty instanceof BooleanProperty booleanProperty) {
                    managedValid.set(booleanProperty);
                }
            } else {
                var booleanProperty = new SimpleBooleanProperty(true);
                valid = booleanProperty;
                managedValid.set(booleanProperty);
            }

            Consumer<OnboardingContext> existingOnEnterAfterUI = this.onEnterAfterUI;
            this.onEnterAfterUI = ctx -> {
                loadDataFromContext(ctx);
                existingOnEnterAfterUI.accept(ctx);
            };

            Consumer<OnboardingContext> existingOnExit = this.onExit;
            this.onExit = ctx -> {
                saveDataToContext(ctx);
                existingOnExit.accept(ctx);
            };

            Supplier<OnboardingSection> sectionSupplier = () -> {
                OnboardingSection section = getOrDefaultSection().get();

                if (managedValid.get() != null && !trackedComponents.isEmpty()) {
                    setupAutoValidation(managedValid.get());
                }

                return section;
            };

            return new OnboardingFormStep(this, sectionSupplier, valid);
        }

        private @NotNull Supplier<OnboardingSection> getOrDefaultSection() {
            Supplier<OnboardingSection> section = this.section;
            if (section == null) {
                section = () -> {
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

                    return new FormOnboardingSection(resolvedForm);
                };
            }

            return section;
        }

        private void trackComponent(FormComponent<?, ?, ?, ?> component) {
            if (component == null)
                return;

            if (!componentsByDataKey.containsKey(component.dataKey()))
                trackedComponents.add(component);

            componentsByDataKey.put(component.dataKey(), component);
        }

        private void addComponent(FormSection.Builder sectionBuilder, ComponentSpec spec) {
            FormComponent<?, ?, ?, ?> component = spec.builder() != null ? spec.builder().build() : spec.component();
            if (component == null)
                return;

            sectionBuilder.appendComponent(component);
            trackComponent(component);

            String contextKey = spec.contextKey() != null ? spec.contextKey() : component.dataKey();
            Function<Object, Object> transformer = spec.transformer() != null ? spec.transformer() : Function.identity();
            Function<Object, Object> reverseTransformer = spec.reverseTransformer() != null ? spec.reverseTransformer() : Function.identity();
            dataMappings.put(component.dataKey(), new DataMapping(contextKey, transformer, reverseTransformer));
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

        private void loadDataFromContext(OnboardingContext ctx) {
            dataMappings.forEach((dataKey, mapping) -> {
                String contextKey = mapping.contextKey();
                if(!ctx.needsRefresh(contextKey))
                    return;

                FormComponent<?, ?, ?, ?> component = componentsByDataKey.get(dataKey);
                if (component == null)
                    return;

                Object value = ctx.get(contextKey);
                if (value != null) {
                    Object transformed = mapping.reverseTransformer().apply(value);
                    component.getComponent().setValue(transformed);
                }
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

        private record DataMapping(String contextKey, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer) {}
    }
}
