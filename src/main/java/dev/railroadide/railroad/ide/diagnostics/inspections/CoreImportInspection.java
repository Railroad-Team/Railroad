package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRule;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisteredInspection
public final class CoreImportInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-imports";

    private static final List<JavaInspectionRule> RULES = List.of(
            rule(JavaSemanticRules.DUPLICATE_IMPORT),
            rule(JavaSemanticRules.AMBIGUOUS_IMPORT),
            rule(JavaSemanticRules.UNRESOLVED_IMPORT)
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
                Set.of("core", "imports"),
                switch (semanticRule.id()) {
                    case "SEM_DUPLICATE_IMPORT" -> CoreImportInspection::reportDuplicateImports;
                    case "SEM_AMBIGUOUS_IMPORT" -> CoreImportInspection::reportAmbiguousImports;
                    case "SEM_UNRESOLVED_IMPORT" -> CoreImportInspection::reportUnresolvedImports;
                    default -> (context, reporter) -> {
                    };
                }
        );
    }

    private static void reportDuplicateImports(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Map<String, JavaRuleContext.ImportEntry> seen = new LinkedHashMap<>();
        for (JavaRuleContext.ImportEntry importEntry : context.importEntries()) {
            String key = (importEntry.isStatic() ? "static " : "") + importEntry.qualifiedTarget();
            if (seen.putIfAbsent(key, importEntry) != null)
                reporter.report(importEntry.targetNode(), importEntry.qualifiedTarget());
        }
    }

    private static void reportAmbiguousImports(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Map<String, JavaRuleContext.ImportEntry> seenBySimpleName = new LinkedHashMap<>();
        for (JavaRuleContext.ImportEntry importEntry : context.importEntries()) {
            if (importEntry.isStatic() || importEntry.isWildcard())
                continue;

            JavaRuleContext.ImportEntry previous = seenBySimpleName.putIfAbsent(importEntry.importedName(), importEntry);
            if (previous == null)
                continue;
            if (previous.qualifiedTarget().equals(importEntry.qualifiedTarget()))
                continue;

            reporter.report(
                    importEntry.targetNode(),
                    importEntry.importedName(),
                    previous.qualifiedTarget(),
                    importEntry.qualifiedTarget()
            );
        }
    }

    private static void reportUnresolvedImports(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        JavaRuleContext.ImportIndex importIndex = context.importIndex();
        for (JavaRuleContext.ImportEntry importEntry : context.importEntries()) {
            if (importEntry.isStatic()) {
                if (!importIndex.isResolvableType(importEntry.ownerName())) {
                    reporter.report(importEntry.targetNode(), importEntry.ownerName());
                    continue;
                }
                if (!importEntry.isWildcard() && !importIndex.hasResolvableStaticMember(importEntry.ownerName(), importEntry.importedName()))
                    reporter.report(importEntry.targetNode(), importEntry.qualifiedTarget());
                continue;
            }

            if (importEntry.isWildcard()) {
                if (!importIndex.isResolvablePackagePrefix(importEntry.ownerName()))
                    reporter.report(importEntry.targetNode(), importEntry.ownerName() + ".*");
                continue;
            }

            if (!importIndex.isResolvableType(importEntry.qualifiedTarget()))
                reporter.report(importEntry.targetNode(), importEntry.qualifiedTarget());
        }
    }
}
