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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RegisteredInspection
public final class CoreControlFlowInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-control-flow";

    private static final String JAVA_BREAK_STATEMENT = "JAVA_BREAK_STATEMENT";
    private static final String JAVA_CONTINUE_STATEMENT = "JAVA_CONTINUE_STATEMENT";
    private static final String JAVA_RETURN_STATEMENT = "JAVA_RETURN_STATEMENT";
    private static final String JAVA_YIELD_STATEMENT = "JAVA_YIELD_STATEMENT";
    private static final String JAVA_THROW_STATEMENT = "JAVA_THROW_STATEMENT";
    private static final String JAVA_METHOD_DECLARATION = "JAVA_METHOD_DECLARATION";
    private static final String JAVA_CONSTRUCTOR_DECLARATION = "JAVA_CONSTRUCTOR_DECLARATION";
    private static final String JAVA_RECORD_COMPACT_CONSTRUCTOR = "JAVA_RECORD_COMPACT_CONSTRUCTOR";
    private static final String JAVA_LAMBDA_EXPRESSION = "JAVA_LAMBDA_EXPRESSION";
    private static final String JAVA_SWITCH_EXPRESSION = "JAVA_SWITCH_EXPRESSION";
    private static final String JAVA_BLOCK = "JAVA_BLOCK";
    private static final String JAVA_IF_STATEMENT = "JAVA_IF_STATEMENT";
    private static final String JAVA_SYNCHRONIZED_STATEMENT = "JAVA_SYNCHRONIZED_STATEMENT";
    private static final String JAVA_LABELED_STATEMENT = "JAVA_LABELED_STATEMENT";
    private static final String JAVA_SWITCH_STATEMENT = "JAVA_SWITCH_STATEMENT";
    private static final String JAVA_SWITCH_RULE = "JAVA_SWITCH_RULE";
    private static final String JAVA_SWITCH_LABEL = "JAVA_SWITCH_LABEL";
    private static final String JAVA_TRY_STATEMENT = "JAVA_TRY_STATEMENT";
    private static final String JAVA_CATCH_CLAUSE = "JAVA_CATCH_CLAUSE";
    private static final String JAVA_FINALLY_CLAUSE = "JAVA_FINALLY_CLAUSE";
    private static final String JAVA_WHILE_STATEMENT = "JAVA_WHILE_STATEMENT";
    private static final String JAVA_DO_WHILE_STATEMENT = "JAVA_DO_WHILE_STATEMENT";
    private static final String JAVA_FOR_STATEMENT = "JAVA_FOR_STATEMENT";
    private static final String JAVA_TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";

    private static final Set<String> LOOP_KINDS = Set.of(
            JAVA_WHILE_STATEMENT,
            JAVA_DO_WHILE_STATEMENT,
            JAVA_FOR_STATEMENT
    );

    private static final Set<String> CONTROL_FLOW_BARRIER_KINDS = Set.of(
            JAVA_METHOD_DECLARATION,
            JAVA_CONSTRUCTOR_DECLARATION,
            JAVA_RECORD_COMPACT_CONSTRUCTOR,
            JAVA_LAMBDA_EXPRESSION,
            "JAVA_CLASS_DECLARATION",
            "JAVA_INTERFACE_DECLARATION",
            "JAVA_ENUM_DECLARATION",
            "JAVA_ANNOTATION_TYPE_DECLARATION",
            "JAVA_RECORD_DECLARATION"
    );

    private static final List<JavaInspectionRule> RULES = List.of(
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.INVALID_CONTROL_FLOW.id(),
                    JavaSemanticRules.INVALID_CONTROL_FLOW.defaultSeverity(),
                    JavaSemanticRules.INVALID_CONTROL_FLOW.messageTemplate(),
                    Set.of("core", "control-flow"),
                    CoreControlFlowInspection::reportInvalidControlFlow
            ),
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.MISSING_RETURN.id(),
                    JavaSemanticRules.MISSING_RETURN.defaultSeverity(),
                    JavaSemanticRules.MISSING_RETURN.messageTemplate(),
                    Set.of("core", "control-flow", "returns"),
                    CoreControlFlowInspection::reportMissingReturns
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

    private static void reportInvalidControlFlow(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            switch (node.kind().id()) {
                case JAVA_BREAK_STATEMENT -> reportBreakStatement(context, reporter, node);
                case JAVA_CONTINUE_STATEMENT -> reportContinueStatement(context, reporter, node);
                case JAVA_RETURN_STATEMENT -> reportReturnStatement(context, reporter, node);
                case JAVA_YIELD_STATEMENT -> reportYieldStatement(reporter, node);
                default -> {
                }
            }
        });
    }

    private static void reportMissingReturns(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!JAVA_METHOD_DECLARATION.equals(node.kind().id()))
                return;

            SyntaxNode typeRef = context.directChild(node, JAVA_TYPE_REFERENCE);
            if (typeRef == null)
                return;

            String returnType = context.resolveQualifiedTypeName(typeRef);
            if (returnType == null || "void".equals(returnType))
                return;

            SyntaxNode body = context.directChild(node, JAVA_BLOCK);
            if (body == null)
                return;

            if (definitelyReturnsOrThrows(context, body))
                return;

            String methodName = callableName(context, node);
            if (methodName == null || methodName.isBlank())
                methodName = "<method>";
            reporter.report(node, "method '%s' must return '%s' on all paths".formatted(methodName, context.simpleTypeName(returnType)));
        });
    }

    private static void reportBreakStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        String label = breakOrContinueLabel(context, node);
        if (label == null) {
            if (!hasBreakTarget(node))
                reporter.report(node, "'break' is only allowed inside loops or switch statements");
            return;
        }

        SyntaxNode target = findLabeledTarget(node, label);
        if (target == null)
            reporter.report(node, "cannot resolve break label '%s'".formatted(label));
    }

    private static void reportContinueStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        String label = breakOrContinueLabel(context, node);
        if (label == null) {
            if (!hasContinueTarget(node))
                reporter.report(node, "'continue' is only allowed inside loops");
            return;
        }

        SyntaxNode target = findLabeledTarget(node, label);
        if (target == null) {
            reporter.report(node, "cannot resolve continue label '%s'".formatted(label));
            return;
        }

        SyntaxNode labeledStatement = labeledStatementTarget(target);
        if (labeledStatement == null || !LOOP_KINDS.contains(labeledStatement.kind().id()))
            reporter.report(node, "continue label '%s' must target a loop".formatted(label));
    }

    private static void reportReturnStatement(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode node) {
        SyntaxNode enclosingCallable = nearestCallableOrLambda(node);
        if (enclosingCallable == null) {
            reporter.report(node, "'return' is only allowed inside methods, constructors, or lambdas");
            return;
        }

        String callableKind = enclosingCallable.kind().id();
        boolean hasValue = returnExpression(node) != null;

        if (JAVA_LAMBDA_EXPRESSION.equals(callableKind))
            return;

        if (JAVA_CONSTRUCTOR_DECLARATION.equals(callableKind) || JAVA_RECORD_COMPACT_CONSTRUCTOR.equals(callableKind)) {
            if (hasValue)
                reporter.report(node, "constructors cannot return a value");
            return;
        }

        SyntaxNode typeRef = context.directChild(enclosingCallable, JAVA_TYPE_REFERENCE);
        String returnType = typeRef == null ? "void" : context.resolveQualifiedTypeName(typeRef);
        if ("void".equals(returnType)) {
            if (hasValue)
                reporter.report(node, "void methods cannot return a value");
        } else if (!hasValue) {
            String methodName = callableName(context, enclosingCallable);
            if (methodName == null || methodName.isBlank())
                methodName = "<method>";
            reporter.report(node, "non-void method '%s' must return a value".formatted(methodName));
        }
    }

    private static void reportYieldStatement(JavaInspectionRuleReporter reporter, SyntaxNode node) {
        if (!hasYieldTarget(node))
            reporter.report(node, "'yield' is only allowed inside switch expressions");
    }

    private static boolean hasBreakTarget(SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            Optional<SyntaxNode> parent = current.parent();
            if (parent.isEmpty())
                return false;

            current = parent.get();
            String kindId = current.kind().id();
            if (LOOP_KINDS.contains(kindId) || JAVA_SWITCH_STATEMENT.equals(kindId))
                return true;
            if (CONTROL_FLOW_BARRIER_KINDS.contains(kindId))
                return false;
        }
    }

    private static boolean hasContinueTarget(SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            Optional<SyntaxNode> parent = current.parent();
            if (parent.isEmpty())
                return false;

            current = parent.get();
            String kindId = current.kind().id();
            if (LOOP_KINDS.contains(kindId))
                return true;
            if (CONTROL_FLOW_BARRIER_KINDS.contains(kindId))
                return false;
        }
    }

    private static boolean hasYieldTarget(SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            Optional<SyntaxNode> parent = current.parent();
            if (parent.isEmpty())
                return false;

            current = parent.get();
            String kindId = current.kind().id();
            if (JAVA_SWITCH_EXPRESSION.equals(kindId))
                return true;
            if (JAVA_SWITCH_STATEMENT.equals(kindId) || CONTROL_FLOW_BARRIER_KINDS.contains(kindId))
                return false;
        }
    }

    private static SyntaxNode findLabeledTarget(SyntaxNode node, String label) {
        SyntaxNode current = node;
        while (true) {
            Optional<SyntaxNode> parent = current.parent();
            if (parent.isEmpty())
                return null;

            current = parent.get();
            String kindId = current.kind().id();
            if (JAVA_LABELED_STATEMENT.equals(kindId)) {
                String candidate = labelName(current);
                if (label.equals(candidate))
                    return current;
            }
            if (CONTROL_FLOW_BARRIER_KINDS.contains(kindId))
                return null;
        }
    }

    private static @org.jetbrains.annotations.Nullable SyntaxNode labeledStatementTarget(SyntaxNode labeledStatement) {
        for (SyntaxNode child : labeledStatement.children()) {
            if (!(child instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken)
                    && !JAVA_LABELED_STATEMENT.equals(child.kind().id())) {
                return child;
            }
        }
        return null;
    }

    private static String labelName(SyntaxNode labeledStatement) {
        List<String> identifiers = identifierLikeTokens(labeledStatement);
        return identifiers.isEmpty() ? null : identifiers.getFirst();
    }

    private static String breakOrContinueLabel(JavaRuleContext context, SyntaxNode node) {
        List<String> identifiers = identifierLikeTokens(node);
        if (identifiers.isEmpty())
            return null;
        return context.lastIdentifierLikeTokenText(node);
    }

    private static String callableName(JavaRuleContext context, SyntaxNode callableNode) {
        Symbol declared = context.declaredSymbol(callableNode).orElse(null);
        if (declared != null && declared.kind() == SymbolKind.METHOD)
            return declared.simpleName();
        return context.lastIdentifierLikeTokenText(callableNode);
    }

    private static List<String> identifierLikeTokens(SyntaxNode node) {
        List<String> identifiers = new ArrayList<>();
        collectIdentifierLikeTokens(node, identifiers);
        return List.copyOf(identifiers);
    }

    private static void collectIdentifierLikeTokens(SyntaxNode node, List<String> out) {
        if (node instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken token) {
            String text = token.text();
            if (!text.isBlank()
                    && Character.isJavaIdentifierStart(text.charAt(0))
                    && !Set.of("break", "continue", "return").contains(text)) {
                out.add(text);
            }
            return;
        }

        for (SyntaxNode child : node.children())
            collectIdentifierLikeTokens(child, out);
    }

    private static @org.jetbrains.annotations.Nullable SyntaxNode returnExpression(SyntaxNode returnStatement) {
        for (SyntaxNode child : returnStatement.children()) {
            if (child instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken)
                continue;
            return child;
        }
        return null;
    }

    private static @org.jetbrains.annotations.Nullable SyntaxNode nearestCallableOrLambda(SyntaxNode node) {
        SyntaxNode current = node;
        while (true) {
            Optional<SyntaxNode> parent = current.parent();
            if (parent.isEmpty())
                return null;

            current = parent.get();
            String kindId = current.kind().id();
            if (JAVA_METHOD_DECLARATION.equals(kindId)
                    || JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)
                    || JAVA_RECORD_COMPACT_CONSTRUCTOR.equals(kindId)
                    || JAVA_LAMBDA_EXPRESSION.equals(kindId)) {
                return current;
            }
        }
    }

    private static boolean definitelyReturnsOrThrows(JavaRuleContext context, SyntaxNode node) {
        return switch (node.kind().id()) {
            case JAVA_RETURN_STATEMENT, JAVA_THROW_STATEMENT -> true;
            case JAVA_BLOCK -> blockDefinitelyReturnsOrThrows(context, node);
            case JAVA_IF_STATEMENT -> ifDefinitelyReturnsOrThrows(context, node);
            case JAVA_LABELED_STATEMENT -> {
                SyntaxNode target = labeledStatementTarget(node);
                yield target != null && definitelyReturnsOrThrows(context, target);
            }
            case JAVA_SYNCHRONIZED_STATEMENT -> {
                SyntaxNode block = context.directChild(node, JAVA_BLOCK);
                yield block != null && definitelyReturnsOrThrows(context, block);
            }
            case JAVA_TRY_STATEMENT -> tryDefinitelyReturnsOrThrows(context, node);
            case JAVA_SWITCH_STATEMENT -> switchDefinitelyReturnsOrThrows(context, node);
            default -> false;
        };
    }

    private static boolean blockDefinitelyReturnsOrThrows(JavaRuleContext context, SyntaxNode block) {
        for (SyntaxNode child : block.children()) {
            if (child instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken)
                continue;
            if (definitelyReturnsOrThrows(context, child))
                return true;
        }
        return false;
    }

    private static boolean ifDefinitelyReturnsOrThrows(JavaRuleContext context, SyntaxNode ifStatement) {
        List<SyntaxNode> statements = directNonTokenChildrenExcluding(ifStatement, "JAVA_PARENTHESIZED_EXPRESSION");
        if (statements.size() < 2)
            return false;
        return definitelyReturnsOrThrows(context, statements.get(0))
                && definitelyReturnsOrThrows(context, statements.get(1));
    }

    private static boolean tryDefinitelyReturnsOrThrows(JavaRuleContext context, SyntaxNode tryStatement) {
        SyntaxNode tryBlock = context.directChild(tryStatement, JAVA_BLOCK);
        if (tryBlock == null)
            return false;

        SyntaxNode finallyClause = context.directChild(tryStatement, JAVA_FINALLY_CLAUSE);
        if (finallyClause != null) {
            SyntaxNode finallyBlock = context.directChild(finallyClause, JAVA_BLOCK);
            if (finallyBlock != null && definitelyReturnsOrThrows(context, finallyBlock))
                return true;
        }

        if (!definitelyReturnsOrThrows(context, tryBlock))
            return false;

        List<SyntaxNode> catches = directChildrenOfKind(tryStatement, JAVA_CATCH_CLAUSE);
        if (catches.isEmpty())
            return true;

        for (SyntaxNode catchClause : catches) {
            SyntaxNode catchBlock = context.directChild(catchClause, JAVA_BLOCK);
            if (catchBlock == null || !definitelyReturnsOrThrows(context, catchBlock))
                return false;
        }
        return true;
    }

    private static boolean switchDefinitelyReturnsOrThrows(JavaRuleContext context, SyntaxNode switchStatement) {
        List<SyntaxNode> rules = directChildrenOfKind(switchStatement, JAVA_SWITCH_RULE);
        if (rules.isEmpty())
            return false;

        boolean hasDefault = false;
        for (SyntaxNode rule : rules) {
            SyntaxNode label = context.directChild(rule, JAVA_SWITCH_LABEL);
            if (label != null && labelContainsDefault(label))
                hasDefault = true;
            if (!switchRuleDefinitelyReturnsOrThrows(context, rule))
                return false;
        }
        return hasDefault;
    }

    private static boolean switchRuleDefinitelyReturnsOrThrows(JavaRuleContext context, SyntaxNode rule) {
        for (SyntaxNode child : rule.children()) {
            if (child instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken)
                continue;
            if (JAVA_SWITCH_LABEL.equals(child.kind().id()))
                continue;
            if (definitelyReturnsOrThrows(context, child))
                return true;
        }
        return false;
    }

    private static boolean labelContainsDefault(SyntaxNode switchLabel) {
        List<String> tokens = new ArrayList<>();
        collectLeafTokenTexts(switchLabel, tokens);
        return tokens.contains("default");
    }

    private static List<SyntaxNode> directChildrenOfKind(SyntaxNode node, String kindId) {
        List<SyntaxNode> matches = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (kindId.equals(child.kind().id()))
                matches.add(child);
        }
        return List.copyOf(matches);
    }

    private static List<SyntaxNode> directNonTokenChildrenExcluding(SyntaxNode node, String excludedKindId) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (child instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken)
                continue;
            if (excludedKindId.equals(child.kind().id()))
                continue;
            children.add(child);
        }
        return List.copyOf(children);
    }

    private static void collectLeafTokenTexts(SyntaxNode node, List<String> out) {
        if (node instanceof dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken token) {
            out.add(token.text());
            return;
        }

        for (SyntaxNode child : node.children())
            collectLeafTokenTexts(child, out);
    }
}
