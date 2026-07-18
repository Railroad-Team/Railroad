package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RegisteredInspection
public class CoreImplicitNumericConversionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-implicit-numeric-conversion";

    private static final Map<String, Set<String>> WIDENING_CONVERSIONS = Map.of(
        "byte", Set.of("short", "int", "long", "float", "double"),
        "short", Set.of("int", "long", "float", "double"),
        "char", Set.of("int", "long", "float", "double"),
        "int", Set.of("long", "float", "double"),
        "long", Set.of("float", "double"),
        "float", Set.of("double")
    );

    private static final Set<String> NUMERIC_PRIMITIVES = Set.of("byte", "short", "char", "int", "long", "float", "double");

    private static final Set<JavaTokenType> SHIFTING_COMPOUND_ASSIGNMENT_OPERATORS = Set.of(
        JavaTokenType.LEFT_SHIFT_EQUALS,
        JavaTokenType.RIGHT_SHIFT_EQUALS,
        JavaTokenType.UNSIGNED_RIGHT_SHIFT_EQUALS
    );

    private static final Set<JavaTokenType> ARITHMETIC_COMPOUND_ASSIGNMENT_OPERATORS = Set.of(
        JavaTokenType.PLUS_EQUALS,
        JavaTokenType.MINUS_EQUALS,
        JavaTokenType.STAR_EQUALS,
        JavaTokenType.SLASH_EQUALS,
        JavaTokenType.PERCENT_EQUALS
    );

    private static final Set<JavaTokenType> BITWISE_COMPOUND_ASSIGNMENT_OPERATORS = Set.of(
        JavaTokenType.AMPERSAND_EQUALS,
        JavaTokenType.CARET_EQUALS,
        JavaTokenType.PIPE_EQUALS
    );

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.IMPLICIT_NUMERIC_CONVERSION.id(),
                JavaSemanticRules.IMPLICIT_NUMERIC_CONVERSION.defaultSeverity(),
                JavaSemanticRules.IMPLICIT_NUMERIC_CONVERSION.messageTemplate(),
                Set.of("core", "numeric", "readability"),
                CoreImplicitNumericConversionInspection::reportImplicitNumericConversion
            )
        );
    }

    @Override
    public String id() {
        return ID;
    }

    private static void reportImplicitNumericConversion(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
//        int myInt = 5;
//        long x = myInt; // implicit numeric conversion from int to long
        for (SyntaxNode node : context.nodesOfKind(JavaSyntaxKinds.VARIABLE_DECLARATOR.id())) {
            inspectVariableDeclarator(context, reporter, node);
        }

//        int intValue = 5;
//        int shortValue = 3;
//        shortValue = intValue; // implicit numeric conversion from int to short
//        shortValue += 5; // implicit numeric conversion from int to short in compound assignment
        for (SyntaxNode node : context.nodesOfKind(JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id())) {
            inspectAssignmentExpression(context, reporter, node);
        }

        // long fooBar() {
        //     int x = 5;
        //     return x; // implicit numeric conversion from int to long in return statement
        // }
        for (SyntaxNode node : context.nodesOfKind(JavaSyntaxKinds.RETURN_STATEMENT.id())) {
            inspectReturnStatement(context, reporter, node);
        }

        // void someMethod(long value) {}
        //
        // int myInt = 5;
        // someMethod(myInt); // implicit numeric conversion from int to long in method invocation
        // or
        // class MyClass {
        //     MyClass(int value) {}
        // }
        //
        // long myLong = 10L;
        // MyClass obj = new MyClass(myLong); // implicit numeric conversion from long to int in class instance creation
        for (SyntaxNode node : context.nodesOfKinds(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id(), JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id())) {
            inspectMethodInvocation(context, reporter, node);
        }
    }

    private static void inspectVariableDeclarator(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        SyntaxNode initializer = context.firstDirectExpressionChild(node);
        if (initializer == null)
            return;

        Type declaredType = context.declaredTypeOfVariable(node);
        reportImplicitConversion(context, reporter, initializer, declaredType);
    }

    private static void inspectAssignmentExpression(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        List<SyntaxNode> expressionChildren = context.directExpressionChildren(node);
        if (expressionChildren.size() != 2)
            return;

        SyntaxNode left = expressionChildren.get(0);
        SyntaxNode right = expressionChildren.get(1);
        Type leftType = context.inferredType(left).orElse(new Type.UnknownType("<unknown>"));
        if (context.hasOperatorToken(node, JavaTokenType.EQUALS)) {
            reportImplicitConversion(context, reporter, right, leftType);
            return;
        }

        inspectCompoundAssignment(context, reporter, node, leftType, right);
    }

    private static void inspectCompoundAssignment(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode assignment, Type leftType, SyntaxNode rightExpression) {
        if (!isNumericPrimitive(leftType))
            return;

        Type rightType = context.inferredType(rightExpression).orElse(new Type.UnknownType("<unknown>"));
        if (!isNumericPrimitive(rightType))
            return;

        String leftPrimitive = leftType.displayName();
        String rightPrimitive = rightType.displayName();

        String promotedType = promotedTypeForCompoundAssignment(context, assignment, leftPrimitive, rightPrimitive);
        if (promotedType == null)
            return;

        if (promotedType.equals(leftPrimitive))
            return;

        if (!isNarrowingNumericConversion(promotedType, leftPrimitive))
            return;

        reporter.report(assignment, ConversionKind.NARROWING.displayName(), promotedType, leftPrimitive);
    }

    private static void inspectReturnStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        SyntaxNode expression = context.firstDirectExpressionChild(node);
        if (expression == null)
            return;

        SyntaxNode enclosing = context.nearestEnclosingCallableOrLambda(node);
        if (enclosing == null)
            return;

        if (Objects.equals(JavaSyntaxKinds.LAMBDA_EXPRESSION.id(), enclosing.kind().id()))
            return; // TODO: skipped lambda expressions for now since their target type can be hard to determine

        SyntaxNode returnTypeRef = context.directChild(enclosing, JavaSyntaxKinds.TYPE_REFERENCE.id());
        if (returnTypeRef == null)
            return;

        Type returnType = context.inferredType(returnTypeRef).orElse(new Type.UnknownType("<unknown>"));
        reportImplicitConversion(context, reporter, expression, returnType);
    }

    private static void inspectMethodInvocation(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode invocation) {
        SyntaxNode argumentList = context.directChild(invocation, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList == null)
            return;

        Symbol resolvedSymbol = context.resolvedSymbol(invocation).orElse(null);
        if (resolvedSymbol == null)
            return;

        List<Type> parameterTypes = context.callableParameterTypes(resolvedSymbol);
        if (parameterTypes.isEmpty())
            return;

        List<SyntaxNode> arguments = context.directExpressionChildren(argumentList);
        for (int index = 0; index < arguments.size(); index++) {
            Type targetType = targetParameterType(parameterTypes, arguments.size(), index);
            if (targetType == null)
                continue;

            reportImplicitConversion(context, reporter, arguments.get(index), targetType);
        }
    }

    private static Type targetParameterType(List<Type> parameterTypes, int argumentCount, int argumentIndex) {
        if (argumentIndex < parameterTypes.size()) {
            Type directParameterType = parameterTypes.get(argumentIndex);

            if (argumentCount > parameterTypes.size()
                && argumentIndex >= parameterTypes.size() - 1
                && directParameterType.kind() == Type.Kind.ARRAY)
                return ((Type.ArrayType) directParameterType).componentType();

            return directParameterType;
        }

        if (parameterTypes.isEmpty())
            return null;

        Type lastParameterType = parameterTypes.getLast();
        if (lastParameterType.kind() == Type.Kind.ARRAY)
            return ((Type.ArrayType) lastParameterType).componentType();

        return null;
    }

    private static String promotedTypeForCompoundAssignment(JavaRuleContext context, SyntaxNode assignment, String leftPrimitive, String rightPrimitive) {
        if (hasAnyOperator(context, assignment, SHIFTING_COMPOUND_ASSIGNMENT_OPERATORS))
            return unaryNumberPromotionType(leftPrimitive);

        if (hasAnyOperator(context, assignment, ARITHMETIC_COMPOUND_ASSIGNMENT_OPERATORS))
            return binaryNumericPromotionType(leftPrimitive, rightPrimitive);

        if (hasAnyOperator(context, assignment, BITWISE_COMPOUND_ASSIGNMENT_OPERATORS))
            return binaryNumericPromotionType(leftPrimitive, rightPrimitive);

        return null;
    }

    private static String unaryNumberPromotionType(String primitive) {
        return switch (primitive) {
            case "byte", "short", "char", "int" -> "int";
            case "long" -> "long";
            case "float" -> "float";
            case "double" -> "double";
            default -> null;
        };
    }

    private static String binaryNumericPromotionType(String leftPrimitive, String rightPrimitive) {
        if (leftPrimitive.equals("double") || rightPrimitive.equals("double"))
            return "double";

        if (leftPrimitive.equals("float") || rightPrimitive.equals("float"))
            return "float";

        if (leftPrimitive.equals("long") || rightPrimitive.equals("long"))
            return "long";

        if (isNumericPrimitiveName(leftPrimitive) && isNumericPrimitiveName(rightPrimitive))
            return "int";

        return null;
    }

    private static boolean hasAnyOperator(JavaRuleContext context, SyntaxNode node, Set<JavaTokenType> operatorTokens) {
        return operatorTokens.stream().anyMatch(token -> context.hasOperatorToken(node, token));
    }

    private static void reportImplicitConversion(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode sourceExpression, Type targetType) {
        if (isExplicitCastExpression(sourceExpression))
            return;

        Type sourceType = context.inferredType(sourceExpression).orElse(new Type.UnknownType("<unknown>"));
        ConversionKind conversionKind = classifyImplicitConversion(sourceType, targetType);
        if (conversionKind == null)
            return;

        if (!context.isAssignable(targetType, sourceType))
            return;

        reporter.report(sourceExpression, conversionKind.displayName(), sourceType.displayName(), targetType.displayName());
    }

    private static ConversionKind classifyImplicitConversion(Type sourceType, Type targetType) {
        if ((sourceType == null || targetType == null) || (!isNumericPrimitive(sourceType) || !isNumericPrimitive(targetType)))
            return null;

        String source = sourceType.displayName();
        String target = targetType.displayName();
        if (source.equals(target))
            return null;

        if (isWideningNumericConversion(source, target))
            return ConversionKind.WIDENING;

        if (isNarrowingNumericConversion(source, target))
            return ConversionKind.NARROWING;

        return null;
    }

    private static boolean isWideningNumericConversion(String source, String target) {
        return WIDENING_CONVERSIONS
            .getOrDefault(source, Set.of())
            .contains(target);
    }

    private static boolean isNarrowingNumericConversion(String source, String target) {
        return WIDENING_CONVERSIONS.entrySet().stream()
            .filter(entry -> entry.getKey().equals(target))
            .anyMatch(entry -> entry.getValue().contains(source));
    }

    private static boolean isNumericPrimitive(Type type) {
        return type != null
            && type.kind() == Type.Kind.PRIMITIVE
            && NUMERIC_PRIMITIVES.contains(type.displayName());
    }

    private static boolean isNumericPrimitiveName(String typeName) {
        return NUMERIC_PRIMITIVES.contains(typeName);
    }

    private static boolean isExplicitCastExpression(SyntaxNode node) {
        SyntaxNode current = node;
        while (current != null && Objects.equals(JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id(), current.kind().id())) {
            List<SyntaxNode> nestedExpressions = current.children().stream()
                .filter(child -> !(child instanceof SyntaxToken))
                .toList();

            current = nestedExpressions.isEmpty() ? null : nestedExpressions.getFirst();
        }

        return current != null && Objects.equals(JavaSyntaxKinds.CAST_EXPRESSION.id(), current.kind().id());
    }

    private enum ConversionKind {
        WIDENING("widening"),
        NARROWING("narrowing");

        private final String displayName;

        ConversionKind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
