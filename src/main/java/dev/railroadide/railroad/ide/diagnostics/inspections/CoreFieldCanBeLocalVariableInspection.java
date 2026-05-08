package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.*;

public class CoreFieldCanBeLocalVariableInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-field-can-be-local-variable";

    private static final List<JavaInspectionRule> RULES = List.of(new SimpleJavaInspectionRule(JavaSemanticRules.FIELD_CAN_BE_LOCAL_VARIABLE.id(), JavaSemanticRules.FIELD_CAN_BE_LOCAL_VARIABLE.defaultSeverity(), JavaSemanticRules.FIELD_CAN_BE_LOCAL_VARIABLE.messageTemplate(), Set.of("core", "field"), CoreFieldCanBeLocalVariableInspection::reportFieldCanBeLocalVariable));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return RULES;
    }

    private static void reportFieldCanBeLocalVariable(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Map<Symbol, Set<SyntaxNode>> callablesBySymbol = new HashMap<>();
        Set<Symbol> referencesOutsideCallable = new HashSet<>();

        context.traverse(node -> {
            if (!node.kind().id().equals(JavaSyntaxKinds.NAME_EXPRESSION.id())) return;

            Symbol symbol = context.resolvedSymbol(node).orElse(null);
            if (symbol == null) return;

            SyntaxNode callable = context.nearestEnclosingCallableOrLambda(node);
            if (callable == null) {
                referencesOutsideCallable.add(symbol);
                return;
            }

            callablesBySymbol.computeIfAbsent(symbol, k -> new HashSet<>()).add(callable);
        });

        for (SyntaxNode node : context.nodesOfKind(JavaSyntaxKinds.VARIABLE_DECLARATOR.id())) {
            SyntaxNode parent = node.parent().orElse(null);

            if (parent == null || !JavaSyntaxKinds.FIELD_DECLARATION.id().equals(parent.kind().id())) continue;

            if (!context.hasDirectModifierToken(parent, JavaTokenType.PRIVATE_KEYWORD) ||
                context.hasDirectModifierToken(parent, JavaTokenType.STATIC_KEYWORD) ||
                context.hasDirectModifierToken(parent, JavaTokenType.VOLATILE_KEYWORD) ||
                context.hasDirectModifierToken(parent, JavaTokenType.TRANSIENT_KEYWORD))
                continue;

            Symbol declaredSymbol = context.declaredSymbol(node).orElse(null);

            if (declaredSymbol == null) continue;

            Set<SyntaxNode> callables = callablesBySymbol.getOrDefault(declaredSymbol, Set.of());
            if (referencesOutsideCallable.contains(declaredSymbol) || callables.size() != 1) continue;

            reporter.report(node, declaredSymbol.simpleName());
        }
    }
}
