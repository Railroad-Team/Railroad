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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

            AtomicBoolean isReferencedOutsideMethod = new AtomicBoolean(false);
            LinkedHashSet<SyntaxNode> methodContainingReferences = new LinkedHashSet<>();
            context.traverse(descendant -> {
                if (!descendant.kind().id().equals(JavaSyntaxKinds.NAME_EXPRESSION.id())) return;

                Symbol resolvedSymbol = context.resolvedSymbol(descendant).orElse(null);

                if (resolvedSymbol == null) return;

                if (!resolvedSymbol.equals(declaredSymbol)) return;

                SyntaxNode methodNode = context.nearestEnclosingCallableOrLambda(descendant);

                if (methodNode == null) {
                    isReferencedOutsideMethod.set(true);
                    return;
                }

                methodContainingReferences.add(methodNode);
            });

            if (isReferencedOutsideMethod.get() || methodContainingReferences.size() != 1) continue;

            reporter.report(node, declaredSymbol.simpleName());
        }
    }

    public static class Test {


        public Test() {
            int testVar = 1;
            System.out.println(testVar);
            testVar = 2;
            System.out.println(testVar);
        }
    }
}
