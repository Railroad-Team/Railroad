package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.ide.runconfig.defaults.*;

import java.util.Objects;

/**
 * Registers and exposes the built-in run configuration types.
 */
public class RunConfigurationTypes {
    /**
     * Built-in type for launching a Java main class.
     */
    public static final JavaApplicationRunConfigurationType JAVA_APPLICATION = register("railroad:java_application",
        new JavaApplicationRunConfigurationType());
    /**
     * Built-in type for executing Gradle tasks.
     */
    public static final GradleRunConfigurationType GRADLE = register("railroad:gradle",
        new GradleRunConfigurationType());
    /**
     * Built-in type for executing a group of child configurations.
     */
    public static final CompoundRunConfigurationType COMPOUND = register("railroad:compound",
        new CompoundRunConfigurationType());
    /**
     * Built-in type for launching an executable JAR.
     */
    public static final JarApplicationRunConfigurationType JAR_APPLICATION = register("railroad:jar_application",
        new JarApplicationRunConfigurationType());
    /**
     * Built-in type for executing a script file or script text.
     */
    public static final ShellScriptRunConfigurationType SHELL_SCRIPT = register("railroad:shell_script",
        new ShellScriptRunConfigurationType());

    /**
     * Registers a run configuration type under its identifier and returns the registered instance.
     *
     * @param <D> the type-specific run configuration data
     * @param <T> the concrete run configuration type being registered
     * @param id the nonnull registry identifier
     * @param type the type instance to register
     * @return the registered type instance
     */
    @SuppressWarnings("unchecked")
    public static <D extends RunConfigurationData, T extends RunConfigurationType<D>> T register(String id, T type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        return (T) RunConfigurationType.REGISTRY.register(id, type);
    }

    /**
     * Triggers class initialization so all built-in run configuration types are registered.
     */
    public static void initialize() {
        // Intentionally left blank.
    }
}
