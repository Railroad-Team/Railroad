package dev.railroadide.railroad.ide.diagnostics.inspections;

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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class CoreNegativeHexIntInLongContextInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-negative-hex-int-in-long-context";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.NEGATIVE_HEX_INT_IN_LONG_CONTEXT.id(),
                JavaSemanticRules.NEGATIVE_HEX_INT_IN_LONG_CONTEXT.defaultSeverity(),
                JavaSemanticRules.NEGATIVE_HEX_INT_IN_LONG_CONTEXT.messageTemplate(),
                Set.of("core", "bug"),
                CoreNegativeHexIntInLongContextInspection::reportNegativeHexIntInLongContext
            )
        );
    }

    private static void reportNegativeHexIntInLongContext(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode literalExprNode : context.nodesOfKinds(JavaSyntaxKinds.LITERAL_EXPRESSION.id())) {
            if (!isHexIntegerLiteral(literalExprNode))
                continue;

            SyntaxToken token = hexIntegerToken(literalExprNode);
            if (token == null)
                continue;

            String tokenText = token.text();
            if (tokenText == null || tokenText.isBlank())
                continue;

            String normalizedTokenText = tokenText.toLowerCase(Locale.ROOT).replace("_", "");
            if (normalizedTokenText.endsWith("l"))
                continue;

            if (!normalizedTokenText.startsWith("0x"))
                continue;

            String digits = normalizedTokenText.substring(2);

            long value;
            try {
                value = Long.parseUnsignedLong(digits, 16);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (value > 0xFFFF_FFFFL)
                continue; // not an integer literal any more

            if ((value & 0x8000_0000L) == 0)
                continue; // int, but not negative

            boolean longContext = isLongContext(context, literalExprNode);
            if (!longContext)
                continue;

            reporter.report(literalExprNode, tokenText);
        }
    }

    private static boolean isHexIntegerLiteral(SyntaxNode node) {
        if (!JavaSyntaxKinds.LITERAL_EXPRESSION.id().equals(node.kind().id()))
            return false;

        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token) {
                String kindId = token.kind().id();
                if (kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_HEXADECIMAL_LITERAL).id()))
                    return true;
            }
        }

        return false;
    }

    private static SyntaxToken hexIntegerToken(SyntaxNode literalExprNode) {
        for (SyntaxNode child : literalExprNode.children()) {
            if (child instanceof SyntaxToken token) {
                String kindId = token.kind().id();
                if (kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_HEXADECIMAL_LITERAL).id()))
                    return token;
            }
        }

        return null;
    }

    private static boolean isLongContext(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            SyntaxNode parent = current.parent().orElse(null);
            if (parent == null)
                return false;

            if (isTransparentExpression(parent)) {
                current = parent;
                continue;
            }

            if (Objects.equals(JavaSyntaxKinds.ARGUMENT_LIST.id(), parent.kind().id())) {
                SyntaxNode invocation = parent.parent().orElse(null);
                if (invocation != null
                    && Objects.equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id(), invocation.kind().id())
                    && isLongMethodArgContext(context, current, invocation)) {
                    return true;
                }

                current = parent;
                continue;
            }

            if (isLongExpressionContext(context, current, parent))
                return true;

            current = parent;
        }
    }

    private static boolean isLongExpressionContext(JavaRuleContext context, SyntaxNode node, SyntaxNode parent) {
        if (isLongType(context.inferredType(parent).orElse(null)))
            return true;

        String parentKindId = parent.kind().id();
        if (Objects.equals(JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id(), parentKindId)) {
            List<SyntaxNode> expressionChildren = context.directExpressionChildren(parent);
            if (expressionChildren.size() >= 2 && expressionChildren.get(1) == node)
                return isLongType(context.inferredType(expressionChildren.getFirst()).orElse(null));
        }

        if (Objects.equals(JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id(), parentKindId)) {
            SyntaxNode conditionalType = enclosingTypeReference(context, parent);
            return conditionalType != null && isLongTypeText(context.canonicalTypeText(conditionalType));
        }

        if (Objects.equals(JavaSyntaxKinds.CAST_EXPRESSION.id(), parentKindId)) {
            SyntaxNode typeRef = context.directChild(parent, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef != null && isLongTypeText(context.canonicalTypeText(typeRef));
        }

        SyntaxNode arrayInitializer = nearestAncestor(node, JavaSyntaxKinds.ARRAY_INITIALIZER_EXPRESSION.id());
        if (arrayInitializer != null)
            return isLongArrayInitializerContext(context, arrayInitializer);

        if (Objects.equals(JavaSyntaxKinds.VARIABLE_DECLARATOR.id(), parentKindId)
            || Objects.equals(JavaSyntaxKinds.FIELD_DECLARATION.id(), parentKindId)) {
            SyntaxNode typeRef = enclosingTypeReference(context, parent);
            return typeRef != null && isLongTypeText(context.canonicalTypeText(typeRef));
        }

        if (Objects.equals(JavaSyntaxKinds.RETURN_STATEMENT.id(), parentKindId)) {
            SyntaxNode callable = context.nearestEnclosingCallableOrLambda(parent);
            if (callable == null)
                return false;

            SyntaxNode typeRef = context.directChild(callable, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef != null && isLongTypeText(context.canonicalTypeText(typeRef));
        }

        if (Objects.equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id(), parentKindId))
            return isLongMethodArgContext(context, node, parent);

        return false;
    }

    private static SyntaxNode nearestAncestor(SyntaxNode node, String kindId) {
        SyntaxNode current = node;
        while (true) {
            SyntaxNode parent = current.parent().orElse(null);
            if (parent == null)
                return null;

            if (isTransparentExpression(parent)) {
                current = parent;
                continue;
            }

            if (Objects.equals(kindId, parent.kind().id()))
                return parent;

            current = parent;
        }
    }

    private static boolean isLongArrayInitializerContext(JavaRuleContext context, SyntaxNode arrayInitializer) {
        SyntaxNode parent = arrayInitializer.parent().orElse(null);
        if (parent == null)
            return false;

        String parentKindId = parent.kind().id();
        if (Objects.equals(JavaSyntaxKinds.VARIABLE_DECLARATOR.id(), parentKindId))
            return isLongArrayType(context.declaredTypeOfVariable(parent));

        if (Objects.equals(JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id(), parentKindId)) {
            List<SyntaxNode> expressionChildren = context.directExpressionChildren(parent);
            if (expressionChildren.size() >= 2 && expressionChildren.get(1) == arrayInitializer)
                return isLongArrayType(context.inferredType(expressionChildren.getFirst()).orElse(null));
        }

        if (Objects.equals(JavaSyntaxKinds.RETURN_STATEMENT.id(), parentKindId)) {
            SyntaxNode callable = context.nearestEnclosingCallableOrLambda(parent);
            if (callable == null)
                return false;

            SyntaxNode typeRef = context.directChild(callable, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef != null && isLongArrayType(context.inferredType(typeRef).orElse(null));
        }

        if (Objects.equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id(), parentKindId))
            return isLongArrayMethodArgContext(context, arrayInitializer, parent);

        return false;
    }

    private static boolean isLongMethodArgContext(JavaRuleContext context, SyntaxNode node, SyntaxNode parent) {
        SyntaxNode argumentList = context.directChild(parent, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList == null)
            return false;

        List<SyntaxNode> arguments = context.directExpressionChildren(argumentList);
        int index = arguments.indexOf(node);
        if (index < 0)
            return false;

        Symbol methodSymbol = context.resolvedSymbol(parent).orElse(null);
        if (methodSymbol == null)
            return false;

        List<Type> parameterTypes = context.callableParameterTypes(methodSymbol);
        if (index >= parameterTypes.size())
            return false;

        return isLongType(parameterTypes.get(index));
    }

    private static boolean isLongArrayMethodArgContext(JavaRuleContext context, SyntaxNode node, SyntaxNode parent) {
        SyntaxNode argumentList = context.directChild(parent, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList == null)
            return false;

        List<SyntaxNode> arguments = context.directExpressionChildren(argumentList);
        int index = arguments.indexOf(node);
        if (index < 0)
            return false;

        Symbol methodSymbol = context.resolvedSymbol(parent).orElse(null);
        if (methodSymbol == null)
            return false;

        List<Type> parameterTypes = context.callableParameterTypes(methodSymbol);
        if (index >= parameterTypes.size())
            return false;

        return isLongArrayType(parameterTypes.get(index));
    }

    private static boolean isTransparentExpression(SyntaxNode node) {
        String kindId = node.kind().id();
        return JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(kindId)
            || JavaSyntaxKinds.PRIMARY_EXPRESSION.id().equals(kindId);
    }

    private static boolean isLongType(Type type) {
        if (type == null)
            return false;

        return type.kind() == Type.Kind.PRIMITIVE && "long".equals(type.displayName().toLowerCase(Locale.ROOT));
    }

    private static boolean isLongTypeText(String text) {
        return text != null && "long".equals(text.toLowerCase(Locale.ROOT).trim());
    }

    private static SyntaxNode enclosingTypeReference(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            SyntaxNode typeRef = context.directChild(current, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef != null)
                return typeRef;

            SyntaxNode parent = current.parent().orElse(null);
            if (parent == null)
                return null;

            current = parent;
        }
    }

    private static boolean isLongArrayType(Type type) {
        if (type == null)
            return false;

        if (type.kind() != Type.Kind.ARRAY)
            return false;

        Type componentType = ((Type.ArrayType) type).componentType();
        return isLongType(componentType);
    }
}
