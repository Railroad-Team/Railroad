package dev.railroadide.railroad.project.onboarding.step;

import dev.railroadide.railroad.form.*;
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

    private OnboardingFormStep(
        Builder builder,
        Supplier<OnboardingSection> section,
        ReadOnlyBooleanProperty validProperty
    ) {
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

    /**
     * Creates a builder for a form-backed onboarding step.
     *
     * @return a new step builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     * Uses the component source's data key as the context key. Values are transferred without conversion.
     *
     * @param builder builder used to create the form component
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(FormComponentBuilder<?, ?, ?, ?> builder) {
        return component(builder, builder != null ? builder.dataKey() : null, Function.identity(), Function.identity(),
            null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     * Uses the component source's data key as the context key.
     *
     * @param builder builder used to create the form component
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponentBuilder<?, ?, ?, ?> builder,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer
    ) {
        return component(builder, builder != null ? builder.dataKey() : null, transformer, reverseTransformer, null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context. Values are transferred
     * without conversion.
     *
     * @param builder builder used to create the form component
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(FormComponentBuilder<?, ?, ?, ?> builder, String contextKey) {
        return component(builder, contextKey, Function.identity(), Function.identity(), null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     *
     * @param builder builder used to create the form component
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponentBuilder<?, ?, ?, ?> builder,
        String contextKey,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer
    ) {
        return new ComponentSpec(builder, null, contextKey, transformer, reverseTransformer, null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     * Uses the component source's data key as the context key. Values are transferred without conversion.
     *
     * @param component existing form component to include
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(FormComponent<?, ?, ?, ?> component) {
        return component(component, component != null ? component.dataKey() : null, Function.identity(),
            Function.identity(), null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     * Uses the component source's data key as the context key.
     *
     * @param component existing form component to include
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponent<?, ?, ?, ?> component,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer
    ) {
        return component(component, component != null ? component.dataKey() : null, transformer, reverseTransformer,
            null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context. Values are transferred
     * without conversion.
     *
     * @param component existing form component to include
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(FormComponent<?, ?, ?, ?> component, String contextKey) {
        return component(component, contextKey, Function.identity(), Function.identity(), null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     *
     * @param component existing form component to include
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponent<?, ?, ?, ?> component,
        String contextKey,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer
    ) {
        return new ComponentSpec(null, component, contextKey, transformer, reverseTransformer, null);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     * Uses the component source's data key as the context key. Values are transferred without conversion.
     *
     * @param builder builder used to create the form component
     * @param customizer callback applied to the component before it is added, or {@code null} for no customization
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponentBuilder<?, ?, ?, ?> builder,
        Consumer<FormComponent<?, ?, ?, ?>> customizer
    ) {
        return component(builder, builder != null ? builder.dataKey() : null, Function.identity(), Function.identity(),
            customizer);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     *
     * @param builder builder used to create the form component
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @param customizer callback applied to the component before it is added, or {@code null} for no customization
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponentBuilder<?, ?, ?, ?> builder,
        String contextKey,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer,
        Consumer<FormComponent<?, ?, ?, ?>> customizer
    ) {
        return new ComponentSpec(builder, null, contextKey, transformer, reverseTransformer, customizer);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     * Uses the component source's data key as the context key. Values are transferred without conversion.
     *
     * @param component existing form component to include
     * @param customizer callback applied to the component before it is added, or {@code null} for no customization
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponent<?, ?, ?, ?> component,
        Consumer<FormComponent<?, ?, ?, ?>> customizer
    ) {
        return component(component, component != null ? component.dataKey() : null, Function.identity(),
            Function.identity(), customizer);
    }

    /**
     * Creates a component specification for synchronizing form data with the onboarding context.
     *
     * @param component existing form component to include
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @param customizer callback applied to the component before it is added, or {@code null} for no customization
     * @return the component and context mapping specification
     * @throws IllegalArgumentException if no component source is supplied
     */
    public static ComponentSpec component(
        FormComponent<?, ?, ?, ?> component,
        String contextKey,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer,
        Consumer<FormComponent<?, ?, ?, ?>> customizer
    ) {
        return new ComponentSpec(null, component, contextKey, transformer, reverseTransformer, customizer);
    }

    /**
     * Describes a component source and the conversions between its value and the onboarding context.
     * When both sources are supplied, the builder takes precedence.
     *
     * @param builder component builder, or {@code null} to use the existing component
     * @param component existing component, required when the builder is {@code null}
     * @param contextKey context key for the component value, or {@code null} to use its data key
     * @param transformer conversion from the component value to the stored context value; {@code null} means identity
     * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
     *     identity
     * @param customizer callback applied to the component before it is added, or {@code null} for no customization
     */
    public record ComponentSpec(
        FormComponentBuilder<?, ?, ?, ?> builder,
        FormComponent<?, ?, ?, ?> component,
        String contextKey,
        Function<Object, Object> transformer,
        Function<Object, Object> reverseTransformer,
        Consumer<FormComponent<?, ?, ?, ?>> customizer
    ) {
        /**
         * Creates a specification, substituting identity functions for null transformations.
         *
         * @param builder component builder, or {@code null} to use the existing component
         * @param component existing component, required when the builder is {@code null}
         * @param contextKey context key for the component value, or {@code null} to use its data key
         * @param transformer conversion from the component value to the stored context value; {@code null} means
         *     identity
         * @param reverseTransformer conversion from the stored context value to the component value; {@code null} means
         *     identity
         * @param customizer callback applied to the component before it is added, or {@code null} for no customization
         * @throws IllegalArgumentException if both the builder and component are null
         */
        public ComponentSpec(
            FormComponentBuilder<?, ?, ?, ?> builder,
            FormComponent<?, ?, ?, ?> component,
            String contextKey,
            Function<Object, Object> transformer,
            Function<Object, Object> reverseTransformer,
            Consumer<FormComponent<?, ?, ?, ?>> customizer
        ) {
            if (builder == null && component == null)
                throw new IllegalArgumentException(
                    "Component specification must provide a builder or component instance");

            this.builder = builder;
            this.component = component;
            this.contextKey = contextKey;
            this.transformer = transformer != null ? transformer : Function.identity();
            this.reverseTransformer = reverseTransformer != null ? reverseTransformer : Function.identity();
            this.customizer = customizer;
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

    /**
     * Configures a form step, its validation, lifecycle callbacks, and context data mappings.
     */
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
        private Function<OnboardingContext, CompletableFuture<Void>> beforeNext = ctx -> CompletableFuture
            .completedFuture(null);
        private final List<FormSection> formSections = new ArrayList<>();
        private final List<Consumer<FormSection.Builder>> sectionConfigurators = new ArrayList<>();
        private final List<Consumer<Form.Builder>> formCustomizers = new ArrayList<>();
        private final List<FormComponent<?, ?, ?, ?>> trackedComponents = new ArrayList<>();
        private final Map<String, FormComponent<?, ?, ?, ?>> componentsByDataKey = new HashMap<>();
        private final Map<String, DataMapping> dataMappings = new HashMap<>();
        private Integer spacing;
        private Insets padding;
        private boolean disableSubmitButton = true;
        private boolean disableResetButton = true;
        private Form form;
        private Supplier<OnboardingSection> section;

        /**
         * Sets the identifier used to navigate to this step.
         *
         * @param id identifier of the step
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the localization key for the step heading.
         *
         * @param title localization key for the step heading
         * @return this builder
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the localization key for the step description.
         *
         * @param description localization key for the step description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the validity property used to enable navigation.
         *
         * @param validProperty property determining whether the user may advance; a writable property is updated by
         *     tracked component validation
         * @return this builder
         */
        public Builder validProperty(ReadOnlyBooleanProperty validProperty) {
            this.validProperty = validProperty;
            return this;
        }

        /**
         * Uses an existing form and clears any custom section factory.
         *
         * @param form form displayed by the onboarding section
         * @return this builder
         */
        public Builder form(Form form) {
            this.form = form;
            this.section = null;
            return this;
        }

        /**
         * Adds a callback to configure the generated form before it is built.
         *
         * @param formConfigurer callback applied to the generated form builder before building the form
         * @return this builder
         */
        public Builder form(Consumer<Form.Builder> formConfigurer) {
            this.formCustomizers.add(formConfigurer);
            return this;
        }

        /**
         * Uses a custom section factory and clears any existing form.
         *
         * @param section factory for a custom onboarding section
         * @return this builder
         */
        public Builder section(Supplier<OnboardingSection> section) {
            this.section = section;
            this.form = null;
            return this;
        }

        /**
         * Appends a section to the generated form.
         *
         * @param formSection existing form section to append
         * @return this builder
         */
        public Builder appendSection(FormSection formSection) {
            this.formSections.add(formSection);
            return this;
        }

        /**
         * Queues a section configuration callback for when the form is created.
         *
         * @param sectionConfigurer callback configuring a section when the form is created
         * @return this builder
         */
        public Builder appendSection(Consumer<FormSection.Builder> sectionConfigurer) {
            this.sectionConfigurators.add(sectionConfigurer);
            return this;
        }

        /**
         * Appends a titled section and tracks its components for validation and context synchronization.
         *
         * @param titleKey localization key for the section title
         * @param components components to append in their supplied order
         * @return this builder
         */
        public Builder appendSection(String titleKey, FormComponent<?, ?, ?, ?>... components) {
            ComponentSpec[] specs = Arrays.stream(components)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, builder -> {
            }, specs);
        }

        /**
         * Appends a titled section and tracks its components for validation and context synchronization.
         *
         * @param titleKey localization key for the section title
         * @param customizer callback applied to the section builder before its components are appended
         * @param components components to append in their supplied order
         * @return this builder
         */
        public Builder appendSection(
            String titleKey,
            Consumer<FormSection.Builder> customizer,
            FormComponent<?, ?, ?, ?>... components
        ) {
            ComponentSpec[] specs = Arrays.stream(components)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, customizer, specs);
        }

        /**
         * Appends a titled section and tracks its components for validation and context synchronization.
         *
         * @param titleKey localization key for the section title
         * @param componentBuilders builders for the components to append in their supplied order
         * @return this builder
         */
        public Builder appendSection(String titleKey, FormComponentBuilder<?, ?, ?, ?>... componentBuilders) {
            ComponentSpec[] specs = Arrays.stream(componentBuilders)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, builder -> {
            }, specs);
        }

        /**
         * Appends a titled section and tracks its components for validation and context synchronization.
         *
         * @param titleKey localization key for the section title
         * @param customizer callback applied to the section builder before its components are appended
         * @param componentBuilders builders for the components to append in their supplied order
         * @return this builder
         */
        public Builder appendSection(
            String titleKey,
            Consumer<FormSection.Builder> customizer,
            FormComponentBuilder<?, ?, ?, ?>... componentBuilders
        ) {
            ComponentSpec[] specs = Arrays.stream(componentBuilders)
                .map(OnboardingFormStep::component)
                .toArray(ComponentSpec[]::new);
            return appendSection(titleKey, customizer, specs);
        }

        /**
         * Appends a titled section and tracks its components for validation and context synchronization.
         *
         * @param titleKey localization key for the section title
         * @param components component specifications, including context keys and value transformations
         * @return this builder
         */
        public Builder appendSection(String titleKey, ComponentSpec... components) {
            return appendSection(titleKey, builder -> {
            }, components);
        }

        /**
         * Appends a titled section and tracks its components for validation and context synchronization.
         *
         * @param titleKey localization key for the section title
         * @param customizer callback applied to the section builder before its components are appended
         * @param components component specifications, including context keys and value transformations
         * @return this builder
         */
        public Builder appendSection(
            String titleKey,
            Consumer<FormSection.Builder> customizer,
            ComponentSpec... components
        ) {
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(customizer, "customizer");
            Objects.requireNonNull(components, "components");

            return appendSection(sectionBuilder -> {
                sectionBuilder.title(titleKey).borderColor(Color.DARKGRAY);
                customizer.accept(sectionBuilder);
                Arrays.stream(components).forEach(spec -> addComponent(sectionBuilder, spec));
            });
        }

        /**
         * Sets the spacing between generated form sections.
         *
         * @param spacing spacing between form sections, in pixels
         * @return this builder
         */
        public Builder spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        /**
         * Sets the padding of the generated form.
         *
         * @param padding padding around the form content, in pixels
         * @return this builder
         */
        public Builder padding(Insets padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Sets the padding of the generated form.
         *
         * @param padding padding around the form content, in pixels
         * @return this builder
         */
        public Builder padding(int padding) {
            return padding(new Insets(padding));
        }

        /**
         * Sets the padding of the generated form.
         *
         * @param top top padding, in pixels
         * @param right right padding, in pixels
         * @param bottom bottom padding, in pixels
         * @param left left padding, in pixels
         * @return this builder
         */
        public Builder padding(int top, int right, int bottom, int left) {
            return padding(new Insets(top, right, bottom, left));
        }

        /**
         * Disables the generated form's submit button, as is the default for onboarding.
         *
         * @return this builder
         */
        public Builder disableSubmitButton() {
            this.disableSubmitButton = true;
            return this;
        }

        /**
         * Allows the generated form's submit button to be shown.
         *
         * @return this builder
         */
        public Builder enableSubmitButton() {
            this.disableSubmitButton = false;
            return this;
        }

        /**
         * Disables the generated form's reset button, as is the default for onboarding.
         *
         * @return this builder
         */
        public Builder disableResetButton() {
            this.disableResetButton = true;
            return this;
        }

        /**
         * Allows the generated form's reset button to be shown.
         *
         * @return this builder
         */
        public Builder enableResetButton() {
            this.disableResetButton = false;
            return this;
        }

        /**
         * Adds a callback to customize the generated form before it is built.
         *
         * @param customizer callback applied to the generated form builder
         * @return this builder
         */
        public Builder customizeForm(Consumer<Form.Builder> customizer) {
            this.formCustomizers.add(customizer);
            return this;
        }

        /**
         * Sets the callback run asynchronously before displaying the step.
         *
         * @param onEnter callback invoked before the step UI is displayed
         * @return this builder
         */
        public Builder onEnter(Consumer<OnboardingContext> onEnter) {
            this.onEnter = onEnter;
            return this;
        }

        /**
         * Sets the callback run on the JavaFX application thread after displaying and restoring the form.
         *
         * @param onEnterAfterUI callback invoked after context values are restored into the displayed form
         * @return this builder
         */
        public Builder onEnterAfterUI(Consumer<OnboardingContext> onEnterAfterUI) {
            this.onEnterAfterUI = onEnterAfterUI;
            return this;
        }

        /**
         * Sets the callback run after saving the form values when leaving the step.
         *
         * @param onExit callback invoked after form values have been saved to the context
         * @return this builder
         */
        public Builder onExit(Consumer<OnboardingContext> onExit) {
            this.onExit = onExit;
            return this;
        }

        /**
         * Sets the callback run when the step is discarded or onboarding finishes.
         *
         * @param onDispose callback that releases resources when the step is discarded
         * @return this builder
         */
        public Builder onDispose(Consumer<OnboardingContext> onDispose) {
            this.onDispose = onDispose;
            return this;
        }

        /**
         * Sets asynchronous work that must finish successfully before advancing or finishing.
         *
         * @param beforeNext asynchronous action that must succeed before advancing or finishing
         * @return this builder
         */
        public Builder beforeNext(Function<OnboardingContext, CompletableFuture<Void>> beforeNext) {
            this.beforeNext = beforeNext;
            return this;
        }

        /**
         * Builds a step that lazily creates its section and synchronizes tracked components with the context.
         *
         * @return the configured step
         */
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
                        if (spacing != null) {
                            builder.spacing(spacing);
                        }
                        if (padding != null) {
                            builder.padding(padding);
                        }

                        if (disableSubmitButton) {
                            builder.disableSubmitButton();
                        }
                        if (disableResetButton) {
                            builder.disableResetButton();
                        }

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

            if (!componentsByDataKey.containsKey(component.dataKey())) {
                trackedComponents.add(component);
            }

            componentsByDataKey.put(component.dataKey(), component);
        }

        private void addComponent(FormSection.Builder sectionBuilder, ComponentSpec spec) {
            FormComponent<?, ?, ?, ?> component = spec.builder() != null ? spec.builder().build() : spec.component();
            if (component == null)
                return;

            if (spec.customizer() != null) {
                spec.customizer().accept(component);
            }

            sectionBuilder.appendComponent(component);
            trackComponent(component);

            String contextKey = spec.contextKey() != null ? spec.contextKey() : component.dataKey();
            Function<Object, Object> transformer = spec.transformer() != null
                ? spec.transformer()
                : Function.identity();
            Function<Object, Object> reverseTransformer = spec.reverseTransformer() != null
                ? spec.reverseTransformer()
                : Function.identity();
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
                if (ctx.needsRefresh(contextKey))
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

        private record DataMapping(
            String contextKey,
            Function<Object, Object> transformer,
            Function<Object, Object> reverseTransformer
        ) {
        }
    }
}
