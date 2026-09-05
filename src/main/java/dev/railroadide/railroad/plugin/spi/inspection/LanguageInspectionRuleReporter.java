package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

/**
 * Emits findings for one rule using its diagnostic metadata and configured severity.
 */
public interface LanguageInspectionRuleReporter {
    /**
     * Reports a finding by formatting the rule's message template.
     *
     * @param node syntax node whose source range should be highlighted
     * @param messageArgs arguments substituted into the rule's message template
     */
    void report(SyntaxNode node, Object... messageArgs);

    /**
     * Reports a finding with a complete message, bypassing template formatting.
     *
     * @param node syntax node whose source range should be highlighted
     * @param message diagnostic message to display
     */
    void reportMessage(SyntaxNode node, String message);
}
