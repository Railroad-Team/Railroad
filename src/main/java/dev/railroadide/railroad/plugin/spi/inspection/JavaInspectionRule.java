package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

public interface JavaInspectionRule extends LanguageInspectionRule<JavaRuleContext> {
    void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter);

    @Override
    default void evaluate(JavaRuleContext context, LanguageInspectionRuleReporter reporter) {
        if (reporter instanceof JavaInspectionRuleReporter javaReporter) {
            evaluate(context, javaReporter);
            return;
        }

        evaluate(context, new JavaInspectionRuleReporter() {
            @Override
            public void report(SyntaxNode node, Object... messageArgs) {
                reporter.report(node, messageArgs);
            }

            @Override
            public void reportMessage(SyntaxNode node, String message) {
                reporter.reportMessage(node, message);
            }
        });
    }
}
