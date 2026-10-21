package dev.railroadide.railroad.css;

import javafx.css.CssParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Validates application stylesheets without initializing the JavaFX toolkit. */
public final class JavafxCssValidator {
    private JavafxCssValidator() {
    }

    /** Validates all CSS files beneath the supplied directory, failing on any parser diagnostic. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1)
            throw new IllegalArgumentException("Usage: JavafxCssValidator <styles-directory>");
        var root = Path.of(args[0]);
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".css"))
                .sorted().toList();
        }
        if (files.isEmpty())
            throw new IllegalStateException("No JavaFX CSS files found in " + root);
        var errors = new ArrayList<String>();
        for (Path file : files) {
            errors.addAll(validate(file));
        }
        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
            throw new IllegalStateException("JavaFX CSS validation failed with " + errors.size() + " error(s).");
        }
        System.out.println("Validated " + files.size() + " JavaFX stylesheets.");
    }

    /** Returns parser diagnostics and read failures for a stylesheet, including its source location. */
    public static synchronized List<String> validate(Path file) {
        // JavaFX only collects diagnostics once this global observable list has been requested.
        var parserErrors = CssParser.errorsProperty();
        parserErrors.clear();
        var errors = new ArrayList<String>();
        try {
            new CssParser().parse(file.toUri().toURL());
        } catch (IOException | RuntimeException exception) {
            errors.add(file + ": " + exception);
        }
        for (CssParser.ParseError error : parserErrors) {
            errors.add(file + ": " + error);
        }
        parserErrors.clear();
        return List.copyOf(errors);
    }
}
