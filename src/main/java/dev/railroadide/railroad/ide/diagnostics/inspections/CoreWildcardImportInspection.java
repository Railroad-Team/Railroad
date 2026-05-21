package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public class CoreWildcardImportInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-wildcard-imports";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.WILDCARD_IMPORT.id(),
            JavaSemanticRules.WILDCARD_IMPORT.defaultSeverity(),
            JavaSemanticRules.WILDCARD_IMPORT.messageTemplate(),
            Set.of("core", "imports"),
            CoreWildcardImportInspection::reportWildcardImports
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

    private static void reportWildcardImports(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (JavaRuleContext.ImportEntry importEntry : context.importEntries()) {
            if (importEntry.isWildcard()) {
                reporter.report(importEntry.targetNode(), importEntry.qualifiedTarget());
            }
        }
    }
}
