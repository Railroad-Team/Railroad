package io.github.railroad;

import io.github.railroad.ide.DefaultIDEStateService;
import io.github.railroad.localization.L18n;
import io.github.railroad.railroadpluginapi.services.ApplicationInfoService;
import io.github.railroad.railroadpluginapi.services.IDEStateService;

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

    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> serviceClass) {
        if (serviceClass == ApplicationInfoService.class) {
            return (T) APPLICATION_INFO;
        } else if (serviceClass == IDEStateService.class) {
            return (T) IDE_STATE;
        }

        throw new IllegalArgumentException("Service " + serviceClass.getName() + " is not available.");
    }
}
