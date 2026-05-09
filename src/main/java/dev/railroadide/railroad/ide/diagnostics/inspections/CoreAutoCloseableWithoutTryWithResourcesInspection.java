package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CoreAutoCloseableWithoutTryWithResourcesInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-auto-closeable-without-try-with-resources";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES.id(),
                JavaSemanticRules.AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES.defaultSeverity(),
                JavaSemanticRules.AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES.messageTemplate(),
                Set.of("core", "resource-management"),
                CoreAutoCloseableWithoutTryWithResourcesInspection::reportAutoCloseableWithoutTryWithResources
            )
        );
    }

    private static void reportAutoCloseableWithoutTryWithResources(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode declarator : context.nodesOfKind(JavaSyntaxKinds.VARIABLE_DECLARATOR.id())) {
            SyntaxNode declaration = declarator.parent().orElse(null);
            if (declaration == null || !Objects.equals(declaration.kind().id(), JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.id()))
                continue;

            if (isManagedByTryWithResources(declaration))
                continue;

            SyntaxNode initializer = context.firstExpressionChild(declarator);
            if (initializer == null)
                continue;

            String qualifiedTypeName = autoCloseableQualifiedTypeName(context, declarator, initializer);
            if (qualifiedTypeName == null || !context.isAutoCloseableType(qualifiedTypeName))
                continue;

            if (!looksLikeResourceLeak(context, initializer))
                continue;

            reporter.report(declarator, qualifiedTypeName);
        }
    }

    private static boolean isManagedByTryWithResources(SyntaxNode node) {
        SyntaxNode current = node.parent().orElse(null);
        while (current != null) {
            if (Objects.equals(JavaSyntaxKinds.TRY_RESOURCE.id(), current.kind().id()))
                return true;

            current = current.parent().orElse(null);
        }

        return false;
    }

    private static String autoCloseableQualifiedTypeName(JavaRuleContext context, SyntaxNode declarator, SyntaxNode initializer) {
        SyntaxNode declaration = declarator.parent().orElse(null);
        if (declaration != null) {
            SyntaxNode typeRef = context.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef != null && !isVarTypeReference(context, typeRef)) {
                String qualifiedName = context.resolveQualifiedTypeName(typeRef);
                if (qualifiedName != null)
                    return qualifiedName;
            }
        }

        String fromInitializer = qualifiedTypeNameFromInitializerSymbol(context, initializer);
        if (fromInitializer != null)
            return fromInitializer;

        Type inferredType = context.inferredType(initializer).orElse(new Type.UnknownType("<unknown>"));
        if (inferredType.kind() == Type.Kind.UNKNOWN)
            return null;

        return context.resolveQualifiedTypeName(inferredType.displayName());
    }

    private static boolean isVarTypeReference(JavaRuleContext context, SyntaxNode typeRef) {
        String text = context.canonicalTypeText(typeRef);
        return "var".equals(text);
    }

    private static String qualifiedTypeNameFromInitializerSymbol(JavaRuleContext context, SyntaxNode initializer) {
        Symbol symbol = context.resolvedSymbol(initializer).orElse(null);
        if (symbol == null)
            return null;

        return switch (symbol.kind()) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> symbol.qualifiedName().orElse(null);
            case CONSTRUCTOR -> context.ownerQualifiedName(symbol).orElse(null);
            case METHOD -> {
                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    yield null;

                SyntaxNode returnTypeRef = context.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
                yield returnTypeRef == null ? null : context.resolveQualifiedTypeName(returnTypeRef);
            }
            default -> null;
        };
    }

    private static boolean looksLikeResourceLeak(JavaRuleContext context, SyntaxNode initializer) {
        SyntaxNode expression = context.unwrapTransparentExpression(initializer);
        if (expression == null)
            return false;

        if (Objects.equals(expression.kind().id(), JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id()))
            return true;

        if (Objects.equals(expression.kind().id(), JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            String methodName = context.firstIdentifierLikeTokenText(context.selectorNameNode(expression));
            if (methodName == null)
                return false;

            // This is a very rough heuristic and can easily produce false positives and false negatives, but it's better than nothing
            return methodName.toLowerCase().contains("open") || methodName.toLowerCase().contains("create") || methodName.toLowerCase().contains("new") || methodName.toLowerCase().contains("get");
        }

        if (Objects.equals(expression.kind().id(), JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id()) ||
            Objects.equals(expression.kind().id(), JavaSyntaxKinds.NAME_EXPRESSION.id())
            || Objects.equals(expression.kind().id(), JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id()))
            return false;

        if (Objects.equals(expression.kind().id(), JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id())) {
            for (SyntaxNode child : context.directExpressionChildren(expression)) {
                if (looksLikeResourceLeak(context, child))
                    return true;
            }

            return false;
        }

        if (Objects.equals(expression.kind().id(), JavaSyntaxKinds.CAST_EXPRESSION.id())) {
            SyntaxNode castExpression = context.firstDirectExpressionChild(expression);
            return castExpression != null && looksLikeResourceLeak(context, castExpression);
        }

        return false;
    }
}
