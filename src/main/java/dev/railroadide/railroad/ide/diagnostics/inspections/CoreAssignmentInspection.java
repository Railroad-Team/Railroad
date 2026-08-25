package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreAssignmentInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-assignment";
    private static final String JAVA_VARIABLE_DECLARATOR = "JAVA_VARIABLE_DECLARATOR";
    private static final String JAVA_ASSIGNMENT_EXPRESSION = "JAVA_ASSIGNMENT_EXPRESSION";
    private static final String JAVA_LAMBDA_EXPRESSION = "JAVA_LAMBDA_EXPRESSION";
    private static final String JAVA_METHOD_REFERENCE_EXPRESSION = "JAVA_METHOD_REFERENCE_EXPRESSION";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INCOMPATIBLE_ASSIGNMENT.id(),
            JavaSemanticRules.INCOMPATIBLE_ASSIGNMENT.defaultSeverity(),
            JavaSemanticRules.INCOMPATIBLE_ASSIGNMENT.messageTemplate(),
            Set.of("core", "assignments", "types"),
            (context, reporter) -> context.traverse(node -> {
                String kindId = node.kind().id();
                if (JAVA_VARIABLE_DECLARATOR.equals(kindId)) {
                    reportVariableDeclarator(context, reporter, node);
                    return;
                }
                if (JAVA_ASSIGNMENT_EXPRESSION.equals(kindId)) {
                    reportAssignmentExpression(context, reporter, node);
                }
            })));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }
    private static void reportVariableDeclarator(JavaRuleContext context, JavaInspectionRuleReporter reporter,
        SyntaxNode node) {
        Type declaredType = context.declaredTypeOfVariable(node);
        SyntaxNode initializer = context.firstDirectExpressionChild(node);
        if (initializer == null)
            return;

        Type sourceType = context.inferredType(initializer).orElse(new Type.UnknownType("<unknown>"));
        if (isUnknownLike(declaredType) || isUnknownLike(sourceType))
            return;
        if (isPolyExpression(initializer)) {
            if (!context.isFunctionalInterface(declaredType)) {
                reporter.report(node, sourceType.displayName(), declaredType.displayName());
            }
            return;
        }
        if (!context.isAssignable(declaredType, sourceType)) {
            reporter.report(node, sourceType.displayName(), declaredType.displayName());
        }
    }

    private static void reportAssignmentExpression(JavaRuleContext context, JavaInspectionRuleReporter reporter,
        SyntaxNode node) {
        List<SyntaxNode> expressionChildren = context.directExpressionChildren(node);
        if (expressionChildren.size() < 2)
            return;

        Type leftType = context.inferredType(expressionChildren.getFirst()).orElse(new Type.UnknownType("<unknown>"));
        Type rightType = context.inferredType(expressionChildren.get(1)).orElse(new Type.UnknownType("<unknown>"));
        if (isUnknownLike(leftType) || isUnknownLike(rightType))
            return;
        if (isPolyExpression(expressionChildren.get(1))) {
            if (!context.isFunctionalInterface(leftType)) {
                reporter.report(node, rightType.displayName(), leftType.displayName());
            }
            return;
        }
        if (!context.isAssignable(leftType, rightType)) {
            reporter.report(node, rightType.displayName(), leftType.displayName());
        }
    }

    private static boolean isPolyExpression(SyntaxNode expression) {
        String kindId = expression.kind().id();
        return JAVA_LAMBDA_EXPRESSION.equals(kindId) || JAVA_METHOD_REFERENCE_EXPRESSION.equals(kindId);
    }

    private static boolean isUnknownLike(Type type) {
        return type.kind() == Type.Kind.UNKNOWN
            || "<unknown>".equals(type.displayName())
            || type.displayName().isBlank();
    }
}
