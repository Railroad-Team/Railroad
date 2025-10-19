package dev.railroadide.railroad.project.creation;

import dev.railroadide.core.project.ProjectCreationPipeline;
import dev.railroadide.core.project.ProjectType;
import dev.railroadide.core.project.creation.CreationStepProvider;
import dev.railroadide.core.project.creation.CreationStepRegistry;
import dev.railroadide.core.project.creation.ProjectCreationPipelineService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.*;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.creation.step.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class DefaultProjectCreationPipelineService implements ProjectCreationPipelineService {
    private final List<CreationStepProvider> providers = new ArrayList<>();

    @Override
    public ProjectCreationPipeline createProject(@NotNull ProjectType type, @NotNull ProjectServiceRegistry services) {
        var registry = new CreationStepRegistry();
        registerDefaultProviders(registry, type, services);

        for (CreationStepProvider provider : this.providers) {
            if (provider.supports(type)) {
                provider.provideSteps(services, registry);
            }
        }

        return new ProjectCreationPipeline(registry.getSteps());
    }

    @Override
    public void addProvider(@NotNull CreationStepProvider provider) {
        this.providers.add(provider);
    }

    @Override
    public void registerDefaultProviders(@NotNull CreationStepRegistry registry, @NotNull ProjectType type, @NotNull ProjectServiceRegistry services) {
        if (type.equals(ProjectTypeRegistry.FABRIC)) {
            registry.addAll(
                new CreateDirectoriesStep(services.get(FilesService.class)),
                new ResolveFabricMdkVersionStep(),
                new DownloadFabricExampleModStep(
                    services.get(HttpService.class), services.get(FilesService.class),
                    services.get(ZipService.class), services.get(ChecksumService.class)),
                new ExtractFabricExampleModStep(services.get(FilesService.class), services.get(ZipService.class)),
                new UpdateGradlePropertiesStep(services.get(FilesService.class)),
                new RenamePackagesStep(services.get(FilesService.class)),
                new UpdateFabricModJsonStep(services.get(FilesService.class)),
                new RenameMixinsStep(services.get(FilesService.class)),
                new RenameClassesStep(services.get(FilesService.class)),
                new UpdateGradleFilesStep(services.get(FilesService.class), services.get(HttpService.class),
                    services.get(TemplateEngineService.class), "dev", false),
                new RunGenSourcesStep(services.get(GradleService.class)),
                new InitGitStep(services.get(GitService.class))
            );
        } else if (type.equals(ProjectTypeRegistry.FORGE) || type.equals(ProjectTypeRegistry.NEOFORGE)) {
            registry.addAll(
                new CreateDirectoriesStep(services.get(FilesService.class)),
                new DownloadForgeMdkStep(
                    services.get(HttpService.class), services.get(FilesService.class),
                    services.get(ZipService.class), services.get(ChecksumService.class)),
                new ExtractForgeMdkStep(services.get(FilesService.class), services.get(ZipService.class)),
                new UpdateGradlePropertiesStep(services.get(FilesService.class)),
                new RenamePackagesStep(services.get(FilesService.class)),
                new UpdateForgeModsTomlStep(services.get(FilesService.class)),
                new RenameClassesStep(services.get(FilesService.class)),
                new UpdateGradleFilesStep(
                    services.get(FilesService.class), services.get(HttpService.class),
                    services.get(TemplateEngineService.class), "dev", true),
                new CreateMixinsJsonStep(services.get(FilesService.class)),
                new CreateAccessTransformerStep(services.get(FilesService.class)),
                new SetupForgeGradleWrapperStep(services.get(GradleService.class)),
                new InitGitStep(services.get(GitService.class))
            );
        }
    }
}
