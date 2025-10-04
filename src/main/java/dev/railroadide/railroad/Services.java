package dev.railroadide.railroad;

import dev.railroadide.core.project.creation.ProjectCreationPipelineService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.*;
import dev.railroadide.railroad.ide.DefaultIDEStateService;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.creation.DefaultProjectCreationPipelineService;
import dev.railroadide.railroad.project.creation.service.*;
import dev.railroadide.railroad.utility.DiscardingOutputStream;
import dev.railroadide.railroadpluginapi.services.ApplicationInfoService;
import dev.railroadide.railroadpluginapi.services.DocumentEditorStateService;
import dev.railroadide.railroadpluginapi.services.IDEStateService;
import dev.railroadide.railroadpluginapi.services.VCSService;
import dev.railroadide.railroad.ide.DefaultDocumentEditorStateService;
import javafx.application.HostServices;

/**
 * Provides access to various services used in the Railroad application.
 * This class serves as a central point to retrieve instances of different services.
 */
public class Services {
    public static final ApplicationInfoService APPLICATION_INFO = new ApplicationInfoService() {
        @Override
        public String getVersion() {
            return L18n.localize("railroad.app.version");
        }

        @Override
        public String getName() {
            return L18n.localize("railroad.app.name");
        }

        @Override
        public String getBuildTimestamp() {
            return "";
        }
    };

    public static final DefaultIDEStateService IDE_STATE = DefaultIDEStateService.getInstance();

    public static final DefaultDocumentEditorStateService DOCUMENT_EDITOR_STATE = new DefaultDocumentEditorStateService();

    public static final ProjectServiceRegistry PROJECT_SERVICE_REGISTRY = new ProjectServiceRegistry() {{
        bind(ChecksumService.class, new MessageDigestChecksumService());
        bind(FilesService.class, new NioFilesService());
        bind(GitService.class, new JGitService());
        bind(GradleService.class, new ToolingGradleService(new DiscardingOutputStream()));
        bind(HttpService.class, new OkHttpService(Railroad.HTTP_CLIENT));
        bind(TemplateEngineService.class, new GroovyTemplateEngineService());
        bind(ZipService.class, new NioZipService());
    }};

    public static final DefaultProjectCreationPipelineService PROJECT_CREATION_PIPELINE = new DefaultProjectCreationPipelineService();

    /**
     * Retrieves a service instance by its class type.
     *
     * @param <T>          The type of the service to retrieve.
     * @param serviceClass The class type of the service to retrieve.
     * @return An instance of the requested service.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> serviceClass) {
        if (serviceClass == null)
            throw new IllegalArgumentException("Service class cannot be null.");

        if (serviceClass == ApplicationInfoService.class) {
            return (T) APPLICATION_INFO;
        } else if (serviceClass == IDEStateService.class) {
            return (T) IDE_STATE;
        } else if (serviceClass == VCSService.class) {
            return (T) Railroad.REPOSITORY_MANAGER;
        } else if (serviceClass == HostServices.class) {
            return (T) Railroad.getHostServicess();
        } else if (serviceClass == DocumentEditorStateService.class) {
            return (T) DOCUMENT_EDITOR_STATE;
        } else if (serviceClass == ProjectServiceRegistry.class) {
            return (T) PROJECT_SERVICE_REGISTRY;
        } else if (serviceClass == ProjectCreationPipelineService.class) {
            return (T) PROJECT_CREATION_PIPELINE;
        }

        throw new IllegalArgumentException("Service " + serviceClass.getName() + " is not available.");
    }
}
