package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.CompoundRunConfigurationData;
import dev.railroadide.railroad.project.Project;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.concurrent.CompletableFuture;

public class CompoundRunConfigurationType extends RunConfigurationType<CompoundRunConfigurationData> {
    public CompoundRunConfigurationType() {
        super("railroad.runconfig.compound", FontAwesomeSolid.LAYER_GROUP, Color.web("#9b59b6"));
    }

    @Override
    public CompletableFuture<Void> run(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return CompletableFuture.supplyAsync(() -> {
            for (RunConfiguration<?> runConfiguration : configuration.data().getConfigurations()) {
                if (runConfiguration == null) {
                    Railroad.LOGGER.warn("Null run configuration found in compound run configuration: {}", configuration.data().getName());
                    continue;
                }

                if (configuration.data().getRunMode() == CompoundRunConfigurationData.RunMode.PARALLEL) {
                    runConfiguration.run(project);
                } else {
                    runConfiguration.run(project).join();
                }
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> debug(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return CompletableFuture.supplyAsync(() -> {
            for (RunConfiguration<?> runConfiguration : configuration.data().getConfigurations()) {
                if (runConfiguration == null) {
                    Railroad.LOGGER.warn("Null run configuration found in compound run configuration: {}", configuration.data().getName());
                    continue;
                }

                if (runConfiguration.isDebuggingSupported(project)) {
                    if (configuration.data().getRunMode() == CompoundRunConfigurationData.RunMode.PARALLEL) {
                        runConfiguration.debug(project);
                    } else {
                        runConfiguration.debug(project).join();
                    }
                }
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> stop(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return CompletableFuture.supplyAsync(() -> {
            for (RunConfiguration<?> runConfiguration : configuration.data().getConfigurations()) {
                if (runConfiguration == null) {
                    Railroad.LOGGER.warn("Null run configuration found in compound run configuration: {}", configuration.data().getName());
                    continue;
                }

                runConfiguration.stop(project);
            }

            return null;
        });
    }

    @Override
    public boolean isDebuggingSupported(Project project, RunConfiguration<CompoundRunConfigurationData> configuration) {
        return configuration.data().getConfigurations().stream()
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
}
