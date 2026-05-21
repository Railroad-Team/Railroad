package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisteredInspection
public final class CoreDuplicateDeclarationInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-duplicate-declaration";
    private static final String JAVA_BLOCK = "JAVA_BLOCK";
    private static final String JAVA_METHOD_DECLARATION = "JAVA_METHOD_DECLARATION";
    private static final String JAVA_CONSTRUCTOR_DECLARATION = "JAVA_CONSTRUCTOR_DECLARATION";
    private static final String JAVA_CLASS_DECLARATION = "JAVA_CLASS_DECLARATION";
    private static final String JAVA_INTERFACE_DECLARATION = "JAVA_INTERFACE_DECLARATION";
    private static final String JAVA_ENUM_DECLARATION = "JAVA_ENUM_DECLARATION";
    private static final String JAVA_ANNOTATION_TYPE_DECLARATION = "JAVA_ANNOTATION_TYPE_DECLARATION";
    private static final String JAVA_RECORD_DECLARATION = "JAVA_RECORD_DECLARATION";

    private static final List<JavaInspectionRule> RULES = List.of(
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.DUPLICATE_DECLARATION.id(),
                    JavaSemanticRules.DUPLICATE_DECLARATION.defaultSeverity(),
                    JavaSemanticRules.DUPLICATE_DECLARATION.messageTemplate(),
                    Set.of("core", "declarations"),
                    (context, reporter) -> visitScopes(context, context.syntaxTree().root(), new ScopeTracker(), reporter)
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
    private static void visitScopes(JavaRuleContext context, SyntaxNode node, ScopeTracker scope, JavaInspectionRuleReporter reporter) {
        Symbol symbol = context.declaredSymbol(node).orElse(null);
        if (symbol != null && symbol.kind() != SymbolKind.IMPORT) {
            if (!scope.firstDeclarationByName.containsKey(symbol.simpleName())) {
                scope.firstDeclarationByName.put(symbol.simpleName(), node);
            } else {
                reporter.report(node, symbol.simpleName());
            }
        }

        ScopeTracker childScope = opensScope(node) ? new ScopeTracker() : scope;
        for (SyntaxNode child : node.children())
            visitScopes(context, child, childScope, reporter);
    }

    private static boolean opensScope(SyntaxNode node) {
        String kindId = node.kind().id();
        return JAVA_BLOCK.equals(kindId)
                || JAVA_METHOD_DECLARATION.equals(kindId)
                || JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)
                || JAVA_CLASS_DECLARATION.equals(kindId)
                || JAVA_INTERFACE_DECLARATION.equals(kindId)
                || JAVA_ENUM_DECLARATION.equals(kindId)
                || JAVA_ANNOTATION_TYPE_DECLARATION.equals(kindId)
                || JAVA_RECORD_DECLARATION.equals(kindId);
    }

    private static final class ScopeTracker {
        private final Map<String, SyntaxNode> firstDeclarationByName = new LinkedHashMap<>();
    }
}
