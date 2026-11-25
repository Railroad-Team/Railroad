package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaDeclarationAnalysis;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Provides built-in Java inspections for {@link JavaSemanticRules#OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK}.
 */
@RegisteredInspection
public class CoreOptionalGetWithoutIsPresentCheckInspection implements JavaInspectionRuleProvider {
    /**
     * Stable identifier used to register this inspection provider.
     */
    public static final String ID = "railroad:core-optional-get-without-is-present-check";

    private static final Map<String, String> OPTIONAL_TYPE_TO_GET_METHOD = Map.of(
        "java.util.Optional", "get",
        "java.util.OptionalDouble", "getAsDouble",
        "java.util.OptionalInt", "getAsInt",
        "java.util.OptionalLong", "getAsLong");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK.id(),
                JavaSemanticRules.OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK.defaultSeverity(),
                JavaSemanticRules.OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK.messageTemplate(),
                Set.of("core", "optional", "null-safety"),
                CoreOptionalGetWithoutIsPresentCheckInspection::reportOptionalGetWithoutIsPresentCheck));
    }

    private static void reportOptionalGetWithoutIsPresentCheck(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter
    ) {
        Map<Symbol, List<Integer>> writes = new HashMap<>();
        for (SyntaxNode assignment : context.nodesOfKind(JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id())) {
            SyntaxNode target = context.unwrapTransparentExpression(context.firstExpressionChild(assignment));
            if (target != null) {
                context.resolvedSymbol(target).ifPresent(
                    symbol -> writes.computeIfAbsent(symbol, unused -> new ArrayList<>()).add(assignment.start()));
            }
        }

        for (SyntaxNode invocation : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            if (!context.hasNoArguments(invocation) || OPTIONAL_TYPE_TO_GET_METHOD.values().stream()
                .noneMatch(name -> context.isMethodInvocationNamed(invocation, name)))
                continue;

            Symbol receiver = receiverSymbol(context, invocation);
            if (receiver == null)
                continue;

            String type = receiverQualifiedTypeName(context, receiver);
            String getter = type == null ? null : OPTIONAL_TYPE_TO_GET_METHOD.get(type);
            if (getter != null && context.isMethodInvocationNamed(invocation, getter)
                && !isGuarded(context, invocation, receiver, writes.getOrDefault(receiver, List.of()))) {
                reporter.report(invocation);
            }
        }
    }

    private static @Nullable Symbol receiverSymbol(JavaRuleContext context, SyntaxNode invocation) {
        if (context.simpleReceiverName(invocation) == null)
            return null;

        SyntaxNode receiver = context.unwrapTransparentExpression(context.invocationReceiver(invocation));
        return receiver == null ? null : context.resolvedSymbol(receiver).orElse(null);
    }

    private static boolean isGuarded(JavaRuleContext context, SyntaxNode read, Symbol receiver, List<Integer> writes) {
        boolean local = receiver.kind() == JavaSymbolKind.LOCAL_VARIABLE || receiver.kind() == JavaSymbolKind.PARAMETER;
        int lastWrite = lastWriteBefore(writes, read.start());
        SyntaxNode child = read;
        for (SyntaxNode parent = child.parent().orElse(null); parent != null; child = parent, parent = parent.parent()
            .orElse(null)) {
            context.checkActive();
            switch (parent.kind().id()) {
                case "JAVA_BLOCK" -> {
                    if (local && guardedByEarlierExit(context, parent, child, receiver, lastWrite))
                        return true;
                }

                case "JAVA_BINARY_EXPRESSION" -> {
                    boolean and = context.hasOperatorToken(parent, JavaTokenType.AND);
                    if (local && (and || context.hasOperatorToken(parent, JavaTokenType.OR))) {
                        List<SyntaxNode> operands = context.directExpressionChildren(parent);
                        if (operands.size() == 2 && operands.get(1) == child
                            && impliesPresent(context, operands.getFirst(), and, receiver, lastWrite))
                            return true;
                    }
                }

                case "JAVA_CONDITIONAL_EXPRESSION" -> {
                    List<SyntaxNode> parts = context.directExpressionChildren(parent);
                    if (local && parts.size() == 3 && (child == parts.get(1) || child == parts.get(2))
                        && impliesPresent(context, parts.getFirst(), child == parts.get(1), receiver, lastWrite))
                        return true;
                }

                case "JAVA_IF_STATEMENT" -> {
                    boolean thenBranch = child == context.thenBranchOf(parent);
                    if ((thenBranch || child == context.elseBranchOf(parent))
                        && impliesPresent(context, context.conditionOf(parent), thenBranch, receiver, lastWrite))
                        return true;
                }

                case "JAVA_FOR_STATEMENT", "JAVA_WHILE_STATEMENT", "JAVA_DO_WHILE_STATEMENT" -> {
                    if ((child == context.forBodyOf(parent) || child == context.guardedBodyOf(parent))
                        && !JavaSyntaxKinds.DO_WHILE_STATEMENT.equals(parent.kind())
                        && impliesPresent(context, context.conditionOf(parent), true, receiver, lastWrite))
                        return true;

                    // A write later in the body can reach this read on the next iteration.
                    lastWrite = lastWriteBefore(writes, parent.end());
                }

                case "JAVA_ARGUMENT_LIST" -> {
                    SyntaxNode call = parent.parent().orElse(null);
                    if (child.kind().equals(JavaSyntaxKinds.LAMBDA_EXPRESSION) && call != null
                        && call.start() > lastWrite && context.isMethodInvocationNamed(call, "ifPresent")
                        && receiver.equals(receiverSymbol(context, call)))
                        return true;
                }
            }
        }

        return false;
    }

    private static int lastWriteBefore(List<Integer> writes, int offset) {
        return writes.stream().mapToInt(Integer::intValue).filter(write -> write < offset).max().orElse(-1);
    }

    private static boolean guardedByEarlierExit(
        JavaRuleContext context,
        SyntaxNode block,
        SyntaxNode child,
        Symbol receiver,
        int lastWrite
    ) {
        for (SyntaxNode statement : block.children()) {
            if (statement == child)
                break;

            if (statement.end() <= lastWrite)
                continue;

            if (JavaSyntaxKinds.EXPRESSION_STATEMENT.equals(statement.kind())
                && guardedByHelper(context, context.firstExpressionChild(statement), receiver, lastWrite))
                return true;

            if (!JavaSyntaxKinds.IF_STATEMENT.equals(statement.kind()))
                continue;

            SyntaxNode condition = context.conditionOf(statement);
            SyntaxNode thenBranch = context.thenBranchOf(statement);
            if (thenBranch != null && !JavaDeclarationAnalysis.canCompleteNormally(thenBranch)
                && impliesPresent(context, condition, false, receiver, lastWrite))
                return true;

            SyntaxNode elseBranch = context.elseBranchOf(statement);
            if (elseBranch != null && !JavaDeclarationAnalysis.canCompleteNormally(elseBranch)
                && impliesPresent(context, condition, true, receiver, lastWrite))
                return true;
        }

        return false;
    }

    private static boolean guardedByHelper(
        JavaRuleContext context,
        @Nullable SyntaxNode call,
        Symbol receiver,
        int lastWrite
    ) {
        if (call == null || !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.equals(call.kind()))
            return false;

        Symbol method = context.resolvedSymbol(call).orElse(null);
        SyntaxNode declaration = method == null ? null : method.declaration().orElse(null);
        if (declaration == null || !JavaSyntaxKinds.METHOD_DECLARATION.equals(declaration.kind()))
            return false;

        int modifiers = context.symbolModifiers(method);
        if (!Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers) && !Modifier.isFinal(modifiers)
            && !context.enclosingTypeSymbol(declaration).map(context::symbolModifiers).map(Modifier::isFinal)
                .orElse(false))
            return false;

        SyntaxNode guard = firstStatement(context.directChild(declaration, JavaSyntaxKinds.BLOCK.id()));
        if (guard == null || !JavaSyntaxKinds.IF_STATEMENT.equals(guard.kind()))
            return false;

        SyntaxNode failure = context.thenBranchOf(guard);
        if (failure != null && JavaSyntaxKinds.BLOCK.equals(failure.kind())) {
            failure = firstStatement(failure);
        }

        if (failure == null || !JavaSyntaxKinds.THROW_STATEMENT.equals(failure.kind()))
            return false;

        JavaRuleContext.NegationUnwrapResult condition = context.unwrapLeadingNegations(context.conditionOf(guard));
        if (condition == null || condition.expression() == null
            || !JavaSyntaxKinds.NAME_EXPRESSION.equals(condition.expression().kind()))
            return false;

        SyntaxNode testedParameter = context.resolvedSymbol(condition.expression()).flatMap(Symbol::declaration)
            .orElse(null);
        SyntaxNode parameters = context.directChild(declaration, JavaSyntaxKinds.PARAMETER_LIST.id());
        SyntaxNode arguments = context.directChild(call, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (testedParameter == null || parameters == null || arguments == null)
            return false;

        List<SyntaxNode> formals = parameters.children().stream()
            .filter(node -> JavaSyntaxKinds.PARAMETER.equals(node.kind()))
            .toList();
        int index = formals.indexOf(testedParameter);
        List<SyntaxNode> actuals = context.directExpressionChildren(arguments);
        return index >= 0 && index < actuals.size()
            && impliesPresent(context, actuals.get(index), (condition.negationCount() & 1) != 0, receiver, lastWrite);
    }

    private static @Nullable SyntaxNode firstStatement(@Nullable SyntaxNode block) {
        return block == null
            ? null
            : block.children().stream().filter(node -> !(node instanceof SyntaxToken))
                .findFirst().orElse(null);
    }

    private static boolean impliesPresent(
        JavaRuleContext context,
        @Nullable SyntaxNode condition,
        boolean outcome,
        Symbol receiver,
        int lastWrite
    ) {
        if (condition == null)
            return false;

        JavaRuleContext.NegationUnwrapResult unwrapped = context.unwrapLeadingNegations(condition);
        if (unwrapped == null || unwrapped.expression() == null)
            return false;

        SyntaxNode expression = unwrapped.expression();
        boolean value = (unwrapped.negationCount() & 1) == 0 ? outcome : !outcome;
        if (JavaSyntaxKinds.BINARY_EXPRESSION.equals(expression.kind())
            && context.hasOperatorToken(expression, value ? JavaTokenType.AND : JavaTokenType.OR))
            return context.directExpressionChildren(expression).stream()
                .anyMatch(operand -> impliesPresent(context, operand, value, receiver, lastWrite));

        if (expression.start() <= lastWrite
            || !context.isMethodInvocationNamed(expression, value ? "isPresent" : "isEmpty")
            || !context.hasNoArguments(expression))
            return false;

        SyntaxNode call = expression;
        while (!receiver.equals(receiverSymbol(context, call))) {
            call = context.unwrapTransparentExpression(context.invocationReceiver(call));
            if (call == null || !context.isMethodInvocationNamed(call, "filter"))
                return false;
        }

        return true;
    }

    private static @Nullable String receiverQualifiedTypeName(JavaRuleContext context, Symbol receiverSymbol) {
        SyntaxNode declaration = receiverSymbol.declaration().orElse(null);
        if (declaration == null)
            return null;

        if (Objects.equals(JavaSyntaxKinds.VARIABLE_DECLARATOR.id(), declaration.kind().id()))
            return normalizeOptionalTypeName(context.declaredTypeOfVariable(declaration).displayName());

        SyntaxNode typeReference = context.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
        if (typeReference != null)
            return normalizeOptionalTypeName(context.canonicalTypeText(typeReference));

        return null;
    }

    private static @Nullable String normalizeOptionalTypeName(@Nullable String rawTypeName) {
        if (rawTypeName == null || rawTypeName.isBlank())
            return null;

        int genericStart = rawTypeName.indexOf('<');
        String baseTypeName = genericStart >= 0 ? rawTypeName.substring(0, genericStart) : rawTypeName;

        if (baseTypeName.startsWith("java.util.OptionalInt"))
            return "java.util.OptionalInt";

        if (baseTypeName.startsWith("java.util.OptionalLong"))
            return "java.util.OptionalLong";

        if (baseTypeName.startsWith("java.util.OptionalDouble"))
            return "java.util.OptionalDouble";

        if (baseTypeName.startsWith("java.util.Optional"))
            return "java.util.Optional";

        return baseTypeName;
    }
}
