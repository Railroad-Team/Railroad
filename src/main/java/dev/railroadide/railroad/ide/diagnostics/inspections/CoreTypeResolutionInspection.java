package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreTypeResolutionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-type-resolution";
    private static final String JAVA_TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";
    private static final String JAVA_INTERSECTION_TYPE_REFERENCE = "JAVA_INTERSECTION_TYPE_REFERENCE";
    private static final String JAVA_UNION_TYPE_REFERENCE = "JAVA_UNION_TYPE_REFERENCE";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNRESOLVED_TYPE.id(),
            JavaSemanticRules.UNRESOLVED_TYPE.defaultSeverity(),
            JavaSemanticRules.UNRESOLVED_TYPE.messageTemplate(),
            Set.of("core", "types"),
            CoreTypeResolutionInspection::reportUnresolvedTypeReferences
        )
    );

    private static void reportUnresolvedTypeReferences(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Set<String> availableTypeNames = context.availableTypeNames();
        context.traverse(node -> {
            String kindId = node.kind().id();
            if (!JAVA_TYPE_REFERENCE.equals(kindId)
                && !JAVA_INTERSECTION_TYPE_REFERENCE.equals(kindId)
                && !JAVA_UNION_TYPE_REFERENCE.equals(kindId)) {
                return;
            }

            Type type = context.inferredType(node).orElse(null);
            if (type == null || type.kind() != Type.Kind.DECLARED)
                return;

            String simpleName = context.simpleTypeName(type.displayName());
            if (availableTypeNames.contains(simpleName) || availableTypeNames.contains(type.displayName()))
                return;

            reporter.report(node, type.displayName());
        });
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }
}
