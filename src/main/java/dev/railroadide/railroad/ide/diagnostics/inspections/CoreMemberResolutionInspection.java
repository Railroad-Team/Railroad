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
public final class CoreMemberResolutionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-member-resolution";

    private static final List<JavaInspectionRule> RULES = List.of(
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.UNRESOLVED_MEMBER.id(),
                    JavaSemanticRules.UNRESOLVED_MEMBER.defaultSeverity(),
                    JavaSemanticRules.UNRESOLVED_MEMBER.messageTemplate(),
                    Set.of("core", "members"),
                    CoreMemberResolutionInspection::reportUnresolvedMembers
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

    private static void reportUnresolvedMembers(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!"JAVA_FIELD_ACCESS_EXPRESSION".equals(node.kind().id()))
                return;
            if (context.resolvedSymbol(node).isPresent())
                return;

            SyntaxNode memberNode = context.selectorNameNode(node);
            if (memberNode == null)
                return;

            String memberName = context.canonicalQualifiedName(memberNode);
            if (memberName == null || memberName.isBlank())
                return;

            reporter.report(memberNode, memberName);
        });
    }
}
