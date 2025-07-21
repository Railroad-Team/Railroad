package dev.railroadide.railroad;

import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.railroad.ui.layout.Layout;
import dev.railroadide.railroad.ui.layout.LayoutParseException;
import dev.railroadide.railroad.ui.layout.LayoutParser;
import javafx.application.Application;

import java.nio.file.Path;

public class Startup {
    private static final Logger LOGGER = LoggerManager.create(Startup.class).dontLogToLatest().build();
    private static final boolean TEST = false;

    public static void main(String[] args) {
        if (TEST) {
            LoggerManager.init();
            LOGGER.debug("Running tests...");
            try {
                Layout layout = LayoutParser.parse(Path.of("template.railayout"));
                layout.print();
            } catch (LayoutParseException exception) {
                throw new RuntimeException(exception);
            }

            LoggerManager.shutdown();
        } else {
            Application.launch(Railroad.class, args);
        }
    }
}