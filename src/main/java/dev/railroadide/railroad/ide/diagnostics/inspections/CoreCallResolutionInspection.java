package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreCallResolutionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-call-resolution";

    private static final List<JavaInspectionRule> RULES = List.of(
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.UNRESOLVED_CALL.id(),
                    JavaSemanticRules.UNRESOLVED_CALL.defaultSeverity(),
                    JavaSemanticRules.UNRESOLVED_CALL.messageTemplate(),
                    Set.of("core", "calls"),
                    CoreCallResolutionInspection::reportUnresolvedCalls
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

    private static void reportUnresolvedCalls(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            String kindId = node.kind().id();
            if ("JAVA_METHOD_INVOCATION_EXPRESSION".equals(kindId)) {
                if (context.resolvedSymbol(node).isPresent())
                    return;

                SyntaxNode memberNode = context.selectorNameNode(node);
                String callName = memberNode == null
                        ? context.firstIdentifierLikeTokenText(node)
                        : context.canonicalQualifiedName(memberNode);
                if (callName == null || callName.isBlank())
                    return;
                reporter.report(memberNode == null ? node : memberNode, callName);
                return;
            }

            if (!"JAVA_CLASS_INSTANCE_CREATION_EXPRESSION".equals(kindId))
                return;
            if (context.resolvedSymbol(node).isPresent())
                return;

            SyntaxNode typeRef = context.directChild(node, "JAVA_TYPE_REFERENCE");
            String typeName = typeRef == null ? null : context.canonicalTypeText(typeRef);
            if (typeName == null || typeName.isBlank())
                return;
            reporter.report(typeRef, typeName);
        });
    }
}
