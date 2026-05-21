package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@RegisteredInspection
public class CoreDoubleNegationInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-double-negation";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.DOUBLE_NEGATION.id(),
            JavaSemanticRules.DOUBLE_NEGATION.defaultSeverity(),
            JavaSemanticRules.DOUBLE_NEGATION.messageTemplate(),
            Set.of("core", "control-flow"),
            CoreDoubleNegationInspection::reportDoubleNegation
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

    private static void reportDoubleNegation(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind(JavaSyntaxKinds.UNARY_EXPRESSION.id())) {
            if (!context.hasOperatorToken(syntaxNode, JavaTokenType.EXCLAMATION_MARK))
                continue;

            SyntaxNode parent = syntaxNode.parent().orElse(null);
            if (parent != null
                && Objects.equals(parent.kind().id(), JavaSyntaxKinds.UNARY_EXPRESSION.id())
                && context.hasOperatorToken(parent, JavaTokenType.EXCLAMATION_MARK))
                continue;

            JavaRuleContext.NegationUnwrapResult negationUnwrapResult = context.unwrapLeadingNegations(syntaxNode);
            if (negationUnwrapResult != null && negationUnwrapResult.negationCount() >= 2) {
                reporter.report(syntaxNode);
            }
        }
    }
}
