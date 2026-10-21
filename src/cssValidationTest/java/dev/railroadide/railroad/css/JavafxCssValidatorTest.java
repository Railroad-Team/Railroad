package dev.railroadide.railroad.css;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavafxCssValidatorTest {
    @TempDir
    public Path directory;

    @Test
    public void acceptsJavafxValuesAndRelativeImports() throws IOException {
        Files.writeString(directory.resolve("palette.css"), ".root { -color-bg: #123456; }");
        var stylesheet = Files.writeString(directory.resolve("valid.css"), """
            @import "palette.css";
            .tab-pane > .tab {
                -fx-background-color: derive(-color-bg, -12%);
                -fx-padding: 1 2 3 4;
            }
            """);
        assertTrue(JavafxCssValidator.validate(stylesheet).isEmpty());
    }

    @Test
    public void rejectsInvalidValuesAndClearsPreviousErrors() throws IOException {
        var stylesheet = Files.writeString(directory.resolve("invalid.css"),
            ".root { -fx-background-color: rgb(1, 2); }");
        var errors = JavafxCssValidator.validate(stylesheet);
        assertFalse(errors.isEmpty());
        assertTrue(errors.getFirst().contains("invalid.css"));
        Files.writeString(stylesheet, ".root { -fx-padding: 1px; }");
        assertTrue(JavafxCssValidator.validate(stylesheet).isEmpty());
    }

    @Test
    public void rejectsChildCombinatorOnNewLine() throws IOException {
        var stylesheet = Files.writeString(directory.resolve("wrapped.css"),
            ".tab-pane\n    > .tab { -fx-padding: 1; }");
        assertFalse(JavafxCssValidator.validate(stylesheet).isEmpty());
        Files.writeString(stylesheet, ".tab-pane > .tab { -fx-padding: 1; }");
        assertTrue(JavafxCssValidator.validate(stylesheet).isEmpty());
    }

    @Test
    public void rejectsMissingStylesheetsAndImports() throws IOException {
        assertFalse(JavafxCssValidator.validate(directory.resolve("missing.css")).isEmpty());
        var stylesheet = Files.writeString(directory.resolve("import.css"), "@import \"missing.css\";");
        assertFalse(JavafxCssValidator.validate(stylesheet).isEmpty());
    }

    @Test
    public void commandFailsForInvalidNestedStylesheetOrEmptyDirectory() throws IOException {
        assertThrows(IllegalStateException.class, () -> JavafxCssValidator.main(new String[]{directory.toString()}));
        var nested = Files.createDirectory(directory.resolve("components"));
        Files.writeString(nested.resolve("bad.css"), ".root { -fx-padding: ; }");
        assertThrows(IllegalStateException.class, () -> JavafxCssValidator.main(new String[]{directory.toString()}));
    }
}
