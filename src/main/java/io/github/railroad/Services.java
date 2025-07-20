package io.github.railroad;

import io.github.railroad.ide.DefaultIDEStateService;
import io.github.railroad.localization.L18n;
import io.github.railroad.railroadpluginapi.services.ApplicationInfoService;
import io.github.railroad.railroadpluginapi.services.IDEStateService;
import io.github.railroad.railroadpluginapi.services.VCSService;

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

    public static final IDEStateService IDE_STATE = DefaultIDEStateService.getInstance();

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
        }

        throw new IllegalArgumentException("Service " + serviceClass.getName() + " is not available.");
    }
}
