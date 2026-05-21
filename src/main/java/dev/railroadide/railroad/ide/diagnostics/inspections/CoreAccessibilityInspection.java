package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreAccessibilityInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-accessibility";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INACCESSIBLE_TYPE.id(),
            JavaSemanticRules.INACCESSIBLE_TYPE.defaultSeverity(),
            JavaSemanticRules.INACCESSIBLE_TYPE.messageTemplate(),
            Set.of("core", "accessibility", "types"),
            CoreAccessibilityInspection::reportInaccessibleTypes
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INACCESSIBLE_MEMBER.id(),
            JavaSemanticRules.INACCESSIBLE_MEMBER.defaultSeverity(),
            JavaSemanticRules.INACCESSIBLE_MEMBER.messageTemplate(),
            Set.of("core", "accessibility", "members"),
            CoreAccessibilityInspection::reportInaccessibleMembers
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INACCESSIBLE_CALL.id(),
            JavaSemanticRules.INACCESSIBLE_CALL.defaultSeverity(),
            JavaSemanticRules.INACCESSIBLE_CALL.messageTemplate(),
            Set.of("core", "accessibility", "calls"),
            CoreAccessibilityInspection::reportInaccessibleCalls
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

    private static void reportInaccessibleTypes(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if ("JAVA_TYPE_REFERENCE".equals(node.kind().id())) {
                String qualifiedTypeName = context.resolveQualifiedTypeName(node);
                if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
                    return;
                if (context.isTypeAccessible(qualifiedTypeName, node))
                    return;
                reporter.report(node, context.simpleTypeName(qualifiedTypeName));
                return;
            }

            if (!"JAVA_NAME_EXPRESSION".equals(node.kind().id()) || context.isSelectorNameExpression(node))
                return;

            Symbol resolved = context.resolvedSymbol(node).orElse(null);
            if (resolved == null || !context.isTypeSymbol(resolved.kind()))
                return;
            String qualifiedTypeName = resolved.qualifiedName().orElse(resolved.simpleName());
            if (context.isTypeAccessible(qualifiedTypeName, node))
                return;
            reporter.report(node, resolved.simpleName());
        });
    }

    private static void reportInaccessibleMembers(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if ("JAVA_FIELD_ACCESS_EXPRESSION".equals(node.kind().id())) {
                Symbol resolved = context.resolvedSymbol(node).orElse(null);
                if (resolved == null || resolved.kind() != SymbolKind.FIELD)
                    return;
                if (ownerTypeInaccessible(context, resolved, node) || context.isSymbolAccessible(resolved, node))
                    return;

                SyntaxNode memberNode = context.selectorNameNode(node);
                reporter.report(memberNode == null ? node : memberNode, resolved.simpleName());
                return;
            }

            if (!"JAVA_NAME_EXPRESSION".equals(node.kind().id()) || context.isSelectorNameExpression(node))
                return;

            Symbol resolved = context.resolvedSymbol(node).orElse(null);
            if (resolved == null || resolved.kind() != SymbolKind.FIELD)
                return;
            if (ownerTypeInaccessible(context, resolved, node) || context.isSymbolAccessible(resolved, node))
                return;
            reporter.report(node, resolved.simpleName());
        });
    }

    private static void reportInaccessibleCalls(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            String kindId = node.kind().id();
            if ("JAVA_METHOD_INVOCATION_EXPRESSION".equals(kindId)) {
                Symbol resolved = context.resolvedSymbol(node).orElse(null);
                if (resolved == null || resolved.kind() != SymbolKind.METHOD)
                    return;
                if (ownerTypeInaccessible(context, resolved, node) || context.isSymbolAccessible(resolved, node))
                    return;

                SyntaxNode memberNode = context.selectorNameNode(node);
                reporter.report(memberNode == null ? node : memberNode, resolved.simpleName());
                return;
            }

            if (!"JAVA_CLASS_INSTANCE_CREATION_EXPRESSION".equals(kindId))
                return;

            Symbol resolved = context.resolvedSymbol(node).orElse(null);
            if (resolved == null || resolved.kind() != SymbolKind.CONSTRUCTOR)
                return;
            if (ownerTypeInaccessible(context, resolved, node) || context.isSymbolAccessible(resolved, node))
                return;

            SyntaxNode typeRef = context.directChild(node, "JAVA_TYPE_REFERENCE");
            String displayName = typeRef == null
                ? context.ownerQualifiedName(resolved).orElse(resolved.simpleName())
                : context.canonicalTypeText(typeRef);
            if (displayName == null || displayName.isBlank())
                displayName = resolved.simpleName();
            reporter.report(typeRef == null ? node : typeRef, context.simpleTypeName(displayName));
        });
    }

    private static boolean ownerTypeInaccessible(JavaRuleContext context, Symbol symbol, SyntaxNode usageSite) {
        String ownerQualifiedName = context.ownerQualifiedName(symbol).orElse(null);
        return ownerQualifiedName != null && !context.isTypeAccessible(ownerQualifiedName, usageSite);
    }
}
