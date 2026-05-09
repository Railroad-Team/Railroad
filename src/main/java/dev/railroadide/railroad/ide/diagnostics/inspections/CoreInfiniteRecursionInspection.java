package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CoreInfiniteRecursionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-infinite-recursion";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.INFINITE_RECURSION.id(),
                JavaSemanticRules.INFINITE_RECURSION.defaultSeverity(),
                JavaSemanticRules.INFINITE_RECURSION.messageTemplate(),
                Set.of("core", "recursion"),
                CoreInfiniteRecursionInspection::reportInfiniteRecursion
            )
        );
    }

    private static void reportInfiniteRecursion(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode method : context.nodesOfKind(JavaSyntaxKinds.METHOD_DECLARATION.id())) {
            SyntaxNode body = context.directChild(method, JavaSyntaxKinds.BLOCK.id());
            if (body == null)
                continue;

            Symbol methodSymbol = context.declaredSymbol(method).orElse(null);
            if (methodSymbol == null)
                continue;

            String methodName = methodSymbol.simpleName();

            if (isImmediatelyInfiniteRecursive(context, methodSymbol, body)) {
                reporter.report(method, methodName);
            }
        }
    }

    private static boolean isImmediatelyInfiniteRecursive(JavaRuleContext context, Symbol methodSymbol, SyntaxNode body) {
        FlowAnalysisResult result = analyzeFlow(context, body, methodSymbol);
        return result.isInfiniteRecursive()
            && !result.canCompleteNormally()
            && !result.canExitWithoutRecursing();
    }

    private static FlowAnalysisResult analyzeFlow(JavaRuleContext context, SyntaxNode node, Symbol methodSymbol) {
        String kindId = node.kind().id();
        return switch (kindId) {
            case "JAVA_BLOCK" -> analyzeBlock(context, node, methodSymbol);
            case "JAVA_RETURN_STATEMENT" -> analyzeReturnStatement(context, node, methodSymbol);
            case "JAVA_EXPRESSION_STATEMENT" -> analyzeExpressionStatement(context, node, methodSymbol);
            case "JAVA_IF_STATEMENT" -> analyzeIfStatement(context, node, methodSymbol);
            case "JAVA_FOR_STATEMENT", "JAVA_WHILE_STATEMENT", "JAVA_DO_WHILE_STATEMENT" ->
                FlowAnalysisResult.fallsThrough(); // Conservatively assume loops can complete normally and don't cause infinite recursion
            case "JAVA_THROW_STATEMENT" ->
                FlowAnalysisResult.stops(); // Throwing an exception stops normal execution and thus can't be part of an infinite recursion
            default -> FlowAnalysisResult.fallsThrough();
        };
    }

    private static FlowAnalysisResult analyzeBlock(JavaRuleContext context, SyntaxNode block, Symbol methodSymbol) {
        boolean canExitWithoutRecursing = false;
        for (SyntaxNode child : block.children()) {
            if (child instanceof SyntaxToken)
                continue;

            FlowAnalysisResult result = analyzeFlow(context, child, methodSymbol);
            if (result.canExitWithoutRecursing())
                canExitWithoutRecursing = true;

            if (result.isInfiniteRecursive()) {
                if (canExitWithoutRecursing)
                    return new FlowAnalysisResult(false, false, true);

                return result;
            }

            if (!result.canCompleteNormally())
                return new FlowAnalysisResult(false, false, canExitWithoutRecursing || result.canExitWithoutRecursing());
        }

        return new FlowAnalysisResult(false, true, canExitWithoutRecursing);
    }

    private static FlowAnalysisResult analyzeReturnStatement(JavaRuleContext context, SyntaxNode returnStatement, Symbol methodSymbol) {
        for (SyntaxNode child : returnStatement.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if (containsDirectSelfCall(context, child, methodSymbol))
                return FlowAnalysisResult.recurses();
        }

        return FlowAnalysisResult.stops();
    }

    private static FlowAnalysisResult analyzeExpressionStatement(JavaRuleContext context, SyntaxNode expressionStatement, Symbol methodSymbol) {
        for (SyntaxNode child : expressionStatement.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if (containsDirectSelfCall(context, child, methodSymbol))
                return FlowAnalysisResult.recurses();
        }

        return FlowAnalysisResult.fallsThrough();
    }

    private static FlowAnalysisResult analyzeIfStatement(JavaRuleContext context, SyntaxNode ifStatement, Symbol methodSymbol) {
        SyntaxNode thenBranch = context.thenBranchOf(ifStatement);
        SyntaxNode elseBranch = context.elseBranchOf(ifStatement);

        if (thenBranch == null)
            return FlowAnalysisResult.fallsThrough();

        FlowAnalysisResult thenResult = analyzeFlow(context, thenBranch, methodSymbol);
        if (elseBranch == null) {
            return new FlowAnalysisResult(
                false,
                true,
                thenResult.canExitWithoutRecursing()
            );
        }

        FlowAnalysisResult elseResult = analyzeFlow(context, elseBranch, methodSymbol);
        return new FlowAnalysisResult(
            thenResult.isInfiniteRecursive() && elseResult.isInfiniteRecursive(),
            thenResult.canCompleteNormally() || elseResult.canCompleteNormally(),
            thenResult.canExitWithoutRecursing() || elseResult.canExitWithoutRecursing()
        );
    }

    private static boolean containsDirectSelfCall(JavaRuleContext context, SyntaxNode node, Symbol methodSymbol) {
        String kindId = node.kind().id();
        if (Objects.equals(JavaSyntaxKinds.LAMBDA_EXPRESSION.id(), kindId))
            return false; // Don't consider method calls within lambda expressions as contributing to infinite recursion

        if (Objects.equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id(), kindId)) {
            Symbol calledSymbol = context.resolvedSymbol(node).orElse(null);
            if (calledSymbol != null && isSameMethod(context, methodSymbol, calledSymbol))
                return true;
        }

        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if (containsDirectSelfCall(context, child, methodSymbol))
                return true;
        }

        return false;
    }

    private static boolean isSameMethod(JavaRuleContext context, Symbol method1, Symbol method2) {
        String name1 = method1.qualifiedName().orElse(null);
        String name2 = method2.qualifiedName().orElse(null);

        SyntaxNode paramList1 = method1.declaration()
            .map(decl -> context.directChild(decl, JavaSyntaxKinds.PARAMETER_LIST.id()))
            .orElse(null);

        SyntaxNode paramList2 = method2.declaration()
            .map(decl -> context.directChild(decl, JavaSyntaxKinds.PARAMETER_LIST.id()))
            .orElse(null);

        if (name1 == null || name2 == null || paramList1 == null || paramList2 == null)
            return false;

        if (!name1.equals(name2))
            return false;

        List<SyntaxNode> params1 = paramList1.children()
            .stream()
            .filter(node -> Objects.equals(node.kind().id(), JavaSyntaxKinds.PARAMETER.id()))
            .toList();
        List<SyntaxNode> params2 = paramList2.children()
            .stream()
            .filter(node -> Objects.equals(node.kind().id(), JavaSyntaxKinds.PARAMETER.id()))
            .toList();

        if (params1.size() != params2.size())
            return false;

        for (int i = 0; i < params1.size(); i++) {
            String type1 = context.resolveQualifiedTypeName(context.directChild(params1.get(i), JavaSyntaxKinds.TYPE_REFERENCE.id()));
            String type2 = context.resolveQualifiedTypeName(context.directChild(params2.get(i), JavaSyntaxKinds.TYPE_REFERENCE.id()));

            if (!Objects.equals(type1, type2))
                return false;
        }

        return true;
    }

    private record FlowAnalysisResult(boolean isInfiniteRecursive, boolean canCompleteNormally, boolean canExitWithoutRecursing) {
        private static FlowAnalysisResult recurses() {
            return new FlowAnalysisResult(true, false, false);
        }

        private static FlowAnalysisResult stops() {
            return new FlowAnalysisResult(false, false, true);
        }

        private static FlowAnalysisResult fallsThrough() {
            return new FlowAnalysisResult(false, true, false);
        }
    }
}
