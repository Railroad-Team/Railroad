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
public class CoreLowerCaseClassNameInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-lower-case-class-names";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.LOWERCASE_CLASS_NAME.id(),
            JavaSemanticRules.LOWERCASE_CLASS_NAME.defaultSeverity(),
            JavaSemanticRules.LOWERCASE_CLASS_NAME.messageTemplate(),
            Set.of("core", "naming"),
            CoreLowerCaseClassNameInspection::reportLowerCaseClassNames
        )
    );

    private static final List<String> TOP_LEVEL_TYPE_KINDS = List.of(
        "JAVA_CLASS_DECLARATION",
        "JAVA_RECORD_DECLARATION",
        "JAVA_INTERFACE_DECLARATION",
        "JAVA_ENUM_DECLARATION",
        "JAVA_ANNOTATION_TYPE_DECLARATION"
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportLowerCaseClassNames(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (String topLevelTypeKind : TOP_LEVEL_TYPE_KINDS) {
            for (SyntaxNode syntaxNode : context.nodesOfKind(topLevelTypeKind)) {
                Symbol symbol = context.declaredSymbol(syntaxNode).orElse(null);
                if (symbol == null)
                    continue;

                String name = symbol.simpleName();
                if (name.isEmpty() || Character.isLowerCase(name.charAt(0))) {
                    reporter.report(syntaxNode, name);
                }
            }
        }
    }
}
