package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CoreFallthroughCaseInSwitchInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-fallthrough-case-in-switch";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.FALLTHROUGH_CASE_IN_SWITCH.id(),
                JavaSemanticRules.FALLTHROUGH_CASE_IN_SWITCH.defaultSeverity(),
                JavaSemanticRules.FALLTHROUGH_CASE_IN_SWITCH.messageTemplate(),
                Set.of("core", "switch"),
                CoreFallthroughCaseInSwitchInspection::reportFallthroughCasesInSwitch
            )
        );
    }

    private static void reportFallthroughCasesInSwitch(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode switchStatement : context.nodesOfKind(JavaSyntaxKinds.SWITCH_STATEMENT.id())) {
            List<SyntaxNode> rules = switchStatement.children().stream()
                .filter(child -> JavaSyntaxKinds.SWITCH_RULE.id().equals(child.kind().id()))
                .toList();
            if (rules.size() < 2)
                continue;

            for (int i = 0; i < rules.size() - 1; i++) {
                SyntaxNode switchRule = rules.get(i);
                if (CoreDefiniteAssignmentInspection.isArrowSwitchRule(switchRule)) // TODO: Possibly extract this method into JavaRuleContext
                    continue;

                if (!canRuleFallThrough(context, switchRule))
                    continue;

                SyntaxNode label = context.directChild(switchRule, JavaSyntaxKinds.SWITCH_LABEL.id());
                if (label == null)
                    continue;

                reporter.report(label, labelText(context, label));
            }
        }
    }

    private static boolean canRuleFallThrough(JavaRuleContext context, SyntaxNode switchRule) {
        List<SyntaxNode> body = ruleBody(switchRule);
        if (body.isEmpty())
            return false;

        boolean reachable = true;
        for (SyntaxNode statement : body) {
            if (!reachable)
                return false;

            reachable = CoreUnreachableCodeInspection.completesNormally(context, null, statement);
        }

        return reachable;
    }

    private static List<SyntaxNode> ruleBody(SyntaxNode rule) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : rule.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if (Objects.equals(child.kind().id(), JavaSyntaxKinds.SWITCH_LABEL.id()))
                continue;

            children.add(child);
        }

        return List.copyOf(children);
    }

    private static String labelText(JavaRuleContext context, SyntaxNode label) {
        String text = context.firstIdentifierLikeTokenText(label);
        return text == null ? "<case>" : text;
    }
}
