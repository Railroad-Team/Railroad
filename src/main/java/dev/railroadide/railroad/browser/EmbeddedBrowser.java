package dev.railroadide.railroad.browser;

import javafx.scene.Node;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

public interface EmbeddedBrowser {
    Node node();

    String getCurrentUrl();

    void loadUrl(String url);

    void executeJavaScript(String script);

    void dispose();

    CompletableFuture<BufferedImage> captureScreenshot();

    void setZoomLevel(double zoomLevel);

    double getZoomLevel();

    void goBack();

    void goForward();

    boolean canGoBack();

    boolean canGoForward();

    void openDevTools();

    void closeDevTools();

    void reload(boolean noCache);

    void stopLoading();

    void print();

    void startDownload(String url);

    void viewSource();
}
