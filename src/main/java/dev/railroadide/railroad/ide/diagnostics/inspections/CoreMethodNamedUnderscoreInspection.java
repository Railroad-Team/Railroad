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
public class CoreMethodNamedUnderscoreInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-method-named-underscore";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.METHOD_NAMED_UNDERSCORE.id(),
            JavaSemanticRules.METHOD_NAMED_UNDERSCORE.defaultSeverity(),
            JavaSemanticRules.METHOD_NAMED_UNDERSCORE.messageTemplate(),
            Set.of("core", "naming"),
            CoreMethodNamedUnderscoreInspection::reportMethodNamedUnderscore
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

    private static void reportMethodNamedUnderscore(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind("JAVA_METHOD_DECLARATION")) {
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
