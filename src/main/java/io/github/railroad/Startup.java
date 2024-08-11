package io.github.railroad;

import io.github.railroad.ui.layout.Layout;
import io.github.railroad.ui.layout.LayoutParseException;
import io.github.railroad.ui.layout.LayoutParser;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Startup {
    private static final Logger LOGGER = LoggerFactory.getLogger(Railroad.class);
    private static final boolean TEST = false;

    public static void main(String[] args) {
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
