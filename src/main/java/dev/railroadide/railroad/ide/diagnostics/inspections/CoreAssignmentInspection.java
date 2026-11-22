package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;

/**
 * Provides built-in Java inspections for {@link JavaSemanticRules#INCOMPATIBLE_ASSIGNMENT}.
 */
@RegisteredInspection
public final class CoreAssignmentInspection implements JavaInspectionRuleProvider {
    /**
     * Stable identifier used to register this inspection provider.
     */
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
    private static void reportVariableDeclarator(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter,
        SyntaxNode node
    ) {
        Type declaredType = context.declaredTypeOfVariable(node);
        SyntaxNode initializer = context.firstDirectExpressionChild(node);
        if (initializer == null)
            return;

        checkValue(context, reporter, node, declaredType, initializer);
    }

    private static void reportAssignmentExpression(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter,
        SyntaxNode node
    ) {
        List<SyntaxNode> expressionChildren = context.directExpressionChildren(node);
        if (expressionChildren.size() < 2)
            return;

        Type leftType = context.inferredType(expressionChildren.getFirst())
            .orElse(new JavaTypes.UnknownType("<unknown>"));
        SyntaxNode value = expressionChildren.get(1);
        if (context.hasOperatorToken(node, JavaTokenType.EQUALS)) {
            checkValue(context, reporter, node, leftType, value);
            return;
        }

        Type rightType = context.inferredType(value).orElse(new JavaTypes.UnknownType("<unknown>"));
        if (isUnknownLike(leftType) || isUnknownLike(rightType))
            return;
        for (SyntaxNode child : node.children()) {
            JavaTokenType operator = COMPOUND_OPERATORS.get(child.kind());
            if (operator != null) {
                if (!context.isCompoundAssignmentCompatible(operator, leftType, rightType)) {
                    reporter.report(node, rightType.displayName(), leftType.displayName());
                }
                return;
            }
        }
    }

    private static final Map<SyntaxKind, JavaTokenType> COMPOUND_OPERATORS = Arrays.stream(JavaTokenType.values())
        .filter(type -> type.isAssignmentOperator() && type != JavaTokenType.EQUALS)
        .collect(Collectors.toUnmodifiableMap(JavaSyntaxKinds::tokenKind, Function.identity()));

    private static void checkValue(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter,
        SyntaxNode site,
        Type target,
        SyntaxNode expression
    ) {
        if (isUnknownLike(target))
            return;
        SyntaxNode value = context.unwrapTransparentExpression(expression);
        if (value == null)
            return;
        if ("JAVA_CONDITIONAL_EXPRESSION".equals(value.kind().id()) && target.kind() == JavaTypes.Kind.DECLARED) {
            var operands = context.directExpressionChildren(value);
            if (operands.size() == 3 && (containsFunctionalBranch(context, operands.get(1))
                || containsFunctionalBranch(context, operands.get(2)))) {
                checkValue(context, reporter, operands.get(1), target, operands.get(1));
                checkValue(context, reporter, operands.get(2), target, operands.get(2));
                return;
            }
        }
        Type source = context.inferredType(value).orElse(new JavaTypes.UnknownType("<unknown>"));
        if (isPolyExpression(value)) {
            if (!context.isFunctionalInterface(target)) {
                reporter.report(site, source.displayName(), target.displayName());
            }
            return;
        }
        if (!isUnknownLike(source) && !context.isAssignable(target, source, site)
            && !fitsIntegerLiteral(context, target, source, value)) {
            reporter.report(site, source.displayName(), target.displayName());
        }
    }

    private static boolean containsFunctionalBranch(JavaRuleContext context, SyntaxNode expression) {
        SyntaxNode value = context.unwrapTransparentExpression(expression);
        if (value == null)
            return false;
        if (isPolyExpression(value))
            return true;
        if (!value.kind().equals(JavaSyntaxKinds.CONDITIONAL_EXPRESSION))
            return false;
        var operands = context.directExpressionChildren(value);
        return operands.size() == 3 && (containsFunctionalBranch(context, operands.get(1))
            || containsFunctionalBranch(context, operands.get(2)));
    }

    private static boolean fitsIntegerLiteral(JavaRuleContext context, Type target, Type source, SyntaxNode value) {
        String primitive = target.displayName();
        if (!"int".equals(source.displayName())
            || !Set.of("byte", "short", "char", "java.lang.Byte", "java.lang.Short", "java.lang.Character")
                .contains(primitive))
            return false;
        boolean negative = context.hasOperatorToken(value, JavaTokenType.MINUS);
        if ("JAVA_UNARY_EXPRESSION".equals(value.kind().id())
            && (negative || context.hasOperatorToken(value, JavaTokenType.PLUS))) {
            value = context.unwrapTransparentExpression(context.firstExpressionChild(value));
        }
        if (value == null || !"JAVA_LITERAL_EXPRESSION".equals(value.kind().id()))
            return false;
        try {
            int number = new IntegerLiteralExpr(context.sourceText(value).strip()).asNumber().intValue();
            if (negative) {
                number = -number;
            }
            return switch (primitive) {
                case "byte", "java.lang.Byte" -> number == (byte) number;
                case "short", "java.lang.Short" -> number == (short) number;
                default -> number == (char) number;
            };
        } catch (NumberFormatException _) {
            return false;
        }
    }

    private static boolean isPolyExpression(SyntaxNode expression) {
        String kindId = expression.kind().id();
        return JAVA_LAMBDA_EXPRESSION.equals(kindId) || JAVA_METHOD_REFERENCE_EXPRESSION.equals(kindId);
    }

    private static boolean isUnknownLike(Type type) {
        return type.kind() == JavaTypes.Kind.UNKNOWN
            || "<unknown>".equals(type.displayName())
            || type.displayName().isBlank();
    }
}
