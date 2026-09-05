package dev.railroadide.railroad;

import com.google.gson.Gson;
import dev.railroadide.logger.Logger;
import dev.railroadide.railroad.ide.DefaultDocumentEditorStateService;
import dev.railroadide.railroad.ide.DefaultIDEStateService;
import dev.railroadide.railroad.ide.DefaultWorkspaceService;
import dev.railroadide.railroad.ide.diagnostics.LanguageInspectionRegistries;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService;
import dev.railroadide.railroad.ide.ui.editor.EditorTabManager;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.localization.Language;
import dev.railroadide.railroad.localization.LocalizationService;
import dev.railroadide.railroad.plugin.spi.inspection.LanguageInspectionProvider;
import dev.railroadide.railroad.plugin.spi.services.ApplicationInfoService;
import dev.railroadide.railroad.plugin.spi.services.DocumentEditorStateService;
import dev.railroadide.railroad.plugin.spi.services.IDEStateService;
import dev.railroadide.railroad.plugin.spi.services.VCSService;
import dev.railroadide.railroad.plugin.spi.services.WorkspaceService;
import dev.railroadide.railroad.project.creation.ProjectCreationPipelineService;
import dev.railroadide.railroad.project.creation.ProjectServiceRegistry;
import dev.railroadide.railroad.project.creation.service.*;
import dev.railroadide.railroad.project.onboarding.creation.DefaultProjectCreationPipelineService;
import dev.railroadide.railroad.project.onboarding.creation.service.*;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.ui.UIManager;
import dev.railroadide.railroad.utility.DiscardingOutputStream;
import javafx.application.HostServices;
import javafx.beans.property.ObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides access to various services used in the Railroad application.
 * This class serves as a central point to retrieve instances of different services.
 */
public class Services {
    private static final String APPLICATION_VERSION = loadApplicationVersion();

    /**
     * Provides application information such as version, name, and build timestamp.
     */
    public static final ApplicationInfoService APPLICATION_INFO = new ApplicationInfoService() {
        @Override
        public String getVersion() {
            return APPLICATION_VERSION;
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

    private static String loadApplicationVersion() {
        var properties = new Properties();
        try (InputStream input = Services.class.getResourceAsStream("/railroad-version.properties")) {
            if (input != null) {
                properties.load(input);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank())
                    return version;
            }
        } catch (IOException _) {
            // Fall back to manifest metadata below.
        }

        String manifestVersion = Services.class.getPackage().getImplementationVersion();
        return manifestVersion == null || manifestVersion.isBlank() ? "development" : manifestVersion;
    }

    /**
     * Provides access to the IDE state service, which manages the state of the IDE.
     */
    public static final DefaultIDEStateService IDE_STATE = DefaultIDEStateService.getInstance();

    /**
     * Provides access to the workspace service, which manages the workspace and its related operations.
     */
    public static final DefaultWorkspaceService WORKSPACE = new DefaultWorkspaceService();

    /**
     * Provides access to the document editor state service, which manages the state of document editors.
     */
    public static final DefaultDocumentEditorStateService DOCUMENT_EDITOR_STATE = new DefaultDocumentEditorStateService();

    /**
     * Provides access to the editor tab manager, which manages the tabs in the editor.
     */
    public static final EditorTabManager EDITOR_TAB_MANAGER = new EditorTabManager();

    /**
     * Provides access to the localization service, which handles localization and internationalization.
     */
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

    /**
     * Provides access to the project service registry, which manages various services related to project creation and
     * management.
     */
    public static final ProjectServiceRegistry PROJECT_SERVICE_REGISTRY = new ProjectServiceRegistry() {
        {
            bind(ChecksumService.class, new MessageDigestChecksumService());
            bind(FilesService.class, new NioFilesService());
            bind(GitService.class, new JGitService());
            bind(GradleService.class, new ToolingGradleService(new DiscardingOutputStream()));
            bind(HttpService.class, new OkHttpService(Railroad.HTTP_CLIENT));
            bind(TemplateEngineService.class, new GroovyTemplateEngineService());
            bind(ZipService.class, new NioZipService());
        }
    };

    /**
     * Provides access to the project creation pipeline service, which manages the pipeline for creating new projects.
     */
    public static final DefaultProjectCreationPipelineService PROJECT_CREATION_PIPELINE = new DefaultProjectCreationPipelineService();

    /**
     * Provides access to the project language index service, which manages the indexing of languages used in projects.
     */
    public static final ProjectLanguageIndexService PROJECT_LANGUAGE_INDEX_SERVICE = new ProjectLanguageIndexService();

    /**
     * Provides access to the language inspection provider registry, which manages the registration of language
     * inspection providers.
     */
    public static final Registry<LanguageInspectionProvider> LANGUAGE_INSPECTION_PROVIDER_REGISTRY = LanguageInspectionRegistries.LANGUAGE_INSPECTION_PROVIDER_REGISTRY;

    /**
     * Provides access to the UI manager, which manages the user interface components and their interactions.
     */
    public static final UIManager UI_MANAGER = new UIManager();

    /**
     * Retrieves a service instance by its class type.
     *
     * @param <T> The type of the service to retrieve.
     * @param serviceClass The class type of the service to retrieve.
     * @return An instance of the requested service.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> serviceClass) {
        if (serviceClass == null)
            throw new IllegalArgumentException("Service class cannot be null.");

        if (serviceClass == ApplicationInfoService.class)
            return (T) APPLICATION_INFO;
        else if (serviceClass == IDEStateService.class)
            return (T) IDE_STATE;
        else if (serviceClass == WorkspaceService.class)
            return (T) WORKSPACE;
        else if (serviceClass == VCSService.class)
            return (T) Railroad.REPOSITORY_MANAGER;
        else if (serviceClass == HostServices.class)
            return (T) Railroad.getHostServicess();
        else if (serviceClass == DocumentEditorStateService.class)
            return (T) DOCUMENT_EDITOR_STATE;
        else if (serviceClass == EditorTabManager.class)
            return (T) EDITOR_TAB_MANAGER;
        else if (serviceClass == LocalizationService.class)
            return (T) LOCALIZATION_SERVICE;
        else if (serviceClass == Logger.class)
            return (T) Railroad.LOGGER;
        else if (serviceClass == Gson.class)
            return (T) Railroad.GSON;
        else if (serviceClass == ProjectServiceRegistry.class)
            return (T) PROJECT_SERVICE_REGISTRY;
        else if (serviceClass == ProjectCreationPipelineService.class)
            return (T) PROJECT_CREATION_PIPELINE;
        else if (serviceClass == ProjectLanguageIndexService.class)
            return (T) PROJECT_LANGUAGE_INDEX_SERVICE;
        else if (serviceClass == UIManager.class)
            return (T) UI_MANAGER;

        throw new IllegalArgumentException("Service " + serviceClass.getName() + " is not available.");
    }
}
