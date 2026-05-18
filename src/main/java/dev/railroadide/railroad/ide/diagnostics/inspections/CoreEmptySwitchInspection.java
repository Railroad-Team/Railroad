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
public class CoreEmptySwitchInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-empty-switch";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.EMPTY_SWITCH.id(),
            JavaSemanticRules.EMPTY_SWITCH.defaultSeverity(),
            JavaSemanticRules.EMPTY_SWITCH.messageTemplate(),
            Set.of("core", "control-flow"),
            CoreEmptySwitchInspection::reportEmptySwitch
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

    private static void reportEmptySwitch(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind("JAVA_SWITCH_STATEMENT")) {
            if (isEmptySwitch(syntaxNode))
                reporter.report(syntaxNode);
        }
    }

    private static boolean isEmptySwitch(SyntaxNode switchNode) {
        for (SyntaxNode child : switchNode.children()) {
            if ("JAVA_SWITCH_LABEL".equals(child.kind().id()) || "JAVA_SWITCH_RULE".equals(child.kind().id()))
                return false;
        }

        return true;
    }
}
