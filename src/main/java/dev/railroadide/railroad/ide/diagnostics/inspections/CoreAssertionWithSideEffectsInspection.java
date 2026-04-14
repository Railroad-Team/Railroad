package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CoreAssertionWithSideEffectsInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-assertion-with-side-effects";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.ASSERTION_WITH_SIDE_EFFECTS.id(),
                JavaSemanticRules.ASSERTION_WITH_SIDE_EFFECTS.defaultSeverity(),
                JavaSemanticRules.ASSERTION_WITH_SIDE_EFFECTS.messageTemplate(),
                Set.of("core", "assertions"),
                CoreAssertionWithSideEffectsInspection::reportAssertionsWithSideEffects
            )
        );
    }

    private static void reportAssertionsWithSideEffects(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode assertionNode : context.nodesOfKind(JavaSyntaxKinds.ASSERT_STATEMENT.id())) {
            SyntaxNode conditionNode = context.directExpressionChildren(assertionNode).stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
            if (conditionNode == null)
                continue;

            Set<SyntaxNode> visited = new HashSet<>();
            boolean hasSideEffects = hasSideEffects(conditionNode, context, visited);
            if (hasSideEffects) {
                reporter.report(assertionNode);
            }
        }
    }

    private static boolean hasSideEffects(SyntaxNode node, JavaRuleContext context, Set<SyntaxNode> visited) {
        if (!visited.add(node))
            return false; // Prevent infinite recursion on cyclic graphs

        String kindId = node.kind().id();
        if (Objects.equals(kindId, JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id()))
            return true;

        if (Objects.equals(kindId, JavaSyntaxKinds.UNARY_EXPRESSION.id())
            || Objects.equals(kindId, JavaSyntaxKinds.POSTFIX_EXPRESSION.id())) {
            if (isIncrementOrDecrement(node))
                return true;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            SyntaxNode methodDeclaration = context.resolvedSymbol(node)
                .flatMap(Symbol::declaration)
                .orElse(null);
            if (methodDeclaration != null) {
                SyntaxNode body = context.directChild(methodDeclaration, JavaSyntaxKinds.BLOCK.id());
                return body != null && hasSideEffects(body, context, visited);
            }

            return false;
        }

        for (SyntaxNode child : node.children()) {
            if (child != null && hasSideEffects(child, context, visited))
                return true;
        }

        return false;
    }

    private static boolean isIncrementOrDecrement(SyntaxNode node) {
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token) {
                String text = token.text();
                if ("++".equals(text) || "--".equals(text))
                    return true;
            }
        }

        return false;
    }
}
