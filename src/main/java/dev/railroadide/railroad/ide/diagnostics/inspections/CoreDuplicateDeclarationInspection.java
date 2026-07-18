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

import java.util.*;

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
    private static final String JAVA_LAMBDA_EXPRESSION = "JAVA_LAMBDA_EXPRESSION";
    private static final String JAVA_PATTERN = "JAVA_PATTERN";
    private static final String JAVA_TRY_STATEMENT = "JAVA_TRY_STATEMENT";
    private static final String JAVA_CATCH_CLAUSE = "JAVA_CATCH_CLAUSE";
    private static final String JAVA_FOR_STATEMENT = "JAVA_FOR_STATEMENT";

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
            if (JAVA_PATTERN.equals(node.kind().id()))
                symbol = null;
            if (symbol != null) {
                String declarationKey = declarationKey(context, symbol, node);
                if (!scope.firstDeclarationByName.containsKey(declarationKey)) {
                    scope.firstDeclarationByName.put(declarationKey, node);
                } else {
                    reporter.report(node, symbol.simpleName());
                }
            }
        }

        ScopeTracker childScope = opensScope(node) ? new ScopeTracker() : scope;
        for (SyntaxNode child : node.children())
            visitScopes(context, child, childScope, reporter);
    }

    private static String declarationKey(JavaRuleContext context, Symbol symbol, SyntaxNode node) {
        if (symbol.kind() != SymbolKind.METHOD && symbol.kind() != SymbolKind.CONSTRUCTOR)
            return symbol.simpleName();

        SyntaxNode parameterList = context.directChild(node, "JAVA_PARAMETER_LIST");
        if (parameterList == null)
            return symbol.simpleName() + "()";

        List<String> parameterTypes = new ArrayList<>();
        for (SyntaxNode child : parameterList.children()) {
            if (!"JAVA_PARAMETER".equals(child.kind().id()))
                continue;
            SyntaxNode typeReference = context.directChild(child, "JAVA_TYPE_REFERENCE");
            String typeText = typeReference == null ? null : context.canonicalTypeText(typeReference);
            if (typeText == null) {
                parameterTypes.add("<unknown>");
            } else {
                parameterTypes.add(context.hasTokenKind(child, dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType.ELLIPSIS)
                    ? typeText + "[]"
                    : typeText);
            }
        }
        return symbol.simpleName() + "(" + String.join(",", parameterTypes) + ")";
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
                || JAVA_RECORD_DECLARATION.equals(kindId)
                || JAVA_LAMBDA_EXPRESSION.equals(kindId)
                || JAVA_TRY_STATEMENT.equals(kindId)
                || JAVA_CATCH_CLAUSE.equals(kindId)
                || JAVA_FOR_STATEMENT.equals(kindId);
    }

    private static final class ScopeTracker {
        private final Map<String, SyntaxNode> firstDeclarationByName = new LinkedHashMap<>();
    }
}
