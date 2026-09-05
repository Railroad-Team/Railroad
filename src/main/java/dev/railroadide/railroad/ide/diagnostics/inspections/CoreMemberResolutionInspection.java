package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

/**
 * Provides built-in Java inspections for {@link JavaSemanticRules#UNRESOLVED_MEMBER}.
 */
@RegisteredInspection
public final class CoreMemberResolutionInspection implements JavaInspectionRuleProvider {
    /**
     * Stable identifier used to register this inspection provider.
     */
    public static final String ID = "railroad:core-member-resolution";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNRESOLVED_MEMBER.id(),
            JavaSemanticRules.UNRESOLVED_MEMBER.defaultSeverity(),
            JavaSemanticRules.UNRESOLVED_MEMBER.messageTemplate(),
            Set.of("core", "members"),
            CoreMemberResolutionInspection::reportUnresolvedMembers));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportUnresolvedMembers(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!"JAVA_FIELD_ACCESS_EXPRESSION".equals(node.kind().id()))
                return;
            if (context.resolvedSymbol(node).isPresent())
                return;
            if (isQualifiedTypePrefix(context, node))
                return;

            SyntaxNode memberNode = context.selectorNameNode(node);
            if (memberNode == null)
                return;

            String memberName = context.canonicalQualifiedName(memberNode);
            if (memberName == null || memberName.isBlank())
                return;
            if (isArrayLengthAccess(context, node, memberName))
                return;

            reporter.report(memberNode, memberName);
        });
    }

    private static boolean isQualifiedTypePrefix(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode current = node.parent().orElse(null);
        while (current != null && "JAVA_FIELD_ACCESS_EXPRESSION".equals(current.kind().id())) {
            if (context.resolvedSymbol(current)
                .map(symbol -> isTypeSymbol(symbol.kind()))
                .orElse(false))
                return true;
            current = current.parent().orElse(null);
        }
        return false;
    }

    private static boolean isTypeSymbol(SymbolKind kind) {
        return kind == SymbolKind.CLASS
            || kind == SymbolKind.INTERFACE
            || kind == SymbolKind.ENUM
            || kind == SymbolKind.ANNOTATION
            || kind == SymbolKind.RECORD;
    }

    private static boolean isArrayLengthAccess(JavaRuleContext context, SyntaxNode fieldAccess, String memberName) {
        if (!"length".equals(memberName))
            return false;

        SyntaxNode receiver = context.invocationReceiver(fieldAccess);
        return receiver != null
            && context.inferredType(receiver).map(type -> type.kind() == Type.Kind.ARRAY).orElse(false);
    }
}
