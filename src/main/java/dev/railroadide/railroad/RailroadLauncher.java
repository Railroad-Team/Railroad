package dev.railroadide.railroad;

import javafx.application.Application;

public final class RailroadLauncher {
    private RailroadLauncher() {
    }

    public static void main(String[] args) {
        launchWithPreloader(args);
    }

    public static void launchWithPreloader(String[] args) {
        String preloader = System.getProperty("javafx.preloader");
        if (preloader == null || preloader.isBlank()) {
            System.setProperty("javafx.preloader", RailroadPreloader.class.getName());
        }
        Application.launch(Railroad.class, args);
    }
}
