package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRule;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreNameResolutionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-name-resolution";
    private static final String JAVA_NAME_EXPRESSION = "JAVA_NAME_EXPRESSION";

    private static final List<JavaInspectionRule> RULES = List.of(
            rule(JavaSemanticRules.UNRESOLVED_NAME),
            rule(JavaSemanticRules.AMBIGUOUS_NAME)
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static JavaInspectionRule rule(JavaSemanticRule semanticRule) {
        return new SimpleJavaInspectionRule(
                semanticRule.id(),
                semanticRule.defaultSeverity(),
                semanticRule.messageTemplate(),
                Set.of("core", "names"),
                switch (semanticRule.id()) {
                    case "SEM_UNRESOLVED_NAME" -> CoreNameResolutionInspection::reportUnresolvedNames;
                    case "SEM_AMBIGUOUS_NAME" -> CoreNameResolutionInspection::reportAmbiguousNames;
                    default -> (context, reporter) -> {
                    };
                }
        );
    }

    private static void reportUnresolvedNames(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!JAVA_NAME_EXPRESSION.equals(node.kind().id()))
                return;

            String qualifiedName = context.canonicalQualifiedName(node);
            if (qualifiedName == null || qualifiedName.isBlank())
                return;
            if (context.isSelectorNameExpression(node))
                return;
            if (isSwitchCaseLabel(node))
                return;
            if (context.resolvedSymbol(node).isPresent())
                return;
            if (isQualifiedTypePrefix(context, node))
                return;

            reporter.report(node, qualifiedName);
        });
    }

    private static boolean isSwitchCaseLabel(SyntaxNode node) {
        SyntaxNode current = node.parent().orElse(null);
        while (current != null) {
            String kindId = current.kind().id();
            if ("JAVA_SWITCH_CASE_ITEM".equals(kindId))
                return true;
            if ("JAVA_SWITCH_RULE".equals(kindId))
                return false;
            current = current.parent().orElse(null);
        }
        return false;
    }

    private static boolean isQualifiedTypePrefix(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode current = node.parent().orElse(null);
        while (current != null && "JAVA_FIELD_ACCESS_EXPRESSION".equals(current.kind().id())) {
            if (context.resolvedSymbol(current)
                    .map(symbol -> isTypeSymbol(symbol.kind()))
                    .orElse(false)) {
                return true;
            }
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

    private static void reportAmbiguousNames(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!JAVA_NAME_EXPRESSION.equals(node.kind().id()))
                return;

            String qualifiedName = context.canonicalQualifiedName(node);
            if (qualifiedName == null || qualifiedName.isBlank())
                return;

            String simpleName = context.lastSegment(qualifiedName);
            List<?> candidates = context.isMethodNameReference(node)
                    ? context.resolveStaticImportedMethods(simpleName, node, -1)
                    : context.resolveStaticImportedFields(simpleName, node);
            if (candidates.size() > 1)
                reporter.report(node, simpleName);
        });
    }
}
