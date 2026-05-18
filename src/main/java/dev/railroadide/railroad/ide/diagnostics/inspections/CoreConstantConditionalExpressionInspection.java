package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.*;

@RegisteredInspection
public class CoreConstantConditionalExpressionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-constant-conditional-expression";
    private static final Set<String> DATA_FLOW_ROOT_KINDS = Set.of(
        "JAVA_METHOD_DECLARATION",
        "JAVA_CONSTRUCTOR_DECLARATION",
        "JAVA_RECORD_COMPACT_CONSTRUCTOR",
        "JAVA_INSTANCE_INITIALIZER",
        "JAVA_STATIC_INITIALIZER"
    );
    private static final Set<String> DATA_FLOW_BARRIER_KINDS = Set.of(
        "JAVA_CLASS_DECLARATION",
        "JAVA_INTERFACE_DECLARATION",
        "JAVA_ENUM_DECLARATION",
        "JAVA_ANNOTATION_TYPE_DECLARATION",
        "JAVA_RECORD_DECLARATION",
        "JAVA_METHOD_DECLARATION",
        "JAVA_CONSTRUCTOR_DECLARATION",
        "JAVA_RECORD_COMPACT_CONSTRUCTOR",
        "JAVA_LAMBDA_EXPRESSION"
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL.id(),
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL.defaultSeverity(),
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL.messageTemplate(),
                Set.of("core", "constant-conditions"),
                CoreConstantConditionalExpressionInspection::reportConstantConditionalExpressionsWithHardcodedLiterals
            ),
            new SimpleJavaInspectionRule(
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT.id(),
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT.defaultSeverity(),
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT.messageTemplate(),
                Set.of("core", "constant-conditions"),
                CoreConstantConditionalExpressionInspection::reportConstantConditionalExpressionsWithCompileTimeConstants
            ),
            new SimpleJavaInspectionRule(
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT.id(),
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT.defaultSeverity(),
                JavaSemanticRules.CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT.messageTemplate(),
                Set.of("core", "constant-conditions"),
                CoreConstantConditionalExpressionInspection::reportConstantConditionalExpressionsWithDataFlowConstants
            )
        );
    }

    private static void reportConstantConditionalExpressionsWithHardcodedLiterals(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            String kindId = node.kind().id();

            String conditionType = null;
            switch (kindId) {
                case "JAVA_IF_STATEMENT" -> conditionType = "if";
                case "JAVA_WHILE_STATEMENT" -> conditionType = "while";
                case "JAVA_DO_WHILE_STATEMENT" -> conditionType = "do-while";
                case "JAVA_FOR_STATEMENT" -> conditionType = "for";
                case "JAVA_CONDITIONAL_EXPRESSION" -> conditionType = "ternary";
            }

            SyntaxNode conditionNode = extractConditionNode(context, node);
            if (conditionNode != null) {
                checkAndReportLiteral(context, reporter, conditionNode, conditionType);
            }
        });
    }

    private static SyntaxNode extractConditionNode(JavaRuleContext context, SyntaxNode node) {
        return switch (node.kind().id()) {
            case "JAVA_IF_STATEMENT", "JAVA_WHILE_STATEMENT", "JAVA_DO_WHILE_STATEMENT" ->
                context.firstDirectExpressionChild(node);
            case "JAVA_FOR_STATEMENT" -> extractForCondition(context, node);
            case "JAVA_CONDITIONAL_EXPRESSION" -> node.children().isEmpty() ? null : node.children().getFirst();
            default -> null;
        };
    }

    private static SyntaxNode extractForCondition(JavaRuleContext context, SyntaxNode forLoopNode) {
        SyntaxNode basicFor = null;
        for (SyntaxNode child : forLoopNode.children()) {
            if (JavaSyntaxKinds.BASIC_FOR_STATEMENT.id().equals(child.kind().id())) {
                basicFor = child;
                break;
            }
        }

        if (basicFor == null)
            return null;

        int semicolonCount = 0;
        for (SyntaxNode child : basicFor.children()) {
            if (child instanceof SyntaxToken token
                && ";".equals(token.text())) {
                semicolonCount++;
                continue;
            }

            if (semicolonCount == 1 && context.isExpressionNode(child))
                return child;
        }

        return null;
    }

    private static void checkAndReportLiteral(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode conditionNode, String conditionType) {
        SyntaxNode inner = unwrapExpression(context, conditionNode);
        if (inner == null)
            return;

        if (!Objects.equals(inner.kind().id(), JavaSyntaxKinds.LITERAL_EXPRESSION.id()))
            return;

        String text = booleanLiteralText(inner);
        if ("while".equals(conditionType) && "true".equals(text))
            return;

        if ("true".equals(text) || "false".equals(text)) {
            reporter.report(conditionNode, conditionType, text);
        }
    }

    private static void reportConstantConditionalExpressionsWithCompileTimeConstants(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            String kindId = node.kind().id();

            String conditionType = null;
            switch (kindId) {
                case "JAVA_IF_STATEMENT" -> conditionType = "if";
                case "JAVA_WHILE_STATEMENT" -> conditionType = "while";
                case "JAVA_DO_WHILE_STATEMENT" -> conditionType = "do-while";
                case "JAVA_FOR_STATEMENT" -> conditionType = "for";
                case "JAVA_CONDITIONAL_EXPRESSION" -> conditionType = "ternary";
            }

            SyntaxNode conditionNode = extractConditionNode(context, node);
            if (conditionNode != null) {
                SyntaxNode inner = unwrapExpression(context, conditionNode);
                if (inner != null && !Objects.equals(inner.kind().id(), JavaSyntaxKinds.LITERAL_EXPRESSION.id())) {
                    Boolean result = evaluateBooleanConstant(context, inner);
                    if (result != null) {
                        reporter.report(conditionNode, conditionType, result.toString());
                    }
                }
            }
        });
    }

    @SuppressWarnings("ConstantValue")
    private static Boolean evaluateBooleanConstant(JavaRuleContext context, SyntaxNode node) {
        return evaluateBooleanConstant(context, node, new HashSet<>());
    }

    @SuppressWarnings("ConstantValue")
    private static Boolean evaluateBooleanConstant(JavaRuleContext context, SyntaxNode node, Set<SyntaxNode> visitedDeclarators) {
        if (node == null)
            return null;

        SyntaxNode inner = unwrapExpression(context, node);
        if (inner == null)
            return null;

        String kindId = inner.kind().id();

        if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(kindId))
            return evaluateNamedBooleanConstant(context, inner, visitedDeclarators);

        // 1. Handle literals
        if (JavaSyntaxKinds.LITERAL_EXPRESSION.id().equals(kindId)) {
            String text = booleanLiteralText(inner);
            if ("true".equals(text))
                return true;

            if ("false".equals(text))
                return false;

            return null;
        }

        // 2. Unary expressions
        if (JavaSyntaxKinds.UNARY_EXPRESSION.id().equals(kindId)) {
            SyntaxNode operand = context.firstExpressionChild(inner);
            if (operand == null)
                return null;

            Boolean operandValue = evaluateBooleanConstant(context, operand, visitedDeclarators);
            if (operandValue == null)
                return null;

            String operator = firstNonTriviaTokenText(inner);
            if ("!".equals(operator))
                return !operandValue;

            return null;
        }

        // 3. Binary expressions
        if (JavaSyntaxKinds.BINARY_EXPRESSION.id().equals(kindId)) {
            List<SyntaxNode> operands = context.directExpressionChildren(inner);
            if (operands.size() != 2)
                return null;

            String operator = null;
            for (SyntaxNode child : inner.children()) {
                if (child instanceof SyntaxToken token && !JavaSemanticAnalyzer.isTriviaToken(token)
                    && !"(".equals(token.text()) && !")".equals(token.text())) {
                    operator = token.text();
                    break;
                }
            }

            if (operator == null)
                return null;

            Boolean leftValue = evaluateBooleanConstant(context, operands.get(0), visitedDeclarators);
            Boolean rightValue = evaluateBooleanConstant(context, operands.get(1), visitedDeclarators);
            if ("&&".equals(operator)) {
                if (leftValue != null && !leftValue)
                    return false;

                if (rightValue != null && !rightValue)
                    return false;

                if (leftValue != null && rightValue != null)
                    return leftValue && rightValue;
            } else if ("||".equals(operator)) {
                if (leftValue != null && leftValue)
                    return true;

                if (rightValue != null && rightValue)
                    return true;

                if (leftValue != null && rightValue != null)
                    return leftValue || rightValue;
            }
        }

        return null;
    }

    private static void reportConstantConditionalExpressionsWithDataFlowConstants(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode root : context.nodesOfKinds(DATA_FLOW_ROOT_KINDS)) {
            SyntaxNode blockNode = context.directChild(root, JavaSyntaxKinds.BLOCK.id());
            if (blockNode != null) {
                analyzeBlockForDataFlowConstants(context, reporter, blockNode, new HashMap<>());
            }
        }
    }

    private static void analyzeBlockForDataFlowConstants(JavaRuleContext context, JavaInspectionRuleReporter reporter, SyntaxNode blockNode, Map<String, Boolean> knownFacts) {
        if (blockNode == null || DATA_FLOW_BARRIER_KINDS.contains(blockNode.kind().id()))
            return;

        String kindId = blockNode.kind().id();
        if (Objects.equals(kindId, JavaSyntaxKinds.BLOCK.id())) {
            Map<String, Boolean> scopedFacts = new HashMap<>(knownFacts);
            for (SyntaxNode node : blockNode.children()) {
                if (node instanceof SyntaxToken)
                    continue;
                analyzeBlockForDataFlowConstants(context, reporter, node, scopedFacts);
            }
            return;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.id())) {
            for (SyntaxNode child : blockNode.children()) {
                if (!Objects.equals(child.kind().id(), JavaSyntaxKinds.VARIABLE_DECLARATOR.id()))
                    continue;

                String name = context.firstIdentifierLikeTokenText(child);
                if (name == null)
                    continue;

                Boolean value = evaluateInitializerAsBooleanConstant(context, child, knownFacts);
                if (value != null) {
                    knownFacts.put(name, value);
                } else {
                    knownFacts.remove(name);
                }
            }
            return;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.EXPRESSION_STATEMENT.id())) {
            SyntaxNode expression = context.firstDirectExpressionChild(blockNode);
            if (expression != null) {
                applyExpressionFacts(context, expression, knownFacts);
            }
            return;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.IF_STATEMENT.id())) {
            SyntaxNode conditionNode = context.firstDirectExpressionChild(blockNode);
            reportKnownCondition(context, reporter, conditionNode, knownFacts);

            BooleanFact branchFact = extractBooleanFact(context, conditionNode);
            SyntaxNode thenBranch = context.thenBranchOf(blockNode);
            if (thenBranch != null) {
                Map<String, Boolean> thenBranchFacts = new HashMap<>(knownFacts);
                if (branchFact != null) {
                    thenBranchFacts.put(branchFact.variableName(), branchFact.truthValue());
                }
                analyzeBlockForDataFlowConstants(context, reporter, thenBranch, thenBranchFacts);
            }

            SyntaxNode elseBranch = context.elseBranchOf(blockNode);
            if (elseBranch != null) {
                Map<String, Boolean> elseBranchFacts = new HashMap<>(knownFacts);
                if (branchFact != null) {
                    elseBranchFacts.put(branchFact.variableName(), !branchFact.truthValue());
                }
                analyzeBlockForDataFlowConstants(context, reporter, elseBranch, elseBranchFacts);
            }
            return;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.WHILE_STATEMENT.id())) {
            SyntaxNode conditionNode = context.firstDirectExpressionChild(blockNode);
            reportKnownCondition(context, reporter, conditionNode, knownFacts);

            SyntaxNode body = firstStatementChildAfterCondition(context, blockNode);
            if (body != null) {
                Map<String, Boolean> bodyFacts = new HashMap<>(knownFacts);
                BooleanFact branchFact = extractBooleanFact(context, conditionNode);
                if (branchFact != null) {
                    bodyFacts.put(branchFact.variableName(), branchFact.truthValue());
                }
                analyzeBlockForDataFlowConstants(context, reporter, body, bodyFacts);
            }
            return;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.DO_WHILE_STATEMENT.id())) {
            SyntaxNode body = firstNonTokenChild(blockNode);
            if (body != null) {
                analyzeBlockForDataFlowConstants(context, reporter, body, new HashMap<>(knownFacts));
            }

            SyntaxNode conditionNode = context.firstDirectExpressionChild(blockNode);
            reportKnownCondition(context, reporter, conditionNode, knownFacts);
            return;
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.FOR_STATEMENT.id())) {
            SyntaxNode condition = extractForCondition(context, blockNode);
            reportKnownCondition(context, reporter, condition, knownFacts);

            BooleanFact branchFact = extractBooleanFact(context, condition);
            SyntaxNode body = context.forBodyOf(blockNode);
            if (body != null) {
                Map<String, Boolean> loopFacts = new HashMap<>(knownFacts);
                if (branchFact != null) {
                    if (isModifiedInLoop(context, blockNode, branchFact.variableName())) {
                        loopFacts.remove(branchFact.variableName());
                    } else {
                        loopFacts.put(branchFact.variableName(), branchFact.truthValue());
                    }
                }
                analyzeBlockForDataFlowConstants(context, reporter, body, loopFacts);
            }
            return;
        }

        for (SyntaxNode child : blockNode.children()) {
            if (child instanceof SyntaxToken)
                continue;
            analyzeBlockForDataFlowConstants(context, reporter, child, knownFacts);
        }
    }

    private static Boolean evaluateInitializerAsBooleanConstant(JavaRuleContext context, SyntaxNode declaratorNode, Map<String, Boolean> knownFacts) {
        SyntaxNode initializer = initializerOf(context, declaratorNode);

        if (initializer == null)
            return null;

        return evaluateBooleanDataFlowConstant(context, initializer, knownFacts);
    }

    private static SyntaxNode initializerOf(JavaRuleContext context, SyntaxNode declaratorNode) {
        SyntaxNode initializer = null;
        boolean sawEquals = false;
        for (SyntaxNode child : declaratorNode.children()) {
            if (child instanceof SyntaxToken token && "=".equals(token.text())) {
                sawEquals = true;
                continue;
            }

            if (sawEquals && context.isExpressionNode(child)) {
                initializer = child;
                break;
            }
        }

        return initializer;
    }

    private static boolean isModifiedInLoop(JavaRuleContext context, SyntaxNode node, String variableName) {
        SyntaxNode basicFor = context.directChild(node, JavaSyntaxKinds.BASIC_FOR_STATEMENT.id());
        if (basicFor == null)
            return false;

        int semicolonCount = 0;
        for (SyntaxNode child : basicFor.children()) {
            if (child instanceof SyntaxToken token && ";".equals(token.text())) {
                semicolonCount++;
                continue;
            }

            if (semicolonCount == 2) {
                if (isAssignableTo(context, child, Set.of(variableName)))
                    return true;
            }
        }

        return false;
    }

    private static String getSimpleVariableName(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode inner = unwrapExpression(context, node);
        if (inner == null)
            return null;

        if (!Objects.equals(inner.kind().id(), JavaSyntaxKinds.NAME_EXPRESSION.id()))
            return null;

        if (context.isMethodNameReference(inner))
            return null;

        return context.firstIdentifierLikeTokenText(inner);
    }

    private static void applyExpressionFacts(JavaRuleContext context, SyntaxNode expression, Map<String, Boolean> knownFacts) {
        SyntaxNode inner = unwrapExpression(context, expression);
        if (inner == null || !Objects.equals(inner.kind().id(), JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id()))
            return;

        List<SyntaxNode> expressions = context.directExpressionChildren(inner);
        if (expressions.size() < 2)
            return;

        String variableName = getLeftHandSideVariableName(context, inner);
        if (variableName == null)
            return;

        Boolean value = evaluateBooleanDataFlowConstant(context, expressions.get(1), knownFacts);
        if (value != null) {
            knownFacts.put(variableName, value);
        } else {
            knownFacts.remove(variableName);
        }
    }

    private static void reportKnownCondition(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter,
        SyntaxNode conditionNode,
        Map<String, Boolean> knownFacts
    ) {
        BooleanFact fact = extractBooleanFact(context, conditionNode);
        if (fact == null)
            return;

        Boolean knownValue = knownFacts.get(fact.variableName());
        if (knownValue == null)
            return;

        boolean conditionValue = fact.truthValue() ? knownValue : !knownValue;
        reporter.report(conditionNode, fact.variableName(), knownValue.toString(), Boolean.toString(conditionValue));
    }

    private static BooleanFact extractBooleanFact(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode inner = unwrapExpression(context, node);
        if (inner == null)
            return null;

        if (Objects.equals(inner.kind().id(), JavaSyntaxKinds.NAME_EXPRESSION.id())) {
            String variableName = getSimpleVariableName(context, inner);
            return variableName == null ? null : new BooleanFact(variableName, true);
        }

        if (Objects.equals(inner.kind().id(), JavaSyntaxKinds.UNARY_EXPRESSION.id())) {
            String operator = firstNonTriviaTokenText(inner);
            if (!"!".equals(operator))
                return null;

            SyntaxNode operand = context.firstExpressionChild(inner);
            BooleanFact nestedFact = extractBooleanFact(context, operand);
            if (nestedFact == null)
                return null;

            return new BooleanFact(nestedFact.variableName(), !nestedFact.truthValue());
        }

        return null;
    }

    private static Boolean evaluateNamedBooleanConstant(JavaRuleContext context, SyntaxNode node, Set<SyntaxNode> visitedDeclarators) {
        Symbol symbol = context.resolvedSymbol(node).orElse(null);
        if (symbol == null)
            return null;

        SyntaxNode declaration = symbol.declaration().orElse(null);
        if (declaration == null || !Objects.equals(declaration.kind().id(), JavaSyntaxKinds.VARIABLE_DECLARATOR.id()))
            return null;

        if (!isFinalVariableDeclarator(context, declaration))
            return null;

        if (!visitedDeclarators.add(declaration))
            return null;

        try {
            return evaluateBooleanConstant(context, initializerOf(context, declaration), visitedDeclarators);
        } finally {
            visitedDeclarators.remove(declaration);
        }
    }

    private static Boolean evaluateBooleanDataFlowConstant(JavaRuleContext context, SyntaxNode node, Map<String, Boolean> knownFacts) {
        if (node == null)
            return null;

        SyntaxNode inner = unwrapExpression(context, node);
        if (inner == null)
            return null;

        String kindId = inner.kind().id();
        if (Objects.equals(kindId, JavaSyntaxKinds.NAME_EXPRESSION.id())) {
            String variableName = getSimpleVariableName(context, inner);
            return variableName == null ? null : knownFacts.get(variableName);
        }

        if (Objects.equals(kindId, JavaSyntaxKinds.LITERAL_EXPRESSION.id())
            || Objects.equals(kindId, JavaSyntaxKinds.UNARY_EXPRESSION.id())
            || Objects.equals(kindId, JavaSyntaxKinds.BINARY_EXPRESSION.id())) {
            if (Objects.equals(kindId, JavaSyntaxKinds.UNARY_EXPRESSION.id())
                || Objects.equals(kindId, JavaSyntaxKinds.BINARY_EXPRESSION.id())) {
                List<SyntaxNode> expressions = context.directExpressionChildren(inner);
                for (SyntaxNode expression : expressions) {
                    if (evaluateBooleanDataFlowConstant(context, expression, knownFacts) == null
                        && evaluateBooleanConstant(context, unwrapExpression(context, expression)) == null) {
                        return null;
                    }
                }
            }

            Boolean direct = evaluateBooleanConstant(context, inner);
            if (direct != null)
                return direct;

            if (Objects.equals(kindId, JavaSyntaxKinds.UNARY_EXPRESSION.id())) {
                SyntaxNode operand = context.firstExpressionChild(inner);
                Boolean operandValue = evaluateBooleanDataFlowConstant(context, operand, knownFacts);
                if (operandValue != null && "!".equals(firstNonTriviaTokenText(inner)))
                    return !operandValue;
            }

            if (Objects.equals(kindId, JavaSyntaxKinds.BINARY_EXPRESSION.id())) {
                List<SyntaxNode> operands = context.directExpressionChildren(inner);
                if (operands.size() == 2) {
                    String operator = null;
                    for (SyntaxNode child : inner.children()) {
                        if (child instanceof SyntaxToken token && !JavaSemanticAnalyzer.isTriviaToken(token)
                            && !"(".equals(token.text()) && !")".equals(token.text())) {
                            operator = token.text();
                            break;
                        }
                    }

                    Boolean leftValue = evaluateBooleanDataFlowConstant(context, operands.get(0), knownFacts);
                    Boolean rightValue = evaluateBooleanDataFlowConstant(context, operands.get(1), knownFacts);
                    if ("&&".equals(operator)) {
                        if (Boolean.FALSE.equals(leftValue) || Boolean.FALSE.equals(rightValue))
                            return false;
                        if (leftValue != null && rightValue != null)
                            return leftValue && rightValue;
                    } else if ("||".equals(operator)) {
                        if (Boolean.TRUE.equals(leftValue) || Boolean.TRUE.equals(rightValue))
                            return true;
                        if (leftValue != null && rightValue != null)
                            return leftValue || rightValue;
                    }
                }
            }
        }

        return null;
    }

    private static SyntaxNode firstStatementChildAfterCondition(JavaRuleContext context, SyntaxNode node) {
        boolean conditionSeen = false;
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken)
                continue;

            if (!conditionSeen && context.isExpressionNode(child)) {
                conditionSeen = true;
                continue;
            }

            if (conditionSeen)
                return child;
        }

        return null;
    }

    private static SyntaxNode firstNonTokenChild(SyntaxNode node) {
        for (SyntaxNode child : node.children()) {
            if (!(child instanceof SyntaxToken))
                return child;
        }

        return null;
    }


    private static boolean isAssignableTo(JavaRuleContext context, SyntaxNode node, Set<String> trackedVariables) {
        String kindId = node.kind().id();
        if (Objects.equals(JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id(), kindId)) {
            String variableName = getLeftHandSideVariableName(context, node);
            return trackedVariables.contains(variableName);
        }

        if (Objects.equals(JavaSyntaxKinds.VARIABLE_DECLARATOR.id(), kindId)) {
            String variableName = context.firstIdentifierLikeTokenText(node);
            return trackedVariables.contains(variableName);
        }

        return false;
    }

    private static String getLeftHandSideVariableName(JavaRuleContext context, SyntaxNode assignmentNode) {
        SyntaxNode lhs = assignmentNode.children().stream()
            .filter(context::isExpressionNode)
            .findFirst()
            .orElse(null);
        if (lhs == null)
            return null;

        while (Objects.equals(lhs.kind().id(), JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id())) {
            lhs = context.firstExpressionChild(lhs);
            if (lhs == null)
                return null;
        }

        return context.firstIdentifierLikeTokenText(lhs);
    }

    private static SyntaxNode unwrapExpression(JavaRuleContext context, SyntaxNode node) {
        SyntaxNode current = node;
        while (current != null) {
            current = context.unwrapTransparentExpression(current);
            if (current == null)
                return null;

            if (!Objects.equals(current.kind().id(), JavaSyntaxKinds.EXPRESSION.id()))
                return current;

            current = context.firstExpressionChild(current);
        }

        return null;
    }

    private static boolean isFinalVariableDeclarator(JavaRuleContext context, SyntaxNode declaratorNode) {
        if (context.hasTokenKind(declaratorNode, JavaTokenType.FINAL_KEYWORD))
            return true;

        SyntaxNode parent = declaratorNode.parent().orElse(null);
        if (parent != null && context.hasTokenKind(parent, JavaTokenType.FINAL_KEYWORD))
            return true;

        SyntaxNode grandParent = parent == null ? null : parent.parent().orElse(null);
        return grandParent != null && context.hasTokenKind(grandParent, JavaTokenType.FINAL_KEYWORD);
    }

    private static String booleanLiteralText(SyntaxNode node) {
        for (SyntaxToken token : JavaSemanticAnalyzer.leafTokens(node)) {
            if (Objects.equals(token.kind().id(), JavaSyntaxKinds.tokenKind(JavaTokenType.BOOLEAN_LITERAL).id()))
                return token.text();
        }

        return null;
    }

    private static String firstNonTriviaTokenText(SyntaxNode node) {
        for (SyntaxToken token : JavaSemanticAnalyzer.leafTokens(node)) {
            if (!JavaSemanticAnalyzer.isTriviaToken(token) && !JavaSemanticAnalyzer.isMissingTokenKind(token.kind().id()))
                return token.text();
        }

        return null;
    }

    private record BooleanFact(String variableName, boolean truthValue) {
    }
}
