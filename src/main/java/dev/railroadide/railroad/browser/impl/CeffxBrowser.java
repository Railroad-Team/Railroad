package dev.railroadide.railroad.browser.impl;

import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.CefBrowserSettings;
import com.techsenger.ceffx.core.CefClient;
import com.techsenger.ceffx.core.CefSettings;
import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.browser.CefBrowserFactory;
import com.techsenger.ceffx.core.browser.CefFrame;
import com.techsenger.ceffx.core.handler.CefDisplayHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefLifeSpanHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefLoadHandler;
import com.techsenger.ceffx.core.handler.CefLoadHandlerAdapter;
import dev.railroadide.railroad.browser.EmbeddedBrowser;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class CeffxBrowser implements EmbeddedBrowser {
    private static final Logger LOGGER = LoggerFactory.getLogger(CeffxBrowser.class);

    private final StackPane root = new StackPane();
    private volatile CefClient client;
    private volatile CefBrowser browser;

    public CeffxBrowser(String url) {
        this.root.setFocusTraversable(true);
        this.root.setMinSize(0, 0);
        this.root.setStyle("-fx-background-color: -rr-background-color, -fx-control-inner-background;");
        showStatus("Starting embedded browser...");

        CefApp.runLater(() -> {
            try {
                this.client = CeffxManager.getCefApp().createClient();
                installHandlers(this.client);

                CefBrowserSettings browserSettings = new CefBrowserSettings();
                CefBrowser createdBrowser = CefBrowserFactory.create(this.client, url, true, false,
                    null, browserSettings);
                createdBrowser.setWindowlessFrameRate(60);
                this.browser = createdBrowser;

                Platform.runLater(() -> this.root.getChildren().setAll(createdBrowser.getPane()));
            } catch (Throwable throwable) {
                LOGGER.error("Failed to initialize CEFFX browser", throwable);
                showStatus("Embedded browser failed to start: " + throwable.getMessage());
            }
        });
    }

    @Override
    public Node node() {
        return this.root;
    }

    @Override
    public String getCurrentUrl() {
        CefBrowser current = this.browser;
        return current != null ? current.getURL() : null;
    }

    @Override
    public void loadUrl(String url) {
        withBrowser(browser -> browser.loadURL(url));
    }

    @Override
    public void executeJavaScript(String script) {
        withBrowser(browser -> browser.executeJavaScript(script, browser.getURL(), 0));
    }

    @Override
    public void dispose() {
        CefBrowser currentBrowser = this.browser;
        CefClient currentClient = this.client;
        CefApp.runLater(() -> {
            if (currentBrowser != null) {
                currentBrowser.close(true);
            }
            if (currentClient != null) {
                currentClient.dispose();
            }
        });
    }

    @Override
    public CompletableFuture<BufferedImage> captureScreenshot() {
        CompletableFuture<BufferedImage> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            WritableImage snapshot = this.root.snapshot(null, null);
            future.complete(SwingFXUtils.fromFXImage(snapshot, null));
        });
        return future;
    }

    @Override
    public void setZoomLevel(double zoomLevel) {
        withBrowser(browser -> browser.setZoomLevel(zoomLevel));
    }

    @Override
    public double getZoomLevel() {
        CefBrowser current = this.browser;
        return current != null ? current.getZoomLevel() : 0.0;
    }

    @Override
    public void goBack() {
        withBrowser(CefBrowser::goBack);
    }

    @Override
    public void goForward() {
        withBrowser(CefBrowser::goForward);
    }

    @Override
    public boolean canGoBack() {
        CefBrowser current = this.browser;
        return current != null && current.canGoBack();
    }

    @Override
    public boolean canGoForward() {
        CefBrowser current = this.browser;
        return current != null && current.canGoForward();
    }

    @Override
    public void openDevTools() {
        withBrowser(CefBrowser::openDevTools);
    }

    @Override
    public void closeDevTools() {
        withBrowser(CefBrowser::closeDevTools);
    }

    @Override
    public void reload(boolean noCache) {
        withBrowser(browser -> {
            if (noCache) {
                browser.reloadIgnoreCache();
            } else {
                browser.reload();
            }
        });
    }

    @Override
    public void stopLoading() {
        withBrowser(CefBrowser::stopLoad);
    }

    @Override
    public void print() {
        withBrowser(CefBrowser::print);
    }

    @Override
    public void startDownload(String url) {
        withBrowser(browser -> browser.startDownload(url));
    }

    @Override
    public void viewSource() {
        withBrowser(CefBrowser::viewSource);
    }

    private void installHandlers(CefClient client) {
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                LOGGER.debug("CEFFX console [{}] {}:{} {}", level, source, line, message);
                return false;
            }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame,
                                    CefLoadHandler.ErrorCode errorCode, String errorText,
                                    String failedUrl) {
                LOGGER.warn("CEFFX load error [{}] {}: {}", errorCode, failedUrl, errorText);
            }
        });
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl,
                                         String targetFrameName) {
                if (targetUrl != null && !targetUrl.isBlank()) {
                    CeffxBrowser.this.loadUrl(targetUrl);
                }

                return true;
            }
        });
    }

    private void withBrowser(Consumer<CefBrowser> action) {
        CefBrowser current = this.browser;
        if (current != null) {
            CefApp.runLater(() -> action.accept(current));
        }
    }

    private void showStatus(String message) {
        Platform.runLater(() -> {
            Label label = new Label(message);
            label.setWrapText(true);
            label.setPadding(new Insets(12));
            this.root.getChildren().setAll(label);
        });
    }
}
