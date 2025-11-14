package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.CompoundRunConfigurationData;
import dev.railroadide.railroad.project.Project;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CompoundRunConfigurationType extends RunConfigurationType<CompoundRunConfigurationData> {
    public CompoundRunConfigurationType() {
        super("railroad.runconfig.compound", FontAwesomeSolid.LAYER_GROUP, Color.web("#9b59b6"));
    }

    @Override
    public CompletableFuture<Void> run(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return executeChildren(project, configuration, false);
    }

    @Override
    public CompletableFuture<Void> debug(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return executeChildren(project, configuration, true);
    }

    @Override
    public CompletableFuture<Void> stop(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        List<RunConfiguration<?>> children = getResolvedChildren(project, configuration);
        if (children.isEmpty())
            return CompletableFuture.completedFuture(null);

        List<CompletableFuture<Void>> stopFutures = new ArrayList<>(children.size());
        for (RunConfiguration<?> child : children) {
            try {
                stopFutures.add(child.stop(project));
            } catch (Throwable throwable) {
                stopFutures.add(CompletableFuture.failedFuture(throwable));
            }
        }

        return CompletableFuture.allOf(stopFutures.toArray(new CompletableFuture[0]));
    }

    @Override
    public boolean isDebuggingSupported(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return getResolvedChildren(project, configuration).stream()
            .allMatch(rc -> rc.isDebuggingSupported(project));
    }

    @Override
    public CompoundRunConfigurationData createDataInstance(Project project) {
        var data = new CompoundRunConfigurationData();
        data.setName("New Compound Configuration");
        return data;
    }

    @Override
    public Class<CompoundRunConfigurationData> getDataClass() {
        return CompoundRunConfigurationData.class;
    }

    private List<RunConfiguration<?>> getResolvedChildren(Project project,
                                                          RunConfiguration<CompoundRunConfigurationData> configuration) {
        List<RunConfiguration<?>> resolved =
            configuration.data().resolveConfigurations(project.getRunConfigManager().getConfigurations());
        List<RunConfiguration<?>> valid = new ArrayList<>(resolved.size());
        for (RunConfiguration<?> child : resolved) {
            if (child == null) {
                Railroad.LOGGER.warn("Null run configuration found in compound run configuration: {}", configuration.data().getName());
                continue;
            }

            valid.add(child);
        }

        return valid;
    }

    private CompletableFuture<Void> executeChildren(Project project,
                                                    RunConfiguration<CompoundRunConfigurationData> configuration,
                                                    boolean debug) {
        var children = getResolvedChildren(project, configuration);
        if (children.isEmpty())
            return CompletableFuture.completedFuture(null);

        if (debug) {
            children = children.stream()
                .filter(child -> {
                    boolean supported = child.isDebuggingSupported(project);
                    if (!supported) {
                        Railroad.LOGGER.warn("Skipping '{}' in compound configuration '{}' because it does not support debugging.",
                            child.data().getName(), configuration.data().getName());
                    }

                    return supported;
                })
                .toList();
            if (children.isEmpty())
                return CompletableFuture.completedFuture(null);
        }

        return configuration.data().getRunMode() == CompoundRunConfigurationData.RunMode.PARALLEL ?
            runParallel(children, project, debug) :
            runSequential(children, project, debug);
    }

    private CompletableFuture<Void> runParallel(List<RunConfiguration<?>> children,
                                                Project project,
                                                boolean debug) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(children.size());
        for (RunConfiguration<?> child : children) {
            futures.add(invokeChild(child, project, debug));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> runSequential(List<RunConfiguration<?>> children,
                                                  Project project,
                                                  boolean debug) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (RunConfiguration<?> child : children) {
            chain = chain.thenCompose(ignored -> invokeChild(child, project, debug));
        }

        return chain;
    }

    private CompletableFuture<Void> invokeChild(RunConfiguration<?> child,
                                                Project project,
                                                boolean debug) {
        try {
            return debug ? child.debug(project) : child.run(project);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }
}
