package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Provides built-in Java inspections for type resolution and local variable type inference.
 */
@RegisteredInspection
public final class CoreTypeResolutionInspection implements JavaInspectionRuleProvider {
    /**
     * Stable identifier used to register this inspection provider.
     */
    public static final String ID = "railroad:core-type-resolution";
    private static final String JAVA_TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";
    private static final String JAVA_INTERSECTION_TYPE_REFERENCE = "JAVA_INTERSECTION_TYPE_REFERENCE";
    private static final String JAVA_UNION_TYPE_REFERENCE = "JAVA_UNION_TYPE_REFERENCE";
    private static final String VAR_KEYWORD = JavaSyntaxKinds.tokenKind(JavaTokenType.VAR_KEYWORD).id();

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNRESOLVED_TYPE.id(),
            JavaSemanticRules.UNRESOLVED_TYPE.defaultSeverity(),
            JavaSemanticRules.UNRESOLVED_TYPE.messageTemplate(),
            Set.of("core", "types"),
            CoreTypeResolutionInspection::reportUnresolvedTypeReferences),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.SELF_REFERENCING_VAR.id(),
            JavaSemanticRules.SELF_REFERENCING_VAR.defaultSeverity(),
            JavaSemanticRules.SELF_REFERENCING_VAR.messageTemplate(),
            Set.of("core", "types"),
            CoreTypeResolutionInspection::reportSelfReferencingInitializers));

    private static void reportUnresolvedTypeReferences(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Set<String> availableTypeNames = context.availableTypeNames();
        context.traverse(node -> {
            String kindId = node.kind().id();
            if (!JAVA_TYPE_REFERENCE.equals(kindId)
                && !JAVA_INTERSECTION_TYPE_REFERENCE.equals(kindId)
                && !JAVA_UNION_TYPE_REFERENCE.equals(kindId))
                return;

            Type type = context.inferredType(node).orElse(null);
            if (type == null || type.kind() != JavaTypes.Kind.DECLARED)
                return;

            String simpleName = context.simpleTypeName(type.displayName());
            if ("var".equals(simpleName))
                return;

            if (availableTypeNames.contains(simpleName) || availableTypeNames.contains(type.displayName()))
                return;

            reporter.report(node, type.displayName());
        });
    }

    private static void reportSelfReferencingInitializers(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter
    ) {
        Set<SyntaxNode> reported = Collections.newSetFromMap(new IdentityHashMap<>());
        context.traverse(node -> {
            if (!JavaSyntaxKinds.NAME_EXPRESSION.equals(node.kind()))
                return;

            Symbol symbol = context.resolvedSymbol(node).orElse(null);
            if (symbol == null || symbol.kind() != JavaSymbolKind.LOCAL_VARIABLE)
                return;

            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null || node.start() < declaration.start() || node.end() > declaration.end())
                return;

            SyntaxNode statement = declaration.parent().orElse(null);
            if (statement == null || !JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.equals(statement.kind())
                || context.directChild(statement, VAR_KEYWORD) == null || !reported.add(declaration))
                return;

            String name = symbol.simpleName();
            SyntaxNode identifier = declaration.children().stream()
                .filter(
                    child -> child instanceof SyntaxToken && name.equals(context.firstIdentifierLikeTokenText(child)))
                .findFirst().orElse(declaration);
            reporter.report(identifier, name);
        });
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }
}
