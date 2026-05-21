package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public class CoreParameterNamedUnderscoreInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-parameter-named-underscore";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.PARAMETER_NAME_UNDERSCORE.id(),
            JavaSemanticRules.PARAMETER_NAME_UNDERSCORE.defaultSeverity(),
            JavaSemanticRules.PARAMETER_NAME_UNDERSCORE.messageTemplate(),
            Set.of("core", "naming"),
            CoreParameterNamedUnderscoreInspection::reportParameterNamedUnderscore
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

    private static void reportParameterNamedUnderscore(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind("JAVA_PARAMETER")) {
            Symbol symbol = context.declaredSymbol(syntaxNode).orElse(null);
            if (symbol == null)
                continue;

            String name = symbol.simpleName();
            if (name.equalsIgnoreCase("_")) {
                reporter.report(syntaxNode, name);
            }
        }
    }
}
