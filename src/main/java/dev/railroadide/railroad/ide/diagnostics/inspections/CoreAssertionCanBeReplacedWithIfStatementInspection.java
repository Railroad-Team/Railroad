package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public class CoreAssertionCanBeReplacedWithIfStatementInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-assertion-can-be-replaced-with-if-statement";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT.id(),
            JavaSemanticRules.ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT.defaultSeverity(),
            JavaSemanticRules.ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT.messageTemplate(),
            Set.of("core", "performance"),
            CoreAssertionCanBeReplacedWithIfStatementInspection::reportAssertionCanBeReplacedWithIfStatement
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

    private static void reportAssertionCanBeReplacedWithIfStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode assertNode : context.nodesOfKind(JavaSyntaxKinds.ASSERT_STATEMENT.id())) {
            SyntaxNode enclosingMethod = enclosingMethod(assertNode);
            if (enclosingMethod == null)
                continue;

            boolean isPublic = context.hasDirectModifierToken(enclosingMethod, JavaTokenType.PUBLIC_KEYWORD);
            boolean isProtected = context.hasDirectModifierToken(enclosingMethod, JavaTokenType.PROTECTED_KEYWORD);
            boolean inInterface = context.enclosingTypeSymbol(enclosingMethod)
                .map(symbol -> symbol.kind() == SymbolKind.INTERFACE || symbol.kind() == SymbolKind.ANNOTATION)
                .orElse(false);

            boolean hasMessage = context.directExpressionChildren(assertNode).size() > 1;
            if (hasMessage || isPublic || isProtected || inInterface) {
                reporter.report(assertNode);
            }
        }
    }

    private static SyntaxNode enclosingMethod(SyntaxNode node) {
        SyntaxNode current = node;
        while (current != null) {
            if (JavaSyntaxKinds.METHOD_DECLARATION.id().equals(current.kind().id())
                || JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id().equals(current.kind().id()))
                return current;

            current = current.parent().orElse(null);
        }

        return null;
    }
}
