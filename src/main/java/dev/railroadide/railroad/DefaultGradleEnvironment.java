package dev.railroadide.railroad;

import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.GradleSettings;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.project.Project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DefaultGradleEnvironment(Project project, Path gradleInstallationPath,
                                       GradleSettings settings) implements GradleEnvironment {
    @Override
    public boolean useWrapper() {
        return settings.useWrapper();
    }

    @Override
    public Optional<Path> installationPath() {
        return Optional.ofNullable(gradleInstallationPath);
    }

    @Override
    public Optional<Path> userHomePath() {
        return Optional.ofNullable(settings.gradleUserHome());
    }

    @Override
    public Optional<JDK> jvm() {
        return Optional.ofNullable(settings.gradleJvm());
    }

    @Override
    public String jvmArgumentsFor(GradleTaskExecutionRequest request, JDK jvm) {
        List<RunConfiguration<?>> configurations = settings.configurations();
        if (configurations == null || configurations.isEmpty())
            return "";

        for (RunConfiguration<?> configuration : configurations) {
            if(!(configuration.data() instanceof GradleRunConfigurationData data))
                continue;

            if (!matchesConfiguration(request, jvm, data))
                continue;

            String[] vmOptions = data.getVmOptions();
            if (vmOptions != null && vmOptions.length > 0)
                return String.join(" ", vmOptions);
        }

        return "";
    }

    @Override
    public boolean isDaemonEnabled() {
        return settings.isDaemonEnabled();
    }

    @Override
    public Optional<Duration> daemonIdleTimeout() {
        return Optional.ofNullable(settings.daemonIdleTimeout());
    }

    private boolean matchesConfiguration(GradleTaskExecutionRequest request,
                                         JDK jvm,
                                         GradleRunConfigurationData data) {
        if (request == null || data == null)
            return false;

        String configuredTask = data.getTask();
        if (configuredTask == null || !configuredTask.equals(request.taskPath()))
            return false;

        Path configuredProjectPath = data.getGradleProjectPath();
        if (!pathsMatch(configuredProjectPath, project.getPath()))
            return false;

        JDK configurationJdk = data.getJavaHome();
        return jvm == null || configurationJdk == null || configurationJdk.equals(jvm);
    }

    private static boolean pathsMatch(Path first, Path second) {
        if (first == null || second == null)
            return Objects.equals(first, second);

        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }
}
