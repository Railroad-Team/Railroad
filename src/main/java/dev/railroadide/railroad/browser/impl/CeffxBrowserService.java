package dev.railroadide.railroad.browser.impl;

import dev.railroadide.railroad.browser.BrowserService;
import dev.railroadide.railroad.browser.EmbeddedBrowser;

public final class CeffxBrowserService implements BrowserService {
    @Override
    public EmbeddedBrowser createBrowser(String url) {
        return new CeffxBrowser(url);
    }
}
