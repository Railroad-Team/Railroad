package dev.railroadide.railroad;

import com.google.gson.Gson;
import dev.railroadide.logger.Logger;
import dev.railroadide.railroad.ide.DefaultDocumentEditorStateService;
import dev.railroadide.railroad.ide.DefaultIDEStateService;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.sst.project.ProjectSemanticService;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.localization.Language;
import dev.railroadide.railroad.localization.LocalizationService;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.services.ApplicationInfoService;
import dev.railroadide.railroad.plugin.spi.services.DocumentEditorStateService;
import dev.railroadide.railroad.plugin.spi.services.IDEStateService;
import dev.railroadide.railroad.plugin.spi.services.VCSService;
import dev.railroadide.railroad.project.creation.ProjectCreationPipelineService;
import dev.railroadide.railroad.project.creation.ProjectServiceRegistry;
import dev.railroadide.railroad.project.creation.service.*;
import dev.railroadide.railroad.project.onboarding.creation.DefaultProjectCreationPipelineService;
import dev.railroadide.railroad.project.onboarding.creation.service.*;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.utility.DiscardingOutputStream;
import javafx.application.HostServices;
import javafx.beans.property.ObjectProperty;

/**
 * Provides access to various services used in the Railroad application.
 * This class serves as a central point to retrieve instances of different services.
 */
public class Services {
    public static final ApplicationInfoService APPLICATION_INFO = new ApplicationInfoService() {
        @Override
        public String getVersion() {
            return "0.0.3";
        }

        @Override
        public String getName() {
            return "Railroad IDE";
        }

        @Override
        public String getBuildTimestamp() {
            return "";
        }
    };

    public static final DefaultIDEStateService IDE_STATE = DefaultIDEStateService.getInstance();

    public static final DefaultDocumentEditorStateService DOCUMENT_EDITOR_STATE = new DefaultDocumentEditorStateService();

    public static final LocalizationService LOCALIZATION_SERVICE = new LocalizationService() {
        @Override
        public String get(String key, Object... args) {
            return L18n.localize(key, args);
        }

        @Override
        public ObjectProperty<? extends Language> currentLanguageProperty() {
            return L18n.currentLanguageProperty();
        }

        @Override
        public boolean isKeyValid(String key) {
            return L18n.isKeyValid(key);
        }
    };

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
    public static final ProjectSemanticService PROJECT_SEMANTIC_SERVICE = new ProjectSemanticService();
    public static final Registry<JavaInspectionRuleProvider> JAVA_INSPECTION_RULE_PROVIDER_REGISTRY =
            JavaInspectionRegistries.JAVA_INSPECTION_RULE_PROVIDER_REGISTRY;

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
        } else if (serviceClass == LocalizationService.class) {
            return (T) LOCALIZATION_SERVICE;
        } else if (serviceClass == Logger.class) {
            return (T) Railroad.LOGGER;
        } else if (serviceClass == Gson.class) {
            return (T) Railroad.GSON;
        } else if (serviceClass == ProjectServiceRegistry.class) {
            return (T) PROJECT_SERVICE_REGISTRY;
        } else if (serviceClass == ProjectCreationPipelineService.class) {
            return (T) PROJECT_CREATION_PIPELINE;
        } else if (serviceClass == ProjectSemanticService.class) {
            return (T) PROJECT_SEMANTIC_SERVICE;
        }

        throw new IllegalArgumentException("Service " + serviceClass.getName() + " is not available.");
    }
}
