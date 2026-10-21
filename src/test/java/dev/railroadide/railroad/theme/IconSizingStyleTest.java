package dev.railroadide.railroad.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IconSizingStyleTest {
    @Test
    public void iconSizesMustNotDependOnTheirOwnFont() throws IOException {
        // Ikonli updates its Text font when iconSize changes. Relative CSS units
        // therefore compound on successive CSS passes instead of staying stable.
        var relativeIconSize = Pattern.compile("-fx-icon-size\\s*:\\s*[^;{}]*?(?:em|ex|%)\\s*;");
        var violations = new ArrayList<String>();
        try (var styles = Files.walk(Path.of("src/main/resources/assets/railroad/styles"))) {
            for (Path stylesheet : styles.filter(path -> path.toString().endsWith(".css")).toList()) {
                var matcher = relativeIconSize.matcher(Files.readString(stylesheet));
                while (matcher.find()) {
                    violations.add(stylesheet + ": " + matcher.group());
                }
            }
        }

        assertTrue(violations.isEmpty(), "Use logical pixels for Ikonli icon sizes: " + violations);
    }
}
