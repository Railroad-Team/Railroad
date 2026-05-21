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
public class CoreFieldNameSameAsClassInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-field-name-same-as-class";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.FIELD_NAME_SAME_AS_CLASS_NAME.id(),
            JavaSemanticRules.FIELD_NAME_SAME_AS_CLASS_NAME.defaultSeverity(),
            JavaSemanticRules.FIELD_NAME_SAME_AS_CLASS_NAME.messageTemplate(),
            Set.of("core", "naming"),
            CoreFieldNameSameAsClassInspection::reportFieldNameSameAsClass
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

    private static void reportFieldNameSameAsClass(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind("JAVA_VARIABLE_DECLARATOR")) {
            SyntaxNode parent = syntaxNode.parent().orElse(null);
            if (parent == null || !"JAVA_FIELD_DECLARATION".equals(parent.kind().id()))
                continue;

            Symbol symbol = context.declaredSymbol(syntaxNode).orElse(null);
            if (symbol == null)
                continue;

            Symbol classSymbol = context.enclosingTypeSymbol(syntaxNode).orElse(null);
            if (classSymbol == null)
                continue;

            if (symbol.simpleName().equals(classSymbol.simpleName())) {
                reporter.report(syntaxNode, symbol.simpleName(), classSymbol.simpleName());
            }
        }
    }
}
