package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

public class CoreBigDecimalEqualsInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-big-decimal-equals";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.BIG_DECIMAL_EQUALS.id(),
            JavaSemanticRules.BIG_DECIMAL_EQUALS.defaultSeverity(),
            JavaSemanticRules.BIG_DECIMAL_EQUALS.messageTemplate(),
            Set.of("core", "numeric-precision"),
            CoreBigDecimalEqualsInspection::reportBigDecimalEquals
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

    private static void reportBigDecimalEquals(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode invocation : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            if (!context.isMethodInvocationNamed(invocation, "equals"))
                continue;

            SyntaxNode argument = firstArgument(context, invocation);
            if (argument == null)
                continue;

            Symbol symbol = context.resolvedSymbol(invocation).orElse(null);
            if (symbol == null || symbol.kind() != SymbolKind.METHOD)
                continue;

            String owner = context.ownerQualifiedName(symbol).orElse(null);
            if (!"java.math.BigDecimal".equals(owner))
                continue;

            String argumentType = qualifiedTypeNameOfExpression(context, argument);
            if (!"java.math.BigDecimal".equals(argumentType))
                continue;

            reporter.report(invocation);
        }
    }

    private static SyntaxNode firstArgument(JavaRuleContext context, SyntaxNode invocation) {
        SyntaxNode argumentList = context.directChild(invocation, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList == null)
            return null;

        for (SyntaxNode child : argumentList.children()) {
            if (context.isExpressionNode(child))
                return child;
        }

        return null;
    }

    private static String qualifiedTypeNameOfExpression(JavaRuleContext context, SyntaxNode expression) {
        Type inferred = context.inferredType(expression).orElse(new Type.UnknownType("<unknown>"));
        return inferred.kind() == Type.Kind.UNKNOWN ? null : context.resolveQualifiedTypeName(inferred.displayName());
    }
}
