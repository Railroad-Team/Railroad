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
public class CoreSingleLetterFieldNameInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-single-letter-field-name";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.SINGLE_LETTER_FIELD_NAME.id(),
            JavaSemanticRules.SINGLE_LETTER_FIELD_NAME.defaultSeverity(),
            JavaSemanticRules.SINGLE_LETTER_FIELD_NAME.messageTemplate(),
            Set.of("core", "naming"),
            CoreSingleLetterFieldNameInspection::reportSingleLetterFieldNames
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

    private static void reportSingleLetterFieldNames(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind("JAVA_VARIABLE_DECLARATOR")) {
            Symbol symbol = context.declaredSymbol(syntaxNode).orElse(null);
            if (symbol == null)
                continue;

            SyntaxNode parent = syntaxNode.parent().orElse(null);
            if (parent == null || !"JAVA_FIELD_DECLARATION".equals(parent.kind().id()))
                continue;

            String name = symbol.simpleName();
            if (name.length() == 1) {
                reporter.report(syntaxNode, name);
            }
        }
    }
}
