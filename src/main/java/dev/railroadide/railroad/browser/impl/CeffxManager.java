package dev.railroadide.railroad.browser.impl;

import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.CefSettings;

public final class CeffxManager {
    private static CefApp cefApp;

    private CeffxManager() {
    }

    public static synchronized CefApp getCefApp() {
        if (cefApp == null) {
            CefApp.startup(new String[0]);
            cefApp = CefApp.getInstance(createSettings());
        }

        return cefApp;
    }

    private static CefSettings createSettings() {
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.multi_threaded_message_loop = true;
        settings.external_message_pump = false;
        settings.command_line_args_disabled = false;
        return settings;
    }
}
