package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

public interface LanguageInspectionRuleReporter {
    void report(SyntaxNode node, Object... messageArgs);

    void reportMessage(SyntaxNode node, String message);
}
