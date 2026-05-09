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

public class CoreOptionalGetWithoutIsPresentCheckInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-optional-get-without-is-present-check";

    private static final Map<String, String> OPTIONAL_TYPE_TO_GET_METHOD = Map.of(
        "java.util.Optional", "get",
        "java.util.OptionalDouble", "getAsDouble",
        "java.util.OptionalInt", "getAsInt",
        "java.util.OptionalLong", "getAsLong"
    );

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
                CoreOptionalGetWithoutIsPresentCheckInspection::reportOptionalGetWithoutIsPresentCheck
            )
        );
    }

    private static void reportOptionalGetWithoutIsPresentCheck(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        Set<SyntaxNode> guardedGets = new HashSet<>();
        collectGuardedGetsFromIfStatements(context, guardedGets);
        collectGuardedGetsFromWhileStatements(context, guardedGets);
        collectGuardedGetsFromForStatements(context, guardedGets);
        collectGuardedGetsFromIfPresentCalls(context, guardedGets);

        for (SyntaxNode invocation : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            if (!isOptionalGetInvocation(context, invocation))
                continue;

            if (guardedGets.contains(invocation))
                continue;

            reporter.report(invocation);
        }
    }

    private static void collectGuardedGetsFromIfStatements(JavaRuleContext context, Set<SyntaxNode> guardedGets) {
        for (SyntaxNode ifStatement : context.nodesOfKind(JavaSyntaxKinds.IF_STATEMENT.id())) {
            SyntaxNode condition = context.conditionOf(ifStatement);
            if (condition == null)
                continue;

            OptionalPresenceFact positiveFact = extractPositiveIsPresentFact(context, condition);
            if (positiveFact != null) {
                SyntaxNode thenBranch = context.thenBranchOf(ifStatement);
                if (thenBranch != null) {
                    collectMatchingGetsInBranch(context, thenBranch, positiveFact, guardedGets);
                }
            }

            OptionalPresenceFact negativeFact = extractNegativeIsPresentFact(context, condition);
            if (negativeFact != null) {
                SyntaxNode elseBranch = context.elseBranchOf(ifStatement);
                if (elseBranch != null) {
                    collectMatchingGetsInBranch(context, elseBranch, negativeFact, guardedGets);
                }
            }
        }
    }

    private static void collectGuardedGetsFromWhileStatements(JavaRuleContext context, Set<SyntaxNode> guardedGets) {
        for (SyntaxNode whileNode : context.nodesOfKind(JavaSyntaxKinds.WHILE_STATEMENT.id())) {
            SyntaxNode condition = context.conditionOf(whileNode);
            if (condition == null)
                continue;

            OptionalPresenceFact fact = extractPositiveIsPresentFact(context, condition);
            if (fact == null)
                continue;

            collectMatchingGetsInBranch(context, context.guardedBodyOf(whileNode), fact, guardedGets);
        }
    }

    private static void collectGuardedGetsFromForStatements(JavaRuleContext context, Set<SyntaxNode> guardedGets) {
        for (SyntaxNode forNode : context.nodesOfKind(JavaSyntaxKinds.FOR_STATEMENT.id())) {
            SyntaxNode condition = context.conditionOf(forNode);
            if (condition == null)
                continue;

            OptionalPresenceFact fact = extractPositiveIsPresentFact(context, condition);
            if (fact == null)
                continue;

            collectMatchingGetsInBranch(context, context.forBodyOf(forNode), fact, guardedGets);
        }
    }

    private static void collectGuardedGetsFromIfPresentCalls(JavaRuleContext context, Set<SyntaxNode> guardedGets) {
        for (SyntaxNode invocation : context.nodesOfKind(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id())) {
            if (!context.isMethodInvocationNamed(invocation, "ifPresent"))
                continue;

            SyntaxNode receiver = context.unwrapTransparentExpression(context.invocationReceiver(invocation));
            if (receiver == null || !isOptionalReceiver(context, receiver))
                continue;

            String receiverName = context.simpleReceiverName(invocation);
            if (receiverName == null || receiverName.isBlank())
                continue;

            SyntaxNode lambda = firstLambdaArgument(context, invocation);
            if (lambda == null)
                continue;

            SyntaxNode lambdaBody = context.lambdaBodyOf(lambda);
            if (lambdaBody == null)
                continue;

            collectMatchingGetsInBranch(context, lambdaBody, new OptionalPresenceFact(receiverName), guardedGets);
        }
    }

    private static SyntaxNode firstLambdaArgument(JavaRuleContext context, SyntaxNode invocation) {
        SyntaxNode argumentList = context.directChild(invocation, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList == null)
            return null;

        for (SyntaxNode child : argumentList.children()) {
            if (Objects.equals(JavaSyntaxKinds.LAMBDA_EXPRESSION.id(), child.kind().id()))
                return child;
        }

        return null;
    }

    private static void collectMatchingGetsInBranch(JavaRuleContext context, SyntaxNode body, OptionalPresenceFact fact, Set<SyntaxNode> guardedGets) {
        if (body == null)
            return;

        collectMatchingGetsInBranchRecursive(context, body, fact, guardedGets);
    }

    private static void collectMatchingGetsInBranchRecursive(JavaRuleContext context, SyntaxNode node, OptionalPresenceFact fact, Set<SyntaxNode> guardedGets) {
        if (isOptionalGetInvocation(context, node)) {
            String receiverName = context.simpleReceiverName(node);
            if (fact.receiverName().equals(receiverName)) {
                guardedGets.add(node);
            }
        }

        for (SyntaxNode child : node.children()) {
            collectMatchingGetsInBranchRecursive(context, child, fact, guardedGets);
        }
    }

    private static OptionalPresenceFact extractPositiveIsPresentFact(JavaRuleContext context, SyntaxNode condition) {
        JavaRuleContext.NegationUnwrapResult result = context.unwrapLeadingNegations(condition);
        if (result == null || result.expression() == null)
            return null;

        if ((result.negationCount() & 1) != 0)
            return null;

        SyntaxNode current = context.unwrapTransparentExpression(result.expression());
        if (current == null)
            return null;

        if (Objects.equals(JavaSyntaxKinds.BINARY_EXPRESSION.id(), current.kind().id())
            && context.hasOperatorToken(current, JavaTokenType.AND)) {
            for (SyntaxNode child : context.directExpressionChildren(current)) {
                OptionalPresenceFact fact = extractPositiveIsPresentFact(context, child);
                if (fact != null)
                    return fact;
            }

            return null;
        }

        if (!isOptionalIsPresentInvocation(context, current))
            return null;

        String receiverName = context.simpleReceiverName(current);
        if (receiverName == null || receiverName.isBlank())
            return null;

        return new OptionalPresenceFact(receiverName);
    }

    private static boolean isOptionalIsPresentInvocation(JavaRuleContext context, SyntaxNode invocation) {
        if (!context.isMethodInvocationNamed(invocation, "isPresent"))
            return false;

        if (!context.hasNoArguments(invocation))
            return false;

        String receiverName = context.simpleReceiverName(invocation);
        if (receiverName == null || receiverName.isBlank())
            return false;

        SyntaxNode receiver = context.unwrapTransparentExpression(context.invocationReceiver(invocation));
        if (receiver == null)
            return false;

        return isOptionalReceiver(context, receiver);
    }

    private static OptionalPresenceFact extractNegativeIsPresentFact(JavaRuleContext context, SyntaxNode condition) {
        JavaRuleContext.NegationUnwrapResult result = context.unwrapLeadingNegations(condition);
        if (result == null || result.expression() == null || (result.negationCount() & 1) == 0)
            return null;

        return extractPositiveIsPresentFact(context, result.expression());
    }

    private static boolean isOptionalGetInvocation(JavaRuleContext context, SyntaxNode invocation) {
        String expectedMethodName = null;
        for (String methodName : OPTIONAL_TYPE_TO_GET_METHOD.values()) {
            if (context.isMethodInvocationNamed(invocation, methodName)) {
                expectedMethodName = methodName;
                break;
            }
        }

        if (expectedMethodName == null)
            return false;

        if (!context.hasNoArguments(invocation))
            return false;

        String receiverName = context.simpleReceiverName(invocation);
        if (receiverName == null || receiverName.isBlank())
            return false;

        SyntaxNode receiver = context.unwrapTransparentExpression(context.invocationReceiver(invocation));
        if (receiver == null)
            return false;

        String receiverQualifiedTypeName = receiverQualifiedTypeName(context, receiver);
        if (receiverQualifiedTypeName == null)
            return false;

        return Objects.equals(expectedMethodName, OPTIONAL_TYPE_TO_GET_METHOD.get(receiverQualifiedTypeName));
    }

    private static boolean isOptionalReceiver(JavaRuleContext context, SyntaxNode receiver) {
        String qualifiedTypeName = receiverQualifiedTypeName(context, receiver);
        return OPTIONAL_TYPE_TO_GET_METHOD.containsKey(qualifiedTypeName);
    }

    private static String receiverQualifiedTypeName(JavaRuleContext context, SyntaxNode receiver) {
        Symbol receiverSymbol = context.resolvedSymbol(receiver).orElse(null);
        if (receiverSymbol == null)
            return null;

        SyntaxNode declaration = receiverSymbol.declaration().orElse(null);
        if (declaration == null)
            return null;

        if (Objects.equals(JavaSyntaxKinds.VARIABLE_DECLARATOR.id(), declaration.kind().id())) {
            return normalizeOptionalTypeName(context.declaredTypeOfVariable(declaration).displayName());
        }

        SyntaxNode typeReference = context.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
        if (typeReference != null)
            return normalizeOptionalTypeName(context.canonicalTypeText(typeReference));

        return null;
    }

    private static String normalizeOptionalTypeName(String rawTypeName) {
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

    private record OptionalPresenceFact(String receiverName) {
    }
}
