package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RegisteredInspection
public final class CoreExceptionInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-exceptions";

    private static final String JAVA_METHOD_DECLARATION = "JAVA_METHOD_DECLARATION";
    private static final String JAVA_CONSTRUCTOR_DECLARATION = "JAVA_CONSTRUCTOR_DECLARATION";
    private static final String JAVA_RECORD_COMPACT_CONSTRUCTOR = "JAVA_RECORD_COMPACT_CONSTRUCTOR";
    private static final String JAVA_THROW_STATEMENT = "JAVA_THROW_STATEMENT";
    private static final String JAVA_METHOD_INVOCATION_EXPRESSION = "JAVA_METHOD_INVOCATION_EXPRESSION";
    private static final String JAVA_CLASS_INSTANCE_CREATION_EXPRESSION = "JAVA_CLASS_INSTANCE_CREATION_EXPRESSION";
    private static final String JAVA_TRY_STATEMENT = "JAVA_TRY_STATEMENT";
    private static final String JAVA_TRY_RESOURCE = "JAVA_TRY_RESOURCE";
    private static final String JAVA_CATCH_CLAUSE = "JAVA_CATCH_CLAUSE";
    private static final String JAVA_FINALLY_CLAUSE = "JAVA_FINALLY_CLAUSE";
    private static final String JAVA_THROWS_CLAUSE = "JAVA_THROWS_CLAUSE";
    private static final String JAVA_TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";
    private static final String JAVA_UNION_TYPE_REFERENCE = "JAVA_UNION_TYPE_REFERENCE";
    private static final String JAVA_BLOCK = "JAVA_BLOCK";
    private static final Set<String> EXCEPTION_ANALYSIS_BARRIERS = Set.of(
        JAVA_METHOD_DECLARATION,
        JAVA_CONSTRUCTOR_DECLARATION,
        JAVA_RECORD_COMPACT_CONSTRUCTOR,
        "JAVA_CLASS_DECLARATION",
        "JAVA_INTERFACE_DECLARATION",
        "JAVA_ENUM_DECLARATION",
        "JAVA_ANNOTATION_TYPE_DECLARATION",
        "JAVA_RECORD_DECLARATION",
        "JAVA_LAMBDA_EXPRESSION"
    );
    private static final Set<String> BANNED_EXCEPTION_TYPES_IN_METHOD_SIGNATURES = Set.of(
        "java.lang.Throwable",
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.Error"
    );

    private static final List<JavaInspectionRule> RULES = List.of(
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNCAUGHT_CHECKED_EXCEPTION.id(),
            JavaSemanticRules.UNCAUGHT_CHECKED_EXCEPTION.defaultSeverity(),
            JavaSemanticRules.UNCAUGHT_CHECKED_EXCEPTION.messageTemplate(),
            Set.of("core", "exceptions"),
            CoreExceptionInspection::reportUnhandledCheckedExceptions
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.UNREACHABLE_CATCH.id(),
            JavaSemanticRules.UNREACHABLE_CATCH.defaultSeverity(),
            JavaSemanticRules.UNREACHABLE_CATCH.messageTemplate(),
            Set.of("core", "exceptions"),
            CoreExceptionInspection::reportUnreachableCatches
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.INVALID_EXCEPTION_TYPE.id(),
            JavaSemanticRules.INVALID_EXCEPTION_TYPE.defaultSeverity(),
            JavaSemanticRules.INVALID_EXCEPTION_TYPE.messageTemplate(),
            Set.of("core", "exceptions"),
            CoreExceptionInspection::reportInvalidExceptionTypes
        ),
        new SimpleJavaInspectionRule(
            JavaSemanticRules.DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE.id(),
            JavaSemanticRules.DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE.defaultSeverity(),
            JavaSemanticRules.DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE.messageTemplate(),
            Set.of("core", "api-design"),
            CoreExceptionInspection::reportDisallowedExceptionInMethodSignature
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

    private static void reportUnhandledCheckedExceptions(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
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

            Set<String> allowed = new LinkedHashSet<>(context.declaredThrownTypeNames(node));
            List<ThrownException> unhandled = collectUnhandledCheckedExceptions(context, body, allowed);
            for (ThrownException exception : unhandled) {
                if (isAllowed(exception.qualifiedTypeName(), allowed, context))
                    continue;
                reporter.report(exception.reportNode(), exception.qualifiedTypeName());
            }
        });
    }

    private static void reportUnreachableCatches(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            if (!JAVA_TRY_STATEMENT.equals(node.kind().id()))
                return;

            List<SyntaxNode> catchClauses = directChildrenOfKind(node, JAVA_CATCH_CLAUSE);
            List<String> previouslyCaught = new ArrayList<>();
            for (SyntaxNode catchClause : catchClauses) {
                List<String> catchTypes = context.catchParameterTypeNames(catchClause);
                for (String catchType : catchTypes) {
                    if (isCoveredByAny(catchType, previouslyCaught, context)) {
                        reporter.report(catchClause, catchType);
                    } else {
                        previouslyCaught.add(catchType);
                    }
                }
            }
        });
    }

    private static void reportInvalidExceptionTypes(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        context.traverse(node -> {
            String kindId = node.kind().id();
            if (JAVA_METHOD_DECLARATION.equals(kindId)
                || JAVA_CONSTRUCTOR_DECLARATION.equals(kindId)
                || JAVA_RECORD_COMPACT_CONSTRUCTOR.equals(kindId)) {
                SyntaxNode throwsClause = context.directChild(node, JAVA_THROWS_CLAUSE);
                if (throwsClause != null)
                    reportInvalidTypeReferences(context, reporter, throwsClause, "declared thrown type");
                return;
            }

            if (JAVA_CATCH_CLAUSE.equals(kindId)) {
                reportInvalidTypeReferences(context, reporter, node, "caught type");
                return;
            }

            if (!JAVA_THROW_STATEMENT.equals(kindId))
                return;

            SyntaxNode expression = context.firstDirectExpressionChild(node);
            if (expression == null)
                return;

            Type thrownType = context.inferredType(expression).orElse(new Type.UnknownType("<unknown>"));
            if (thrownType.kind() != Type.Kind.DECLARED)
                return;

            String qualifiedTypeName = context.resolveQualifiedTypeName(thrownType.displayName());
            if (qualifiedTypeName == null || context.isThrowableType(qualifiedTypeName))
                return;

            reporter.reportMessage(expression, "thrown expression type '%s' must extend Throwable".formatted(qualifiedTypeName));
        });
    }

    // TODO: We need to make these exceptions a user-defined policy decision rather than hardcoding them in the inspection
    private static void reportDisallowedExceptionInMethodSignature(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (SyntaxNode syntaxNode : context.nodesOfKinds(JavaSyntaxKinds.METHOD_DECLARATION.id(), JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id(), JavaSyntaxKinds.RECORD_COMPACT_CONSTRUCTOR.id())) {
            Symbol methodSymbol = context.declaredSymbol(syntaxNode).orElse(null);
            if (methodSymbol == null)
                continue;

            SyntaxNode throwsClauseNode = context.directChild(syntaxNode, JavaSyntaxKinds.THROWS_CLAUSE.id());
            if (throwsClauseNode == null)
                continue;

            for (SyntaxNode typeNode : directTypeNodes(throwsClauseNode)) {
                String qualifiedTypeName = context.resolveQualifiedTypeName(typeNode);
                if (qualifiedTypeName == null)
                    continue;

                if (BANNED_EXCEPTION_TYPES_IN_METHOD_SIGNATURES.contains(qualifiedTypeName)) {
                    reporter.report(typeNode, methodSymbol.simpleName(), qualifiedTypeName);
                }
            }
        }
    }

    private static List<ThrownException> collectUnhandledCheckedExceptions(JavaRuleContext context, SyntaxNode node, Set<String> allowed) {
        String kindId = node.kind().id();
        if (EXCEPTION_ANALYSIS_BARRIERS.contains(kindId))
            return List.of();

        if (JAVA_TRY_STATEMENT.equals(kindId))
            return collectUnhandledFromTry(context, node, allowed);

        List<ThrownException> exceptions = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (JAVA_CATCH_CLAUSE.equals(child.kind().id()) || JAVA_FINALLY_CLAUSE.equals(child.kind().id()))
                continue;
            exceptions.addAll(collectUnhandledCheckedExceptions(context, child, allowed));
        }

        switch (kindId) {
            case JAVA_THROW_STATEMENT -> addThrownExpressionException(context, node, exceptions);
            case JAVA_METHOD_INVOCATION_EXPRESSION, JAVA_CLASS_INSTANCE_CREATION_EXPRESSION ->
                addCallableThrownExceptions(context, node, exceptions);
            default -> {
            }
        }

        return List.copyOf(exceptions);
    }

    private static List<ThrownException> collectUnhandledFromTry(JavaRuleContext context, SyntaxNode tryStatement, Set<String> allowed) {
        List<ThrownException> exceptions = new ArrayList<>();

        for (SyntaxNode resource : directChildrenOfKind(tryStatement, JAVA_TRY_RESOURCE)) {
            exceptions.addAll(collectUnhandledCheckedExceptions(context, resource, allowed));
            addResourceCloseExceptions(context, resource, exceptions);
        }

        SyntaxNode tryBlock = context.directChild(tryStatement, JAVA_BLOCK);
        List<ThrownException> tryExceptions = tryBlock == null
            ? List.of()
            : collectUnhandledCheckedExceptions(context, tryBlock, allowed);

        List<SyntaxNode> catchClauses = directChildrenOfKind(tryStatement, JAVA_CATCH_CLAUSE);
        for (ThrownException exception : tryExceptions) {
            if (!isCaught(exception.qualifiedTypeName(), catchClauses, context))
                exceptions.add(exception);
        }

        for (SyntaxNode catchClause : catchClauses) {
            SyntaxNode catchBlock = context.directChild(catchClause, JAVA_BLOCK);
            if (catchBlock != null)
                exceptions.addAll(collectUnhandledCheckedExceptions(context, catchBlock, allowed));
        }

        SyntaxNode finallyClause = context.directChild(tryStatement, JAVA_FINALLY_CLAUSE);
        if (finallyClause != null) {
            SyntaxNode finallyBlock = context.directChild(finallyClause, JAVA_BLOCK);
            if (finallyBlock != null)
                exceptions.addAll(collectUnhandledCheckedExceptions(context, finallyBlock, allowed));
        }

        return List.copyOf(exceptions);
    }

    private static void addResourceCloseExceptions(JavaRuleContext context, SyntaxNode resource, List<ThrownException> out) {
        String resourceTypeName = context.tryResourceTypeName(resource);
        if (resourceTypeName == null || !context.isAutoCloseableType(resourceTypeName))
            return;

        for (String qualifiedTypeName : context.closeThrownTypeNames(resourceTypeName)) {
            if (context.isCheckedExceptionType(qualifiedTypeName))
                out.add(new ThrownException(resource, qualifiedTypeName));
        }
    }

    private static void addThrownExpressionException(JavaRuleContext context, SyntaxNode throwStatement, List<ThrownException> out) {
        SyntaxNode expression = context.firstDirectExpressionChild(throwStatement);
        if (expression == null)
            return;

        Type thrownType = context.inferredType(expression).orElse(new Type.UnknownType("<unknown>"));
        if (thrownType.kind() != Type.Kind.DECLARED)
            return;

        String qualifiedTypeName = context.resolveQualifiedTypeName(thrownType.displayName());
        if (qualifiedTypeName == null || !context.isCheckedExceptionType(qualifiedTypeName))
            return;

        out.add(new ThrownException(expression, qualifiedTypeName));
    }

    private static void addCallableThrownExceptions(JavaRuleContext context, SyntaxNode node, List<ThrownException> out) {
        Symbol symbol = context.resolvedSymbol(node).orElse(null);
        if (symbol == null || (symbol.kind() != SymbolKind.METHOD && symbol.kind() != SymbolKind.CONSTRUCTOR))
            return;

        for (String qualifiedTypeName : context.thrownTypeNames(symbol)) {
            if (!context.isCheckedExceptionType(qualifiedTypeName))
                continue;
            out.add(new ThrownException(reportNodeForCallable(context, node), qualifiedTypeName));
        }
    }

    private static SyntaxNode reportNodeForCallable(JavaRuleContext context, SyntaxNode node) {
        if (JAVA_METHOD_INVOCATION_EXPRESSION.equals(node.kind().id())) {
            SyntaxNode selector = context.selectorNameNode(node);
            if (selector != null)
                return selector;
        }
        return node;
    }

    private static boolean isAllowed(String qualifiedTypeName, Set<String> allowed, JavaRuleContext context) {
        return isCoveredByAny(qualifiedTypeName, allowed, context);
    }

    private static boolean isCaught(String qualifiedTypeName, List<SyntaxNode> catchClauses, JavaRuleContext context) {
        for (SyntaxNode catchClause : catchClauses) {
            if (isCoveredByAny(qualifiedTypeName, context.catchParameterTypeNames(catchClause), context))
                return true;
        }
        return false;
    }

    private static boolean isCoveredByAny(String qualifiedTypeName, Iterable<String> candidates, JavaRuleContext context) {
        for (String candidate : candidates) {
            if (qualifiedTypeName.equals(candidate) || context.isSubtype(qualifiedTypeName, candidate))
                return true;
        }
        return false;
    }

    private static void reportInvalidTypeReferences(
        JavaRuleContext context,
        JavaInspectionRuleReporter reporter,
        SyntaxNode root,
        String role
    ) {
        for (SyntaxNode typeNode : directTypeNodes(root)) {
            String qualifiedTypeName = context.resolveQualifiedTypeName(typeNode);
            if (qualifiedTypeName == null || context.isThrowableType(qualifiedTypeName))
                continue;
            reporter.reportMessage(typeNode, "%s '%s' must extend Throwable".formatted(role, qualifiedTypeName));
        }
    }

    private static List<SyntaxNode> directTypeNodes(SyntaxNode root) {
        List<SyntaxNode> nodes = new ArrayList<>();
        collectTypeNodes(root, nodes);
        return List.copyOf(nodes);
    }

    private static void collectTypeNodes(SyntaxNode node, List<SyntaxNode> out) {
        String kindId = node.kind().id();
        if (JAVA_TYPE_REFERENCE.equals(kindId)) {
            out.add(node);
            return;
        }

        if (JAVA_UNION_TYPE_REFERENCE.equals(kindId)) {
            for (SyntaxNode child : node.children()) {
                if (JAVA_TYPE_REFERENCE.equals(child.kind().id()))
                    out.add(child);
            }
            return;
        }
        for (SyntaxNode child : node.children())
            collectTypeNodes(child, out);
    }

    private static List<SyntaxNode> directChildrenOfKind(SyntaxNode node, String kindId) {
        List<SyntaxNode> children = new ArrayList<>();
        for (SyntaxNode child : node.children()) {
            if (kindId.equals(child.kind().id()))
                children.add(child);
        }
        return List.copyOf(children);
    }

    private record ThrownException(SyntaxNode reportNode, String qualifiedTypeName) {
    }
}
