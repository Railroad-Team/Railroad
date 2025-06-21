package io.github.railroad;

import io.github.railroad.logging.Logger;
import io.github.railroad.ui.layout.Layout;
import io.github.railroad.ui.layout.LayoutParseException;
import io.github.railroad.ui.layout.LayoutParser;
import javafx.application.Application;

import java.nio.file.Path;

public class Startup {
    private static final Logger LOGGER = new Logger(Startup.class);
    private static final boolean TEST = false;

    public static void main(String[] args) {
        Logger.initialise();
        if (TEST) {
            LOGGER.debug("Running tests...");
            try {
                Layout layout = LayoutParser.parse(Path.of("template.railayout"));
                layout.print();
            } catch (LayoutParseException exception) {
                throw new RuntimeException(exception);
            }
        } else {
            Application.launch(Railroad.class, args);
        }
    }
}