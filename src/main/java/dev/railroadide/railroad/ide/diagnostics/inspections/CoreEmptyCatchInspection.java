package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public class CoreEmptyCatchInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-empty-catch";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.EMPTY_CATCH.id(),
            JavaSemanticRules.EMPTY_CATCH.defaultSeverity(),
            JavaSemanticRules.EMPTY_CATCH.messageTemplate(),
            Set.of("core", "control-flow"),
            CoreEmptyCatchInspection::reportEmptyCatch
        )
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportEmptyCatch(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind("JAVA_CATCH_CLAUSE")) {
            SyntaxNode block = context.directChild(syntaxNode, "JAVA_BLOCK");
            if (block == null)
                continue;

            if (context.isEmptyBlock(block))
                reporter.report(syntaxNode);
        }
    }
}
