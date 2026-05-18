package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RegisteredInspection
public class CoreUnreachableCodeInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-unreachable-code";

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNREACHABLE_CODE.id(),
            JavaSemanticRules.UNREACHABLE_CODE.defaultSeverity(),
            JavaSemanticRules.UNREACHABLE_CODE.messageTemplate(),
            Set.of("core", "control-flow"),
            CoreUnreachableCodeInspection::reportUnreachableCode
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

    private static void reportUnreachableCode(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        analyzeExecutableBodies(context, reporter, "JAVA_METHOD_DECLARATION");
        analyzeExecutableBodies(context, reporter, "JAVA_CONSTRUCTOR_DECLARATION");

        analyzeExecutableBodies(context, reporter, "JAVA_INSTANCE_INITIALIZER");
        analyzeExecutableBodies(context, reporter, "JAVA_STATIC_INITIALIZER");
        analyzeExecutableBodies(context, reporter, "JAVA_LAMBDA_EXPRESSION");
    }

    private static void analyzeExecutableBodies(JavaRuleContext context, JavaInspectionRuleReporter reporter, String bodyKind) {
        for (SyntaxNode owner : context.nodesOfKind(bodyKind)) {
            SyntaxNode block = context.directChild(owner, "JAVA_BLOCK");
            if (block != null) {
                analyzeBlock(context, reporter, block);
            }
        }
    }

    private static boolean analyzeBlock(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode block) {
        boolean reachable = true;
        for (SyntaxNode statement : directExecutableChildren(block)) {
            if (!reachable) {
                if(reporter != null) {
                    reporter.report(statement);
                }

                continue;
            }

            reachable = completesNormally(context, reporter, statement);
        }

        return reachable;
    }

    public static boolean completesNormally(JavaRuleContext context, @Nullable JavaInspectionRuleReporter reporter, SyntaxNode statement) {
        String kindId = statement.kind().id();
        return switch (kindId) {
            case "JAVA_RETURN_STATEMENT",
                 "JAVA_THROW_STATEMENT",
                 "JAVA_BREAK_STATEMENT",
                 "JAVA_CONTINUE_STATEMENT" -> false;
            case "JAVA_BLOCK" -> analyzeBlock(context, reporter, statement);
            case "JAVA_IF_STATEMENT" -> analyzeIfStatement(context, reporter, statement);
            case "JAVA_WHILE_STATEMENT" -> analyzeWhileStatement(context, reporter, statement);
            case "JAVA_DO_WHILE_STATEMENT" -> analyzeDoWhileStatement(context, reporter, statement);
            case "JAVA_FOR_STATEMENT",
                 "JAVA_ENHANCED_FOR_STATEMENT" -> analyzeForStatement(context, reporter, statement);
            case "JAVA_SWITCH_STATEMENT" -> analyzeSwitchStatement(context, reporter, statement);
            default -> true;
        };
    }

    private static boolean analyzeWhileStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode whileStatement) {
        SyntaxNode body = firstDirectStatementChild(whileStatement);
        if (body == null)
            return true;

        completesNormally(context, reporter, body);
        return true;
    }

    private static boolean analyzeDoWhileStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode doWhileStatement) {
        SyntaxNode body = firstDirectStatementChild(doWhileStatement);
        if (body == null)
            return true;

        completesNormally(context, reporter, body);
        return true;
    }

    private static boolean analyzeForStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode forStatement) {
        SyntaxNode body = lastDirectStatementChild(forStatement);
        if (body == null)
            return true;

        completesNormally(context, reporter, body);
        return true;
    }

    private static boolean analyzeSwitchStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode switchStatement) {
        for (SyntaxNode child : switchStatement.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if ("JAVA_SWITCH_RULE".equals(child.kind().id())) {
                analyzeSwitchRule(context, reporter, child);
            }
        }

        return true;
    }

    private static void analyzeSwitchRule(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode switchRule) {
        boolean reachable = true;
        for (SyntaxNode child : switchRule.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if ("JAVA_SWITCH_LABEL".equals(child.kind().id()))
                continue;

            if (!reachable) {
                if(reporter != null) {
                    reporter.report(child);
                }

                continue;
            }

            reachable = completesNormally(context, reporter, child);
        }
    }

    private static boolean analyzeIfStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode ifStatement) {
        List<SyntaxNode> statements = directStatementChildren(ifStatement);
        if (statements.isEmpty())
            return true;

        SyntaxNode thenBranch = statements.getFirst();
        boolean thenCompletes = completesNormally(context, reporter, thenBranch);

        if (statements.size() == 1)
            return true;

        SyntaxNode elseBranch = statements.get(1);
        boolean elseCompletes = completesNormally(context, reporter, elseBranch);

        return thenCompletes || elseCompletes;
    }

    private static List<SyntaxNode> directStatementChildren(SyntaxNode parent) {
        List<SyntaxNode> children = new ArrayList<>();

        for (SyntaxNode child : parent.children()) {
            if (child instanceof SyntaxToken)
                continue;

            String kindId = child.kind().id();
            if ("JAVA_BLOCK".equals(kindId) || kindId.endsWith("_STATEMENT")) {
                children.add(child);
            }
        }

        return children;
    }

    private static List<SyntaxNode> directExecutableChildren(SyntaxNode parent) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : parent.children()) {
            if (child instanceof SyntaxToken)
                continue;

            children.add(child);
        }

        return children;
    }

    private static SyntaxNode firstDirectStatementChild(SyntaxNode parent) {
        List<SyntaxNode> children = directStatementChildren(parent);
        return children.isEmpty() ? null : children.getFirst();
    }

    private static SyntaxNode lastDirectStatementChild(SyntaxNode parent) {
        List<SyntaxNode> children = directStatementChildren(parent);
        return children.isEmpty() ? null : children.getLast();
    }
}
