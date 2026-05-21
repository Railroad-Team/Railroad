package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RegisteredInspection
public class CoreConditionalExpressionWithIdenticalBranchesInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-conditional-expression-with-identical-branches";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES.id(),
            JavaSemanticRules.CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES.defaultSeverity(),
            JavaSemanticRules.CONDITIONAL_EXPRESSION_WITH_IDENTICAL_BRANCHES.messageTemplate(),
            Set.of("core", "control-flow"),
            CoreConditionalExpressionWithIdenticalBranchesInspection::reportConditionalExpressionWithIdenticalBranches
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

    private static void reportConditionalExpressionWithIdenticalBranches(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKind(JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id())) {
            List<SyntaxNode> expressions = context.directExpressionChildren(syntaxNode);
            SyntaxNode thenExpr = context.unwrapTransparentExpression(expressions.get(1));
            SyntaxNode elseExpr = context.unwrapTransparentExpression(expressions.get(2));

            if (isSameExpressionTree(thenExpr, elseExpr)) {
                reporter.report(syntaxNode, syntaxNode);
            }
        }
    }

    private static boolean isSameExpressionTree(SyntaxNode left, SyntaxNode right) {
        if (left == right)
            return true;

        if (left == null || right == null)
            return false;

        if (!Objects.equals(left.kind().id(), right.kind().id()))
            return false;

        if (left instanceof SyntaxToken leftToken && right instanceof SyntaxToken rightToken) {
            if (JavaSemanticAnalyzer.isTriviaToken(leftToken) || JavaSemanticAnalyzer.isTriviaToken(rightToken))
                return JavaSemanticAnalyzer.isTriviaToken(leftToken) == JavaSemanticAnalyzer.isTriviaToken(rightToken);

            return Objects.equals(leftToken.text(), rightToken.text())
                && Objects.equals(leftToken.kind(), rightToken.kind());
        }

        List<SyntaxNode> leftChildren = nonTriviaChildren(left);
        List<SyntaxNode> rightChildren = nonTriviaChildren(right);
        if (leftChildren.size() != rightChildren.size())
            return false;

        for (int i = 0; i < leftChildren.size(); i++) {
            if (!isSameExpressionTree(leftChildren.get(i), rightChildren.get(i)))
                return false;
        }

        return true;
    }

    private static List<SyntaxNode> nonTriviaChildren(SyntaxNode node) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token && JavaSemanticAnalyzer.isTriviaToken(token))
                continue;

            children.add(child);
        }

        return List.copyOf(children);
    }
}
