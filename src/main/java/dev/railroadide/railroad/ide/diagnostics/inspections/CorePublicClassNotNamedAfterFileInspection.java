package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

@RegisteredInspection
public class CorePublicClassNotNamedAfterFileInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-public-class-not-named-after-file";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.PUBLIC_CLASS_NOT_NAMED_AFTER_FILE.id(),
            JavaSemanticRules.PUBLIC_CLASS_NOT_NAMED_AFTER_FILE.defaultSeverity(),
            JavaSemanticRules.PUBLIC_CLASS_NOT_NAMED_AFTER_FILE.messageTemplate(),
            Set.of("core", "naming"),
            CorePublicClassNotNamedAfterFileInspection::reportPublicClassNotNamedAfterFile
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

    private static void reportPublicClassNotNamedAfterFile(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        String fileName = context.filePath().getFileName().toString();
        if (!fileName.endsWith(".java"))
            return;

        String expectedClassName = fileName.substring(0, fileName.length() - ".java".length());
        for (SyntaxNode child : context.syntaxTree().root().children()) {
            if (!TOP_LEVEL_TYPE_KINDS.contains(child.kind().id()))
                continue;

            if (!context.hasDirectModifierToken(child, JavaTokenType.PUBLIC_KEYWORD))
                continue;

            Symbol symbol = context.declaredSymbol(child).orElse(null);
            if (symbol == null)
                continue;

            String actualTypeName = symbol.simpleName();
            if (!expectedClassName.equals(actualTypeName)) {
                reporter.report(child, actualTypeName, expectedClassName);
            }
        }
    }
}
