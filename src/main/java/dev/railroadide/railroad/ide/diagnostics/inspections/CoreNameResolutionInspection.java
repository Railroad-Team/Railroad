package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRule;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
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
            if (context.resolvedSymbol(node).isPresent())
                return;

            reporter.report(node, qualifiedName);
        });
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
