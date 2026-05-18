package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreDefiniteAssignmentInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-definite-assignment";
    private static final int LOOP_FIXPOINT_ITERATION_LIMIT = 32;

    private static final String JAVA_METHOD_DECLARATION = "JAVA_METHOD_DECLARATION";
    private static final String JAVA_CONSTRUCTOR_DECLARATION = "JAVA_CONSTRUCTOR_DECLARATION";
    private static final String JAVA_RECORD_COMPACT_CONSTRUCTOR = "JAVA_RECORD_COMPACT_CONSTRUCTOR";
    private static final String JAVA_CLASS_DECLARATION = "JAVA_CLASS_DECLARATION";
    private static final String JAVA_ENUM_DECLARATION = "JAVA_ENUM_DECLARATION";
    private static final String JAVA_RECORD_DECLARATION = "JAVA_RECORD_DECLARATION";
    private static final String JAVA_BLOCK = "JAVA_BLOCK";
    private static final String JAVA_PARAMETER_LIST = "JAVA_PARAMETER_LIST";
    private static final String JAVA_PARAMETER = "JAVA_PARAMETER";
    private static final String JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT = "JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT";
    private static final String JAVA_VARIABLE_DECLARATOR = "JAVA_VARIABLE_DECLARATOR";
    private static final String JAVA_FIELD_DECLARATION = "JAVA_FIELD_DECLARATION";
    private static final String JAVA_STATIC_INITIALIZER = "JAVA_STATIC_INITIALIZER";
    private static final String JAVA_INSTANCE_INITIALIZER = "JAVA_INSTANCE_INITIALIZER";
    private static final String JAVA_IF_STATEMENT = "JAVA_IF_STATEMENT";
    private static final String JAVA_WHILE_STATEMENT = "JAVA_WHILE_STATEMENT";
    private static final String JAVA_DO_WHILE_STATEMENT = "JAVA_DO_WHILE_STATEMENT";
    private static final String JAVA_FOR_STATEMENT = "JAVA_FOR_STATEMENT";
    private static final String JAVA_BASIC_FOR_STATEMENT = "JAVA_BASIC_FOR_STATEMENT";
    private static final String JAVA_ENHANCED_FOR_STATEMENT = "JAVA_ENHANCED_FOR_STATEMENT";
    private static final String JAVA_SWITCH_STATEMENT = "JAVA_SWITCH_STATEMENT";
    private static final String JAVA_SWITCH_RULE = "JAVA_SWITCH_RULE";
    private static final String JAVA_LABELED_STATEMENT = "JAVA_LABELED_STATEMENT";
    private static final String JAVA_TRY_STATEMENT = "JAVA_TRY_STATEMENT";
    private static final String JAVA_CATCH_CLAUSE = "JAVA_CATCH_CLAUSE";
    private static final String JAVA_FINALLY_CLAUSE = "JAVA_FINALLY_CLAUSE";
    private static final String JAVA_RETURN_STATEMENT = "JAVA_RETURN_STATEMENT";
    private static final String JAVA_THROW_STATEMENT = "JAVA_THROW_STATEMENT";
    private static final String JAVA_BREAK_STATEMENT = "JAVA_BREAK_STATEMENT";
    private static final String JAVA_CONTINUE_STATEMENT = "JAVA_CONTINUE_STATEMENT";
    private static final String JAVA_YIELD_STATEMENT = "JAVA_YIELD_STATEMENT";
    private static final String JAVA_ASSIGNMENT_EXPRESSION = "JAVA_ASSIGNMENT_EXPRESSION";
    private static final String JAVA_NAME_EXPRESSION = "JAVA_NAME_EXPRESSION";
    private static final String JAVA_LAMBDA_EXPRESSION = "JAVA_LAMBDA_EXPRESSION";
    private static final Set<String> ANALYSIS_BARRIERS = Set.of(
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
                    JavaSemanticRules.UNASSIGNED_VARIABLE.id(),
                    JavaSemanticRules.UNASSIGNED_VARIABLE.defaultSeverity(),
                    JavaSemanticRules.UNASSIGNED_VARIABLE.messageTemplate(),
                    Set.of("core", "dataflow", "assignments"),
                    CoreDefiniteAssignmentInspection::reportUnassignedVariables
            ),
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.ILLEGAL_FINAL_ASSIGNMENT.id(),
                    JavaSemanticRules.ILLEGAL_FINAL_ASSIGNMENT.defaultSeverity(),
                    JavaSemanticRules.ILLEGAL_FINAL_ASSIGNMENT.messageTemplate(),
                    Set.of("core", "dataflow", "final"),
                    CoreDefiniteAssignmentInspection::reportIllegalFinalAssignments
            ),
            new SimpleJavaInspectionRule(
                    JavaSemanticRules.UNINITIALIZED_FINAL_FIELD.id(),
                    JavaSemanticRules.UNINITIALIZED_FINAL_FIELD.defaultSeverity(),
                    JavaSemanticRules.UNINITIALIZED_FINAL_FIELD.messageTemplate(),
                    Set.of("core", "dataflow", "final", "fields"),
                    CoreDefiniteAssignmentInspection::reportUninitializedFinalFields
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

    private static void reportUnassignedVariables(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        analyzeExecutableBodies(context, reporter, null);
    }

    private static void reportIllegalFinalAssignments(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        analyzeExecutableBodies(context, null, reporter);
        reportIllegalFinalFieldAssignments(context, reporter);
    }

    private static void analyzeExecutableBodies(
            JavaRuleContext context,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        context.traverse(node -> {
            String kindId = node.kind().id();
            if (!JAVA_METHOD_DECLARATION.equals(kindId)
                    && !JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)
                    && !JAVA_RECORD_COMPACT_CONSTRUCTOR.equals(kindId)) {
                return;
            }

            SyntaxNode body = context.directChild(node, JAVA_BLOCK);
            if (body == null)
                return;

            FlowState state = FlowState.initial();
            SyntaxNode parameterList = context.directChild(node, JAVA_PARAMETER_LIST);
            if (parameterList != null) {
                for (SyntaxNode child : parameterList.children()) {
                    if (!JAVA_PARAMETER.equals(child.kind().id()))
                        continue;
                    Symbol symbol = context.declaredSymbol(child).orElse(null);
                    if (symbol != null)
                        state = state.assign(symbol);
                }
            }

            analyzeBlock(context, body, state, unassignedReporter, illegalFinalReporter);
        });
    }

    private static void reportUninitializedFinalFields(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(typeNode -> {
            String kindId = typeNode.kind().id();
            if (!JAVA_CLASS_DECLARATION.equals(kindId)
                    && !JAVA_ENUM_DECLARATION.equals(kindId)
                    && !JAVA_RECORD_DECLARATION.equals(kindId)) {
                return;
            }

            for (FieldSymbolInfo field : finalFieldSymbols(context, typeNode)) {
                if (!field.isBlankFinal())
                    continue;

                FieldFlowState initializerState = field.isStatic()
                        ? analyzeStaticFieldInitializers(context, typeNode, field.symbol(), null)
                        : analyzeInstanceFieldInitializers(context, typeNode, field.symbol(), null);

                if (field.isStatic()) {
                    if (!initializerState.definitelyAssigned())
                        reporter.report(field.reportNode(), field.symbol().simpleName());
                    continue;
                }

                List<SyntaxNode> constructors = constructorsOf(typeNode);
                if (constructors.isEmpty()) {
                    if (!initializerState.definitelyAssigned())
                        reporter.report(field.reportNode(), field.symbol().simpleName());
                    continue;
                }

                for (SyntaxNode constructor : constructors) {
                    SyntaxNode body = context.directChild(constructor, JAVA_BLOCK);
                    if (body == null)
                        continue;
                    FieldFlowState state = analyzeFieldFlow(context, body, field.symbol(), initializerState, null);
                    if (state.reachable() && !state.definitelyAssigned())
                        reporter.report(constructor, field.symbol().simpleName());
                }
            }
        });
    }

    private static void reportIllegalFinalFieldAssignments(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(typeNode -> {
            String kindId = typeNode.kind().id();
            if (!JAVA_CLASS_DECLARATION.equals(kindId)
                    && !JAVA_ENUM_DECLARATION.equals(kindId)
                    && !JAVA_RECORD_DECLARATION.equals(kindId)) {
                return;
            }

            for (FieldSymbolInfo field : finalFieldSymbols(context, typeNode)) {
                FieldFlowState initializerState = field.isStatic()
                        ? analyzeStaticFieldInitializers(context, typeNode, field.symbol(), reporter)
                        : analyzeInstanceFieldInitializers(context, typeNode, field.symbol(), reporter);
                if (field.isStatic())
                    continue;
                for (SyntaxNode constructor : constructorsOf(typeNode)) {
                    SyntaxNode body = context.directChild(constructor, JAVA_BLOCK);
                    if (body != null)
                        analyzeFieldFlow(context, body, field.symbol(), initializerState, reporter);
                }
            }
        });
    }

    private static FlowState analyzeBlock(
            JavaRuleContext context,
            SyntaxNode block,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState current = state;
        for (SyntaxNode child : block.children()) {
            if (child instanceof SyntaxToken)
                continue;
            if (!current.reachable())
                break;
            current = analyzeNode(context, child, current, unassignedReporter, illegalFinalReporter);
        }
        return current;
    }

    private static FlowState analyzeNode(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        String kindId = node.kind().id();
        return switch (kindId) {
            case JAVA_BLOCK -> analyzeBlock(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT -> analyzeLocalVariableDeclaration(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_IF_STATEMENT -> analyzeIfStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_LABELED_STATEMENT -> analyzeLabeledStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_WHILE_STATEMENT -> analyzeWhileStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_DO_WHILE_STATEMENT -> analyzeDoWhileStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_FOR_STATEMENT -> analyzeForStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_SWITCH_STATEMENT -> analyzeSwitchStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_TRY_STATEMENT -> analyzeTryStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            case JAVA_RETURN_STATEMENT, JAVA_THROW_STATEMENT, JAVA_BREAK_STATEMENT, JAVA_CONTINUE_STATEMENT, JAVA_YIELD_STATEMENT ->
                    analyzeAbruptStatement(context, node, state, unassignedReporter, illegalFinalReporter);
            default -> analyzeGenericNode(context, node, state, unassignedReporter, illegalFinalReporter);
        };
    }

    private static FlowState analyzeLocalVariableDeclaration(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState current = state;
        for (SyntaxNode child : node.children()) {
            if (!JAVA_VARIABLE_DECLARATOR.equals(child.kind().id()))
                continue;

            SyntaxNode initializer = context.firstDirectExpressionChild(child);
            if (initializer != null)
                current = analyzeReads(context, initializer, current, unassignedReporter, illegalFinalReporter);

            Symbol symbol = context.declaredSymbol(child).orElse(null);
            if (symbol != null && initializer != null)
                current = current.assign(symbol);
        }
        return current;
    }

    private static FlowState analyzeIfStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;

        FlowState conditionState = analyzeNode(context, children.getFirst(), state, unassignedReporter, illegalFinalReporter);
        FlowState thenState = children.size() > 1 ? analyzeNode(context, children.get(1), conditionState, unassignedReporter, illegalFinalReporter) : conditionState;
        FlowState elseState = children.size() > 2 ? analyzeNode(context, children.get(2), conditionState, unassignedReporter, illegalFinalReporter) : conditionState;
        return FlowState.mergeBranches(thenState, elseState);
    }

    private static FlowState analyzeLabeledStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        String label = labelName(node);
        SyntaxNode target = labeledStatementTarget(node);
        if (target == null)
            return state;

        FlowState labeledState = analyzeLabeledTarget(context, target, label, state, unassignedReporter, illegalFinalReporter);
        return label == null ? labeledState : labeledState.resumeBreaks(label);
    }

    private static FlowState analyzeLabeledTarget(
            JavaRuleContext context,
            SyntaxNode target,
            @Nullable String label,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        return switch (target.kind().id()) {
            case JAVA_WHILE_STATEMENT -> analyzeWhileStatement(context, target, state, unassignedReporter, illegalFinalReporter, label);
            case JAVA_DO_WHILE_STATEMENT -> analyzeDoWhileStatement(context, target, state, unassignedReporter, illegalFinalReporter, label);
            case JAVA_FOR_STATEMENT -> analyzeForStatement(context, target, state, unassignedReporter, illegalFinalReporter, label);
            default -> analyzeNode(context, target, state, unassignedReporter, illegalFinalReporter);
        };
    }

    private static FlowState analyzeWhileStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        return analyzeWhileStatement(context, node, state, unassignedReporter, illegalFinalReporter, null);
    }

    private static FlowState analyzeWhileStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter,
            @Nullable String loopLabel
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;

        SyntaxNode condition = children.getFirst();
        SyntaxNode body = children.size() > 1 ? children.get(1) : null;
        FlowState loopHead = stabilizeWhileLoopHead(context, condition, body, state, unassignedReporter, illegalFinalReporter, loopLabel);
        FlowState conditionState = analyzeNode(context, condition, loopHead, unassignedReporter, illegalFinalReporter);
        FlowState bodyState = body == null ? conditionState : analyzeNode(context, body, conditionState, unassignedReporter, illegalFinalReporter);
        FlowSnapshot normalExit = isConstantTrueExpression(context, condition) ? null : conditionState.snapshot();
        return FlowState.loopExitFrom(normalExit, bodyState.resumeContinues(loopLabel));
    }

    private static FlowState analyzeDoWhileStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        return analyzeDoWhileStatement(context, node, state, unassignedReporter, illegalFinalReporter, null);
    }

    private static FlowState analyzeDoWhileStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter,
            @Nullable String loopLabel
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;

        FlowState bodyState = analyzeNode(context, children.getFirst(), state, unassignedReporter, illegalFinalReporter);
        FlowState conditionInput = bodyState.resumeContinues(loopLabel);
        SyntaxNode condition = children.size() > 1 ? children.get(1) : null;
        FlowState conditionState = children.size() > 1
                ? analyzeNode(context, condition, conditionInput, unassignedReporter, illegalFinalReporter)
                : conditionInput;
        FlowSnapshot normalExit = conditionState.reachable() && !isConstantTrueExpression(context, condition)
                ? conditionState.snapshot()
                : null;
        return FlowState.loopExitFrom(normalExit, bodyState.resumeContinues(loopLabel));
    }

    private static FlowState analyzeForStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        return analyzeForStatement(context, node, state, unassignedReporter, illegalFinalReporter, null);
    }

    private static FlowState analyzeForStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter,
            @Nullable String loopLabel
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;

        SyntaxNode header = children.getFirst();
        SyntaxNode body = children.size() > 1 ? children.get(1) : null;

        FlowState headerState = state;
        if (JAVA_BASIC_FOR_STATEMENT.equals(header.kind().id())) {
            return analyzeBasicForStatement(context, header, body, state, unassignedReporter, illegalFinalReporter, loopLabel);
        } else if (JAVA_ENHANCED_FOR_STATEMENT.equals(header.kind().id())) {
            headerState = analyzeEnhancedForHeader(context, header, state, unassignedReporter, illegalFinalReporter);
        }

        FlowState bodyState = body == null ? headerState : analyzeNode(context, body, headerState, unassignedReporter, illegalFinalReporter);
        return FlowState.loopExitFrom(headerState.snapshot(), bodyState.resumeContinues(loopLabel));
    }

    private static FlowState analyzeBasicForStatement(
            JavaRuleContext context,
            SyntaxNode header,
            @Nullable SyntaxNode body,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter,
            @Nullable String loopLabel
    ) {
        BasicForSegments segments = basicForSegments(header);
        FlowState initState = analyzeNodeSequence(context, segments.initNodes(), state, unassignedReporter, illegalFinalReporter);
        FlowState loopHead = stabilizeBasicForLoopHead(context, segments, body, initState, unassignedReporter, illegalFinalReporter, loopLabel);
        FlowState conditionState = analyzeNodeSequence(context, segments.conditionNodes(), loopHead, unassignedReporter, illegalFinalReporter);
        FlowState bodyState = body == null ? conditionState : analyzeNode(context, body, conditionState, unassignedReporter, illegalFinalReporter);
        FlowSnapshot normalExit = hasFiniteBasicForExit(segments)
                ? conditionState.snapshot()
                : null;
        return FlowState.loopExitFrom(normalExit, bodyState.resumeContinues(loopLabel));
    }

    private static FlowState stabilizeWhileLoopHead(
            JavaRuleContext context,
            SyntaxNode condition,
            @Nullable SyntaxNode body,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter,
            @Nullable String loopLabel
    ) {
        FlowState baseHead = state.withoutAbruptExits();
        FlowState loopHead = baseHead;
        for (int i = 0; i < LOOP_FIXPOINT_ITERATION_LIMIT; i++) {
            FlowState conditionState = analyzeNode(context, condition, loopHead, unassignedReporter, illegalFinalReporter);
            FlowState bodyState = body == null ? conditionState : analyzeNode(context, body, conditionState, unassignedReporter, illegalFinalReporter);
            FlowState nextHead = FlowState.mergeBranches(baseHead, bodyState.resumeContinues(loopLabel).withoutAbruptExits());
            if (nextHead.withoutAbruptExits().equals(loopHead.withoutAbruptExits()))
                return nextHead;
            loopHead = nextHead;
        }
        return loopHead;
    }

    private static FlowState stabilizeBasicForLoopHead(
            JavaRuleContext context,
            BasicForSegments segments,
            @Nullable SyntaxNode body,
            FlowState initState,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter,
            @Nullable String loopLabel
    ) {
        FlowState baseHead = initState.withoutAbruptExits();
        FlowState loopHead = baseHead;
        for (int i = 0; i < LOOP_FIXPOINT_ITERATION_LIMIT; i++) {
            FlowState conditionState = analyzeNodeSequence(context, segments.conditionNodes(), loopHead, unassignedReporter, illegalFinalReporter);
            FlowState bodyState = body == null ? conditionState : analyzeNode(context, body, conditionState, unassignedReporter, illegalFinalReporter);
            FlowState updateState = analyzeNodeSequence(
                    context,
                    segments.updateNodes(),
                    bodyState.resumeContinues(loopLabel),
                    unassignedReporter,
                    illegalFinalReporter
            );
            FlowState nextHead = FlowState.mergeBranches(baseHead, updateState.withoutAbruptExits());
            if (nextHead.withoutAbruptExits().equals(loopHead.withoutAbruptExits()))
                return nextHead;
            loopHead = nextHead;
        }
        return loopHead;
    }

    private static FlowState analyzeEnhancedForHeader(
            JavaRuleContext context,
            SyntaxNode header,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState current = state;
        for (SyntaxNode child : structuralChildren(header)) {
            if (JAVA_PARAMETER.equals(child.kind().id())) {
                Symbol symbol = context.declaredSymbol(child).orElse(null);
                if (symbol != null)
                    current = current.assign(symbol);
                continue;
            }

            if (context.isExpressionNode(child))
                current = analyzeReads(context, child, current, unassignedReporter, illegalFinalReporter);
        }
        return current;
    }

    private static FlowState analyzeTryStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState current = state;
        for (SyntaxNode resource : directChildrenOfKind(node, "JAVA_TRY_RESOURCE"))
            current = analyzeNode(context, resource, current, unassignedReporter, illegalFinalReporter);

        SyntaxNode tryBlock = context.directChild(node, JAVA_BLOCK);
        FlowState merged = tryBlock == null ? current : analyzeNode(context, tryBlock, current, unassignedReporter, illegalFinalReporter);
        for (SyntaxNode catchClause : directChildrenOfKind(node, JAVA_CATCH_CLAUSE)) {
            FlowState catchState = current;
            for (SyntaxNode child : catchClause.children()) {
                if (!JAVA_PARAMETER.equals(child.kind().id()))
                    continue;
                Symbol symbol = context.declaredSymbol(child).orElse(null);
                if (symbol != null)
                    catchState = catchState.assign(symbol);
            }
            SyntaxNode catchBlock = context.directChild(catchClause, JAVA_BLOCK);
            if (catchBlock != null)
                catchState = analyzeNode(context, catchBlock, catchState, unassignedReporter, illegalFinalReporter);
            merged = FlowState.mergeBranches(merged, catchState);
        }

        SyntaxNode finallyClause = context.directChild(node, JAVA_FINALLY_CLAUSE);
        if (finallyClause != null) {
            SyntaxNode finallyBlock = context.directChild(finallyClause, JAVA_BLOCK);
            if (finallyBlock != null)
                merged = analyzeNode(context, finallyBlock, merged, unassignedReporter, illegalFinalReporter);
        }
        return merged;
    }

    private static FlowState analyzeSwitchStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;

        FlowState selectorState = analyzeNode(context, children.getFirst(), state, unassignedReporter, illegalFinalReporter);
        List<SyntaxNode> rules = directChildrenOfKind(node, JAVA_SWITCH_RULE);
        if (rules.isEmpty())
            return selectorState;

        FlowState switchExit = switchHasDefault(rules) ? selectorState.withNormalFlow(false) : selectorState.withoutAbruptExits();
        FlowState fallthrough = selectorState.withNormalFlow(false);
        for (SyntaxNode rule : rules) {
            if (fallthrough.reachable() && isArrowSwitchRule(rule)) {
                switchExit = FlowState.mergeBranches(switchExit, fallthrough.withoutAbruptExits());
                fallthrough = fallthrough.withNormalFlow(false);
            }

            FlowState entryState = FlowState.mergeBranches(selectorState.withoutAbruptExits(), fallthrough.withoutAbruptExits());
            FlowState ruleState = analyzeSwitchRule(context, rule, entryState, unassignedReporter, illegalFinalReporter);
            FlowState resumedBreaks = ruleState.resumeBreaks();
            if (isArrowSwitchRule(rule)) {
                switchExit = FlowState.mergeBranches(switchExit, resumedBreaks.withoutAbruptExits());
                fallthrough = selectorState.withNormalFlow(false);
            } else {
                switchExit = FlowState.mergeBranches(switchExit, resumedBreaks.withNormalFlow(false));
                fallthrough = ruleState.withoutAbruptExits();
            }
        }
        return FlowState.mergeBranches(switchExit, fallthrough.withoutAbruptExits());
    }

    private static FlowState analyzeSwitchRule(
            JavaRuleContext context,
            SyntaxNode rule,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState current = state;
        boolean pastLabel = false;
        for (SyntaxNode child : structuralChildren(rule)) {
            if (!pastLabel) {
                pastLabel = true;
                continue;
            }
            current = analyzeNode(context, child, current, unassignedReporter, illegalFinalReporter);
            if (!current.reachable())
                return current;
        }
        return current;
    }

    private static FlowState analyzeAbruptStatement(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState analyzed = analyzeGenericNode(context, node, state, unassignedReporter, illegalFinalReporter);
        return switch (node.kind().id()) {
            case JAVA_BREAK_STATEMENT -> analyzed.breakExit(breakOrContinueLabel(context, node));
            case JAVA_CONTINUE_STATEMENT -> analyzed.continueExit(breakOrContinueLabel(context, node));
            default -> analyzed.unreachable();
        };
    }

    private static FlowState analyzeGenericNode(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        String kindId = node.kind().id();
        if (ANALYSIS_BARRIERS.contains(kindId))
            return state;

        if (JAVA_ASSIGNMENT_EXPRESSION.equals(kindId))
            return analyzeAssignmentExpression(context, node, state, unassignedReporter, illegalFinalReporter);
        if (isIncrementExpression(node))
            return analyzeIncrementExpression(context, node, state, unassignedReporter, illegalFinalReporter);
        if (context.isExpressionNode(node))
            return analyzeReads(context, node, state, unassignedReporter, illegalFinalReporter);

        FlowState current = state;
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken)
                continue;
            current = analyzeNode(context, child, current, unassignedReporter, illegalFinalReporter);
        }
        return current;
    }

    private static FlowState analyzeAssignmentExpression(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        List<SyntaxNode> expressions = context.directExpressionChildren(node);
        if (expressions.size() < 2)
            return analyzeGenericNode(context, node, state, unassignedReporter, illegalFinalReporter);

        SyntaxNode left = expressions.getFirst();
        SyntaxNode right = expressions.get(1);
        FlowState current = state;
        JavaTokenType operator = assignmentOperator(node);
        if (operator != JavaTokenType.EQUALS)
            current = analyzeReads(context, left, current, unassignedReporter, illegalFinalReporter);
        current = analyzeReads(context, right, current, unassignedReporter, illegalFinalReporter);
        return applyWrite(context, left, current, illegalFinalReporter);
    }

    private static FlowState analyzeIncrementExpression(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        SyntaxNode target = context.firstDirectExpressionChild(node);
        if (target == null)
            return state;
        FlowState current = analyzeReads(context, target, state, unassignedReporter, illegalFinalReporter);
        return applyWrite(context, target, current, illegalFinalReporter);
    }

    private static FlowState analyzeReads(
            JavaRuleContext context,
            SyntaxNode node,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        String kindId = node.kind().id();
        if (ANALYSIS_BARRIERS.contains(kindId))
            return state;

        if (JAVA_ASSIGNMENT_EXPRESSION.equals(kindId))
            return analyzeAssignmentExpression(context, node, state, unassignedReporter, illegalFinalReporter);
        if (isIncrementExpression(node))
            return analyzeIncrementExpression(context, node, state, unassignedReporter, illegalFinalReporter);

        FlowState current = state;
        if (JAVA_NAME_EXPRESSION.equals(kindId)) {
            Symbol symbol = context.resolvedSymbol(node).orElse(null);
            if (unassignedReporter != null
                    && isTrackedVariable(symbol)
                    && isVisibleAtUse(symbol, node)
                    && !current.definitelyAssigned().contains(symbol)) {
                unassignedReporter.report(node, symbol.simpleName());
            }
        }

        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken)
                continue;
            current = analyzeReads(context, child, current, unassignedReporter, illegalFinalReporter);
        }
        return current;
    }

    private static FlowState applyWrite(
            JavaRuleContext context,
            SyntaxNode target,
            FlowState state,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        Symbol symbol = context.resolvedSymbol(target).orElse(null);
        if (!isTrackedVariable(symbol) || !isVisibleAtUse(symbol, target))
            return state;

        if (illegalFinalReporter != null && isFinalVariable(context, symbol) && state.maybeAssigned().contains(symbol))
            illegalFinalReporter.report(target, symbol.simpleName());

        return state.assign(symbol);
    }

    private static boolean isTrackedVariable(Symbol symbol) {
        return symbol != null && (symbol.kind() == SymbolKind.LOCAL_VARIABLE || symbol.kind() == SymbolKind.PARAMETER);
    }

    private static boolean isVisibleAtUse(Symbol symbol, SyntaxNode usageNode) {
        return symbol.declaration().map(declaration -> declaration.start() <= usageNode.start()).orElse(true);
    }

    private static boolean isFinalVariable(JavaRuleContext context, Symbol symbol) {
        if (symbol.kind() == SymbolKind.PARAMETER)
            return true;
        return java.lang.reflect.Modifier.isFinal(context.symbolModifiers(symbol));
    }

    private static boolean isIncrementExpression(SyntaxNode node) {
        String kindId = node.kind().id();
        if (!"JAVA_UNARY_EXPRESSION".equals(kindId) && !"JAVA_POSTFIX_EXPRESSION".equals(kindId))
            return false;
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token) {
                String text = token.text();
                if ("++".equals(text) || "--".equals(text))
                    return true;
            }
        }
        return false;
    }

    private static JavaTokenType assignmentOperator(SyntaxNode node) {
        for (SyntaxNode child : node.children()) {
            if (!(child instanceof SyntaxToken token))
                continue;
            String text = token.text();
            JavaTokenType operator = switch (text) {
                case "=" -> JavaTokenType.EQUALS;
                case "+=" -> JavaTokenType.PLUS_EQUALS;
                case "-=" -> JavaTokenType.MINUS_EQUALS;
                case "*=" -> JavaTokenType.STAR_EQUALS;
                case "/=" -> JavaTokenType.SLASH_EQUALS;
                case "%=" -> JavaTokenType.PERCENT_EQUALS;
                case "&=" -> JavaTokenType.AMPERSAND_EQUALS;
                case "|=" -> JavaTokenType.PIPE_EQUALS;
                case "^=" -> JavaTokenType.CARET_EQUALS;
                case "<<=" -> JavaTokenType.LEFT_SHIFT_EQUALS;
                case ">>=" -> JavaTokenType.RIGHT_SHIFT_EQUALS;
                case ">>>=" -> JavaTokenType.UNSIGNED_RIGHT_SHIFT_EQUALS;
                default -> null;
            };
            if (operator != null)
                return operator;
        }
        return null;
    }

    private static List<SyntaxNode> structuralChildren(SyntaxNode node) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (!(child instanceof SyntaxToken))
                children.add(child);
        }
        return List.copyOf(children);
    }

    private static List<SyntaxNode> directChildrenOfKind(SyntaxNode node, String kindId) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (kindId.equals(child.kind().id()))
                children.add(child);
        }
        return List.copyOf(children);
    }

    private static FlowState analyzeNodeSequence(
            JavaRuleContext context,
            List<SyntaxNode> nodes,
            FlowState state,
            @Nullable JavaInspectionRuleReporter unassignedReporter,
            @Nullable JavaInspectionRuleReporter illegalFinalReporter
    ) {
        FlowState current = state;
        for (SyntaxNode node : nodes) {
            if (!current.reachable())
                break;
            current = analyzeNode(context, node, current, unassignedReporter, illegalFinalReporter);
        }
        return current;
    }

    private static FieldFlowState analyzeFieldNodeSequence(
            JavaRuleContext context,
            List<SyntaxNode> nodes,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        FieldFlowState current = state;
        for (SyntaxNode node : nodes) {
            if (!current.reachable())
                break;
            current = analyzeFieldFlow(context, node, fieldSymbol, current, reporter);
        }
        return current;
    }

    private static BasicForSegments basicForSegments(SyntaxNode header) {
        List<SyntaxNode> init = new ArrayList<>();
        List<SyntaxNode> condition = new ArrayList<>();
        List<SyntaxNode> update = new ArrayList<>();
        int segment = 0;
        for (SyntaxNode child : header.children()) {
            if (child instanceof SyntaxToken token) {
                if (";".equals(token.text()))
                    segment++;
                continue;
            }

            switch (segment) {
                case 0 -> init.add(child);
                case 1 -> condition.add(child);
                default -> update.add(child);
            }
        }
        return new BasicForSegments(List.copyOf(init), List.copyOf(condition), List.copyOf(update));
    }

    private static boolean hasFiniteBasicForExit(BasicForSegments segments) {
        if (segments.conditionNodes().isEmpty())
            return false;
        return segments.conditionNodes().size() != 1 || !isConstantTrueExpressionText(segments.conditionNodes().getFirst());
    }

    private static boolean switchHasDefault(List<SyntaxNode> rules) {
        for (SyntaxNode rule : rules) {
            SyntaxNode label = findDirectChild(rule, "JAVA_SWITCH_LABEL");
            if (label == null)
                continue;
            for (String identifier : identifierLikeTokens(label)) {
                if ("default".equals(identifier))
                    return true;
            }
        }
        return false;
    }

    public static boolean isArrowSwitchRule(SyntaxNode rule) {
        for (SyntaxNode child : rule.children()) {
            if (child instanceof SyntaxToken token && "->".equals(token.text()))
                return true;
        }

        return false;
    }

    private static @Nullable SyntaxNode findDirectChild(SyntaxNode node, String kindId) {
        for (SyntaxNode child : node.children()) {
            if (kindId.equals(child.kind().id()))
                return child;
        }
        return null;
    }

    private static List<FieldSymbolInfo> finalFieldSymbols(JavaRuleContext context, SyntaxNode typeNode) {
        List<FieldSymbolInfo> fields = new ArrayList<>();
        SyntaxNode body = typeBody(typeNode);
        if (body == null)
            return List.of();

        for (SyntaxNode child : body.children()) {
            if (!JAVA_FIELD_DECLARATION.equals(child.kind().id()))
                continue;
            for (SyntaxNode declarator : directChildrenOfKind(child, JAVA_VARIABLE_DECLARATOR)) {
                Symbol symbol = context.declaredSymbol(declarator).orElse(null);
                if (symbol == null || !java.lang.reflect.Modifier.isFinal(context.symbolModifiers(symbol)))
                    continue;
                boolean hasInitializer = context.firstDirectExpressionChild(declarator) != null;
                fields.add(new FieldSymbolInfo(symbol, declarator, java.lang.reflect.Modifier.isStatic(context.symbolModifiers(symbol)), !hasInitializer));
            }
        }

        return List.copyOf(fields);
    }

    private static boolean assignedByStaticInitialization(JavaRuleContext context, SyntaxNode typeNode, Symbol fieldSymbol) {
        return assignedByInitializers(context, typeNode, fieldSymbol, true);
    }

    private static boolean assignedByInstanceInitialization(JavaRuleContext context, SyntaxNode typeNode, Symbol fieldSymbol) {
        return assignedByInitializers(context, typeNode, fieldSymbol, false);
    }

    private static boolean assignedByInitializers(JavaRuleContext context, SyntaxNode typeNode, Symbol fieldSymbol, boolean staticField) {
        FieldFlowState state = staticField
                ? analyzeStaticFieldInitializers(context, typeNode, fieldSymbol, null)
                : analyzeInstanceFieldInitializers(context, typeNode, fieldSymbol, null);
        return state.definitelyAssigned();
    }

    private static List<SyntaxNode> constructorsOf(SyntaxNode typeNode) {
        SyntaxNode body = typeBody(typeNode);
        if (body == null)
            return List.of();
        return directChildrenOfKind(body, JAVA_CONSTRUCTOR_DECLARATION);
    }

    private static SyntaxNode typeBody(SyntaxNode typeNode) {
        for (SyntaxNode child : typeNode.children()) {
            String kindId = child.kind().id();
            if (kindId.endsWith("_BODY"))
                return child;
        }
        return null;
    }

    private static FieldFlowState analyzeFieldFlow(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        String kindId = node.kind().id();
        if (ANALYSIS_BARRIERS.contains(kindId))
            return state;

        return switch (kindId) {
            case JAVA_BLOCK -> analyzeFieldBlock(context, node, fieldSymbol, state, reporter);
            case JAVA_IF_STATEMENT -> analyzeFieldIf(context, node, fieldSymbol, state, reporter);
            case JAVA_LABELED_STATEMENT -> analyzeFieldLabeledStatement(context, node, fieldSymbol, state, reporter);
            case JAVA_TRY_STATEMENT -> analyzeFieldTry(context, node, fieldSymbol, state, reporter);
            case JAVA_SWITCH_STATEMENT -> analyzeFieldSwitch(context, node, fieldSymbol, state, reporter);
            case JAVA_WHILE_STATEMENT -> analyzeFieldWhile(context, node, fieldSymbol, state, reporter);
            case JAVA_DO_WHILE_STATEMENT -> analyzeFieldDoWhile(context, node, fieldSymbol, state, reporter);
            case JAVA_FOR_STATEMENT -> analyzeFieldFor(context, node, fieldSymbol, state, reporter);
            case JAVA_RETURN_STATEMENT, JAVA_THROW_STATEMENT -> state.unreachable();
            case JAVA_BREAK_STATEMENT -> state.breakExit(breakOrContinueLabel(context, node));
            case JAVA_CONTINUE_STATEMENT -> state.continueExit(breakOrContinueLabel(context, node));
            default -> analyzeFieldGeneric(context, node, fieldSymbol, state, reporter);
        };
    }

    private static FieldFlowState analyzeFieldLabeledStatement(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        String label = labelName(node);
        SyntaxNode target = labeledStatementTarget(node);
        if (target == null)
            return state;

        FieldFlowState labeledState = analyzeFieldLabeledTarget(context, target, label, fieldSymbol, state, reporter);
        return label == null ? labeledState : labeledState.resumeBreaks(label);
    }

    private static FieldFlowState analyzeFieldLabeledTarget(
            JavaRuleContext context,
            SyntaxNode target,
            @Nullable String label,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        return switch (target.kind().id()) {
            case JAVA_WHILE_STATEMENT -> analyzeFieldWhile(context, target, fieldSymbol, state, reporter, label);
            case JAVA_DO_WHILE_STATEMENT -> analyzeFieldDoWhile(context, target, fieldSymbol, state, reporter, label);
            case JAVA_FOR_STATEMENT -> analyzeFieldFor(context, target, fieldSymbol, state, reporter, label);
            default -> analyzeFieldFlow(context, target, fieldSymbol, state, reporter);
        };
    }

    private static FieldFlowState analyzeFieldBlock(
            JavaRuleContext context,
            SyntaxNode block,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        FieldFlowState current = state;
        for (SyntaxNode child : block.children()) {
            if (child instanceof SyntaxToken)
                continue;
            if (!current.reachable())
                break;
            current = analyzeFieldFlow(context, child, fieldSymbol, current, reporter);
        }
        return current;
    }

    private static FieldFlowState analyzeFieldIf(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        FieldFlowState thenState = children.size() > 1 ? analyzeFieldFlow(context, children.get(1), fieldSymbol, state, reporter) : state;
        FieldFlowState elseState = children.size() > 2 ? analyzeFieldFlow(context, children.get(2), fieldSymbol, state, reporter) : state;
        return FieldFlowState.merge(thenState, elseState);
    }

    private static FieldFlowState analyzeFieldTry(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        SyntaxNode tryBlock = context.directChild(node, JAVA_BLOCK);
        FieldFlowState merged = tryBlock == null ? state : analyzeFieldFlow(context, tryBlock, fieldSymbol, state, reporter);
        for (SyntaxNode catchClause : directChildrenOfKind(node, JAVA_CATCH_CLAUSE)) {
            SyntaxNode catchBlock = context.directChild(catchClause, JAVA_BLOCK);
            if (catchBlock != null)
                merged = FieldFlowState.merge(merged, analyzeFieldFlow(context, catchBlock, fieldSymbol, state, reporter));
        }
        SyntaxNode finallyClause = context.directChild(node, JAVA_FINALLY_CLAUSE);
        if (finallyClause != null) {
            SyntaxNode finallyBlock = context.directChild(finallyClause, JAVA_BLOCK);
            if (finallyBlock != null)
                merged = analyzeFieldFlow(context, finallyBlock, fieldSymbol, merged, reporter);
        }
        return merged;
    }

    private static FieldFlowState analyzeFieldSwitch(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        List<SyntaxNode> rules = directChildrenOfKind(node, JAVA_SWITCH_RULE);
        if (rules.isEmpty())
            return state;

        FieldFlowState switchExit = switchHasDefault(rules) ? state.withNormalFlow(false) : state.withoutAbruptExits();
        FieldFlowState fallthrough = state.withNormalFlow(false);
        for (SyntaxNode rule : rules) {
            if (fallthrough.reachable() && isArrowSwitchRule(rule)) {
                switchExit = FieldFlowState.merge(switchExit, fallthrough.withoutAbruptExits());
                fallthrough = fallthrough.withNormalFlow(false);
            }

            FieldFlowState entryState = FieldFlowState.merge(state.withoutAbruptExits(), fallthrough.withoutAbruptExits());
            FieldFlowState ruleState = analyzeFieldSwitchRule(context, rule, fieldSymbol, entryState, reporter);
            FieldFlowState resumedBreaks = ruleState.resumeBreaks();
            if (isArrowSwitchRule(rule)) {
                switchExit = FieldFlowState.merge(switchExit, resumedBreaks.withoutAbruptExits());
                fallthrough = state.withNormalFlow(false);
            } else {
                switchExit = FieldFlowState.merge(switchExit, resumedBreaks.withNormalFlow(false));
                fallthrough = ruleState.withoutAbruptExits();
            }
        }
        return FieldFlowState.merge(switchExit, fallthrough.withoutAbruptExits());
    }

    private static FieldFlowState analyzeFieldSwitchRule(
            JavaRuleContext context,
            SyntaxNode rule,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        FieldFlowState current = state;
        boolean pastLabel = false;
        for (SyntaxNode child : structuralChildren(rule)) {
            if (!pastLabel) {
                pastLabel = true;
                continue;
            }
            current = analyzeFieldFlow(context, child, fieldSymbol, current, reporter);
            if (!current.reachable())
                return current;
        }
        return current;
    }

    private static FieldFlowState analyzeFieldWhile(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        return analyzeFieldWhile(context, node, fieldSymbol, state, reporter, null);
    }

    private static FieldFlowState analyzeFieldWhile(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter,
            @Nullable String loopLabel
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;
        SyntaxNode body = children.size() > 1 ? children.get(1) : null;
        FieldFlowState loopHead = stabilizeFieldWhileLoopHead(context, conditionChild(children), body, fieldSymbol, state, reporter, loopLabel);
        FieldFlowState bodyState = body == null ? loopHead : analyzeFieldFlow(context, body, fieldSymbol, loopHead, reporter);
        return FieldFlowState.loopExitFrom(state.snapshot(), bodyState.resumeContinues(loopLabel));
    }

    private static FieldFlowState analyzeFieldDoWhile(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        return analyzeFieldDoWhile(context, node, fieldSymbol, state, reporter, null);
    }

    private static FieldFlowState analyzeFieldDoWhile(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter,
            @Nullable String loopLabel
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;
        FieldFlowState bodyState = analyzeFieldFlow(context, children.getFirst(), fieldSymbol, state, reporter);
        FieldFlowState continued = bodyState.resumeContinues(loopLabel);
        return FieldFlowState.loopExitFrom(continued.reachable() ? continued.snapshot() : null, continued);
    }

    private static FieldFlowState analyzeFieldFor(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        return analyzeFieldFor(context, node, fieldSymbol, state, reporter, null);
    }

    private static FieldFlowState analyzeFieldFor(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter,
            @Nullable String loopLabel
    ) {
        List<SyntaxNode> children = structuralChildren(node);
        if (children.isEmpty())
            return state;
        SyntaxNode header = children.getFirst();
        if (JAVA_BASIC_FOR_STATEMENT.equals(header.kind().id()))
            return analyzeFieldBasicFor(context, header, children.size() > 1 ? children.get(1) : null, fieldSymbol, state, reporter, loopLabel);
        SyntaxNode body = children.size() > 1 ? children.get(1) : null;
        FieldFlowState bodyState = body == null ? state : analyzeFieldFlow(context, body, fieldSymbol, state, reporter);
        return FieldFlowState.loopExitFrom(state.snapshot(), bodyState.resumeContinues(loopLabel));
    }

    private static FieldFlowState analyzeFieldBasicFor(
            JavaRuleContext context,
            SyntaxNode header,
            @Nullable SyntaxNode body,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter,
            @Nullable String loopLabel
    ) {
        BasicForSegments segments = basicForSegments(header);
        FieldFlowState initState = analyzeFieldNodeSequence(context, segments.initNodes(), fieldSymbol, state, reporter);
        FieldFlowState loopHead = stabilizeFieldBasicForLoopHead(context, segments, body, fieldSymbol, initState, reporter, loopLabel);
        FieldFlowState conditionState = analyzeFieldNodeSequence(context, segments.conditionNodes(), fieldSymbol, loopHead, reporter);
        FieldFlowState bodyState = body == null ? conditionState : analyzeFieldFlow(context, body, fieldSymbol, conditionState, reporter);
        FieldFlowSnapshot normalExit = hasFiniteBasicForExit(segments)
                ? conditionState.snapshot()
                : null;
        return FieldFlowState.loopExitFrom(normalExit, bodyState.resumeContinues(loopLabel));
    }

    private static FieldFlowState stabilizeFieldWhileLoopHead(
            JavaRuleContext context,
            @Nullable SyntaxNode condition,
            @Nullable SyntaxNode body,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter,
            @Nullable String loopLabel
    ) {
        FieldFlowState baseHead = state.withoutAbruptExits();
        FieldFlowState loopHead = baseHead;
        for (int i = 0; i < LOOP_FIXPOINT_ITERATION_LIMIT; i++) {
            FieldFlowState bodyState = body == null ? loopHead : analyzeFieldFlow(context, body, fieldSymbol, loopHead, reporter);
            FieldFlowState nextHead = FieldFlowState.merge(baseHead, bodyState.resumeContinues(loopLabel).withoutAbruptExits());
            if (nextHead.withoutAbruptExits().equals(loopHead.withoutAbruptExits()))
                return nextHead;
            loopHead = nextHead;
        }
        return loopHead;
    }

    private static FieldFlowState stabilizeFieldBasicForLoopHead(
            JavaRuleContext context,
            BasicForSegments segments,
            @Nullable SyntaxNode body,
            Symbol fieldSymbol,
            FieldFlowState initState,
            @Nullable JavaInspectionRuleReporter reporter,
            @Nullable String loopLabel
    ) {
        FieldFlowState baseHead = initState.withoutAbruptExits();
        FieldFlowState loopHead = baseHead;
        for (int i = 0; i < LOOP_FIXPOINT_ITERATION_LIMIT; i++) {
            FieldFlowState conditionState = analyzeFieldNodeSequence(context, segments.conditionNodes(), fieldSymbol, loopHead, reporter);
            FieldFlowState bodyState = body == null ? conditionState : analyzeFieldFlow(context, body, fieldSymbol, conditionState, reporter);
            FieldFlowState updateState = analyzeFieldNodeSequence(
                    context,
                    segments.updateNodes(),
                    fieldSymbol,
                    bodyState.resumeContinues(loopLabel),
                    reporter
            );
            FieldFlowState nextHead = FieldFlowState.merge(baseHead, updateState.withoutAbruptExits());
            if (nextHead.withoutAbruptExits().equals(loopHead.withoutAbruptExits()))
                return nextHead;
            loopHead = nextHead;
        }
        return loopHead;
    }

    private static @Nullable SyntaxNode conditionChild(List<SyntaxNode> children) {
        return children.isEmpty() ? null : children.getFirst();
    }

    private static FieldFlowState analyzeFieldGeneric(
            JavaRuleContext context,
            SyntaxNode node,
            Symbol fieldSymbol,
            FieldFlowState state,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        if (JAVA_ASSIGNMENT_EXPRESSION.equals(node.kind().id())) {
            List<SyntaxNode> expressions = context.directExpressionChildren(node);
            if (expressions.size() >= 2) {
                SyntaxNode left = expressions.getFirst();
                Symbol resolved = context.resolvedSymbol(left).orElse(null);
                if (resolved == fieldSymbol) {
                    if (reporter != null && state.maybeAssigned())
                        reporter.report(left, fieldSymbol.simpleName());
                    return state.assign();
                }
            }
        }

        if (isIncrementExpression(node)) {
            SyntaxNode target = context.firstDirectExpressionChild(node);
            Symbol resolved = target == null ? null : context.resolvedSymbol(target).orElse(null);
            if (resolved == fieldSymbol) {
                if (reporter != null && state.maybeAssigned())
                    reporter.report(target, fieldSymbol.simpleName());
                return state.assign();
            }
        }

        FieldFlowState current = state;
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken)
                continue;
            current = analyzeFieldFlow(context, child, fieldSymbol, current, reporter);
            if (!current.reachable())
                break;
        }
        return current;
    }

    private static FieldFlowState analyzeStaticFieldInitializers(
            JavaRuleContext context,
            SyntaxNode typeNode,
            Symbol fieldSymbol,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        return analyzeFieldInitializers(context, typeNode, fieldSymbol, true, reporter);
    }

    private static FieldFlowState analyzeInstanceFieldInitializers(
            JavaRuleContext context,
            SyntaxNode typeNode,
            Symbol fieldSymbol,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        return analyzeFieldInitializers(context, typeNode, fieldSymbol, false, reporter);
    }

    private static FieldFlowState analyzeFieldInitializers(
            JavaRuleContext context,
            SyntaxNode typeNode,
            Symbol fieldSymbol,
            boolean staticField,
            @Nullable JavaInspectionRuleReporter reporter
    ) {
        SyntaxNode body = typeBody(typeNode);
        if (body == null)
            return FieldFlowState.initial();

        FieldFlowState state = FieldFlowState.initial();
        for (SyntaxNode child : body.children()) {
            if (JAVA_FIELD_DECLARATION.equals(child.kind().id())) {
                for (SyntaxNode declarator : directChildrenOfKind(child, JAVA_VARIABLE_DECLARATOR)) {
                    Symbol symbol = context.declaredSymbol(declarator).orElse(null);
                    if (symbol == null || symbol != fieldSymbol)
                        continue;
                    if (context.firstDirectExpressionChild(declarator) != null) {
                        if (reporter != null && state.maybeAssigned())
                            reporter.report(declarator, fieldSymbol.simpleName());
                        state = state.assign();
                    }
                }
                continue;
            }

            if (staticField && JAVA_STATIC_INITIALIZER.equals(child.kind().id())) {
                SyntaxNode block = context.directChild(child, JAVA_BLOCK);
                if (block != null)
                    state = analyzeFieldFlow(context, block, fieldSymbol, state, reporter);
                continue;
            }

            if (!staticField && JAVA_INSTANCE_INITIALIZER.equals(child.kind().id())) {
                SyntaxNode block = context.directChild(child, JAVA_BLOCK);
                if (block != null)
                    state = analyzeFieldFlow(context, block, fieldSymbol, state, reporter);
            }
        }
        return state;
    }

    private static @Nullable String breakOrContinueLabel(JavaRuleContext context, SyntaxNode node) {
        List<String> identifiers = identifierLikeTokens(node);
        if (identifiers.isEmpty())
            return null;
        return context.lastIdentifierLikeTokenText(node);
    }

    private static @Nullable SyntaxNode labeledStatementTarget(SyntaxNode labeledStatement) {
        for (SyntaxNode child : labeledStatement.children()) {
            if (!(child instanceof SyntaxToken) && !JAVA_LABELED_STATEMENT.equals(child.kind().id()))
                return child;
        }
        return null;
    }

    private static @Nullable String labelName(SyntaxNode labeledStatement) {
        List<String> identifiers = identifierLikeTokens(labeledStatement);
        return identifiers.isEmpty() ? null : identifiers.getFirst();
    }

    private static boolean isConstantTrueExpression(JavaRuleContext context, @Nullable SyntaxNode node) {
        if (node == null)
            return false;
        if (node.start() < 0 || node.end() > context.documentText().length() || node.start() >= node.end())
            return false;
        String text = context.documentText().substring(node.start(), node.end()).replaceAll("\\s+", "");
        return isConstantTrueExpressionText(text);
    }

    private static boolean isConstantTrueExpressionText(SyntaxNode node) {
        return isConstantTrueExpressionText(textOfNode(node));
    }

    private static boolean isConstantTrueExpressionText(String text) {
        while (text.startsWith("(") && text.endsWith(")") && outerParensWrapEntireText(text))
            text = text.substring(1, text.length() - 1);
        return "true".equals(text);
    }

    private static String textOfNode(SyntaxNode node) {
        StringBuilder builder = new StringBuilder();
        appendNodeText(node, builder);
        return builder.toString().replaceAll("\\s+", "");
    }

    private static void appendNodeText(SyntaxNode node, StringBuilder builder) {
        if (node instanceof SyntaxToken token) {
            builder.append(token.text());
            return;
        }

        for (SyntaxNode child : node.children())
            appendNodeText(child, builder);
    }

    private static boolean outerParensWrapEntireText(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < text.length() - 1)
                    return false;
            }
        }
        return depth == 0;
    }

    private static List<String> identifierLikeTokens(SyntaxNode node) {
        List<String> identifiers = new ArrayList<>();
        collectIdentifierLikeTokens(node, identifiers);
        return List.copyOf(identifiers);
    }

    private static void collectIdentifierLikeTokens(SyntaxNode node, List<String> out) {
        if (node instanceof SyntaxToken token) {
            String text = token.text();
            if (!text.isBlank()
                    && Character.isJavaIdentifierStart(text.charAt(0))
                    && !"break".equals(text)
                    && !"continue".equals(text)) {
                out.add(text);
            }
            return;
        }

        for (SyntaxNode child : node.children())
            collectIdentifierLikeTokens(child, out);
    }

    private record FlowState(
            Set<Symbol> definitelyAssigned,
            Set<Symbol> maybeAssigned,
            boolean reachable,
            List<FlowExit> breakExits,
            List<FlowExit> continueExits
    ) {
        private static FlowState initial() {
            return new FlowState(Set.of(), Set.of(), true, List.of(), List.of());
        }

        private FlowState assign(Symbol symbol) {
            Set<Symbol> definitely = new LinkedHashSet<>(definitelyAssigned);
            Set<Symbol> maybe = new LinkedHashSet<>(maybeAssigned);
            definitely.add(symbol);
            maybe.add(symbol);
            return new FlowState(Set.copyOf(definitely), Set.copyOf(maybe), reachable, breakExits, continueExits);
        }

        private FlowState unreachable() {
            return new FlowState(definitelyAssigned, maybeAssigned, false, breakExits, continueExits);
        }

        private FlowState withoutAbruptExits() {
            return new FlowState(definitelyAssigned, maybeAssigned, reachable, List.of(), List.of());
        }

        private FlowState withNormalFlow(boolean reachable) {
            return new FlowState(definitelyAssigned, maybeAssigned, reachable, breakExits, continueExits);
        }

        private FlowState breakExit(@Nullable String label) {
            List<FlowExit> exits = new ArrayList<>(breakExits);
            exits.add(new FlowExit(label, snapshot()));
            return new FlowState(definitelyAssigned, maybeAssigned, false, List.copyOf(exits), continueExits);
        }

        private FlowState continueExit(@Nullable String label) {
            List<FlowExit> exits = new ArrayList<>(continueExits);
            exits.add(new FlowExit(label, snapshot()));
            return new FlowState(definitelyAssigned, maybeAssigned, false, breakExits, List.copyOf(exits));
        }

        private FlowSnapshot snapshot() {
            return new FlowSnapshot(definitelyAssigned, maybeAssigned);
        }

        private FlowState resumeBreaks() {
            return resumeBreaks(null);
        }

        private FlowState resumeBreaks(@Nullable String label) {
            return resumeExits(breakExits, true, label);
        }

        private FlowState resumeContinues(@Nullable String label) {
            return resumeExits(continueExits, false, label);
        }

        private FlowState resumeExits(List<FlowExit> exits, boolean clearingBreaks, @Nullable String label) {
            FlowState resumed = reachable ? new FlowState(definitelyAssigned, maybeAssigned, true, List.of(), List.of()) : null;
            List<FlowExit> remaining = new ArrayList<>();
            for (FlowExit exit : exits) {
                if (!matchesLabel(exit.label(), label)) {
                    remaining.add(exit);
                    continue;
                }
                FlowSnapshot snapshot = exit.snapshot();
                FlowState branch = new FlowState(snapshot.definitelyAssigned(), snapshot.maybeAssigned(), true, List.of(), List.of());
                resumed = resumed == null ? branch : mergeBranches(resumed, branch);
            }
            if (resumed == null)
                resumed = new FlowState(definitelyAssigned, maybeAssigned, false, List.of(), List.of());
            return new FlowState(
                    resumed.definitelyAssigned,
                    resumed.maybeAssigned,
                    resumed.reachable,
                    clearingBreaks ? List.copyOf(remaining) : breakExits,
                    clearingBreaks ? continueExits : List.copyOf(remaining)
            );
        }

        private static FlowState mergeBranches(FlowState left, FlowState right) {
            List<FlowExit> breaks = concat(left.breakExits, right.breakExits);
            List<FlowExit> continues = concat(left.continueExits, right.continueExits);
            if (!left.reachable && !right.reachable) {
                return new FlowState(
                        Set.copyOf(union(left.definitelyAssigned, right.definitelyAssigned)),
                        Set.copyOf(union(left.maybeAssigned, right.maybeAssigned)),
                        false,
                        breaks,
                        continues
                );
            }
            if (!left.reachable)
                return new FlowState(right.definitelyAssigned, right.maybeAssigned, true, breaks, continues);
            if (!right.reachable)
                return new FlowState(left.definitelyAssigned, left.maybeAssigned, true, breaks, continues);
            return new FlowState(
                    Set.copyOf(intersection(left.definitelyAssigned, right.definitelyAssigned)),
                    Set.copyOf(union(left.maybeAssigned, right.maybeAssigned)),
                    true,
                    breaks,
                    continues
            );
        }

        private static FlowState loopExitFrom(@Nullable FlowSnapshot normalExit, FlowState bodyEnd) {
            List<FlowSnapshot> exits = new ArrayList<>();
            if (normalExit != null)
                exits.add(normalExit);
            for (FlowExit exit : bodyEnd.breakExits) {
                if (exit.label() == null)
                    exits.add(exit.snapshot());
            }
            if (exits.isEmpty())
                return new FlowState(bodyEnd.definitelyAssigned, bodyEnd.maybeAssigned, false, List.of(), List.of());

            Set<Symbol> definitely = new LinkedHashSet<>(exits.getFirst().definitelyAssigned());
            Set<Symbol> maybe = new LinkedHashSet<>();
            for (FlowSnapshot exit : exits) {
                definitely.retainAll(exit.definitelyAssigned());
                maybe.addAll(exit.maybeAssigned());
            }
            maybe.addAll(bodyEnd.maybeAssigned);
            return new FlowState(Set.copyOf(definitely), Set.copyOf(maybe), true, List.of(), List.of());
        }

        private static List<FlowExit> concat(List<FlowExit> left, List<FlowExit> right) {
            if (left.isEmpty())
                return right;
            if (right.isEmpty())
                return left;
            ArrayList<FlowExit> merged = new ArrayList<>(left);
            merged.addAll(right);
            return List.copyOf(merged);
        }

        private static Set<Symbol> union(Set<Symbol> left, Set<Symbol> right) {
            LinkedHashSet<Symbol> merged = new LinkedHashSet<>(left);
            merged.addAll(right);
            return merged;
        }

        private static Set<Symbol> intersection(Set<Symbol> left, Set<Symbol> right) {
            LinkedHashSet<Symbol> merged = new LinkedHashSet<>(left);
            merged.retainAll(right);
            return merged;
        }
    }

    private record FlowSnapshot(Set<Symbol> definitelyAssigned, Set<Symbol> maybeAssigned) {
    }

    private record FlowExit(@Nullable String label, FlowSnapshot snapshot) {
    }

    private record FieldSymbolInfo(Symbol symbol, SyntaxNode reportNode, boolean isStatic, boolean isBlankFinal) {
    }

    private record FieldFlowState(
            boolean definitelyAssigned,
            boolean maybeAssigned,
            boolean reachable,
            List<FieldFlowExit> breakExits,
            List<FieldFlowExit> continueExits
    ) {
        private static FieldFlowState initial() {
            return new FieldFlowState(false, false, true, List.of(), List.of());
        }

        private FieldFlowState assign() {
            return new FieldFlowState(true, true, reachable, breakExits, continueExits);
        }

        private FieldFlowState unreachable() {
            return new FieldFlowState(definitelyAssigned, maybeAssigned, false, breakExits, continueExits);
        }

        private FieldFlowState withoutAbruptExits() {
            return new FieldFlowState(definitelyAssigned, maybeAssigned, reachable, List.of(), List.of());
        }

        private FieldFlowState withNormalFlow(boolean reachable) {
            return new FieldFlowState(definitelyAssigned, maybeAssigned, reachable, breakExits, continueExits);
        }

        private FieldFlowState breakExit(@Nullable String label) {
            List<FieldFlowExit> exits = new ArrayList<>(breakExits);
            exits.add(new FieldFlowExit(label, snapshot()));
            return new FieldFlowState(definitelyAssigned, maybeAssigned, false, List.copyOf(exits), continueExits);
        }

        private FieldFlowState continueExit(@Nullable String label) {
            List<FieldFlowExit> exits = new ArrayList<>(continueExits);
            exits.add(new FieldFlowExit(label, snapshot()));
            return new FieldFlowState(definitelyAssigned, maybeAssigned, false, breakExits, List.copyOf(exits));
        }

        private FieldFlowSnapshot snapshot() {
            return new FieldFlowSnapshot(definitelyAssigned, maybeAssigned);
        }

        private FieldFlowState resumeBreaks() {
            return resumeBreaks(null);
        }

        private FieldFlowState resumeBreaks(@Nullable String label) {
            return resumeExits(breakExits, true, label);
        }

        private FieldFlowState resumeContinues(@Nullable String label) {
            return resumeExits(continueExits, false, label);
        }

        private FieldFlowState resumeExits(List<FieldFlowExit> exits, boolean clearingBreaks, @Nullable String label) {
            boolean resumedReachable = reachable || !exits.isEmpty();
            boolean resumedDefinitely = reachable && definitelyAssigned;
            boolean resumedMaybe = reachable && maybeAssigned;
            boolean first = !reachable;
            List<FieldFlowExit> remaining = new ArrayList<>();
            for (FieldFlowExit exit : exits) {
                if (!matchesLabel(exit.label(), label)) {
                    remaining.add(exit);
                    continue;
                }
                FieldFlowSnapshot snapshot = exit.snapshot();
                if (first) {
                    resumedDefinitely = snapshot.definitelyAssigned();
                    resumedMaybe = snapshot.maybeAssigned();
                    first = false;
                } else {
                    resumedDefinitely &= snapshot.definitelyAssigned();
                    resumedMaybe |= snapshot.maybeAssigned();
                }
            }
            return new FieldFlowState(
                    resumedDefinitely,
                    resumedMaybe,
                    resumedReachable,
                    clearingBreaks ? List.copyOf(remaining) : breakExits,
                    clearingBreaks ? continueExits : List.copyOf(remaining)
            );
        }

        private static FieldFlowState merge(FieldFlowState left, FieldFlowState right) {
            List<FieldFlowExit> breaks = concat(left.breakExits, right.breakExits);
            List<FieldFlowExit> continues = concat(left.continueExits, right.continueExits);
            if (!left.reachable && !right.reachable)
                return new FieldFlowState(left.definitelyAssigned || right.definitelyAssigned, left.maybeAssigned || right.maybeAssigned, false, breaks, continues);
            if (!left.reachable)
                return new FieldFlowState(right.definitelyAssigned, right.maybeAssigned, true, breaks, continues);
            if (!right.reachable)
                return new FieldFlowState(left.definitelyAssigned, left.maybeAssigned, true, breaks, continues);
            return new FieldFlowState(left.definitelyAssigned && right.definitelyAssigned, left.maybeAssigned || right.maybeAssigned, true, breaks, continues);
        }

        private static FieldFlowState loopExitFrom(@Nullable FieldFlowSnapshot normalExit, FieldFlowState bodyEnd) {
            List<FieldFlowSnapshot> exits = new ArrayList<>();
            if (normalExit != null)
                exits.add(normalExit);
            for (FieldFlowExit exit : bodyEnd.breakExits) {
                if (exit.label() == null)
                    exits.add(exit.snapshot());
            }
            if (exits.isEmpty())
                return new FieldFlowState(bodyEnd.definitelyAssigned, bodyEnd.maybeAssigned, false, List.of(), List.of());

            boolean definitely = exits.getFirst().definitelyAssigned();
            boolean maybe = bodyEnd.maybeAssigned;
            for (FieldFlowSnapshot exit : exits) {
                definitely &= exit.definitelyAssigned();
                maybe |= exit.maybeAssigned();
            }
            return new FieldFlowState(definitely, maybe, true, List.of(), List.of());
        }

        private static List<FieldFlowExit> concat(List<FieldFlowExit> left, List<FieldFlowExit> right) {
            if (left.isEmpty())
                return right;
            if (right.isEmpty())
                return left;
            ArrayList<FieldFlowExit> merged = new ArrayList<>(left);
            merged.addAll(right);
            return List.copyOf(merged);
        }
    }

    private record FieldFlowSnapshot(boolean definitelyAssigned, boolean maybeAssigned) {
    }

    private record FieldFlowExit(@Nullable String label, FieldFlowSnapshot snapshot) {
    }

    private record BasicForSegments(
            List<SyntaxNode> initNodes,
            List<SyntaxNode> conditionNodes,
            List<SyntaxNode> updateNodes
    ) {
    }

    private static boolean matchesLabel(@Nullable String exitLabel, @Nullable String targetLabel) {
        return targetLabel == null ? exitLabel == null : targetLabel.equals(exitLabel);
    }
}
