package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@RegisteredInspection
public class CoreIntegerDivisionInFloatingPointContextInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-integer-division-in-floating-point-context";

    private static final Set<String> INTEGRAL_TYPE_PRIMITIVE_NAMES = Set.of(
        "byte", "short", "int", "long", "char"
    );
    private static final Set<String> FLOATING_POINT_TYPE_PRIMITIVE_NAMES = Set.of(
        "float", "double"
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT.id(),
                JavaSemanticRules.INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT.defaultSeverity(),
                JavaSemanticRules.INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT.messageTemplate(),
                Set.of("core", "numeric-precision"),
                CoreIntegerDivisionInFloatingPointContextInspection::reportIntegerDivisionInFloatingPointContext
            )
        );
    }

    private static void reportIntegerDivisionInFloatingPointContext(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode binaryExpression : context.nodesOfKind(JavaSyntaxKinds.BINARY_EXPRESSION.id())) {
            if (!isIntegerDivision(context, binaryExpression))
                continue;

            if (!isInFloatingPointContext(context, binaryExpression))
                continue;

            reporter.report(binaryExpression);
        }
    }

    private static boolean isInFloatingPointContext(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            SyntaxNode parent = current.parent().orElse(null);
            if (parent == null)
                return false;

            if (Objects.equals(parent.kind().id(), JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id())
                || Objects.equals(parent.kind().id(), JavaSyntaxKinds.CAST_EXPRESSION.id())) {
                current = parent;
                continue;
            }

            if (Objects.equals(parent.kind().id(), JavaSyntaxKinds.ARGUMENT_LIST.id())) {
                SyntaxNode invocation = parent.parent().orElse(null);
                if (invocation != null
                    && Objects.equals(invocation.kind().id(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())
                    && isInFloatingPointMethodArgContext(context, current, invocation))
                    return true;

                current = parent;
                continue;
            }

            if(isFloatingPointExpressionContext(context, parent))
                return true;

            current = parent;
        }
    }

    private static boolean isInFloatingPointMethodArgContext(JavaRuleContext context, SyntaxNode current, SyntaxNode invocation) {
        return false;
    }

    private static boolean isFloatingPointExpressionContext(JavaRuleContext context, SyntaxNode parent) {
        return false;
    }

    private static boolean isIntegerDivision(JavaRuleContext context, SyntaxNode binaryExpression) {
        if (!context.hasOperatorToken(binaryExpression, JavaTokenType.SLASH))
            return false;

        List<SyntaxNode> children = context.directExpressionChildren(binaryExpression);
        if (children.size() != 2)
            return false;

        Type left = context.inferredType(children.get(0)).orElse(new Type.UnknownType("<unknown>"));
        Type right = context.inferredType(children.get(1)).orElse(new Type.UnknownType("<unknown>"));

        return isIntegralType(left) && isIntegralType(right);
    }

    private static boolean isIntegralType(Type type) {
        return type != null
            && type.kind() == Type.Kind.PRIMITIVE
            && INTEGRAL_TYPE_PRIMITIVE_NAMES.contains(type.displayName());
    }

    private static boolean isFloatingPointType(Type type) {
        return type != null
            && type.kind() == Type.Kind.PRIMITIVE
            && FLOATING_POINT_TYPE_PRIMITIVE_NAMES.contains(type.displayName());
    }
}
