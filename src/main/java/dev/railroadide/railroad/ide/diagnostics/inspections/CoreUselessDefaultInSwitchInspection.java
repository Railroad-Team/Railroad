package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@RegisteredInspection
public class CoreUselessDefaultInSwitchInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-useless-default-in-switch";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.USELESS_DEFAULT_IN_SWITCH.id(),
            JavaSemanticRules.USELESS_DEFAULT_IN_SWITCH.defaultSeverity(),
            JavaSemanticRules.USELESS_DEFAULT_IN_SWITCH.messageTemplate(),
            Set.of("core", "control-flow"),
            CoreUselessDefaultInSwitchInspection::reportUselessDefaultInSwitch
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

    private static void reportUselessDefaultInSwitch(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode switchStatement : context.nodesOfKind("JAVA_SWITCH_STATEMENT")) {
            SyntaxNode defaultSyntaxNode = null;
            boolean hasNonDefault = false;
            for (SyntaxNode rule : switchStatement.children()) {
                if (!Objects.equals(rule.kind().id(), "JAVA_SWITCH_RULE"))
                    continue;

                SyntaxNode label = context.directChild(rule, "JAVA_SWITCH_LABEL");
                if (label == null)
                    continue;

                SyntaxNode node = findDefaultSyntaxNode(label);
                if (node == null) {
                    hasNonDefault = true;
                } else {
                    defaultSyntaxNode = node;
                }
            }

            if (defaultSyntaxNode != null && !hasNonDefault) {
                reporter.report(defaultSyntaxNode);
            }
        }
    }

    private static SyntaxNode findDefaultSyntaxNode(SyntaxNode label) {
        for (SyntaxNode child : label.children()) {
            if (child instanceof SyntaxToken token && "default".equals(token.text()))
                return child;
        }

        return null;
    }
}
