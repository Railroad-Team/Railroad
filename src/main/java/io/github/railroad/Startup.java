package io.github.railroad;

import io.github.railroad.layout.Layout;
import io.github.railroad.layout.LayoutParseException;
import io.github.railroad.layout.LayoutParser;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Startup {
    public static final Logger LOGGER = LoggerFactory.getLogger(Startup.class);
    private static final boolean TEST = false;

    public static void main(String[] args) {
        if (TEST) {
            System.out.println("Running tests...");
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
