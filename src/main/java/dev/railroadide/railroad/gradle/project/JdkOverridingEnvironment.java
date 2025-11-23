package dev.railroadide.railroad.gradle.project;

import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.project.Project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * A Gradle environment that overrides the JVM with a specified JDK.
 *
 * @param delegate    the original Gradle environment to delegate to
 * @param overrideJvm the JDK to use instead of the one from the delegate
 */
record JdkOverridingEnvironment(GradleEnvironment delegate, JDK overrideJvm) implements GradleEnvironment {
    @Override
    public Project project() {
        return delegate.project();
    }

    @Override
    public boolean useWrapper() {
        return delegate.useWrapper();
    }

    @Override
    public Optional<Path> installationPath() {
        return delegate.installationPath();
    }

    @Override
    public Optional<Path> userHomePath() {
        return delegate.userHomePath();
    }

    @Override
    public Optional<JDK> jvm() {
        return Optional.ofNullable(overrideJvm);
    }

    @Override
    public String jvmArgumentsFor(GradleTaskExecutionRequest request, JDK jvm) {
        return delegate.jvmArgumentsFor(request, jvm);
    }

    @Override
    public boolean isDaemonEnabled() {
        return delegate.isDaemonEnabled();
    }

    @Override
    public Optional<Duration> daemonIdleTimeout() {
        return delegate.daemonIdleTimeout();
    }
}
