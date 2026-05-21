package dev.railroadide.railroad.plugin.spi.inspection;

import java.nio.file.Path;

public interface LanguageRuleContext {
    String languageId();

    Path filePath();

    String documentText();
}
