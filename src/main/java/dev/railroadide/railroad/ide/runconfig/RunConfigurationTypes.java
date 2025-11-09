package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.ide.runconfig.defaults.*;

import java.util.Objects;

public class RunConfigurationTypes {
    public static final JavaApplicationRunConfigurationType JAVA_APPLICATION =
        register("railroad:java_application", new JavaApplicationRunConfigurationType());
    public static final GradleRunConfigurationType GRADLE =
        register("railroad:gradle", new GradleRunConfigurationType());
    public static final CompoundRunConfigurationType COMPOUND =
        register("railroad:compound", new CompoundRunConfigurationType());
    public static final JarApplicationRunConfigurationType JAR_APPLICATION =
        register("railroad:jar_application", new JarApplicationRunConfigurationType());
    public static final ShellScriptRunConfigurationType SHELL_SCRIPT =
        register("railroad:shell_script", new ShellScriptRunConfigurationType());

    @SuppressWarnings("unchecked")
    public static <D extends RunConfigurationData, T extends RunConfigurationType<D>> T register(String id, T type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        return (T) RunConfigurationType.REGISTRY.register(id, type);
    }

    public static void initialize() {
        // Intentionally left blank.
    }
}
