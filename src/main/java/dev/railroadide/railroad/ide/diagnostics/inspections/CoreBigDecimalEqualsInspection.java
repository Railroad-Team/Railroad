package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
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

            SyntaxNode receiver = context.invocationReceiver(invocation);
            if (receiver == null || !isBigDecimalType(context, receiver))
                continue;

            SyntaxNode argument = firstArgument(context, invocation);
            if (argument == null || !isBigDecimalType(context, argument))
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
        return qualifiedTypeName(context, inferred);
    }

    private static boolean isBigDecimalType(JavaRuleContext context, SyntaxNode expression) {
        String qualifiedTypeName = qualifiedTypeNameOfExpression(context, expression);
        if (qualifiedTypeName == null)
            return false;

        return "java.math.BigDecimal".equals(qualifiedTypeName)
            || "BigDecimal".equals(qualifiedTypeName)
            || "BigDecimal".equals(context.simpleTypeName(qualifiedTypeName))
            || context.isSubtype(qualifiedTypeName, "java.math.BigDecimal");
    }

    private static String qualifiedTypeName(JavaRuleContext context, Type type) {
        return switch (type.kind()) {
            case UNKNOWN, VOID, TYPE_VARIABLE, WILDCARD -> null;
            case PRIMITIVE -> type.displayName();
            case ARRAY -> {
                if (!(type instanceof Type.ArrayType(Type componentType1)))
                    yield null;

                String componentType = qualifiedTypeName(context, componentType1);
                yield componentType == null ? null : componentType + "[]";
            }
            case DECLARED -> {
                String typeName = type.displayName();
                int genericStart = typeName.indexOf('<');
                if (genericStart >= 0)
                    typeName = typeName.substring(0, genericStart);

                yield context.resolveQualifiedTypeName(typeName);
            }
        };
    }
}
