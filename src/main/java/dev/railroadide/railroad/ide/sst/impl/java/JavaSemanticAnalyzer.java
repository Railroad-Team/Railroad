package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.classparser.stub.*;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.indexing.Indexes;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSourceSnapshotIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSourceSymbolIds;
import dev.railroadide.railroad.ide.sst.project.JavaBinarySymbolExtractor;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import dev.railroadide.railroad.ide.sst.project.BinaryStubPublication;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Java semantic analysis entry point.
 * <p>
 * Current pipeline:
 * 1) declaration collection
 * 2) name resolution
 * 3) baseline type inference/checking
 */
public final class JavaSemanticAnalyzer {
    private static final ReentrantLock JDK_CACHE_LOCK = new ReentrantLock();
    private static volatile Set<String> cachedJdkQualifiedTypeNames;
    private static volatile Map<String, ClassStub> cachedJdkClassStubsByQualifiedName;

    private static final Set<String> IDENTIFIER_LIKE_TOKEN_KIND_IDS = Set.of(
        JavaSyntaxKinds.tokenKind(JavaTokenType.IDENTIFIER).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.UNDERSCORE_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.EXPORTS_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.MODULE_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.NON_SEALED_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.OPENS_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.PERMITS_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.PROVIDES_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.RECORD_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.REQUIRES_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.SEALED_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.TO_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.TRANSITIVE_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.USES_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.VAR_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.WITH_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.YIELD_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.WHEN_KEYWORD).id());

    private static final Set<String> TRIVIA_TOKEN_KIND_IDS = Set.of(
        JavaSyntaxKinds.tokenKind(JavaTokenType.WHITESPACE).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.LINE_TERMINATOR).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.LINE_COMMENT).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.BLOCK_COMMENT).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.JAVADOC_COMMENT).id());

    private static final Set<String> PRIMITIVE_TOKEN_KIND_IDS = Set.of(
        JavaSyntaxKinds.tokenKind(JavaTokenType.BOOLEAN_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.BYTE_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.SHORT_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.INT_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.LONG_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.CHAR_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.FLOAT_KEYWORD).id(),
        JavaSyntaxKinds.tokenKind(JavaTokenType.DOUBLE_KEYWORD).id());

    private static final Set<String> EXPRESSION_KIND_IDS = Set.of(
        JavaSyntaxKinds.EXPRESSION.id(),
        JavaSyntaxKinds.LAMBDA_EXPRESSION.id(),
        JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id(),
        JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id(),
        JavaSyntaxKinds.BINARY_EXPRESSION.id(),
        JavaSyntaxKinds.INSTANCEOF_EXPRESSION.id(),
        JavaSyntaxKinds.UNARY_EXPRESSION.id(),
        JavaSyntaxKinds.CAST_EXPRESSION.id(),
        JavaSyntaxKinds.POSTFIX_EXPRESSION.id(),
        JavaSyntaxKinds.PRIMARY_EXPRESSION.id(),
        JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id(),
        JavaSyntaxKinds.NAME_EXPRESSION.id(),
        JavaSyntaxKinds.THIS_EXPRESSION.id(),
        JavaSyntaxKinds.SUPER_EXPRESSION.id(),
        JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id(),
        JavaSyntaxKinds.ARRAY_ACCESS_EXPRESSION.id(),
        JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id(),
        JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id(),
        JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id(),
        JavaSyntaxKinds.ARRAY_CREATION_EXPRESSION.id(),
        JavaSyntaxKinds.ARRAY_INITIALIZER_EXPRESSION.id(),
        JavaSyntaxKinds.CLASS_LITERAL_EXPRESSION.id(),
        JavaSyntaxKinds.SWITCH_EXPRESSION.id(),
        JavaSyntaxKinds.LITERAL_EXPRESSION.id());

    private JavaSemanticAnalyzer() {
    }

    /**
     * Parses Java source, resolves symbols and types, and runs the core inspection rules.
     *
     * @param source the Java source to analyze
     * @return the semantic model with analysis and core inspection diagnostics
     */
    public static SemanticModel analyze(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyze(JavaSyntaxParser.parse(source));
    }

    /**
     * Parses and analyzes Java source against the supplied project symbols, then runs core inspections.
     *
     * @param source the Java source to analyze
     * @param projectIndex the project index used to resolve external types and members
     * @return the semantic model with analysis and core inspection diagnostics
     */
    public static SemanticModel analyze(CharSequence source, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(source, "source");
        return analyze(JavaSyntaxParser.parse(source), projectIndex);
    }

    /**
     * Resolves declarations, symbols, and types in a parsed tree and runs the core inspection rules.
     *
     * @param syntaxTree the Java syntax tree to analyze
     * @return the semantic model with analysis and core inspection diagnostics
     */
    public static SemanticModel analyze(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeFacts(syntaxTree));
    }

    /**
     * Analyzes a parsed tree against the supplied project symbols and runs the core inspection rules.
     *
     * @param syntaxTree the Java syntax tree to analyze
     * @param projectIndex the project index used to resolve external types and members
     * @return the semantic model with analysis and core inspection diagnostics
     */
    public static SemanticModel analyze(SyntaxTree syntaxTree, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeFacts(syntaxTree, projectIndex));
    }

    /**
     * Parses Java source and collects declarations, resolved symbols, and inferred types without running inspections.
     *
     * @param source the Java source to analyze
     * @return the semantic facts and diagnostics produced by analysis
     */
    public static SemanticModel analyzeFacts(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeFacts(JavaSyntaxParser.parse(source));
    }

    /**
     * Parses and analyzes Java source using project symbols without running inspection rules.
     *
     * @param source the Java source to analyze
     * @param projectIndex the project index used to resolve external types and members
     * @return the semantic facts and diagnostics produced by analysis
     */
    public static SemanticModel analyzeFacts(CharSequence source, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(source, "source");
        return analyzeFacts(JavaSyntaxParser.parse(source), projectIndex);
    }

    /**
     * Parses and analyzes Java source with cooperative checks throughout the semantic passes.
     *
     * @param source captured source text
     * @param projectIndex captured external symbols, or null for the runtime library
     * @param activityCheck request activity check
     * @return completed semantic facts; inactive requests never return a partial model
     */
    public static SemanticModel analyzeFacts(
        CharSequence source,
        @Nullable JavaSymbolIndex projectIndex,
        Runnable activityCheck
    ) {
        return analyzeFacts(JavaSyntaxParser.parse(source, activityCheck), projectIndex, activityCheck);
    }

    /**
     * Analyzes the exact document and controls carried by a feature request.
     *
     * @param request captured text document request
     * @param projectIndex captured external symbols, or null for the runtime library
     * @return completed semantic facts retaining the request's document snapshot
     */
    public static SemanticModel analyzeFacts(FeatureRequestContext request, @Nullable JavaSymbolIndex projectIndex) {
        return analyzeFacts(JavaSyntaxParser.parse(request), projectIndex, request::checkActive);
    }

    /**
     * Resolves a captured tree with checks between passes and during graph traversal.
     *
     * @param syntaxTree captured syntax tree
     * @param projectIndex captured external symbols, or null for the runtime library
     * @param activityCheck request activity check
     * @return completed semantic facts
     */
    public static SemanticModel analyzeFacts(
        SyntaxTree syntaxTree,
        @Nullable JavaSymbolIndex projectIndex,
        Runnable activityCheck
    ) {
        return performAnalysis(Objects.requireNonNull(syntaxTree, "syntaxTree"), true, projectIndex, activityCheck);
    }

    /**
     * Collects declarations, resolves names, and infers types using the standard library for external symbols.
     * Inspection rules are not run.
     *
     * @param syntaxTree the Java syntax tree to analyze
     * @return the semantic facts and diagnostics produced by analysis
     */
    public static SemanticModel analyzeFacts(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return performAnalysis(syntaxTree, true);
    }

    /**
     * Collects declarations, resolves names, and infers types against project symbols without running inspections.
     *
     * @param syntaxTree the Java syntax tree to analyze
     * @param projectIndex the project index used to resolve external types and members
     * @return the semantic facts and diagnostics produced by analysis
     */
    public static SemanticModel analyzeFacts(SyntaxTree syntaxTree, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        Objects.requireNonNull(projectIndex, "projectIndex");
        return performAnalysis(syntaxTree, true, projectIndex);
    }

    /**
     * Parses Java source and collects its declarations and scopes, then runs core inspection rules.
     * Name resolution and expression type inference are skipped.
     *
     * @param source the Java source whose declarations should be analyzed
     * @return the declaration model with analysis and core inspection diagnostics
     */
    public static SemanticModel analyzeDeclarations(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeDeclarations(JavaSyntaxParser.parse(source));
    }

    /**
     * Collects declarations and scopes from a parsed tree and runs core inspection rules.
     * Name resolution and expression type inference are skipped.
     *
     * @param syntaxTree the Java syntax tree whose declarations should be analyzed
     * @return the declaration model with analysis and core inspection diagnostics
     */
    public static SemanticModel analyzeDeclarations(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeDeclarationsFacts(syntaxTree));
    }

    /**
     * Parses Java source and collects declarations and scopes without resolving names, inferring expression
     * types, or running inspection rules.
     *
     * @param source the Java source whose declarations should be collected
     * @return the declaration facts and diagnostics produced while collecting them
     */
    public static SemanticModel analyzeDeclarationsFacts(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeDeclarationsFacts(JavaSyntaxParser.parse(source));
    }

    /**
     * Collects declaration facts while honoring the calling request's cancellation and deadline.
     *
     * @param source captured source text
     * @param activityCheck request activity check
     * @return completed declaration model
     */
    public static SemanticModel analyzeDeclarationsFacts(CharSequence source, Runnable activityCheck) {
        return performAnalysis(JavaSyntaxParser.parse(source, activityCheck), false, null, activityCheck);
    }

    /**
     * Collects declarations and scopes without resolving names, inferring expression types, or running inspections.
     *
     * @param syntaxTree the Java syntax tree whose declarations should be collected
     * @return the declaration facts and diagnostics produced while collecting them
     */
    public static SemanticModel analyzeDeclarationsFacts(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return performAnalysis(syntaxTree, false);
    }

    /**
     * Collects declarations from an exact captured tree with cooperative cancellation.
     *
     * @param syntaxTree captured Java syntax tree whose declarations are collected
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return semantic declaration facts without resolving method bodies
     */
    public static SemanticModel analyzeDeclarationsFacts(SyntaxTree syntaxTree, Runnable activityCheck) {
        return performAnalysis(Objects.requireNonNull(syntaxTree), false, null, activityCheck);
    }

    /**
     * Collects declarations with canonical signatures using captured external type names.
     *
     * @param syntaxTree captured Java syntax tree whose declarations are collected
     * @param index captured external symbols used for name resolution, or {@code null} for local resolution
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return semantic declaration facts without resolving method bodies
     */
    public static SemanticModel analyzeDeclarationsFacts(
        SyntaxTree syntaxTree,
        JavaSymbolIndex index,
        Runnable activityCheck
    ) {
        return performAnalysis(Objects.requireNonNull(syntaxTree), false, index, activityCheck);
    }

    private static SemanticModel withCoreDiagnostics(SemanticModel facts) {
        var context = new JavaRuleContext(Path.of("memory.java"), "", facts);
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        for (var provider : JavaInspectionRegistries.coreRuleProviders()) {
            diagnostics.addAll(JavaInspectionRuleEngine.collectDiagnostics(provider, context));
        }

        return facts.withAdditionalDiagnostics(diagnostics);
    }

    private static SemanticModel performAnalysis(SyntaxTree syntaxTree, boolean includeResolutionAndTypes) {
        return performAnalysis(syntaxTree, includeResolutionAndTypes, null);
    }

    private static SemanticModel performAnalysis(
        SyntaxTree syntaxTree,
        boolean includeResolutionAndTypes,
        @Nullable JavaSymbolIndex projectIndex
    ) {
        return performAnalysis(syntaxTree, includeResolutionAndTypes, projectIndex,
            ActivityCheckpoint::checkInterrupted);
    }

    private static SemanticModel performAnalysis(
        SyntaxTree syntaxTree,
        boolean includeResolutionAndTypes,
        @Nullable JavaSymbolIndex projectIndex,
        Runnable activityCheck
    ) {
        Runnable checkpoint = new ActivityCheckpoint(activityCheck);
        checkpoint.run();
        Scope rootScope = Scope.root();
        SemanticModel.Builder builder = SemanticModel.builder(syntaxTree, rootScope);

        var context = new JavaDeclarationAnalysis.Context(syntaxTree.root(), rootScope, builder, projectIndex,
            checkpoint);
        JavaDeclarationAnalysis.collect(context, syntaxTree.root());

        if (includeResolutionAndTypes) {
            var nameResolver = new NameResolver(context);
            checkpoint.run();
            nameResolver.resolveCompilationUnit(syntaxTree.root());
            checkpoint.run();
            new TypeResolver(context).resolveCompilationUnit(syntaxTree.root());
            checkpoint.run();
            nameResolver.resolveDeferredCallables(syntaxTree.root());
        }

        checkpoint.run();
        SemanticModel result = builder.build();
        checkpoint.run();
        return result;
    }

    private static final class NameResolver {
        private static final String POLY_FUNCTIONAL_ARGUMENT = "<poly-functional";

        private final JavaDeclarationAnalysis.Context context;
        private final @Nullable JavaSymbolIndex projectIndex;
        private final Set<String> localQualifiedTypeNames;
        private final Set<String> availableQualifiedTypeNames;
        private final Map<String, ClassStub> binaryClassStubsByQualifiedName;
        private final List<ImportSpec> imports = new ArrayList<>();
        private final Map<String, ImportSpec> singleTypeImportsBySimpleName = new LinkedHashMap<>();
        private final Map<String, List<ImportSpec>> staticSingleImportsByMemberName = new LinkedHashMap<>();
        private final List<ImportSpec> onDemandTypeImports = new ArrayList<>();
        private final List<ImportSpec> onDemandStaticImports = new ArrayList<>();
        private final Map<String, Set<String>> localStaticFieldsByOwner = new LinkedHashMap<>();
        private final Map<String, Map<String, Set<Integer>>> localStaticMethodAritiesByOwner = new LinkedHashMap<>();
        private final Map<String, Map<String, List<MemberCandidate>>> localFieldsByOwner = new LinkedHashMap<>();
        private final Map<String, Map<String, List<MemberCandidate>>> localMethodsByOwner = new LinkedHashMap<>();
        private final Map<String, List<MemberCandidate>> localConstructorsByOwner = new LinkedHashMap<>();
        private final Set<String> localTypesWithExplicitConstructors = new HashSet<>();
        private final Map<String, List<String>> directSuperTypesByQualifiedName = new LinkedHashMap<>();
        private final Set<String> directSuperTypesInProgress = new HashSet<>();
        private final Map<String, Type> projectMemberValueTypesByKey = new LinkedHashMap<>();
        private final Map<String, List<Type>> projectMethodParameterTypesByKey = new LinkedHashMap<>();
        private final Map<String, Boolean> projectVariableArityByKey = new LinkedHashMap<>();
        private final Map<String, List<JavaRuleContext.MethodDescriptor>> projectSourceMethodsByOwner = new LinkedHashMap<>();
        private final Map<String, Map<String, Type>> projectRecordAccessorTypesByOwner = new LinkedHashMap<>();
        private final Set<SyntaxNode> contextualInferenceInProgress = Collections
            .newSetFromMap(new IdentityHashMap<>());
        private TypeResolver resolvedExpressionTypeResolver;

        private NameResolver(JavaDeclarationAnalysis.Context context) {
            this.context = context;
            this.projectIndex = context.projectIndex;
            Set<String> qualified = new HashSet<>();
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                symbol.qualifiedName().ifPresent(qualified::add);
            }

            this.localQualifiedTypeNames = Set.copyOf(qualified);
            if (projectIndex != null) {
                this.availableQualifiedTypeNames = projectIndex.declaredQualifiedNames();
                this.binaryClassStubsByQualifiedName = projectIndex.classStubsByQualifiedName();
            } else {
                this.availableQualifiedTypeNames = loadJdkQualifiedTypeNames(context.activityCheck);
                this.binaryClassStubsByQualifiedName = loadJdkClassStubsByQualifiedName(context.activityCheck);
            }
            collectImportsFromRootScope();
            classifyImports();
            indexLocalStaticMembers();
            indexLocalMembers();
            this.resolvedExpressionTypeResolver = new TypeResolver(context);
        }

        private void resolveCompilationUnit(SyntaxNode root) {
            resolveNode(root);
        }

        private void resolveDeferredCallables(SyntaxNode root) {
            // The first resolution pass necessarily asks for types before all callables are
            // resolved. Discard that partial cache so deferred calls see the completed symbol
            // graph and the primary type-inference pass that just ran.
            resolvedExpressionTypeResolver = new TypeResolver(context);
            resolveDeferredNode(root);
        }

        private void resolveNode(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                context.poll();
                resolveNode(child);
            }

            String kindId = node.kind().id();

            if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(kindId)) {
                resolveNameExpression(node);
            } else if (JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(kindId)) {
                resolveFieldAccess(node);
            } else if (JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(kindId)) {
                resolveMethodInvocation(node);
            } else if (JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(kindId)) {
                resolveMethodReference(node);
            } else if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(kindId)) {
                resolveClassInstanceCreation(node);
            }
        }

        private void resolveMethodReference(SyntaxNode methodReference) {
            if (hasTokenKind(methodReference, JavaTokenType.NEW_KEYWORD))
                return;

            SyntaxNode receiver = methodReference.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .findFirst()
                .orElse(null);
            String methodName = lastIdentifierLikeTokenText(methodReference);
            if (receiver == null || methodName == null || methodName.isBlank())
                return;

            Symbol receiverSymbol = context.resolvedSymbol(receiver);
            boolean typeReceiver = receiverSymbol != null && isTypeSymbol(receiverSymbol.kind());
            String ownerQualifiedName = typeReceiver
                ? receiverSymbol.qualifiedName().orElse(null)
                : qualifiedTypeNameOfExpression(receiver, methodReference);
            if (ownerQualifiedName == null)
                return;

            List<MemberCandidate> candidates = new ArrayList<>();
            candidates.addAll(findMethodCandidates(ownerQualifiedName, methodName, false));
            if (typeReceiver) {
                candidates.addAll(findMethodCandidates(ownerQualifiedName, methodName, true));
            }
            if (!candidates.isEmpty()) {
                context.resolve(methodReference, candidates.getFirst().symbol());
            }
        }

        private void resolveDeferredNode(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                context.poll();
                resolveDeferredNode(child);
            }

            String kindId = node.kind().id();
            if (JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(kindId)) {
                if (context.resolvedSymbol(node) == null) {
                    resolveDeferredMethodInvocation(node);
                }
            } else if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(kindId)) {
                if (context.resolvedSymbol(node) == null) {
                    resolveClassInstanceCreation(node);
                }
            }
        }

        private void resolveNameExpression(SyntaxNode expressionNode) {
            if (isSelectorNameExpression(expressionNode))
                return;

            String name = canonicalQualifiedName(expressionNode);
            if (name == null || name.isBlank())
                return;

            String simpleName = lastSegment(name);
            Scope scope = context.scopeFor(expressionNode);
            List<Symbol> matches = scope.lookupNearest(simpleName);
            Symbol valueMatch = matches.stream()
                .filter(symbol -> isValueSymbol(symbol.kind()))
                .findFirst()
                .orElseGet(() -> scope.lookupAll(simpleName).stream()
                    .filter(symbol -> isValueSymbol(symbol.kind()))
                    .findFirst()
                    .orElse(null));
            if (valueMatch != null) {
                context.resolve(expressionNode, valueMatch);
                return;
            }
            if (matches.isEmpty()) {
                Symbol enclosingType = nearestEnclosingTypeSymbol(expressionNode);
                while (enclosingType != null) {
                    String ownerQualifiedName = enclosingType.qualifiedName().orElse(null);
                    if (ownerQualifiedName != null) {
                        List<MemberCandidate> fieldCandidates = new ArrayList<>();
                        if (!isStaticContext(expressionNode)) {
                            fieldCandidates.addAll(findFieldCandidates(
                                ownerQualifiedName, simpleName, false));
                        }
                        fieldCandidates.addAll(findFieldCandidates(
                            ownerQualifiedName, simpleName, true));
                        MemberCandidate field = chooseFieldCandidate(fieldCandidates);
                        if (field != null) {
                            context.resolve(expressionNode, field.symbol());
                            return;
                        }
                    }
                    SyntaxNode declaration = enclosingType.declaration().orElse(null);
                    enclosingType = declaration == null ? null : context.enclosingTypeSymbol(declaration);
                }
                resolveNameFromImports(expressionNode, simpleName, name);
                return;
            }

            context.resolve(expressionNode, matches.getFirst());
        }

        private boolean isStaticContext(SyntaxNode usageSite) {
            SyntaxNode current = usageSite.parent().orElse(null);
            while (current != null) {
                context.poll();
                String kindId = current.kind().id();
                if (JavaSyntaxKinds.METHOD_DECLARATION.id().equals(kindId)
                    || JavaSyntaxKinds.FIELD_DECLARATION.id().equals(kindId))
                    return hasDirectTokenKind(current, JavaTokenType.STATIC_KEYWORD);
                if (JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id().equals(kindId))
                    return false;
                if (isTypeDeclarationSyntaxKind(kindId))
                    return false;
                current = current.parent().orElse(null);
            }
            return false;
        }

        private boolean isValueSymbol(SymbolKind kind) {
            return kind == JavaSymbolKind.FIELD
                || kind == JavaSymbolKind.PARAMETER
                || kind == JavaSymbolKind.LOCAL_VARIABLE;
        }

        private void resolveFieldAccess(SyntaxNode expressionNode) {
            SyntaxNode memberNode = selectorNameNode(expressionNode);
            SyntaxNode targetNode = explicitReceiver(expressionNode);
            if (memberNode == null || targetNode == null)
                return;

            String typeLikeName = canonicalQualifiedName(expressionNode);
            String qualifiedTypeName = resolvableQualifiedTypeName(typeLikeName);
            if (qualifiedTypeName != null) {
                Symbol symbol = typeSymbolForQualifiedName(
                    simpleTypeName(qualifiedTypeName),
                    qualifiedTypeName,
                    memberNode);
                context.resolve(expressionNode, symbol);
                context.resolve(memberNode, symbol);
                return;
            }

            String fieldName = lastIdentifierLikeTokenText(memberNode);
            if (fieldName == null || fieldName.isBlank())
                return;

            MemberLookup lookup = resolveMemberLookup(targetNode, expressionNode);
            if (lookup.ownerQualifiedName() == null || lookup.ownerQualifiedName().isBlank())
                return;

            if (lookup.staticAccess()) {
                String nestedType = resolvableQualifiedTypeName(lookup.ownerQualifiedName() + "$" + fieldName);
                if (nestedType != null) {
                    Symbol symbol = typeSymbolForQualifiedName(fieldName, nestedType, memberNode);
                    context.resolve(expressionNode, symbol);
                    context.resolve(memberNode, symbol);
                    return;
                }
            }

            List<MemberCandidate> fieldCandidates = findFieldCandidates(
                lookup.ownerQualifiedName(), fieldName, lookup.staticAccess());
            MemberCandidate chosen = chooseFieldCandidate(fieldCandidates);
            if (chosen == null)
                return;

            context.resolve(expressionNode, chosen.symbol());
            context.resolve(memberNode, chosen.symbol());
        }

        private void resolveMethodInvocation(SyntaxNode invocationNode) {
            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList == null)
                return;
            if (resolveExplicitConstructorInvocation(invocationNode, argumentList))
                return;

            SyntaxNode memberNode = selectorNameNode(invocationNode);
            String methodName = memberNode == null
                ? identifierBeforeChildKind(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id())
                : lastIdentifierLikeTokenText(memberNode);
            if (methodName == null || methodName.isBlank())
                return;
            List<Type> argumentTypes = inferArgumentTypes(argumentList);
            SyntaxNode targetNode = explicitReceiver(invocationNode);
            if (targetNode != null) {
                MemberLookup lookup = resolveMemberLookup(targetNode, invocationNode);
                if (lookup.ownerQualifiedName() == null || lookup.ownerQualifiedName().isBlank())
                    return;

                MemberCandidate chosen = resolveCallableOnOwner(
                    lookup.ownerQualifiedName(),
                    methodName,
                    lookup.staticAccess(),
                    argumentTypes,
                    CallableKind.METHOD,
                    false);
                if (chosen == null) {
                    Type targetType = inferExpressionTypeForResolution(targetNode);
                    if (targetType.kind() == JavaTypes.Kind.TYPE_VARIABLE) {
                        for (String bound : qualifiedTypeVariableBounds(targetType.displayName(), invocationNode)) {
                            context.poll();
                            chosen = resolveCallableOnOwner(
                                bound, methodName, false, argumentTypes, CallableKind.METHOD, false);
                            if (chosen != null)
                                break;
                        }
                    }
                }
                if (chosen == null) {
                    for (String bound : qualifiedResolvedReceiverTypeVariableBounds(targetNode, invocationNode)) {
                        context.poll();
                        chosen = resolveCallableOnOwner(
                            bound, methodName, false, argumentTypes, CallableKind.METHOD, false);
                        if (chosen != null)
                            break;
                    }
                }
                if (chosen == null && hasComplexArgumentShape(argumentList)) {
                    chosen = uniqueArityCandidate(
                        collectCallableCandidates(
                            lookup.ownerQualifiedName(),
                            methodName,
                            lookup.staticAccess(),
                            CallableKind.METHOD),
                        argumentTypes.size());
                }
                if (chosen == null)
                    return;

                context.resolve(invocationNode, chosen.symbol());
                if (memberNode != null) {
                    context.resolve(memberNode, chosen.symbol());
                }
                Type inferredType = inferMethodInvocationTypeForResolution(invocationNode);
                if (inferredType.kind() != JavaTypes.Kind.UNKNOWN) {
                    context.type(invocationNode, inferredType);
                }
                return;
            }

            Scope scope = context.scopeFor(invocationNode);
            MemberCandidate localChosen = selectBestCallable(localMethodCandidates(scope.lookupAll(methodName)),
                argumentTypes);
            if (localChosen != null) {
                context.resolve(invocationNode, localChosen.symbol());
                if (memberNode != null) {
                    context.resolve(memberNode, localChosen.symbol());
                }
                return;
            }

            Symbol enclosingType = nearestEnclosingTypeSymbol(invocationNode);
            while (enclosingType != null) {
                String ownerQualifiedName = enclosingType.qualifiedName().orElse(null);
                if (ownerQualifiedName != null) {
                    MemberCandidate ownerChosen = selectBestCallable(
                        findMethodCandidates(ownerQualifiedName, methodName, false),
                        argumentTypes);
                    if (ownerChosen != null) {
                        context.resolve(invocationNode, ownerChosen.symbol());
                        if (memberNode != null) {
                            context.resolve(memberNode, ownerChosen.symbol());
                        }
                        return;
                    }
                    MemberCandidate inheritedStaticChosen = selectBestCallable(
                        findMethodCandidates(ownerQualifiedName, methodName, true), argumentTypes);
                    if (inheritedStaticChosen != null) {
                        context.resolve(invocationNode, inheritedStaticChosen.symbol());
                        if (memberNode != null) {
                            context.resolve(memberNode, inheritedStaticChosen.symbol());
                        }
                        return;
                    }
                }
                SyntaxNode declaration = enclosingType.declaration().orElse(null);
                enclosingType = declaration == null ? null : context.enclosingTypeSymbol(declaration);
            }

            MemberCandidate importedChosen = selectBestCallable(
                staticImportedMethodCandidates(methodName, argumentTypes),
                argumentTypes,
                directlyContextualFunctionalType(invocationNode));
            if (importedChosen != null) {
                context.resolve(invocationNode, importedChosen.symbol());
                if (memberNode != null) {
                    context.resolve(memberNode, importedChosen.symbol());
                }
            }
        }

        private boolean resolveExplicitConstructorInvocation(SyntaxNode invocationNode, SyntaxNode argumentList) {
            if (explicitReceiver(invocationNode) != null)
                return false;
            SyntaxNode constructorTarget = invocationNode.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .findFirst()
                .orElse(null);
            if (constructorTarget == null
                || !JavaSyntaxKinds.THIS_EXPRESSION.id().equals(constructorTarget.kind().id())
                    && !JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(constructorTarget.kind().id()))
                return false;

            Symbol enclosingType = nearestEnclosingTypeSymbol(invocationNode);
            String enclosingOwner = enclosingType == null ? null : enclosingType.qualifiedName().orElse(null);
            if (enclosingOwner == null || enclosingOwner.isBlank())
                return true;

            String constructorOwner = enclosingOwner;
            if (JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(constructorTarget.kind().id())) {
                constructorOwner = directSuperclassName(enclosingType);
            }
            if (constructorOwner == null || constructorOwner.isBlank())
                return true;

            List<Type> argumentTypes = inferArgumentTypes(argumentList);
            List<MemberCandidate> candidates = findConstructorCandidates(constructorOwner);
            MemberCandidate chosen = selectBestCallable(candidates, argumentTypes);
            if (chosen == null && hasComplexArgumentShape(argumentList)) {
                chosen = uniqueArityCandidate(candidates, argumentTypes.size());
            }
            if (chosen != null) {
                context.resolve(invocationNode, chosen.symbol());
            }
            return true;
        }

        private @Nullable String directSuperclassName(Symbol ownerSymbol) {
            SyntaxNode declaration = ownerSymbol.declaration().orElse(null);
            if (declaration != null
                && JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                SyntaxNode extendsClause = directChild(declaration, JavaSyntaxKinds.EXTENDS_CLAUSE.id());
                if (extendsClause != null) {
                    for (SyntaxNode typeRef : extendsClause.children()) {
                        context.poll();
                        if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(typeRef.kind().id()))
                            continue;
                        String qualified = resolveQualifiedTypeName(typeRef, declaration);
                        if (qualified != null && !qualified.isBlank())
                            return eraseTypeArguments(qualified);
                    }
                }
            }

            String ownerQualifiedName = ownerSymbol.qualifiedName().orElse(null);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return null;
            List<JavaTypes.DeclaredType> semanticSupers = resolvedExpressionTypeResolver
                .sourceDirectSuperTypes(ownerQualifiedName);
            if (!semanticSupers.isEmpty()) {
                String qualified = resolveQualifiedTypeNameForCallMatching(
                    semanticSupers.getFirst().displayName());
                if (qualified != null)
                    return qualified;
            }
            return directSuperTypeNames(ownerQualifiedName).stream()
                .map(JavaSemanticAnalyzer::eraseTypeArguments)
                .findFirst()
                .orElse(null);
        }

        private @Nullable Symbol nearestEnclosingTypeSymbol(SyntaxNode usageSite) {
            SyntaxNode current = usageSite.parent().orElse(null);
            while (current != null) {
                context.poll();
                Symbol declared = context.declaredSymbol(current);
                if (declared != null && isTypeSymbol(declared.kind()))
                    return declared;
                current = current.parent().orElse(null);
            }
            return context.enclosingTypeSymbol(usageSite);
        }

        private void resolveDeferredMethodInvocation(SyntaxNode invocationNode) {
            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList == null)
                return;
            if (resolveExplicitConstructorInvocation(invocationNode, argumentList))
                return;

            SyntaxNode memberNode = selectorNameNode(invocationNode);
            String methodName = memberNode == null
                ? identifierBeforeChildKind(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id())
                : lastIdentifierLikeTokenText(memberNode);
            if (methodName == null || methodName.isBlank())
                return;

            List<Type> argumentTypes = inferArgumentTypes(argumentList);
            SyntaxNode targetNode = explicitReceiver(invocationNode);
            if (targetNode != null) {
                MemberLookup lookup = deferredMemberLookup(targetNode, invocationNode);
                if (lookup == null)
                    return;
                if (lookup.ownerQualifiedName() == null || lookup.ownerQualifiedName().isBlank())
                    return;

                MemberCandidate chosen = resolveCallableOnOwner(
                    lookup.ownerQualifiedName(),
                    methodName,
                    lookup.staticAccess(),
                    argumentTypes,
                    CallableKind.METHOD,
                    true);
                if (chosen == null && !lookup.staticAccess()) {
                    chosen = genericReceiverFallbackCandidate(
                        targetNode,
                        lookup.ownerQualifiedName(),
                        methodName,
                        argumentTypes);
                }
                if (chosen == null && hasComplexArgumentShape(argumentList)) {
                    chosen = uniqueArityCandidate(
                        collectCallableCandidates(
                            lookup.ownerQualifiedName(),
                            methodName,
                            lookup.staticAccess(),
                            CallableKind.METHOD),
                        argumentTypes.size());
                }
                if (chosen == null)
                    return;

                context.resolve(invocationNode, chosen.symbol());
                if (memberNode != null) {
                    context.resolve(memberNode, chosen.symbol());
                }
                Type inferredType = inferMethodInvocationTypeForResolution(invocationNode);
                if (inferredType.kind() != JavaTypes.Kind.UNKNOWN) {
                    context.type(invocationNode, inferredType);
                }
                return;
            }

            Symbol enclosingType = nearestEnclosingTypeSymbol(invocationNode);
            while (enclosingType != null) {
                String ownerQualifiedName = enclosingType.qualifiedName().orElse(null);
                MemberCandidate chosen = ownerQualifiedName == null
                    ? null
                    : resolveCallableOnOwner(
                        ownerQualifiedName, methodName, false, argumentTypes, CallableKind.METHOD, true);
                if (chosen == null && ownerQualifiedName != null && hasComplexArgumentShape(argumentList)) {
                    List<MemberCandidate> candidates = collectCallableCandidates(ownerQualifiedName, methodName, false,
                        CallableKind.METHOD);
                    if (candidates.size() == 1) {
                        chosen = candidates.getFirst();
                    }
                }
                if (chosen != null) {
                    context.resolve(invocationNode, chosen.symbol());
                    if (memberNode != null) {
                        context.resolve(memberNode, chosen.symbol());
                    }
                    Type inferredType = inferMethodInvocationTypeForResolution(invocationNode);
                    if (inferredType.kind() != JavaTypes.Kind.UNKNOWN) {
                        context.type(invocationNode, inferredType);
                    }
                    return;
                }
                SyntaxNode declaration = enclosingType.declaration().orElse(null);
                enclosingType = declaration == null ? null : context.enclosingTypeSymbol(declaration);
            }
        }

        private void resolveClassInstanceCreation(SyntaxNode creationNode) {
            SyntaxNode typeRef = directChild(creationNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef == null)
                return;

            String ownerQualifiedName = resolveQualifiedTypeName(typeRef, creationNode);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return;

            SyntaxNode argumentList = directChild(creationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            List<Type> argumentTypes = argumentList == null ? List.of() : inferArgumentTypes(argumentList);

            List<MemberCandidate> constructorCandidates = collectCallableCandidates(
                ownerQualifiedName, "<init>", false, CallableKind.CONSTRUCTOR);
            List<MemberCandidate> accessibleCandidates = constructorCandidates.stream()
                .filter(candidate -> isConstructorCandidateAccessible(
                    ownerQualifiedName, candidate, creationNode))
                .toList();
            boolean allowArityFallback = containsUnknownLikeArgument(argumentTypes)
                || argumentList != null && hasComplexArgumentShape(argumentList);
            MemberCandidate chosen = chooseCallableCandidate(
                accessibleCandidates, argumentTypes, allowArityFallback);
            if (chosen == null) {
                chosen = chooseCallableCandidate(
                    constructorCandidates, argumentTypes, allowArityFallback);
            }
            if (chosen == null && argumentList != null && hasComplexArgumentShape(argumentList)) {
                List<MemberCandidate> arityMatches = new ArrayList<>();
                for (MemberCandidate candidate : constructorCandidates) {
                    context.poll();
                    if (isArityCompatible(candidate, argumentTypes.size())) {
                        arityMatches.add(candidate);
                    }
                }
                if (arityMatches.size() == 1) {
                    chosen = arityMatches.getFirst();
                }
            }
            if (chosen != null) {
                context.resolve(creationNode, chosen.symbol());
            }
        }

        private boolean isConstructorCandidateAccessible(
            String ownerQualifiedName,
            MemberCandidate candidate,
            SyntaxNode usageSite
        ) {
            int modifiers = constructorModifiers(ownerQualifiedName, candidate);
            if (Modifier.isPublic(modifiers))
                return true;

            ClassStub ownerStub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            String ownerPackage = ownerStub == null ? packagePrefix(ownerQualifiedName) : ownerStub.packageName();
            if (Objects.equals(context.currentPackageName, ownerPackage))
                return true;

            if (Modifier.isPrivate(modifiers)) {
                Symbol currentTopLevel = context.topLevelEnclosingTypeSymbol(usageSite);
                String currentName = currentTopLevel == null
                    ? null
                    : currentTopLevel.qualifiedName().orElse(null);
                String ownerTopLevel = ownerQualifiedName.replace('$', '.');
                int nestedSeparator = ownerTopLevel.indexOf('.', ownerPackage.length() + 1);
                if (nestedSeparator > 0) {
                    ownerTopLevel = ownerTopLevel.substring(0, nestedSeparator);
                }
                return Objects.equals(currentName, ownerTopLevel);
            }

            if (!Modifier.isProtected(modifiers))
                return false;
            if (directChild(usageSite, JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id()) != null)
                return true;
            Symbol currentType = nearestEnclosingTypeSymbol(usageSite);
            String currentName = currentType == null ? null : currentType.qualifiedName().orElse(null);
            return currentName != null && directSuperTypeNames(currentName).stream()
                .anyMatch(superName -> sameRawType(superName, ownerQualifiedName));
        }

        private int constructorModifiers(String ownerQualifiedName, MemberCandidate candidate) {
            SyntaxNode declaration = candidate.symbol().declaration().orElse(null);
            if (declaration != null) {
                if (hasDirectTokenKind(declaration, JavaTokenType.PUBLIC_KEYWORD))
                    return Modifier.PUBLIC;
                if (hasDirectTokenKind(declaration, JavaTokenType.PROTECTED_KEYWORD))
                    return Modifier.PROTECTED;
                if (hasDirectTokenKind(declaration, JavaTokenType.PRIVATE_KEYWORD))
                    return Modifier.PRIVATE;
                return 0;
            }

            ClassStub ownerStub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            if (ownerStub == null)
                return Modifier.PUBLIC;
            String candidateSignature = signatureSuffix(candidate.parameterTypes());
            return ownerStub.constructors().stream()
                .filter(constructor -> candidateSignature.equals(signatureSuffix(
                    constructor.parameters().stream()
                        .map(parameter -> toSemanticType(parameter.type()))
                        .toList())))
                .findFirst()
                .map(ConstructorStub::modifiers)
                .orElse(Modifier.PUBLIC);
        }

        private @Nullable MemberCandidate resolveCallableOnOwner(
            String ownerQualifiedName,
            String callableName,
            boolean staticAccess,
            List<Type> argumentTypes,
            CallableKind kind,
            boolean lenient
        ) {
            List<MemberCandidate> candidates = collectCallableCandidates(ownerQualifiedName, callableName, staticAccess,
                kind);
            boolean allowArityFallback = staticAccess
                || ((lenient || kind == CallableKind.CONSTRUCTOR) && containsUnknownLikeArgument(argumentTypes));
            return chooseCallableCandidate(candidates, argumentTypes, allowArityFallback);
        }

        private @Nullable MemberCandidate uniqueArityCandidate(List<MemberCandidate> candidates, int argumentCount) {
            List<MemberCandidate> matches = new ArrayList<>();
            for (MemberCandidate candidate : candidates) {
                context.poll();
                if (isArityCompatible(candidate, argumentCount)) {
                    matches.add(candidate);
                }
            }
            return matches.size() == 1 ? matches.getFirst() : null;
        }

        private @Nullable MemberCandidate genericReceiverFallbackCandidate(
            SyntaxNode targetNode,
            String ownerQualifiedName,
            String methodName,
            List<Type> argumentTypes
        ) {
            String fallbackOwnerQualifiedName = genericReceiverOwnerQualifiedName(targetNode);
            if (fallbackOwnerQualifiedName == null || fallbackOwnerQualifiedName.isBlank())
                return null;

            List<MemberCandidate> allCandidates = collectCallableCandidates(fallbackOwnerQualifiedName, methodName,
                false, CallableKind.METHOD);
            List<MemberCandidate> matches = new ArrayList<>();
            for (MemberCandidate candidate : allCandidates) {
                context.poll();
                if (isArityCompatible(candidate, argumentTypes.size())) {
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1)
                return matches.getFirst();
            return allCandidates.size() == 1 ? allCandidates.getFirst() : null;
        }

        private @Nullable String genericReceiverOwnerQualifiedName(SyntaxNode targetNode) {
            Type inferred = context.inferredType(targetNode);
            if (inferred != null && inferred.kind() == JavaTypes.Kind.DECLARED && inferred.displayName().contains("<"))
                return resolveQualifiedTypeName(simpleTypeName(inferred.displayName()), targetNode);

            Symbol resolved = originalResolvedSymbol(targetNode);
            if (resolved == null)
                return null;

            SyntaxNode declaration = resolved.declaration().orElse(null);
            if (declaration == null)
                return null;

            if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                SyntaxNode current = declaration.parent().orElse(null);
                while (current != null) {
                    context.poll();
                    SyntaxNode typeRef = directChild(current, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null) {
                        String text = canonicalTypeText(typeRef);
                        return text != null && text.contains("<")
                            ? resolveQualifiedTypeName(simpleTypeName(text), targetNode)
                            : null;
                    }
                    current = current.parent().orElse(null);
                }
            }

            SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            String text = typeRef == null ? null : canonicalTypeText(typeRef);
            return text != null && text.contains("<")
                ? resolveQualifiedTypeName(simpleTypeName(text), targetNode)
                : null;
        }

        private @Nullable Symbol originalResolvedSymbol(SyntaxNode targetNode) {
            if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(targetNode.kind().id())) {
                String simpleName = canonicalQualifiedName(targetNode);
                if (simpleName != null && !simpleName.isBlank()) {
                    List<Symbol> matches = context.scopeFor(targetNode).lookupNearest(simpleName);
                    if (!matches.isEmpty())
                        return matches.getFirst();
                }
            }
            return context.resolvedSymbol(targetNode);
        }

        private @Nullable MemberLookup deferredMemberLookup(SyntaxNode targetNode, SyntaxNode usageSite) {
            Symbol targetSymbol = context.resolvedSymbol(targetNode);
            if (targetSymbol != null && isTypeSymbol(targetSymbol.kind()))
                return new MemberLookup(targetSymbol.qualifiedName().orElse(null), true);

            if (JavaSyntaxKinds.THIS_EXPRESSION.id().equals(targetNode.kind().id())
                || JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(targetNode.kind().id())) {
                String qualifiedOwner = qualifiedEnclosingInstanceOwner(targetNode, usageSite);
                if (qualifiedOwner != null)
                    return new MemberLookup(qualifiedOwner, false);
                Symbol enclosingType = nearestEnclosingTypeSymbol(usageSite);
                String owner = enclosingType == null ? null : enclosingType.qualifiedName().orElse(null);
                if (owner != null && JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(targetNode.kind().id())) {
                    owner = directSuperclassName(enclosingType);
                }
                return new MemberLookup(owner, false);
            }

            String ownerQualifiedName = deferredQualifiedTypeNameOfExpression(targetNode, usageSite);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return null;

            boolean staticAccess = targetSymbol != null && isTypeSymbol(targetSymbol.kind());
            return new MemberLookup(ownerQualifiedName, staticAccess);
        }

        private @Nullable String deferredQualifiedTypeNameOfExpression(
            SyntaxNode expressionNode,
            SyntaxNode usageSite
        ) {
            Symbol resolved = context.resolvedSymbol(expressionNode);
            if (resolved != null) {
                if (isTypeSymbol(resolved.kind()))
                    return resolved.qualifiedName().orElse(null);
                if (resolved.kind() == JavaSymbolKind.CONSTRUCTOR)
                    return ownerQualifiedName(resolved);
            }

            Type inferred = context.inferredType(expressionNode);
            if (inferred != null && inferred.kind() == JavaTypes.Kind.DECLARED)
                return resolveQualifiedTypeName(inferred.displayName(), usageSite);

            return qualifiedTypeNameOfExpression(expressionNode, usageSite);
        }

        private List<MemberCandidate> collectCallableCandidates(
            String ownerQualifiedName,
            String callableName,
            boolean staticAccess,
            CallableKind kind
        ) {
            if (kind == CallableKind.CONSTRUCTOR)
                return findConstructorCandidates(ownerQualifiedName);

            List<MemberCandidate> candidates = findMethodCandidates(ownerQualifiedName, callableName, staticAccess);
            if (candidates.isEmpty() && staticAccess) {
                // Some project and binary symbol sources still lose static metadata.
                candidates = findMethodCandidates(ownerQualifiedName, callableName, false);
            }
            return candidates;
        }

        private void collectImportsFromRootScope() {
            Map<String, List<Symbol>> rootDeclarations = context.rootScope.snapshotDeclarations();
            for (List<Symbol> symbols : rootDeclarations.values()) {
                context.poll();
                for (Symbol symbol : symbols) {
                    context.poll();
                    if (symbol.kind() != JavaSymbolKind.IMPORT)
                        continue;

                    SyntaxNode declarationNode = symbol.declaration().orElse(null);
                    if (declarationNode == null)
                        continue;

                    SyntaxNode targetNode = directChild(declarationNode, JavaSyntaxKinds.IMPORT_TARGET.id());
                    if (targetNode == null)
                        continue;

                    String qualifiedTarget = canonicalQualifiedName(targetNode);
                    if (qualifiedTarget == null || qualifiedTarget.isBlank())
                        continue;

                    boolean isStatic = hasTokenKind(declarationNode, JavaTokenType.STATIC_KEYWORD);
                    boolean isWildcard = qualifiedTarget.endsWith(".*");
                    String ownerName = isWildcard
                        ? qualifiedTarget.substring(0, qualifiedTarget.length() - 2)
                        : packagePrefix(qualifiedTarget);
                    String importedName = isWildcard ? "*" : lastSegment(qualifiedTarget);

                    imports.add(new ImportSpec(
                        declarationNode,
                        targetNode,
                        qualifiedTarget,
                        ownerName,
                        importedName,
                        isStatic,
                        isWildcard));
                }
            }
        }

        private void classifyImports() {
            for (ImportSpec importSpec : imports) {
                context.poll();
                if (importSpec.isWildcard()) {
                    if (importSpec.isStatic()) {
                        onDemandStaticImports.add(importSpec);
                    } else {
                        onDemandTypeImports.add(importSpec);
                    }
                    continue;
                }

                if (importSpec.isStatic()) {
                    staticSingleImportsByMemberName
                        .computeIfAbsent(importSpec.importedName(), _ -> new ArrayList<>())
                        .add(importSpec);
                } else {
                    singleTypeImportsBySimpleName.putIfAbsent(importSpec.importedName(), importSpec);
                }
            }
        }

        private void indexLocalStaticMembers() {
            for (Symbol symbol : context.allDeclaredSymbols()) {
                context.poll();
                if (symbol.kind() != JavaSymbolKind.FIELD && symbol.kind() != JavaSymbolKind.METHOD)
                    continue;

                String qualifiedName = symbol.qualifiedName().orElse(null);
                if (qualifiedName == null || qualifiedName.isBlank())
                    continue;

                int separator = qualifiedName.indexOf('#');
                if (separator <= 0 || separator >= qualifiedName.length() - 1)
                    continue;

                if (!isStaticMemberSymbol(symbol))
                    continue;

                String ownerName = qualifiedName.substring(0, separator);
                String memberName = qualifiedName.substring(separator + 1);
                if (symbol.kind() == JavaSymbolKind.FIELD) {
                    localStaticFieldsByOwner
                        .computeIfAbsent(ownerName, _ -> new HashSet<>())
                        .add(memberName);
                } else {
                    int arity = methodDeclarationArity(symbol);
                    localStaticMethodAritiesByOwner
                        .computeIfAbsent(ownerName, _ -> new LinkedHashMap<>())
                        .computeIfAbsent(memberName, _ -> new HashSet<>())
                        .add(arity);
                }
            }
        }

        private void resolveNameFromImports(SyntaxNode referenceNode, String simpleName, String fullName) {
            List<Symbol> candidates = new ArrayList<>();

            if (singleTypeImportsBySimpleName.containsKey(simpleName)) {
                ImportSpec importSpec = singleTypeImportsBySimpleName.get(simpleName);
                if (isResolvableType(importSpec.qualifiedTarget())) {
                    candidates.add(
                        typeSymbolForQualifiedName(simpleName, importSpec.qualifiedTarget(), importSpec.targetNode()));
                }
            }

            if (context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String packageType = context.currentPackageName + "." + simpleName;
                if (isResolvableType(packageType)) {
                    candidates.add(typeSymbolForQualifiedName(simpleName, packageType, referenceNode));
                }
            }

            String javaLangType = "java.lang." + simpleName;
            if (isResolvableType(javaLangType)) {
                candidates.add(typeSymbolForQualifiedName(simpleName, javaLangType, referenceNode));
            }

            String inheritedMemberType = resolveInheritedMemberType(simpleName, referenceNode);
            if (inheritedMemberType != null) {
                candidates.add(typeSymbolForQualifiedName(simpleName, inheritedMemberType, referenceNode));
            }

            for (ImportSpec onDemandImport : onDemandTypeImports) {
                context.poll();
                String qualified = onDemandImport.ownerName() + "." + simpleName;
                if (isResolvableType(qualified)) {
                    candidates.add(typeSymbolForQualifiedName(simpleName, qualified, onDemandImport.targetNode()));
                }
            }

            List<Symbol> importedStaticFields = resolveStaticImportedFields(simpleName, referenceNode);
            if (!importedStaticFields.isEmpty()) {
                candidates.addAll(importedStaticFields);
            }

            if (isMethodNameReference(referenceNode)) {
                List<Symbol> importedStaticMethods = resolveStaticImportedMethods(simpleName, referenceNode, -1);
                if (!importedStaticMethods.isEmpty()) {
                    candidates.addAll(importedStaticMethods);
                }
            }

            List<Symbol> uniqueCandidates = uniqueByQualifiedName(candidates);
            if (uniqueCandidates.isEmpty())
                return;

            Symbol resolved = selectWithPrecedence(simpleName, uniqueCandidates);
            if (resolved != null) {
                context.resolve(referenceNode, resolved);
            }
        }

        private List<Symbol> resolveStaticImportedFields(String fieldName, SyntaxNode referenceNode) {
            List<Symbol> resolved = new ArrayList<>();

            List<ImportSpec> singleStaticImports = staticSingleImportsByMemberName.get(fieldName);
            if (singleStaticImports != null) {
                for (ImportSpec importSpec : singleStaticImports) {
                    context.poll();
                    findFieldCandidates(importSpec.ownerName(), fieldName, true).stream()
                        .map(MemberCandidate::symbol)
                        .forEach(resolved::add);
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                context.poll();
                findFieldCandidates(onDemandImport.ownerName(), fieldName, true).stream()
                    .map(MemberCandidate::symbol)
                    .forEach(resolved::add);
            }

            return uniqueByQualifiedName(resolved);
        }

        private List<Symbol> resolveStaticImportedMethods(
            String methodName,
            SyntaxNode invocationNode,
            int argumentCountOrUnknown
        ) {
            List<Symbol> resolved = new ArrayList<>();

            List<ImportSpec> singleStaticImports = staticSingleImportsByMemberName.get(methodName);
            if (singleStaticImports != null) {
                for (ImportSpec importSpec : singleStaticImports) {
                    context.poll();
                    if (hasResolvableStaticMethod(importSpec.ownerName(), methodName, argumentCountOrUnknown)) {
                        resolved.add(new SimpleSymbol(
                            JavaSymbolKind.METHOD,
                            methodName,
                            importSpec.ownerName() + "#" + methodName,
                            importSpec.targetNode()));
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                context.poll();
                if (hasResolvableStaticMethod(onDemandImport.ownerName(), methodName, argumentCountOrUnknown)) {
                    resolved.add(new SimpleSymbol(
                        JavaSymbolKind.METHOD,
                        methodName,
                        onDemandImport.ownerName() + "#" + methodName,
                        invocationNode));
                }
            }

            return uniqueByQualifiedName(resolved);
        }

        private void indexLocalMembers() {
            for (Symbol symbol : context.allDeclaredSymbols()) {
                context.poll();
                String ownerQualifiedName = ownerQualifiedName(symbol);
                if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                    continue;

                if (symbol.kind() == JavaSymbolKind.FIELD) {
                    MemberCandidate candidate = localFieldCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localFieldsByOwner
                            .computeIfAbsent(ownerQualifiedName, _ -> new LinkedHashMap<>())
                            .computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>())
                            .add(candidate);
                    }
                } else if (symbol.kind() == JavaSymbolKind.METHOD) {
                    MemberCandidate candidate = localMethodCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localMethodsByOwner
                            .computeIfAbsent(ownerQualifiedName, _ -> new LinkedHashMap<>())
                            .computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>())
                            .add(candidate);
                    }
                } else if (symbol.kind() == JavaSymbolKind.CONSTRUCTOR) {
                    MemberCandidate candidate = localConstructorCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localConstructorsByOwner
                            .computeIfAbsent(ownerQualifiedName, _ -> new ArrayList<>())
                            .add(candidate);
                        localTypesWithExplicitConstructors.add(ownerQualifiedName);
                    }
                }
            }
        }

        private @Nullable MemberCandidate localFieldCandidate(Symbol symbol, String ownerQualifiedName) {
            return new MemberCandidate(symbol, ownerQualifiedName, isStaticMemberSymbol(symbol),
                typeOfFieldDeclaration(symbol), List.of());
        }

        private @Nullable MemberCandidate localMethodCandidate(Symbol symbol, String ownerQualifiedName) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return null;
            return new MemberCandidate(symbol, ownerQualifiedName, isStaticMemberSymbol(symbol),
                typeOfMethodDeclaration(symbol), parameterTypes(declaration), hasVariableArityParameters(declaration));
        }

        private @Nullable MemberCandidate localConstructorCandidate(Symbol symbol, String ownerQualifiedName) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return null;
            return new MemberCandidate(symbol, ownerQualifiedName, false,
                new JavaTypes.DeclaredType(ownerQualifiedName, List.of()), parameterTypes(declaration),
                hasVariableArityParameters(declaration));
        }

        private @Nullable String ownerQualifiedName(Symbol symbol) {
            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName == null || qualifiedName.isBlank())
                return null;
            int separator = qualifiedName.indexOf('#');
            if (separator <= 0)
                return null;
            return qualifiedName.substring(0, separator);
        }

        private static boolean hasVariableArityParameters(@Nullable SyntaxNode declaration) {
            if (declaration == null)
                return false;
            SyntaxNode parameters = JavaSourceSymbolIds.parameterList(declaration);
            return parameters != null && hasTokenKind(parameters, JavaTokenType.ELLIPSIS);
        }

        private List<Type> parameterTypes(SyntaxNode declarationNode) {
            SyntaxNode parameterList = JavaSourceSymbolIds.parameterList(declarationNode);
            if (parameterList == null)
                return List.of();

            List<Type> types = new ArrayList<>();
            for (SyntaxNode child : parameterList.children()) {
                context.poll();
                if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id())
                    && !JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                    continue;
                SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                Type parameterType = typeRef == null
                    ? new JavaTypes.UnknownType("<unknown>")
                    : typeFromTypeReferenceForResolution(typeRef);
                if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                    parameterType = new JavaTypes.ArrayType(parameterType);
                }
                types.add(parameterType);
            }
            return List.copyOf(types);
        }

        private Type typeOfMethodDeclaration(Symbol methodSymbol) {
            SyntaxNode declaration = methodSymbol.declaration().orElse(null);
            if (declaration == null)
                return new JavaTypes.UnknownType("<unknown>");
            SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null
                ? new JavaTypes.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
        }

        private Type typeOfFieldDeclaration(Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return new JavaTypes.UnknownType("<unknown>");

            if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(declaration.kind().id())) {
                Symbol enclosingEnum = nearestEnclosingTypeSymbol(declaration);
                if (enclosingEnum != null)
                    return new JavaTypes.DeclaredType(
                        enclosingEnum.qualifiedName().orElse(enclosingEnum.simpleName()), List.of());
            }

            if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                var parent = declaration.parent();
                while (parent.isPresent()) {
                    context.poll();
                    SyntaxNode candidate = parent.get();
                    if (hasDirectTokenKind(candidate, JavaTokenType.VAR_KEYWORD)) {
                        List<SyntaxNode> expressions = new ArrayList<>();
                        for (SyntaxNode child : declaration.children()) {
                            context.poll();
                            if (isExpressionNode(child)) {
                                expressions.add(child);
                            }
                        }
                        return expressions.isEmpty()
                            ? new JavaTypes.UnknownType("<unknown>")
                            : inferExpressionTypeForResolution(expressions.getFirst());
                    }
                    SyntaxNode typeRef = directChild(candidate, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null)
                        return typeFromTypeReferenceForResolution(typeRef);
                    parent = candidate.parent();
                }
            }

            if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                || JavaSyntaxKinds.RECORD_COMPONENT.id().equals(declaration.kind().id())
                || JavaSyntaxKinds.PATTERN.id().equals(declaration.kind().id())) {
                SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null) {
                    Type parameterType = typeFromTypeReferenceForResolution(typeRef);
                    if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                        && "var".equals(canonicalTypeText(typeRef))) {
                        Type elementType = enhancedForElementType(declaration);
                        if (elementType.kind() != JavaTypes.Kind.UNKNOWN) {
                            parameterType = elementType;
                        }
                    }
                    return hasTokenKind(declaration, JavaTokenType.ELLIPSIS)
                        ? new JavaTypes.ArrayType(parameterType)
                        : parameterType;
                }
                if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                    && enclosingNode(declaration, JavaSyntaxKinds.LAMBDA_EXPRESSION.id()) != null)
                    return contextualLambdaParameterType(declaration);
            }
            if (JavaSyntaxKinds.LAMBDA_PARAMETER.id().equals(declaration.kind().id()))
                return contextualLambdaParameterType(declaration);

            return new JavaTypes.UnknownType("<unknown>");
        }

        private boolean hasDirectTokenKind(SyntaxNode node, JavaTokenType tokenType) {
            String expectedKind = JavaSyntaxKinds.tokenKind(tokenType).id();
            return node.children().stream()
                .anyMatch(child -> child instanceof SyntaxToken token
                    && expectedKind.equals(token.kind().id()));
        }

        private Type contextualLambdaParameterType(SyntaxNode parameterDeclaration) {
            SyntaxNode lambda = enclosingNode(parameterDeclaration, JavaSyntaxKinds.LAMBDA_EXPRESSION.id());
            if (lambda == null)
                return new JavaTypes.UnknownType("<unknown>");
            int parameterIndex = lambdaParameterNodes(lambda).indexOf(parameterDeclaration);
            return parameterIndex < 0
                ? new JavaTypes.UnknownType("<unknown>")
                : contextualLambdaParameterType(lambda, parameterIndex);
        }

        private Type contextualLambdaParameterType(SyntaxNode lambda, int parameterIndex) {
            SyntaxNode contextualExpression = lambda;
            SyntaxNode castParent = contextualExpression.parent().orElse(null);
            while (castParent != null
                && JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(castParent.kind().id())) {
                context.poll();
                contextualExpression = castParent;
                castParent = contextualExpression.parent().orElse(null);
            }
            SyntaxNode castExpression = castParent != null
                && JavaSyntaxKinds.CAST_EXPRESSION.id().equals(castParent.kind().id())
                    ? castParent
                    : null;
            if (castExpression != null) {
                SyntaxNode typeRef = directChild(castExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return functionalParameterType(typeFromTypeReferenceForResolution(typeRef), parameterIndex);
            }

            Type directTargetType = directlyContextualFunctionalType(lambda);
            if (directTargetType.kind() != JavaTypes.Kind.UNKNOWN)
                return functionalParameterType(directTargetType, parameterIndex);

            SyntaxNode argumentList = lambda.parent().orElse(null);
            while (argumentList != null && !JavaSyntaxKinds.ARGUMENT_LIST.id().equals(argumentList.kind().id())) {
                context.poll();
                argumentList = argumentList.parent().orElse(null);
            }
            if (argumentList == null)
                return new JavaTypes.UnknownType("<unknown>");

            SyntaxNode invocation = argumentList.parent().orElse(null);
            if (invocation == null
                || !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(invocation.kind().id())
                    && !JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(invocation.kind().id()))
                return new JavaTypes.UnknownType("<unknown>");

            Symbol callable = context.resolvedSymbol(invocation);
            if (callable == null) {
                resolveMethodInvocation(invocation);
                callable = context.resolvedSymbol(invocation);
            }
            if (callable == null)
                return new JavaTypes.UnknownType("<unknown>");

            int argumentIndex = expressionChildIndex(argumentList, lambda);
            List<Type> callableParameters = specializedCallableParameterTypes(invocation, argumentList, lambda,
                callable);
            if (argumentIndex < 0 || argumentIndex >= callableParameters.size())
                return new JavaTypes.UnknownType("<unknown>");

            return functionalParameterType(callableParameters.get(argumentIndex), parameterIndex);
        }

        private Type directlyContextualFunctionalType(SyntaxNode lambda) {
            SyntaxNode contextualExpression = lambda;
            SyntaxNode parent = contextualExpression.parent().orElse(null);
            while (parent != null && (JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(parent.kind().id())
                || JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id().equals(parent.kind().id()))) {
                context.poll();
                contextualExpression = parent;
                parent = contextualExpression.parent().orElse(null);
            }
            if (parent == null)
                return new JavaTypes.UnknownType("<unknown>");

            if (JavaSyntaxKinds.LAMBDA_BODY.id().equals(parent.kind().id())) {
                SyntaxNode enclosingLambda = parent.parent().orElse(null);
                if (enclosingLambda != null)
                    return effectiveWildcardBound(
                        functionalReturnType(contextualInvocationTargetType(enclosingLambda)));
            }

            if (JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id().equals(parent.kind().id())) {
                for (SyntaxNode child : parent.children()) {
                    context.poll();
                    if (!isExpressionNode(child))
                        continue;
                    if (child == contextualExpression)
                        break;
                    return inferExpressionTypeForResolution(child);
                }
            }

            if (JavaSyntaxKinds.RETURN_STATEMENT.id().equals(parent.kind().id())) {
                SyntaxNode method = enclosingNode(parent, JavaSyntaxKinds.METHOD_DECLARATION.id());
                SyntaxNode typeRef = method == null
                    ? null
                    : directChild(method, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return typeFromTypeReferenceForResolution(typeRef);
            }

            SyntaxNode targetExpression = contextualExpression;
            SyntaxNode declaration = enclosingNode(targetExpression, JavaSyntaxKinds.VARIABLE_DECLARATOR.id());
            if (declaration != null && declaration.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .anyMatch(child -> child == targetExpression)) {
                SyntaxNode current = declaration.parent().orElse(null);
                while (current != null) {
                    context.poll();
                    SyntaxNode typeRef = directChild(current, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null)
                        return typeFromTypeReferenceForResolution(typeRef);
                    if (JavaSyntaxKinds.BLOCK.id().equals(current.kind().id()))
                        break;
                    current = current.parent().orElse(null);
                }
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type enhancedForElementType(SyntaxNode parameterDeclaration) {
            SyntaxNode enhancedFor = parameterDeclaration.parent().orElse(null);
            if (enhancedFor == null
                || !JavaSyntaxKinds.ENHANCED_FOR_STATEMENT.id().equals(enhancedFor.kind().id()))
                return new JavaTypes.UnknownType("<unknown>");
            SyntaxNode iterableExpression = enhancedFor.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .findFirst()
                .orElse(null);
            if (iterableExpression == null)
                return new JavaTypes.UnknownType("<unknown>");

            Type iterableType = inferExpressionTypeForResolution(iterableExpression);
            if (iterableType instanceof JavaTypes.ArrayType arrayType)
                return arrayType.componentType();
            if (!(iterableType instanceof JavaTypes.DeclaredType declaredType))
                return new JavaTypes.UnknownType("<unknown>");

            JavaTypes.DeclaredType iterableView = resolvedExpressionTypeResolver.declaredViewAs(
                declaredType, "java.lang.Iterable", new HashSet<>());
            if (iterableView == null || iterableView.typeArguments().isEmpty())
                return new JavaTypes.UnknownType("<unknown>");
            return resolvedExpressionTypeResolver.unwrapWildcard(iterableView.typeArguments().getFirst());
        }

        private List<Type> specializedCallableParameterTypes(
            SyntaxNode invocation,
            SyntaxNode argumentList,
            SyntaxNode contextualArgument,
            Symbol callable
        ) {
            List<Type> parameterTypes = callableParameterTypes(callable);
            if (parameterTypes.isEmpty())
                return parameterTypes;

            Map<String, Type> substitutions = new LinkedHashMap<>();
            Type receiverType = resolvedExpressionTypeResolver.invocationReceiverType(invocation, callable);
            resolvedExpressionTypeResolver.bindOwnerTypeArguments(callable, receiverType, substitutions);
            resolvedExpressionTypeResolver.bindExplicitMethodTypeArguments(invocation, callable, substitutions);

            int argumentIndex = 0;
            for (SyntaxNode argument : argumentList.children()) {
                context.poll();
                if (!isExpressionNode(argument))
                    continue;
                if (argument != contextualArgument && argumentIndex < parameterTypes.size()) {
                    Type argumentType = inferExpressionTypeForResolution(argument);
                    bindTypeVariables(
                        parameterTypes.get(argumentIndex),
                        argumentType,
                        substitutions);
                }
                argumentIndex++;
            }

            Type contextualTarget = contextualInvocationTargetType(invocation);
            if (contextualTarget.kind() != JavaTypes.Kind.UNKNOWN) {
                bindTypeVariables(typeOfResolvedSymbol(callable), contextualTarget, substitutions);
            }

            return parameterTypes.stream()
                .map(parameterType -> substituteFunctionalType(parameterType, substitutions))
                .toList();
        }

        private Type contextualInvocationTargetType(SyntaxNode invocation) {
            if (!contextualInferenceInProgress.add(invocation))
                return new JavaTypes.UnknownType("<unknown>");
            try {
                Type directTarget = directlyContextualFunctionalType(invocation);
                if (directTarget.kind() != JavaTypes.Kind.UNKNOWN)
                    return directTarget;

                SyntaxNode outerArgumentList = invocation.parent().orElse(null);
                if (outerArgumentList == null
                    || !JavaSyntaxKinds.ARGUMENT_LIST.id().equals(outerArgumentList.kind().id()))
                    return new JavaTypes.UnknownType("<unknown>");
                SyntaxNode outerInvocation = outerArgumentList.parent().orElse(null);
                if (outerInvocation == null
                    || !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(outerInvocation.kind().id())
                        && !JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(outerInvocation.kind().id()))
                    return new JavaTypes.UnknownType("<unknown>");

                Symbol outerCallable = context.resolvedSymbol(outerInvocation);
                if (outerCallable == null) {
                    if (JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(outerInvocation.kind().id())) {
                        resolveMethodInvocation(outerInvocation);
                    } else {
                        resolveClassInstanceCreation(outerInvocation);
                    }
                    outerCallable = context.resolvedSymbol(outerInvocation);
                }
                if (outerCallable == null)
                    return new JavaTypes.UnknownType("<unknown>");

                int argumentIndex = expressionChildIndex(outerArgumentList, invocation);
                List<Type> outerParameters = specializedCallableParameterTypes(
                    outerInvocation, outerArgumentList, invocation, outerCallable);
                return argumentIndex < 0 || argumentIndex >= outerParameters.size()
                    ? new JavaTypes.UnknownType("<unknown>")
                    : outerParameters.get(argumentIndex);
            } finally {
                contextualInferenceInProgress.remove(invocation);
            }
        }

        private @Nullable SyntaxNode enclosingNode(SyntaxNode node, String kindId) {
            SyntaxNode current = node.parent().orElse(null);
            while (current != null) {
                context.poll();
                if (kindId.equals(current.kind().id()))
                    return current;
                current = current.parent().orElse(null);
            }
            return null;
        }

        private int expressionChildIndex(SyntaxNode parent, SyntaxNode target) {
            int index = 0;
            for (SyntaxNode child : parent.children()) {
                context.poll();
                if (!isExpressionNode(child))
                    continue;
                if (child == target)
                    return index;
                index++;
            }
            return -1;
        }

        private List<SyntaxNode> lambdaParameterNodes(SyntaxNode lambda) {
            SyntaxNode parameters = directChild(lambda, JavaSyntaxKinds.LAMBDA_PARAMETERS.id());
            if (parameters == null)
                return List.of();
            return parameters.children().stream()
                .filter(child -> JavaSyntaxKinds.LAMBDA_PARAMETER.id().equals(child.kind().id())
                    || JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id()))
                .toList();
        }

        private List<Type> callableParameterTypes(Symbol callable) {
            if (callable instanceof SyntheticMemberSymbol synthetic)
                return synthetic.parameterTypes();

            SyntaxNode declaration = callable.declaration().orElse(null);
            return declaration == null ? List.of() : parameterTypes(declaration);
        }

        private Type functionalParameterType(Type functionalType, int parameterIndex) {
            if (!(functionalType instanceof JavaTypes.DeclaredType declared))
                return new JavaTypes.UnknownType("<unknown>");

            String rawType = eraseTypeArguments(declared.displayName());
            String qualifiedType = resolveQualifiedTypeName(rawType, null);
            if (qualifiedType == null)
                return new JavaTypes.UnknownType("<unknown>");

            return functionalParameterType(declared, qualifiedType, parameterIndex, new HashSet<>());
        }

        private Type functionalParameterType(
            JavaTypes.DeclaredType functionalType,
            String qualifiedType,
            int parameterIndex,
            Set<String> visited
        ) {
            if (!visited.add(qualifiedType))
                return new JavaTypes.UnknownType("<unknown>");
            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedType);
            if (stub == null)
                return sourceFunctionalParameterType(qualifiedType, parameterIndex);

            Map<String, Type> substitutions = new LinkedHashMap<>();
            int typeArgumentCount = Math.min(stub.typeParameters().size(), functionalType.typeArguments().size());
            for (int index = 0; index < typeArgumentCount; index++) {
                context.poll();
                substitutions.put(stub.typeParameters().get(index).name(), functionalType.typeArguments().get(index));
            }

            Map<String, MethodStub> abstractMethods = stub.methods().stream()
                .filter(method -> Modifier.isAbstract(method.modifiers()))
                .filter(method -> !Modifier.isStatic(method.modifiers()))
                .filter(method -> !isObjectMethodSignature(method.name(), method.parameters().size()))
                .collect(Collectors.toMap(
                    method -> method.name() + signatureSuffix(method.parameters().stream()
                        .map(parameter -> toSemanticType(parameter.type())).toList()),
                    method -> method,
                    (left, right) -> left,
                    LinkedHashMap::new));
            if (abstractMethods.size() == 1) {
                MethodStub sam = abstractMethods.values().iterator().next();
                if (parameterIndex >= sam.parameters().size())
                    return new JavaTypes.UnknownType("<unknown>");
                Type parameterType = substituteFunctionalType(
                    toSemanticType(sam.parameters().get(parameterIndex).type()), substitutions);
                return effectiveWildcardBound(parameterType);
            }

            for (dev.railroadide.railroad.ide.classparser.Type parentInterface : stub.interfaces()) {
                context.poll();
                Type specializedParent = substituteFunctionalType(toSemanticType(parentInterface), substitutions);
                if (!(specializedParent instanceof JavaTypes.DeclaredType parentDeclared))
                    continue;
                String parentQualifiedType = resolveFunctionalSuperTypeName(parentDeclared, qualifiedType);
                if (parentQualifiedType == null)
                    continue;
                Type inheritedParameter = functionalParameterType(
                    parentDeclared, parentQualifiedType, parameterIndex, visited);
                if (inheritedParameter.kind() != JavaTypes.Kind.UNKNOWN)
                    return inheritedParameter;
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type sourceFunctionalParameterType(String qualifiedType, int parameterIndex) {
            List<MemberCandidate> localMethods = localMethodsByOwner
                .getOrDefault(qualifiedType, Map.of())
                .values().stream()
                .flatMap(Collection::stream)
                .filter(candidate -> !candidate.staticMember())
                .filter(candidate -> !isObjectMethodSignature(
                    candidate.symbol().simpleName(), candidate.parameterTypes().size()))
                .toList();
            if (localMethods.size() == 1) {
                List<Type> parameterTypes = localMethods.getFirst().parameterTypes();
                return parameterIndex < parameterTypes.size()
                    ? effectiveWildcardBound(parameterTypes.get(parameterIndex))
                    : new JavaTypes.UnknownType("<unknown>");
            }

            List<JavaRuleContext.MethodDescriptor> projectMethods = projectSourceMethodDescriptors(qualifiedType)
                .stream()
                .filter(method -> method.isAbstract()
                    && !Modifier.isStatic(method.modifiers()))
                .filter(method -> !isObjectMethodSignature(method.name(), method.parameterTypes().size()))
                .toList();
            if (projectMethods.size() != 1)
                return new JavaTypes.UnknownType("<unknown>");
            List<Type> parameterTypes = projectMethods.getFirst().parameterTypes();
            return parameterIndex < parameterTypes.size()
                ? effectiveWildcardBound(parameterTypes.get(parameterIndex))
                : new JavaTypes.UnknownType("<unknown>");
        }

        private @Nullable String resolveFunctionalSuperTypeName(
            JavaTypes.DeclaredType parentType,
            String childQualifiedType
        ) {
            String rawName = eraseTypeArguments(parentType.displayName());
            String resolved = resolveQualifiedTypeName(rawName, null);
            if (resolved != null && binaryClassStubsByQualifiedName.containsKey(resolved))
                return resolved;
            int packageEnd = childQualifiedType.lastIndexOf('.');
            if (packageEnd > 0) {
                String samePackage = childQualifiedType.substring(0, packageEnd + 1) + simpleTypeName(rawName);
                if (binaryClassStubsByQualifiedName.containsKey(samePackage))
                    return samePackage;
            }
            return resolved;
        }

        private Type effectiveWildcardBound(Type type) {
            Type current = type;
            while (current instanceof JavaTypes.WildcardType wildcard) {
                context.poll();
                if (wildcard.lowerBound() != null) {
                    current = wildcard.lowerBound();
                } else if (wildcard.upperBound() != null) {
                    current = wildcard.upperBound();
                } else
                    return new JavaTypes.UnknownType("<unknown>");
            }
            return current;
        }

        private Type substituteFunctionalType(Type type, Map<String, Type> substitutions) {
            return switch (type) {
                case JavaTypes.TypeVariableType variable ->
                    substitutions.getOrDefault(variable.displayName(), variable);
                case JavaTypes.ArrayType array ->
                    new JavaTypes.ArrayType(substituteFunctionalType(array.componentType(), substitutions));
                case JavaTypes.WildcardType wildcard -> new JavaTypes.WildcardType(
                    wildcard.upperBound() == null
                        ? null
                        : substituteFunctionalType(wildcard.upperBound(), substitutions),
                    wildcard.lowerBound() == null
                        ? null
                        : substituteFunctionalType(wildcard.lowerBound(), substitutions));
                case JavaTypes.DeclaredType declared -> new JavaTypes.DeclaredType(
                    declared.displayName(),
                    declared.typeArguments().stream()
                        .map(argument -> substituteFunctionalType(argument, substitutions))
                        .toList());
                default -> type;
            };
        }

        private List<Type> inferArgumentTypes(SyntaxNode argumentList) {
            List<Type> types = new ArrayList<>();
            for (SyntaxNode child : argumentList.children()) {
                context.poll();
                if (!isExpressionNode(child))
                    continue;
                if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(child.kind().id())
                    || JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(child.kind().id())) {
                    String marker = JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(child.kind().id())
                        ? POLY_FUNCTIONAL_ARGUMENT + ":" + lambdaParameterNodes(child).size() + ">"
                        : POLY_FUNCTIONAL_ARGUMENT + ">";
                    types.add(new JavaTypes.UnknownType(marker));
                } else {
                    types.add(inferExpressionTypeForResolution(child));
                }
            }
            return List.copyOf(types);
        }

        private Type inferExpressionTypeForResolution(SyntaxNode node) {
            return switch (node.kind().id()) {
                case "JAVA_LITERAL_EXPRESSION" -> inferLiteralTypeForResolution(node);
                case "JAVA_NAME_EXPRESSION", "JAVA_FIELD_ACCESS_EXPRESSION" -> inferredTypeForResolvedSymbol(node);
                case "JAVA_METHOD_INVOCATION_EXPRESSION" -> inferMethodInvocationTypeForResolution(node);
                case "JAVA_CLASS_INSTANCE_CREATION_EXPRESSION" -> createdTypeForResolution(node);
                case "JAVA_ARRAY_CREATION_EXPRESSION" -> inferArrayCreationTypeForResolution(node);
                case "JAVA_CLASS_LITERAL_EXPRESSION" -> inferClassLiteralTypeForResolution(node);
                case "JAVA_ARRAY_ACCESS_EXPRESSION" -> inferArrayAccessTypeForResolution(node);
                case "JAVA_ASSIGNMENT_EXPRESSION" -> inferAssignmentTypeForResolution(node);
                case "JAVA_BINARY_EXPRESSION" -> inferBinaryTypeForResolution(node);
                case "JAVA_CAST_EXPRESSION" -> inferCastTypeForResolution(node);
                case "JAVA_CONDITIONAL_EXPRESSION" -> inferConditionalTypeForResolution(node);
                case "JAVA_SWITCH_EXPRESSION" -> inferSwitchExpressionTypeForResolution(node);
                case "JAVA_PARENTHESIZED_EXPRESSION" -> firstExpressionChildType(node);
                default -> firstExpressionChildType(node);
            };
        }

        private Type inferLiteralTypeForResolution(SyntaxNode literalExpression) {
            for (SyntaxToken token : leafTokens(literalExpression)) {
                context.poll();
                String kindId = token.kind().id();
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.BOOLEAN_LITERAL).id().equals(kindId))
                    return new JavaTypes.PrimitiveType("boolean");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_FLOATING_POINT_LITERAL).id().equals(kindId))
                    return numericLiteralType(token.text(), true);
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.CHARACTER_LITERAL).id().equals(kindId))
                    return new JavaTypes.PrimitiveType("char");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.STRING_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.TEXT_BLOCK_LITERAL).id().equals(kindId))
                    return new JavaTypes.DeclaredType("java.lang.String", List.of());
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_INT_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_HEXADECIMAL_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_BINARY_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_OCTAL_LITERAL).id().equals(kindId))
                    return numericLiteralType(token.text(), false);
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type inferClassLiteralTypeForResolution(SyntaxNode classLiteralExpression) {
            SyntaxNode typeRef = directChild(classLiteralExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type literalType = typeRef == null
                ? new JavaTypes.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
            return new JavaTypes.DeclaredType("java.lang.Class", List.of(literalType));
        }

        private Type inferArrayCreationTypeForResolution(SyntaxNode arrayCreationExpression) {
            SyntaxNode typeRef = directChild(arrayCreationExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type type = typeRef == null
                ? new JavaTypes.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
            for (SyntaxNode child : arrayCreationExpression.children()) {
                context.poll();
                if (child instanceof SyntaxToken token
                    && JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_BRACKET).id().equals(token.kind().id())) {
                    type = new JavaTypes.ArrayType(type);
                }
            }
            return type;
        }

        private Type inferArrayAccessTypeForResolution(SyntaxNode arrayAccessExpression) {
            Type receiverType = firstExpressionChildType(arrayAccessExpression);
            return receiverType instanceof JavaTypes.ArrayType array
                ? array.componentType()
                : new JavaTypes.UnknownType("<unknown>");
        }

        private Type inferredTypeForResolvedSymbol(SyntaxNode node) {
            Type lambdaParameterType = contextualLambdaReferenceType(node);
            if (lambdaParameterType.kind() != JavaTypes.Kind.UNKNOWN)
                return lambdaParameterType;
            Symbol symbol = context.resolvedSymbol(node);
            return symbol == null ? new JavaTypes.UnknownType("<unknown>") : typeOfResolvedSymbol(symbol);
        }

        private Type contextualLambdaReferenceType(SyntaxNode referenceNode) {
            String referenceName = canonicalQualifiedName(referenceNode);
            if (referenceName == null || referenceName.indexOf('.') >= 0)
                return new JavaTypes.UnknownType("<unknown>");

            SyntaxNode lambda = containingLambda(referenceNode);
            if (lambda == null)
                return new JavaTypes.UnknownType("<unknown>");

            List<SyntaxNode> parameters = lambdaParameterNodes(lambda);
            for (int index = 0; index < parameters.size(); index++) {
                context.poll();
                SyntaxNode parameter = parameters.get(index);
                if (!referenceName.equals(lastIdentifierLikeTokenText(parameter)))
                    continue;
                SyntaxNode typeRef = directChild(parameter, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null && !"var".equals(canonicalTypeText(typeRef)))
                    return typeFromTypeReferenceForResolution(typeRef);
                return contextualLambdaParameterType(lambda, index);
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private @Nullable SyntaxNode containingLambda(SyntaxNode node) {
            return deepestContainingLambda(context.syntaxRoot, node.start(), node.end());
        }

        private @Nullable SyntaxNode deepestContainingLambda(SyntaxNode node, int start, int end) {
            if (node.start() > start || node.end() < end)
                return null;

            for (SyntaxNode child : node.children()) {
                context.poll();
                SyntaxNode nested = deepestContainingLambda(child, start, end);
                if (nested != null)
                    return nested;
            }
            return JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(node.kind().id()) ? node : null;
        }

        private Type inferMethodInvocationTypeForResolution(SyntaxNode invocationNode) {
            Symbol symbol = context.resolvedSymbol(invocationNode);
            if (symbol == null)
                return new JavaTypes.UnknownType("<unknown>");

            Type rawType = typeOfResolvedSymbol(symbol);
            if (symbol.kind() != JavaSymbolKind.METHOD)
                return rawType;

            SyntaxNode receiverNode = explicitReceiver(invocationNode);
            Type receiverType = receiverNode == null
                ? resolvedExpressionTypeResolver.invocationReceiverType(invocationNode, symbol)
                : contextualLambdaReferenceType(receiverNode);
            if (receiverNode != null && receiverType.kind() == JavaTypes.Kind.UNKNOWN) {
                Type inferredReceiver = context.inferredType(receiverNode);
                if (inferredReceiver != null) {
                    receiverType = inferredReceiver;
                }
            }
            if (receiverType.kind() == JavaTypes.Kind.UNKNOWN) {
                receiverType = resolvedExpressionTypeResolver.invocationReceiverType(invocationNode, symbol);
            }

            Map<String, Type> substitutions = new LinkedHashMap<>();
            resolvedExpressionTypeResolver.bindOwnerTypeArguments(symbol, receiverType, substitutions);
            resolvedExpressionTypeResolver.bindExplicitMethodTypeArguments(
                invocationNode, symbol, substitutions);

            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList != null) {
                List<Type> parameterTypes = callableParameterTypes(symbol);
                int argumentIndex = 0;
                for (SyntaxNode argument : argumentList.children()) {
                    context.poll();
                    if (!isExpressionNode(argument))
                        continue;
                    if (argumentIndex >= parameterTypes.size())
                        break;
                    Type parameterType = parameterTypes.get(argumentIndex);
                    if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(argument.kind().id())
                        || JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(argument.kind().id())) {
                        bindFunctionalResultType(parameterType, argument, substitutions);
                    } else {
                        bindTypeVariables(
                            parameterType,
                            inferExpressionTypeForResolution(argument),
                            substitutions);
                    }
                    argumentIndex++;
                }
            }

            Type specialized = substituteFunctionalType(rawType, substitutions);
            return specializeReceiverTypeVariable(symbol, specialized, receiverType);
        }

        private @Nullable ClassStub classStub(@Nullable String qualifiedName) {
            if (qualifiedName == null)
                return null;
            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedName);
            if (stub == null && projectIndex != null) {
                stub = projectIndex.classStubsByQualifiedName().get(qualifiedName);
            }
            return stub;
        }

        private void bindTypeVariables(Type parameterType, Type argumentType, Map<String, Type> substitutions) {
            resolvedExpressionTypeResolver.bindTypeVariables(parameterType, argumentType, substitutions);
        }

        private void bindFunctionalResultType(
            Type parameterType,
            SyntaxNode argument,
            Map<String, Type> substitutions
        ) {
            Type expectedResult = functionalReturnType(substituteFunctionalType(parameterType, substitutions));
            if (expectedResult.kind() == JavaTypes.Kind.UNKNOWN)
                return;

            Type actualResult;
            if (JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(argument.kind().id())) {
                Type specializedParameter = substituteFunctionalType(parameterType, substitutions);
                TypeResolver.FunctionalSignature signature = resolvedExpressionTypeResolver
                    .functionalSignature(specializedParameter);
                actualResult = signature == null
                    ? new JavaTypes.UnknownType("<unknown>")
                    : resolvedExpressionTypeResolver.methodReferenceResultType(argument, signature);
            } else {
                actualResult = resolvedExpressionTypeResolver.lambdaResultType(argument,
                    this::inferExpressionTypeForResolution);
            }
            if (actualResult.kind() != JavaTypes.Kind.UNKNOWN) {
                bindTypeVariables(expectedResult, actualResult, substitutions);
            }
        }

        private Type functionalReturnType(Type functionalType) {
            if (!(functionalType instanceof JavaTypes.DeclaredType declared))
                return new JavaTypes.UnknownType("<unknown>");
            String qualifiedType = resolveQualifiedTypeNameForCallMatching(declared.displayName());
            if (qualifiedType == null)
                return new JavaTypes.UnknownType("<unknown>");
            return functionalReturnType(declared, qualifiedType, new HashSet<>());
        }

        private Type functionalReturnType(
            JavaTypes.DeclaredType functionalType,
            String qualifiedType,
            Set<String> visited
        ) {
            if (!visited.add(qualifiedType))
                return new JavaTypes.UnknownType("<unknown>");
            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedType);
            if (stub == null)
                return new JavaTypes.UnknownType("<unknown>");

            Map<String, Type> substitutions = new LinkedHashMap<>();
            int count = Math.min(stub.typeParameters().size(), functionalType.typeArguments().size());
            for (int index = 0; index < count; index++) {
                context.poll();
                substitutions.put(stub.typeParameters().get(index).name(), functionalType.typeArguments().get(index));
            }

            Map<String, MethodStub> abstractMethods = stub.methods().stream()
                .filter(method -> Modifier.isAbstract(method.modifiers()))
                .filter(method -> !Modifier.isStatic(method.modifiers()))
                .filter(method -> !isObjectMethodSignature(method.name(), method.parameters().size()))
                .collect(Collectors.toMap(
                    method -> method.name() + signatureSuffix(method.parameters().stream()
                        .map(parameter -> toSemanticType(parameter.type())).toList()),
                    method -> method,
                    (left, right) -> left,
                    LinkedHashMap::new));
            if (abstractMethods.size() == 1) {
                MethodStub sam = abstractMethods.values().iterator().next();
                return effectiveWildcardBound(substituteFunctionalType(
                    toSemanticType(sam.returnType()), substitutions));
            }

            for (dev.railroadide.railroad.ide.classparser.Type parentInterface : stub.interfaces()) {
                context.poll();
                Type specializedParent = substituteFunctionalType(toSemanticType(parentInterface), substitutions);
                if (!(specializedParent instanceof JavaTypes.DeclaredType parentDeclared))
                    continue;
                String parentQualifiedType = resolveFunctionalSuperTypeName(parentDeclared, qualifiedType);
                if (parentQualifiedType == null)
                    continue;
                Type inheritedReturn = functionalReturnType(parentDeclared, parentQualifiedType, visited);
                if (inheritedReturn.kind() != JavaTypes.Kind.UNKNOWN)
                    return inheritedReturn;
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type specializeReceiverTypeVariable(Symbol methodSymbol, Type rawType, Type receiverType) {
            Type declaredReturnType = typeOfResolvedSymbol(methodSymbol);
            String variableName = declaredReturnType instanceof JavaTypes.TypeVariableType variable
                ? variable.displayName()
                : declaredReturnType.kind() == JavaTypes.Kind.DECLARED
                    && isLikelyTypeVariableName(declaredReturnType.displayName())
                        ? declaredReturnType.displayName()
                        : null;
            if (variableName == null || !(receiverType instanceof JavaTypes.DeclaredType receiverDeclared))
                return rawType;
            if (rawType.kind() != JavaTypes.Kind.TYPE_VARIABLE
                && rawType.kind() != JavaTypes.Kind.WILDCARD
                && !(rawType.kind() == JavaTypes.Kind.DECLARED
                    && isLikelyTypeVariableName(rawType.displayName())))
                return rawType;

            String ownerQualifiedName = ownerQualifiedName(methodSymbol);
            if (ownerQualifiedName == null)
                return rawType;

            ClassStub ownerStub = classStub(ownerQualifiedName);
            if (ownerStub != null) {
                for (int index = 0; index < ownerStub.typeParameters().size(); index++) {
                    context.poll();
                    TypeParameter parameter = ownerStub.typeParameters().get(index);
                    if (!parameter.name().equals(variableName))
                        continue;

                    Type declaredUpperBound;
                    if (isSelfBoundTypeParameter(ownerQualifiedName, parameter)) {
                        declaredUpperBound = receiverType;
                    } else if (!parameter.bounds().isEmpty()) {
                        declaredUpperBound = toSemanticType(parameter.bounds().getFirst());
                    } else {
                        declaredUpperBound = new JavaTypes.DeclaredType("java.lang.Object", List.of());
                    }
                    if (index < receiverDeclared.typeArguments().size()) {
                        Type actual = receiverDeclared.typeArguments().get(index);
                        return effectiveReceiverTypeArgument(actual, declaredUpperBound);
                    }
                    return declaredUpperBound;
                }
            }

            SourceTypeParameterInfo sourceParameter = sourceTypeParameterInfo(
                ownerQualifiedName, variableName);
            if (sourceParameter != null) {
                if (sourceParameter.index() < receiverDeclared.typeArguments().size()) {
                    Type actual = receiverDeclared.typeArguments().get(sourceParameter.index());
                    return effectiveReceiverTypeArgument(actual, sourceParameter.bound());
                }
                return sourceParameter.bound();
            }

            if (receiverDeclared.typeArguments().size() == 1) {
                Type actual = receiverDeclared.typeArguments().getFirst();
                if (actual instanceof JavaTypes.WildcardType wildcard) {
                    Type bound = wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound();
                    if (bound != null)
                        return bound;
                } else
                    return actual;
            }
            if (!sameRawType(ownerQualifiedName, receiverType.displayName()))
                return receiverType;
            return rawType;
        }

        private Type effectiveReceiverTypeArgument(Type actual, Type declaredUpperBound) {
            if (!(actual instanceof JavaTypes.WildcardType wildcard))
                return actual;

            Type explicitUpperBound = wildcard.upperBound();
            if (explicitUpperBound != null
                && !"java.lang.Object".equals(explicitUpperBound.displayName())
                && !"Object".equals(explicitUpperBound.displayName()))
                return explicitUpperBound;
            return declaredUpperBound;
        }

        private @Nullable SourceTypeParameterInfo sourceTypeParameterInfo(
            String ownerQualifiedName,
            String variableName
        ) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    continue;
                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration != null) {
                    SourceTypeParameterInfo info = sourceTypeParameterInfo(
                        declaration, variableName, null);
                    if (info != null)
                        return info;
                }
            }

            if (projectIndex == null)
                return null;
            JavaProjectSemanticIndex.SymbolDescriptor owner = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> isProjectSourceSymbol(symbol) && isTypeSymbol(symbol.kind()))
                .findFirst()
                .orElse(null);
            if (owner == null)
                return null;
            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, owner.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(
                    owner.sourceFile(), source, model, projectIndex);
                return findProjectSourceTypeParameterInfo(
                    model.syntaxTree().root(), model, sourceContext,
                    owner.qualifiedName(), variableName);
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return null;
            }
        }

        private @Nullable SourceTypeParameterInfo findProjectSourceTypeParameterInfo(
            SyntaxNode node,
            SemanticModel model,
            JavaRuleContext sourceContext,
            String ownerQualifiedName,
            String variableName
        ) {
            Symbol declared = model.declaredSymbol(node).orElse(null);
            if (declared != null && isTypeSymbol(declared.kind())
                && Objects.equals(declared.qualifiedName().orElse(null), ownerQualifiedName))
                return sourceTypeParameterInfo(node, variableName, sourceContext);
            for (SyntaxNode child : node.children()) {
                context.poll();
                SourceTypeParameterInfo info = findProjectSourceTypeParameterInfo(
                    child, model, sourceContext, ownerQualifiedName, variableName);
                if (info != null)
                    return info;
            }
            return null;
        }

        private @Nullable SourceTypeParameterInfo sourceTypeParameterInfo(
            SyntaxNode declaration,
            String variableName,
            @Nullable JavaRuleContext sourceContext
        ) {
            SyntaxNode typeParameters = directChild(declaration, JavaSyntaxKinds.TYPE_PARAMETERS.id());
            if (typeParameters == null)
                return null;
            int index = 0;
            for (SyntaxNode parameter : typeParameters.children()) {
                context.poll();
                if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(parameter.kind().id()))
                    continue;
                if (variableName.equals(firstIdentifierLikeTokenText(parameter))) {
                    SyntaxNode bound = directChild(parameter, JavaSyntaxKinds.TYPE_BOUND.id());
                    List<Type> boundTypes = new ArrayList<>();
                    if (bound != null) {
                        for (SyntaxNode child : bound.children()) {
                            context.poll();
                            if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(child.kind().id()))
                                continue;
                            boundTypes.add(sourceContext == null
                                ? typeFromTypeReferenceForResolution(child)
                                : projectTypeFromTypeReference(sourceContext, child));
                        }
                    }
                    if (boundTypes.isEmpty()) {
                        boundTypes.add(new JavaTypes.DeclaredType("java.lang.Object", List.of()));
                    }
                    return new SourceTypeParameterInfo(index, List.copyOf(boundTypes));
                }
                index++;
            }
            return null;
        }

        private boolean isSelfBoundTypeParameter(String ownerQualifiedName, TypeParameter parameter) {
            for (dev.railroadide.railroad.ide.classparser.Type bound : parameter.bounds()) {
                context.poll();
                if (bound instanceof dev.railroadide.railroad.ide.classparser.Type.ClassType classType
                    && ownerQualifiedName.equals(classType.name()))
                    return true;
            }
            return false;
        }

        private Type createdTypeForResolution(SyntaxNode creationNode) {
            if (context.resolvedSymbol(creationNode) == null) {
                resolveClassInstanceCreation(creationNode);
            }
            return resolvedExpressionTypeResolver.inferClassInstanceCreationType(creationNode);
        }

        private Type inferAssignmentTypeForResolution(SyntaxNode assignmentExpression) {
            SyntaxNode firstExpression = null;
            for (SyntaxNode child : assignmentExpression.children()) {
                context.poll();
                if (!isExpressionNode(child))
                    continue;
                firstExpression = child;
                break;
            }
            return firstExpression == null
                ? new JavaTypes.UnknownType("<unknown>")
                : inferExpressionTypeForResolution(firstExpression);
        }

        private Type inferBinaryTypeForResolution(SyntaxNode binaryExpression) {
            Type left = new JavaTypes.UnknownType("<unknown>");
            Type right = new JavaTypes.UnknownType("<unknown>");
            String operator = null;
            for (SyntaxNode child : binaryExpression.children()) {
                context.poll();
                if (child instanceof SyntaxToken token) {
                    if (isTriviaToken(token) || isMissingTokenKind(token.kind().id()))
                        continue;
                    operator = token.text();
                } else if (isExpressionNode(child)) {
                    if (left.kind() == JavaTypes.Kind.UNKNOWN) {
                        left = inferExpressionTypeForResolution(child);
                    } else {
                        right = inferExpressionTypeForResolution(child);
                    }
                }
            }

            if ("+".equals(operator) && (isStringLike(left) || isStringLike(right)))
                return new JavaTypes.DeclaredType("java.lang.String", List.of());
            if ("&&".equals(operator) || "||".equals(operator)
                || "==".equals(operator) || "!=".equals(operator)
                || "<".equals(operator) || "<=".equals(operator)
                || ">".equals(operator) || ">=".equals(operator))
                return new JavaTypes.PrimitiveType("boolean");
            if (isNumericType(left) && isNumericType(right))
                return promoteNumeric(left, right);
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type firstExpressionChildType(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                context.poll();
                if (isExpressionNode(child))
                    return inferExpressionTypeForResolution(child);
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type typeOfResolvedSymbol(Symbol symbol) {
            if (symbol instanceof SyntheticMemberSymbol synthetic)
                return synthetic.valueType();

            return switch (symbol.kind()) {
                case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
                    JavaSymbolKind.RECORD ->
                    new JavaTypes.DeclaredType(symbol.qualifiedName().orElse(symbol.simpleName()), List.of());
                case JavaSymbolKind.METHOD -> typeOfMethodDeclaration(symbol);
                case JavaSymbolKind.FIELD, JavaSymbolKind.PARAMETER, JavaSymbolKind.LOCAL_VARIABLE ->
                    typeOfFieldDeclaration(symbol);
                case JavaSymbolKind.CONSTRUCTOR -> new JavaTypes.DeclaredType(ownerQualifiedName(symbol), List.of());
                default -> new JavaTypes.UnknownType("<unknown>");
            };
        }

        private List<MemberCandidate> localMethodCandidates(List<Symbol> symbols) {
            List<MemberCandidate> candidates = new ArrayList<>();
            for (Symbol symbol : symbols) {
                context.poll();
                if (symbol.kind() != JavaSymbolKind.METHOD)
                    continue;
                String ownerQualifiedName = ownerQualifiedName(symbol);
                if (ownerQualifiedName == null)
                    continue;
                MemberCandidate candidate = localMethodCandidate(symbol, ownerQualifiedName);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            return List.copyOf(candidates);
        }

        private List<MemberCandidate> findFieldCandidates(
            String ownerQualifiedName,
            String fieldName,
            boolean staticAccess
        ) {
            List<MemberCandidate> candidates = new ArrayList<>();
            collectFieldCandidates(ownerQualifiedName, fieldName, staticAccess, candidates, new HashSet<>());
            return List.copyOf(candidates);
        }

        private List<MemberCandidate> findMethodCandidates(
            String ownerQualifiedName,
            String methodName,
            boolean staticAccess
        ) {
            List<MemberCandidate> candidates = new ArrayList<>();
            collectMethodCandidates(ownerQualifiedName, methodName, staticAccess, candidates, new HashSet<>());
            return dedupeCallableCandidates(candidates);
        }

        private void collectFieldCandidates(
            String ownerQualifiedName,
            String fieldName,
            boolean staticAccess,
            List<MemberCandidate> out,
            Set<String> visitedOwners
        ) {
            if (!visitedOwners.add(ownerQualifiedName))
                return;

            collectSourceFieldCandidates(ownerQualifiedName, fieldName, staticAccess, out);
            collectProjectFieldCandidates(ownerQualifiedName, fieldName, staticAccess, out);
            collectBinaryFieldCandidates(ownerQualifiedName, fieldName, staticAccess, out);

            for (String directSuper : directSuperTypeNames(ownerQualifiedName)) {
                context.poll();
                collectFieldCandidates(directSuper, fieldName, staticAccess, out, visitedOwners);
            }
        }

        private void collectMethodCandidates(
            String ownerQualifiedName,
            String methodName,
            boolean staticAccess,
            List<MemberCandidate> out,
            Set<String> visitedOwners
        ) {
            if (!visitedOwners.add(ownerQualifiedName))
                return;

            collectSourceMethodCandidates(ownerQualifiedName, methodName, staticAccess, out);
            collectProjectMethodCandidates(ownerQualifiedName, methodName, staticAccess, out);
            collectBinaryMethodCandidates(ownerQualifiedName, methodName, staticAccess, out);
            MemberCandidate implicitEnumMethod = implicitEnumMethodCandidate(
                ownerQualifiedName, methodName, staticAccess);
            if (implicitEnumMethod != null) {
                out.add(implicitEnumMethod);
            }

            for (String directSuper : directSuperTypeNames(ownerQualifiedName)) {
                context.poll();
                collectMethodCandidates(directSuper, methodName, staticAccess, out, visitedOwners);
            }
        }

        private @Nullable MemberCandidate implicitEnumMethodCandidate(
            String ownerQualifiedName,
            String methodName,
            boolean staticAccess
        ) {
            if (!staticAccess || !isSourceEnumType(ownerQualifiedName))
                return null;

            Type enumType = new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
            Type returnType;
            List<Type> parameterTypes;
            if ("values".equals(methodName)) {
                returnType = new JavaTypes.ArrayType(enumType);
                parameterTypes = List.of();
            } else if ("valueOf".equals(methodName)) {
                returnType = enumType;
                parameterTypes = List.of(new JavaTypes.DeclaredType("java.lang.String", List.of()));
            } else
                return null;

            Symbol symbol = new SyntheticMemberSymbol(
                JavaSymbolKind.METHOD,
                methodName,
                ownerQualifiedName + "#" + methodName + signatureSuffix(parameterTypes),
                null,
                returnType,
                parameterTypes,
                true);
            return new MemberCandidate(symbol, ownerQualifiedName, true, returnType, parameterTypes);
        }

        private boolean isSourceEnumType(String ownerQualifiedName) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (symbol.kind() == JavaSymbolKind.ENUM
                    && Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    return true;
            }
            return projectIndex != null && projectIndex.lookupQualifiedName(ownerQualifiedName).stream()
                .anyMatch(symbol -> isProjectSourceSymbol(symbol) && symbol.kind() == JavaSymbolKind.ENUM);
        }

        private List<MemberCandidate> findConstructorCandidates(String ownerQualifiedName) {
            List<MemberCandidate> candidates = new ArrayList<>();
            List<MemberCandidate> local = localConstructorsByOwner.get(ownerQualifiedName);
            if (local != null) {
                candidates.addAll(local);
            }
            boolean hasExplicitLocalConstructors = localTypesWithExplicitConstructors.contains(ownerQualifiedName);
            if (localQualifiedTypeNames.contains(ownerQualifiedName)
                && (!hasExplicitLocalConstructors || isLocalRecordType(ownerQualifiedName))) {
                MemberCandidate implicitLocalConstructor = implicitLocalConstructorCandidate(ownerQualifiedName);
                if (implicitLocalConstructor != null && candidates.stream().noneMatch(
                    candidate -> candidate.parameterTypes().equals(implicitLocalConstructor.parameterTypes()))) {
                    candidates.add(implicitLocalConstructor);
                }
            }
            collectProjectConstructorCandidates(ownerQualifiedName, candidates);

            ClassStub stub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            if (stub != null) {
                for (ConstructorStub constructor : stub.constructors()) {
                    context.poll();
                    List<Type> parameterTypes = constructor.parameters().stream()
                        .map(parameter -> toSemanticType(parameter.type()))
                        .toList();
                    Type constructedType = new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
                    candidates.add(new MemberCandidate(
                        new SyntheticMemberSymbol(
                            JavaBinarySymbolExtractor.constructorId(stub, constructor),
                            JavaSymbolKind.CONSTRUCTOR,
                            "<init>",
                            ownerQualifiedName + "#<init>" + signatureSuffix(parameterTypes),
                            null,
                            constructedType,
                            parameterTypes,
                            false),
                        ownerQualifiedName,
                        false,
                        constructedType,
                        parameterTypes, (constructor.modifiers() & Opcodes.ACC_VARARGS) != 0));
                }
            }

            return List.copyOf(candidates);
        }

        private void collectSourceFieldCandidates(
            String ownerQualifiedName,
            String fieldName,
            boolean staticAccess,
            List<MemberCandidate> out
        ) {
            Map<String, List<MemberCandidate>> fields = localFieldsByOwner.get(ownerQualifiedName);
            if (fields != null) {
                for (MemberCandidate candidate : fields.getOrDefault(fieldName, List.of())) {
                    context.poll();
                    if (candidate.staticMember() == staticAccess) {
                        out.add(candidate);
                    }
                }
            }
            if (!staticAccess) {
                MemberCandidate recordField = localRecordFieldCandidate(ownerQualifiedName, fieldName);
                if (recordField != null) {
                    out.add(recordField);
                }
            }
        }

        private void collectSourceMethodCandidates(
            String ownerQualifiedName,
            String methodName,
            boolean staticAccess,
            List<MemberCandidate> out
        ) {
            Map<String, List<MemberCandidate>> methods = localMethodsByOwner.get(ownerQualifiedName);
            if (methods != null) {
                for (MemberCandidate candidate : methods.getOrDefault(methodName, List.of())) {
                    context.poll();
                    if (candidate.staticMember() == staticAccess) {
                        out.add(candidate);
                    }
                }
            }

            if (!staticAccess) {
                MemberCandidate recordAccessor = localRecordAccessorCandidate(ownerQualifiedName, methodName);
                if (recordAccessor != null) {
                    out.add(recordAccessor);
                }
            }
        }

        private void collectProjectFieldCandidates(
            String ownerQualifiedName,
            String fieldName,
            boolean staticAccess,
            List<MemberCandidate> out
        ) {
            if (projectIndex == null)
                return;

            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMember(ownerQualifiedName,
                fieldName)) {
                context.poll();
                if (!isProjectSourceSymbol(symbol) || symbol.kind() != JavaSymbolKind.FIELD
                    || symbol.isStatic() != staticAccess)
                    continue;

                Type valueType = projectMemberValueType(symbol);
                out.add(new MemberCandidate(
                    syntheticProjectMemberSymbol(symbol, valueType, List.of()),
                    ownerQualifiedName,
                    staticAccess,
                    valueType,
                    List.of()));
            }
            if (!staticAccess) {
                Type recordFieldType = projectRecordAccessorTypes(ownerQualifiedName).get(fieldName);
                if (recordFieldType != null) {
                    Symbol field = new SyntheticMemberSymbol(
                        JavaSymbolKind.FIELD,
                        fieldName,
                        ownerQualifiedName + "#" + fieldName,
                        null,
                        recordFieldType,
                        List.of(),
                        false);
                    out.add(new MemberCandidate(
                        field, ownerQualifiedName, false, recordFieldType, List.of()));
                }
            }
        }

        private void collectProjectMethodCandidates(
            String ownerQualifiedName,
            String methodName,
            boolean staticAccess,
            List<MemberCandidate> out
        ) {
            if (projectIndex == null)
                return;

            List<JavaRuleContext.MethodDescriptor> sourceMethods = projectSourceMethodDescriptors(ownerQualifiedName);
            if (!sourceMethods.isEmpty()) {
                for (JavaRuleContext.MethodDescriptor method : sourceMethods) {
                    context.poll();
                    boolean methodStatic = Modifier.isStatic(method.modifiers());
                    if (!method.name().equals(methodName) || methodStatic != staticAccess)
                        continue;
                    List<Type> parameterTypes = method.parameterTypes();
                    out.add(new MemberCandidate(
                        new SyntheticMemberSymbol(
                            JavaSourceSymbolIds.deriveSymbolId(new SimpleSymbol(JavaSymbolKind.METHOD, method.name(),
                                ownerQualifiedName + "#" + method.name(), method.declaration()), projectIndex)
                                .orElse(null),
                            JavaSymbolKind.METHOD,
                            method.name(),
                            ownerQualifiedName + "#" + method.name() + signatureSuffix(parameterTypes),
                            null,
                            method.returnType(),
                            parameterTypes,
                            methodStatic),
                        ownerQualifiedName,
                        methodStatic,
                        method.returnType(),
                        parameterTypes, hasVariableArityParameters(method.declaration())));
                }
                return;
            }

            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMember(ownerQualifiedName,
                methodName)) {
                context.poll();
                if (!isProjectSourceSymbol(symbol) || symbol.kind() != JavaSymbolKind.METHOD
                    || symbol.isStatic() != staticAccess)
                    continue;

                List<Type> parameterTypes = projectMethodParameterTypes(symbol);
                Type valueType = projectMemberValueType(symbol);
                out.add(new MemberCandidate(
                    syntheticProjectMemberSymbol(symbol, valueType, parameterTypes),
                    ownerQualifiedName,
                    staticAccess,
                    valueType,
                    parameterTypes, projectVariableArityByKey.getOrDefault(projectMemberValueKey(symbol), false)));
            }

            if (!staticAccess) {
                MemberCandidate recordAccessor = projectRecordAccessorCandidate(ownerQualifiedName, methodName);
                if (recordAccessor != null) {
                    out.add(recordAccessor);
                }
            }
        }

        private void collectProjectConstructorCandidates(String ownerQualifiedName, List<MemberCandidate> out) {
            if (projectIndex == null)
                return;

            boolean hasExplicitConstructors = false;
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMembers(ownerQualifiedName)) {
                context.poll();
                if (!isProjectSourceSymbol(symbol) || symbol.kind() != JavaSymbolKind.CONSTRUCTOR)
                    continue;
                hasExplicitConstructors = true;

                List<Type> parameterTypes = projectMethodParameterTypes(symbol);
                Type constructedType = new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
                out.add(new MemberCandidate(
                    syntheticProjectMemberSymbol(symbol, constructedType, parameterTypes),
                    ownerQualifiedName,
                    false,
                    constructedType,
                    parameterTypes, projectVariableArityByKey.getOrDefault(projectMemberValueKey(symbol), false)));
            }

            JavaProjectSemanticIndex.SymbolDescriptor ownerType = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> isProjectSourceSymbol(symbol)
                    && (symbol.kind() == JavaSymbolKind.CLASS || symbol.kind() == JavaSymbolKind.RECORD))
                .findFirst()
                .orElse(null);
            if (ownerType == null)
                return;

            if (hasExplicitConstructors && ownerType.kind() != JavaSymbolKind.RECORD)
                return;

            List<Type> parameterTypes = ownerType.kind() == JavaSymbolKind.RECORD
                ? projectRecordCanonicalConstructorParameterTypes(ownerQualifiedName, ownerType.sourceFile())
                : List.of();
            if (out.stream().anyMatch(candidate -> ownerQualifiedName.equals(candidate.ownerQualifiedName())
                && candidate.parameterTypes().equals(parameterTypes)))
                return;
            Type constructedType = new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
            out.add(new MemberCandidate(
                new SyntheticMemberSymbol(
                    JavaSymbolKind.CONSTRUCTOR,
                    "<init>",
                    ownerQualifiedName + "#<init>" + signatureSuffix(parameterTypes),
                    null,
                    constructedType,
                    parameterTypes,
                    false),
                ownerQualifiedName,
                false,
                constructedType,
                parameterTypes));
        }

        private void collectBinaryFieldCandidates(
            String ownerQualifiedName,
            String fieldName,
            boolean staticAccess,
            List<MemberCandidate> out
        ) {
            ClassStub stub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            if (stub == null)
                return;

            for (FieldStub field : stub.fields()) {
                context.poll();
                if (!field.name().equals(fieldName))
                    continue;
                if (Modifier.isStatic(field.modifiers()) != staticAccess)
                    continue;
                Type valueType = toSemanticType(field.type());
                out.add(new MemberCandidate(
                    new SyntheticMemberSymbol(
                        JavaSymbolKind.FIELD,
                        field.name(),
                        ownerQualifiedName + "#" + field.name(),
                        null,
                        valueType,
                        List.of(),
                        staticAccess),
                    ownerQualifiedName,
                    staticAccess,
                    valueType,
                    List.of()));
            }

        }

        private void collectBinaryMethodCandidates(
            String ownerQualifiedName,
            String methodName,
            boolean staticAccess,
            List<MemberCandidate> out
        ) {
            ClassStub stub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            if (stub == null)
                return;

            for (MethodStub method : stub.methods()) {
                context.poll();
                if (!method.name().equals(methodName))
                    continue;
                if (Modifier.isStatic(method.modifiers()) != staticAccess)
                    continue;
                List<Type> parameterTypes = method.parameters().stream()
                    .map(parameter -> toSemanticType(parameter.type()))
                    .toList();
                Type valueType = toSemanticType(method.returnType());
                out.add(new MemberCandidate(
                    new SyntheticMemberSymbol(
                        JavaBinarySymbolExtractor.methodId(stub, method),
                        JavaSymbolKind.METHOD,
                        method.name(),
                        ownerQualifiedName + "#" + method.name() + signatureSuffix(parameterTypes),
                        null,
                        valueType,
                        parameterTypes,
                        staticAccess),
                    ownerQualifiedName,
                    staticAccess,
                    valueType,
                    parameterTypes, (method.modifiers() & Opcodes.ACC_VARARGS) != 0));
            }

        }

        private @Nullable MemberCandidate chooseFieldCandidate(List<MemberCandidate> candidates) {
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        private @Nullable MemberCandidate selectBestCallable(
            List<MemberCandidate> candidates,
            List<Type> argumentTypes
        ) {
            return selectBestCallable(candidates, argumentTypes, new JavaTypes.UnknownType("<unknown>"));
        }

        private @Nullable MemberCandidate selectBestCallable(
            List<MemberCandidate> candidates,
            List<Type> argumentTypes,
            Type contextualReturnType
        ) {
            for (boolean expanded : List.of(false, true)) {
                context.poll();
                MemberCandidate best = null;
                List<Integer> bestCost = null;
                int bestReturnCost = Integer.MAX_VALUE;

                for (MemberCandidate candidate : candidates) {
                    context.poll();
                    if (expanded && !candidate.variableArity())
                        continue;
                    List<Integer> cost = applicabilityCost(candidate.parameterTypes(), argumentTypes, expanded);
                    if (cost == null)
                        continue;
                    int returnCost = contextualReturnCost(contextualReturnType, candidate.valueType());
                    if (best == null) {
                        best = candidate;
                        bestCost = cost;
                        bestReturnCost = returnCost;
                        continue;
                    }

                    int comparison = compareCost(cost, bestCost);
                    if (comparison < 0 || comparison == 0 && returnCost < bestReturnCost) {
                        best = candidate;
                        bestCost = cost;
                        bestReturnCost = returnCost;
                    }
                }

                if (best != null)
                    return best;
            }
            return null;
        }

        private int contextualReturnCost(Type targetType, Type returnType) {
            if (targetType.kind() == JavaTypes.Kind.UNKNOWN)
                return 0;
            if (returnType.kind() == JavaTypes.Kind.VOID)
                return targetType.kind() == JavaTypes.Kind.VOID ? 0 : 100;
            Integer cost = conversionCost(targetType, returnType);
            return cost == null ? 50 : cost;
        }

        private @Nullable MemberCandidate chooseCallableCandidate(
            List<MemberCandidate> candidates,
            List<Type> argumentTypes,
            boolean allowArityFallback
        ) {
            MemberCandidate chosen = selectBestCallable(candidates, argumentTypes);
            if (chosen == null && allowArityFallback)
                return fallbackCallableCandidate(candidates, argumentTypes.size());
            return chosen;
        }

        private @Nullable MemberCandidate fallbackCallableCandidate(
            List<MemberCandidate> candidates,
            int argumentCount
        ) {
            for (MemberCandidate candidate : candidates) {
                context.poll();
                if (isArityCompatible(candidate, argumentCount))
                    return candidate;
            }
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        private boolean containsUnknownLikeArgument(List<Type> argumentTypes) {
            for (Type argumentType : argumentTypes) {
                context.poll();
                if (argumentType.kind() == JavaTypes.Kind.UNKNOWN
                    || argumentType.kind() == JavaTypes.Kind.TYPE_VARIABLE
                    || argumentType.kind() == JavaTypes.Kind.WILDCARD)
                    return true;
            }
            return false;
        }

        private boolean hasComplexArgumentShape(SyntaxNode argumentList) {
            for (SyntaxNode child : argumentList.children()) {
                context.poll();
                String kindId = child.kind().id();
                if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(kindId)
                    || JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(kindId)
                    || JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(kindId)
                    || JavaSyntaxKinds.SWITCH_EXPRESSION.id().equals(kindId)
                    || JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id().equals(kindId))
                    return true;
            }
            return false;
        }

        private boolean isArityCompatible(MemberCandidate candidate, int argumentCount) {
            List<Type> parameterTypes = candidate.parameterTypes();
            boolean varargs = candidate.variableArity();
            if (!varargs)
                return parameterTypes.size() == argumentCount;
            return argumentCount >= parameterTypes.size() - 1;
        }

        private List<MemberCandidate> dedupeCallableCandidates(List<MemberCandidate> candidates) {
            Map<String, MemberCandidate> deduped = new LinkedHashMap<>();
            for (MemberCandidate candidate : candidates) {
                context.poll();
                String key = candidate.symbol().simpleName() + signatureSuffix(candidate.parameterTypes());
                deduped.putIfAbsent(key, candidate);
            }
            return List.copyOf(deduped.values());
        }

        private @Nullable List<Integer> applicabilityCost(
            List<Type> parameterTypes,
            List<Type> argumentTypes,
            boolean varargs
        ) {
            if (!varargs && parameterTypes.size() != argumentTypes.size())
                return null;
            if (varargs && argumentTypes.size() < parameterTypes.size() - 1)
                return null;

            List<Integer> cost = new ArrayList<>(Math.max(parameterTypes.size(), argumentTypes.size()));
            int fixedCount = varargs ? parameterTypes.size() - 1 : parameterTypes.size();
            for (int index = 0; index < fixedCount; index++) {
                context.poll();
                Integer conversionCost = conversionCost(parameterTypes.get(index), argumentTypes.get(index));
                if (conversionCost == null)
                    return null;
                cost.add(conversionCost);
            }

            if (!varargs)
                return List.copyOf(cost);

            JavaTypes.ArrayType varargsArray = (JavaTypes.ArrayType) parameterTypes.getLast();
            Type componentType = varargsArray.componentType();
            if (argumentTypes.size() == parameterTypes.size()) {
                Integer directArrayCost = conversionCost(varargsArray, argumentTypes.getLast());
                if (directArrayCost != null) {
                    cost.add(directArrayCost);
                    return List.copyOf(cost);
                }
            }

            for (int index = fixedCount; index < argumentTypes.size(); index++) {
                context.poll();
                Integer conversionCost = conversionCost(componentType, argumentTypes.get(index));
                if (conversionCost == null)
                    return null;
                cost.add(conversionCost + 1);
            }
            return List.copyOf(cost);
        }

        private @Nullable Integer conversionCost(Type parameterType, Type argumentType) {
            if (argumentType.kind() == JavaTypes.Kind.UNKNOWN
                && argumentType.displayName().startsWith(POLY_FUNCTIONAL_ARGUMENT)) {
                Integer targetArity = functionalInterfaceArity(parameterType, new HashSet<>());
                if (targetArity == null)
                    return null;
                int separator = argumentType.displayName().indexOf(':');
                if (separator >= 0) {
                    int end = argumentType.displayName().indexOf('>', separator);
                    int lambdaArity = Integer.parseInt(argumentType.displayName().substring(separator + 1, end));
                    if (lambdaArity != targetArity)
                        return null;
                }
                return 0;
            }
            if (argumentType.kind() == JavaTypes.Kind.UNKNOWN || parameterType.kind() == JavaTypes.Kind.UNKNOWN)
                return 0;
            if (parameterType.kind() == JavaTypes.Kind.TYPE_VARIABLE || parameterType.kind() == JavaTypes.Kind.WILDCARD)
                return 20;
            if (argumentType.kind() == JavaTypes.Kind.TYPE_VARIABLE || argumentType.kind() == JavaTypes.Kind.WILDCARD)
                return 0;
            if (parameterType.displayName().equals(argumentType.displayName()))
                return 0;
            if (parameterType.kind() == JavaTypes.Kind.PRIMITIVE && argumentType.kind() == JavaTypes.Kind.DECLARED) {
                String wrapper = boxedQualifiedName(parameterType.displayName());
                if (wrapper != null
                    && wrapper.equals(resolveQualifiedTypeNameForCallMatching(argumentType.displayName())))
                    return 10;
            }
            if (parameterType.kind() == JavaTypes.Kind.DECLARED && argumentType.kind() == JavaTypes.Kind.PRIMITIVE) {
                String wrapper = boxedQualifiedName(argumentType.displayName());
                String parameterQualifiedName = resolveQualifiedTypeNameForCallMatching(parameterType.displayName());
                if (wrapper != null) {
                    if (wrapper.equals(parameterQualifiedName))
                        return 10;
                    if (parameterQualifiedName != null && isSubtype(wrapper, parameterQualifiedName))
                        return 60;
                }
            }
            if (parameterType.kind() == JavaTypes.Kind.ARRAY && argumentType.kind() == JavaTypes.Kind.ARRAY) {
                JavaTypes.ArrayType parameterArray = (JavaTypes.ArrayType) parameterType;
                JavaTypes.ArrayType argumentArray = (JavaTypes.ArrayType) argumentType;
                Integer componentCost = conversionCost(parameterArray.componentType(), argumentArray.componentType());
                return componentCost == null ? null : Math.max(0, componentCost - 1);
            }
            if (isNumericType(parameterType) && isNumericType(argumentType)) {
                int targetRank = numericRank(simpleTypeName(parameterType.displayName()));
                int sourceRank = numericRank(simpleTypeName(argumentType.displayName()));
                if (targetRank < 0 || sourceRank < 0 || targetRank < sourceRank)
                    return null;
                return targetRank - sourceRank;
            }
            if (parameterType.kind() == JavaTypes.Kind.DECLARED && argumentType.kind() == JavaTypes.Kind.ARRAY) {
                String parameterQualifiedName = resolveQualifiedTypeNameForCallMatching(parameterType.displayName());
                return "java.lang.Object".equals(parameterQualifiedName) ? 100 : null;
            }
            if (parameterType.kind() == JavaTypes.Kind.DECLARED && argumentType.kind() == JavaTypes.Kind.DECLARED) {
                String parameterQualifiedName = resolveQualifiedTypeNameForCallMatching(parameterType.displayName());
                String argumentQualifiedName = resolveQualifiedTypeNameForCallMatching(argumentType.displayName());
                if (sameQualifiedTypeName(parameterQualifiedName, argumentQualifiedName))
                    return 0;
                if (parameterQualifiedName != null
                    && argumentQualifiedName != null
                    && isSubtype(argumentQualifiedName, parameterQualifiedName))
                    return 50;
                String parameterName = simpleTypeName(parameterType.displayName());
                if ("java.lang.Object".equals(parameterQualifiedName) || "Object".equals(parameterName))
                    return 100;
            }
            return null;
        }

        private @Nullable String boxedQualifiedName(String primitiveName) {
            return switch (primitiveName) {
                case "boolean" -> "java.lang.Boolean";
                case "byte" -> "java.lang.Byte";
                case "short" -> "java.lang.Short";
                case "char" -> "java.lang.Character";
                case "int" -> "java.lang.Integer";
                case "long" -> "java.lang.Long";
                case "float" -> "java.lang.Float";
                case "double" -> "java.lang.Double";
                default -> null;
            };
        }

        private boolean isSubtype(String candidateQualifiedTypeName, String targetQualifiedTypeName) {
            return isSubtype(candidateQualifiedTypeName, targetQualifiedTypeName, new HashSet<>());
        }

        private boolean isSubtype(
            String candidateQualifiedTypeName,
            String targetQualifiedTypeName,
            Set<String> visited
        ) {
            if (sameQualifiedTypeName(candidateQualifiedTypeName, targetQualifiedTypeName))
                return true;
            if (candidateQualifiedTypeName == null
                || targetQualifiedTypeName == null
                || !visited.add(candidateQualifiedTypeName))
                return false;

            for (String directSuper : directSuperTypeNames(candidateQualifiedTypeName)) {
                context.poll();
                if (sameQualifiedTypeName(directSuper, targetQualifiedTypeName)
                    || isSubtype(directSuper, targetQualifiedTypeName, visited))
                    return true;
            }
            return false;
        }

        private static boolean sameQualifiedTypeName(@Nullable String left, @Nullable String right) {
            if (left == null || right == null)
                return left == right;
            return left.replace('$', '.').equals(right.replace('$', '.'));
        }

        private @Nullable String resolveQualifiedTypeNameForCallMatching(@Nullable String text) {
            if (text == null || text.isBlank())
                return null;

            text = eraseTypeArguments(text);
            while (text.endsWith("[]")) {
                context.poll();
                text = text.substring(0, text.length() - 2);
            }
            if (text.isBlank())
                return null;
            if ("void".equals(text)
                || Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return text;
            if (text.indexOf('.') > 0 && isResolvableType(text))
                return text;
            if (text.indexOf('.') > 0 && context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String inCurrentPackage = context.currentPackageName + "." + text;
                if (isResolvableType(inCurrentPackage))
                    return inCurrentPackage;
            }

            String simpleName = simpleTypeName(text);
            for (String localQualifiedTypeName : localQualifiedTypeNames) {
                context.poll();
                if (simpleTypeName(localQualifiedTypeName).equals(simpleName))
                    return localQualifiedTypeName;
            }
            if (singleTypeImportsBySimpleName.containsKey(simpleName))
                return singleTypeImportsBySimpleName.get(simpleName).qualifiedTarget();
            if (context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String inCurrentPackage = context.currentPackageName + "." + simpleName;
                if (isResolvableType(inCurrentPackage))
                    return inCurrentPackage;
            }
            String javaLangType = "java.lang." + simpleName;
            if (isResolvableType(javaLangType))
                return javaLangType;
            for (ImportSpec onDemandImport : onDemandTypeImports) {
                context.poll();
                String imported = onDemandImport.ownerName() + "." + simpleName;
                if (isResolvableType(imported))
                    return imported;
            }
            String projectQualifiedType = uniqueProjectQualifiedTypeName(simpleName);
            if (projectQualifiedType != null)
                return projectQualifiedType;
            return text;
        }

        private List<String> directSuperTypeNames(String qualifiedTypeName) {
            List<String> cached = directSuperTypesByQualifiedName.get(qualifiedTypeName);
            if (cached != null)
                return cached;
            if (!directSuperTypesInProgress.add(qualifiedTypeName))
                return List.of();

            try {
                List<String> directSupers = directSuperTypesFromCurrentFile(qualifiedTypeName);
                if (directSupers.isEmpty()) {
                    directSupers = directSuperTypesFromIndexedSource(qualifiedTypeName);
                }
                if (directSupers.isEmpty()) {
                    directSupers = directSuperTypesFromBinaryStub(qualifiedTypeName);
                }

                List<String> copy = List.copyOf(directSupers);
                directSuperTypesByQualifiedName.put(qualifiedTypeName, copy);
                return copy;
            } finally {
                directSuperTypesInProgress.remove(qualifiedTypeName);
            }
        }

        private @Nullable String uniqueProjectQualifiedTypeName(String simpleName) {
            if (projectIndex == null)
                return null;

            String match = null;
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupSimpleName(simpleName)) {
                context.poll();
                if (!isTypeSymbol(symbol.kind()))
                    continue;
                String qualifiedName = symbol.qualifiedName();
                if (qualifiedName == null || qualifiedName.isBlank())
                    continue;
                if (match != null && !match.equals(qualifiedName))
                    return null;
                match = qualifiedName;
            }
            return match;
        }

        private List<String> directSuperTypesFromCurrentFile(String qualifiedTypeName) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!Objects.equals(symbol.qualifiedName().orElse(null), qualifiedTypeName))
                    continue;

                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    return List.of();

                if (JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id().equals(declaration.kind().id())) {
                    SyntaxNode creation = declaration.parent().orElse(null);
                    SyntaxNode baseType = creation == null
                        ? null
                        : directChild(creation, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    String baseName = baseType == null ? null : resolveQualifiedTypeName(baseType, creation);
                    return baseName == null ? List.of() : List.of(eraseTypeArguments(baseName));
                }
                List<String> directSupers = new ArrayList<>();
                collectDirectSuperTypes(declaration, JavaSyntaxKinds.EXTENDS_CLAUSE.id(), directSupers);
                collectDirectSuperTypes(declaration, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id(), directSupers);
                if (symbol.kind() == JavaSymbolKind.ENUM) {
                    directSupers.addFirst("java.lang.Enum");
                }
                return List.copyOf(directSupers);
            }
            return List.of();
        }

        private List<String> directSuperTypesFromIndexedSource(String qualifiedTypeName) {
            if (projectIndex == null)
                return List.of();

            JavaProjectSemanticIndex.SymbolDescriptor sourceType = projectIndex.lookupQualifiedName(qualifiedTypeName)
                .stream()
                .filter(symbol -> isProjectSourceSymbol(symbol) && isTypeSymbol(symbol.kind()))
                .findFirst()
                .orElse(null);
            if (sourceType == null)
                return List.of();

            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, sourceType.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(sourceType.sourceFile(), source, model, projectIndex);
                Map<String, List<String>> collected = new LinkedHashMap<>();
                collectDirectSuperTypesFromSourceFile(sourceContext, model.syntaxTree().root(), collected);
                collected
                    .forEach((name, supers) -> directSuperTypesByQualifiedName.putIfAbsent(name, List.copyOf(supers)));
                return directSuperTypesByQualifiedName.getOrDefault(sourceType.qualifiedName(), List.of());
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return List.of();
            }
        }

        private List<String> directSuperTypesFromBinaryStub(String qualifiedTypeName) {
            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedTypeName);
            if (stub == null)
                return List.of();

            List<String> directSupers = new ArrayList<>();
            Type superClass = toSemanticType(stub.superClass());
            if (superClass.kind() == JavaTypes.Kind.DECLARED) {
                directSupers.add(superClass.displayName());
            }
            for (dev.railroadide.railroad.ide.classparser.Type iface : stub.interfaces()) {
                context.poll();
                Type ifaceType = toSemanticType(iface);
                if (ifaceType.kind() == JavaTypes.Kind.DECLARED) {
                    directSupers.add(ifaceType.displayName());
                }
            }
            return List.copyOf(directSupers);
        }

        private void collectDirectSuperTypesFromSourceFile(
            JavaRuleContext sourceContext,
            SyntaxNode node,
            Map<String, List<String>> out
        ) {
            sourceContext.declaredSymbol(node).ifPresent(symbol -> {
                if (!isTypeSymbol(symbol.kind()))
                    return;

                String qualifiedName = symbol.qualifiedName().orElse(null);
                if (qualifiedName == null || qualifiedName.isBlank())
                    return;

                List<String> directSupers = new ArrayList<>();
                if (JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id().equals(node.kind().id())) {
                    sourceContext.directSuperTypeNames(qualifiedName).stream()
                        .map(JavaSemanticAnalyzer::eraseTypeArguments).forEach(directSupers::add);
                } else {
                    collectDirectSuperTypes(sourceContext, node, JavaSyntaxKinds.EXTENDS_CLAUSE.id(), directSupers);
                    collectDirectSuperTypes(sourceContext, node, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id(), directSupers);
                }
                if (symbol.kind() == JavaSymbolKind.ENUM) {
                    directSupers.addFirst("java.lang.Enum");
                }
                out.put(qualifiedName, List.copyOf(directSupers));
            });

            for (SyntaxNode child : node.children()) {
                context.poll();
                collectDirectSuperTypesFromSourceFile(sourceContext, child, out);
            }
        }

        private boolean isProjectSourceSymbol(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            Path sourceFile = symbol.sourceFile();
            return sourceFile != null && sourceFile.getFileName() != null
                && sourceFile.getFileName().toString().endsWith(".java");
        }

        private @Nullable MemberCandidate implicitLocalConstructorCandidate(String ownerQualifiedName) {
            Type constructedType = new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
            List<Type> parameterTypes = localRecordCanonicalConstructorParameterTypes(ownerQualifiedName);
            return new MemberCandidate(
                new SyntheticMemberSymbol(
                    JavaSymbolKind.CONSTRUCTOR,
                    "<init>",
                    ownerQualifiedName + "#<init>" + signatureSuffix(parameterTypes),
                    null,
                    constructedType,
                    parameterTypes,
                    false),
                ownerQualifiedName,
                false,
                constructedType,
                parameterTypes);
        }

        private List<Type> localRecordCanonicalConstructorParameterTypes(String ownerQualifiedName) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    continue;
                if (symbol.kind() != JavaSymbolKind.RECORD)
                    return List.of();

                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    return List.of();

                SyntaxNode recordHeader = directChild(declaration, JavaSyntaxKinds.RECORD_HEADER.id());
                if (recordHeader == null)
                    return List.of();

                List<Type> parameterTypes = new ArrayList<>();
                for (SyntaxNode child : recordHeader.children()) {
                    context.poll();
                    if (!JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                        continue;
                    SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null) {
                        parameterTypes.add(typeFromTypeReferenceForResolution(typeRef));
                    }
                }
                return List.copyOf(parameterTypes);
            }
            return List.of();
        }

        private @Nullable MemberCandidate localRecordAccessorCandidate(String ownerQualifiedName, String methodName) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    continue;
                if (symbol.kind() != JavaSymbolKind.RECORD)
                    return null;

                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    return null;

                SyntaxNode recordHeader = directChild(declaration, JavaSyntaxKinds.RECORD_HEADER.id());
                if (recordHeader == null)
                    return null;

                for (SyntaxNode child : recordHeader.children()) {
                    context.poll();
                    if (!JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                        continue;
                    String componentName = lastIdentifierLikeTokenText(child);
                    if (!Objects.equals(componentName, methodName))
                        continue;
                    SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    Type valueType = typeRef == null
                        ? new JavaTypes.UnknownType("<unknown>")
                        : typeFromTypeReferenceForResolution(typeRef);
                    return new MemberCandidate(
                        new SyntheticMemberSymbol(
                            JavaSymbolKind.METHOD,
                            methodName,
                            ownerQualifiedName + "#" + methodName + "()",
                            null,
                            valueType,
                            List.of(),
                            false),
                        ownerQualifiedName,
                        false,
                        valueType,
                        List.of());
                }
                return null;
            }
            return null;
        }

        private @Nullable MemberCandidate projectRecordAccessorCandidate(String ownerQualifiedName, String methodName) {
            Type valueType = projectRecordAccessorTypes(ownerQualifiedName).get(methodName);
            if (valueType == null)
                return null;
            return new MemberCandidate(
                new SyntheticMemberSymbol(
                    JavaSymbolKind.METHOD,
                    methodName,
                    ownerQualifiedName + "#" + methodName + "()",
                    null,
                    valueType,
                    List.of(),
                    false),
                ownerQualifiedName,
                false,
                valueType,
                List.of());
        }

        private List<Type> projectRecordCanonicalConstructorParameterTypes(String ownerQualifiedName, Path sourceFile) {
            return new ArrayList<>(projectRecordAccessorTypes(ownerQualifiedName).values());
        }

        private Map<String, Type> projectRecordAccessorTypes(String ownerQualifiedName) {
            Map<String, Type> cached = projectRecordAccessorTypesByOwner.get(ownerQualifiedName);
            if (cached != null)
                return cached;

            if (projectIndex == null) {
                projectRecordAccessorTypesByOwner.put(ownerQualifiedName, Map.of());
                return Map.of();
            }

            JavaProjectSemanticIndex.SymbolDescriptor ownerType = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> isProjectSourceSymbol(symbol) && symbol.kind() == JavaSymbolKind.RECORD)
                .findFirst()
                .orElse(null);
            if (ownerType == null) {
                projectRecordAccessorTypesByOwner.put(ownerQualifiedName, Map.of());
                return Map.of();
            }

            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, ownerType.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(
                    ownerType.sourceFile(), source, model, projectIndex);
                Map<String, Type> accessorTypes = new LinkedHashMap<>();
                collectProjectRecordAccessorTypes(
                    model.syntaxTree().root(), model, sourceContext, ownerType.qualifiedName(), accessorTypes);
                Map<String, Type> copy = Collections.unmodifiableMap(new LinkedHashMap<>(accessorTypes));
                projectRecordAccessorTypesByOwner.put(ownerQualifiedName, copy);
                return copy;
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                projectRecordAccessorTypesByOwner.put(ownerQualifiedName, Map.of());
                return Map.of();
            }
        }

        private void collectProjectRecordAccessorTypes(
            SyntaxNode node,
            SemanticModel model,
            JavaRuleContext sourceContext,
            String ownerQualifiedName,
            Map<String, Type> out
        ) {
            Symbol declared = model.declaredSymbol(node).orElse(null);
            if (declared != null
                && declared.kind() == JavaSymbolKind.RECORD
                && Objects.equals(declared.qualifiedName().orElse(null), ownerQualifiedName)) {
                SyntaxNode recordHeader = directChild(node, JavaSyntaxKinds.RECORD_HEADER.id());
                if (recordHeader == null)
                    return;
                for (SyntaxNode child : recordHeader.children()) {
                    context.poll();
                    if (!JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                        continue;
                    String componentName = lastIdentifierLikeTokenText(child);
                    if (componentName == null || componentName.isBlank())
                        continue;
                    SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    Type valueType = projectTypeFromTypeReference(sourceContext, typeRef);
                    out.putIfAbsent(componentName, valueType);
                }
                return;
            }

            for (SyntaxNode child : node.children()) {
                context.poll();
                collectProjectRecordAccessorTypes(child, model, sourceContext, ownerQualifiedName, out);
            }
        }

        private Type projectMemberValueType(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            String key = projectMemberValueKey(symbol);
            Type cached = projectMemberValueTypesByKey.get(key);
            if (cached != null)
                return cached;

            Type resolved = computeProjectMemberValueType(symbol);
            projectMemberValueTypesByKey.put(key, resolved);
            return resolved;
        }

        private @Nullable MemberCandidate localRecordFieldCandidate(
            String ownerQualifiedName,
            String fieldName
        ) {
            MemberCandidate accessor = localRecordAccessorCandidate(ownerQualifiedName, fieldName);
            if (accessor == null)
                return null;
            Type valueType = accessor.valueType();
            Symbol field = new SyntheticMemberSymbol(
                JavaSymbolKind.FIELD,
                fieldName,
                ownerQualifiedName + "#" + fieldName,
                null,
                valueType,
                List.of(),
                false);
            return new MemberCandidate(field, ownerQualifiedName, false, valueType, List.of());
        }

        private boolean isLocalRecordType(String ownerQualifiedName) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (symbol.kind() == JavaSymbolKind.RECORD
                    && Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    return true;
            }
            return false;
        }

        private List<Type> projectMethodParameterTypes(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            String key = projectMemberValueKey(symbol);
            List<Type> cached = projectMethodParameterTypesByKey.get(key);
            if (cached != null)
                return cached;

            List<Type> resolved = computeProjectMethodParameterTypes(symbol);
            if (resolved.isEmpty() && symbol.signature() != null && !"()".equals(symbol.signature())) {
                resolved = parameterTypesFromProjectSignature(symbol);
            }
            List<Type> copy = List.copyOf(resolved);
            projectMethodParameterTypesByKey.put(key, copy);
            return copy;
        }

        private List<Type> computeProjectMethodParameterTypes(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            if (symbol.kind() != JavaSymbolKind.METHOD && symbol.kind() != JavaSymbolKind.CONSTRUCTOR)
                return List.of();
            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, symbol.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(symbol.sourceFile(), source, model, projectIndex);
                List<Type> match = findProjectMethodParameterTypes(
                    symbol, model.syntaxTree().root(), model, sourceContext);
                return match == null ? List.of() : match;
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return List.of();
            }
        }

        private List<JavaRuleContext.MethodDescriptor> projectSourceMethodDescriptors(String ownerQualifiedName) {
            return projectSourceMethodsByOwner.computeIfAbsent(
                ownerQualifiedName, this::loadProjectSourceMethodDescriptors);
        }

        private List<JavaRuleContext.MethodDescriptor> loadProjectSourceMethodDescriptors(String ownerQualifiedName) {
            if (projectIndex == null)
                return List.of();
            JavaProjectSemanticIndex.SymbolDescriptor owner = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> isProjectSourceSymbol(symbol) && isTypeSymbol(symbol.kind()))
                .findFirst()
                .orElse(null);
            if (owner == null)
                return List.of();
            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, owner.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(
                    owner.sourceFile(), source, model, projectIndex);
                return List.copyOf(sourceContext.declaredMethodDescriptors(owner.qualifiedName()));
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return List.of();
            }
        }

        private @Nullable List<Type> findProjectMethodParameterTypes(
            JavaProjectSemanticIndex.SymbolDescriptor target,
            SyntaxNode node,
            SemanticModel model,
            JavaRuleContext sourceContext
        ) {
            Symbol declared = model.declaredSymbol(node).orElse(null);
            if (declared != null && matchesProjectMemberSymbol(target, declared, node)) {
                projectVariableArityByKey.put(projectMemberValueKey(target), hasVariableArityParameters(node));
                SyntaxNode parameterList = JavaSourceSymbolIds.parameterList(node);
                if (parameterList == null)
                    return List.of();
                List<Type> parameterTypes = new ArrayList<>();
                for (SyntaxNode child : parameterList.children()) {
                    context.poll();
                    if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id())
                        && !JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                        continue;
                    SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    Type parameterType = projectTypeFromTypeReference(sourceContext, typeRef);
                    if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                        parameterType = new JavaTypes.ArrayType(parameterType);
                    }
                    parameterTypes.add(parameterType);
                }
                return List.copyOf(parameterTypes);
            }

            for (SyntaxNode child : node.children()) {
                context.poll();
                List<Type> match = findProjectMethodParameterTypes(target, child, model, sourceContext);
                if (match != null)
                    return match;
            }
            return null;
        }

        private boolean isFunctionalInterfaceParameter(Type parameterType) {
            return functionalInterfaceArity(parameterType, new HashSet<>()) != null;
        }

        private @Nullable Integer functionalInterfaceArity(Type parameterType, Set<String> visited) {
            if (!(parameterType instanceof JavaTypes.DeclaredType declared))
                return null;
            String qualifiedTypeName = resolveQualifiedTypeNameForCallMatching(declared.displayName());
            if (qualifiedTypeName == null || !visited.add(qualifiedTypeName))
                return null;

            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedTypeName);
            if (stub != null) {
                Map<String, Integer> abstractMethods = new LinkedHashMap<>();
                for (MethodStub method : stub.methods()) {
                    context.poll();
                    if (!Modifier.isAbstract(method.modifiers())
                        || Modifier.isStatic(method.modifiers())
                        || isObjectMethodSignature(method.name(), method.parameters().size()))
                        continue;
                    List<Type> parameterTypes = method.parameters().stream()
                        .map(parameter -> toSemanticType(parameter.type()))
                        .toList();
                    abstractMethods.put(method.name() + signatureSuffix(parameterTypes), parameterTypes.size());
                }
                if (abstractMethods.size() == 1)
                    return abstractMethods.values().iterator().next();
                if (abstractMethods.isEmpty()) {
                    Map<String, Type> substitutions = new LinkedHashMap<>();
                    int count = Math.min(stub.typeParameters().size(), declared.typeArguments().size());
                    for (int index = 0; index < count; index++) {
                        context.poll();
                        substitutions.put(stub.typeParameters().get(index).name(), declared.typeArguments().get(index));
                    }
                    for (dev.railroadide.railroad.ide.classparser.Type parentInterface : stub.interfaces()) {
                        context.poll();
                        Type specializedParent = substituteFunctionalType(toSemanticType(parentInterface),
                            substitutions);
                        if (!(specializedParent instanceof JavaTypes.DeclaredType parentDeclared))
                            continue;
                        String parentQualifiedType = resolveFunctionalSuperTypeName(parentDeclared, qualifiedTypeName);
                        Type qualifiedParent = parentQualifiedType == null
                            ? parentDeclared
                            : new JavaTypes.DeclaredType(parentQualifiedType, parentDeclared.typeArguments());
                        Integer parentArity = functionalInterfaceArity(qualifiedParent, visited);
                        if (parentArity != null)
                            return parentArity;
                    }
                }
            }

            if (projectIndex == null)
                return null;
            boolean sourceInterface = projectIndex.lookupQualifiedName(qualifiedTypeName).stream()
                .anyMatch(symbol -> isProjectSourceSymbol(symbol) && symbol.kind() == JavaSymbolKind.INTERFACE);
            if (!sourceInterface)
                return null;
            Map<String, Integer> sourceMethods = new LinkedHashMap<>();
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMembers(qualifiedTypeName)) {
                context.poll();
                if (isProjectSourceSymbol(symbol) && symbol.kind() == JavaSymbolKind.METHOD && !symbol.isStatic()) {
                    sourceMethods.put(
                        symbol.simpleName() + Objects.toString(symbol.signature(), ""),
                        parameterTypesFromProjectSignature(symbol).size());
                }
            }
            return sourceMethods.size() == 1 ? sourceMethods.values().iterator().next() : null;
        }

        private static boolean isObjectMethodSignature(String name, int parameterCount) {
            return switch (name) {
                case "toString", "hashCode", "clone", "finalize" -> parameterCount == 0;
                case "equals" -> parameterCount == 1;
                default -> false;
            };
        }

        private String projectMemberValueKey(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            if (symbol.descriptor() != null)
                return symbol.descriptor().id().value();
            return symbol.kind() + "|" + symbol.qualifiedName() + "|" + symbol.signature() + "|" + symbol.sourceFile();
        }

        private Type computeProjectMemberValueType(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            if (symbol.kind() == JavaSymbolKind.CONSTRUCTOR) {
                String ownerQualifiedName = symbol.ownerQualifiedName();
                return ownerQualifiedName == null
                    ? new JavaTypes.UnknownType("<unknown>")
                    : new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
            }

            if (symbol.kind() != JavaSymbolKind.METHOD && symbol.kind() != JavaSymbolKind.FIELD)
                return new JavaTypes.UnknownType("<unknown>");

            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, symbol.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(
                    symbol.sourceFile(),
                    source,
                    model,
                    projectIndex);
                return findProjectMemberValueType(
                    symbol, model.syntaxTree().root(), model, sourceContext);
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return new JavaTypes.UnknownType("<unknown>");
            }
        }

        private Type findProjectMemberValueType(
            JavaProjectSemanticIndex.SymbolDescriptor target,
            SyntaxNode node,
            SemanticModel model,
            JavaRuleContext sourceContext
        ) {
            Symbol declared = model.declaredSymbol(node).orElse(null);
            if (declared != null && matchesProjectMemberSymbol(target, declared, node)) {
                if (target.kind() == JavaSymbolKind.FIELD) {
                    if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(node.kind().id())) {
                        String ownerQualifiedName = target.ownerQualifiedName();
                        return ownerQualifiedName == null || ownerQualifiedName.isBlank()
                            ? new JavaTypes.UnknownType("<unknown>")
                            : new JavaTypes.DeclaredType(ownerQualifiedName, List.of());
                    }
                    SyntaxNode typeRef = nearestTypeReferenceForProjectField(node);
                    return projectTypeFromTypeReference(sourceContext, typeRef);
                }
                SyntaxNode typeRef = directChild(node, JavaSyntaxKinds.TYPE_REFERENCE.id());
                return projectTypeFromTypeReference(sourceContext, typeRef);
            }

            for (SyntaxNode child : node.children()) {
                context.poll();
                Type match = findProjectMemberValueType(target, child, model, sourceContext);
                if (match.kind() != JavaTypes.Kind.UNKNOWN)
                    return match;
            }
            return new JavaTypes.UnknownType("<unknown>");
        }

        private Type inferCastTypeForResolution(SyntaxNode castExpression) {
            SyntaxNode typeRef = directChild(castExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null
                ? new JavaTypes.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
        }

        private Type inferConditionalTypeForResolution(SyntaxNode conditionalExpression) {
            List<SyntaxNode> expressions = new ArrayList<>();
            for (SyntaxNode child : conditionalExpression.children()) {
                context.poll();
                if (isExpressionNode(child)) {
                    expressions.add(child);
                }
            }
            if (expressions.size() < 3)
                return new JavaTypes.UnknownType("<unknown>");
            Type whenTrue = inferExpressionTypeForResolution(expressions.get(1));
            Type whenFalse = inferExpressionTypeForResolution(expressions.get(2));
            Type common = commonConditionalType(whenTrue, whenFalse, projectIndex);
            if (common.kind() != JavaTypes.Kind.UNKNOWN)
                return common;
            String commonSupertype = nearestCommonSupertype(whenTrue, whenFalse, conditionalExpression);
            return commonSupertype == null
                ? new JavaTypes.UnknownType("<unknown>")
                : new JavaTypes.DeclaredType(commonSupertype, List.of());
        }

        private @Nullable String nearestCommonSupertype(Type left, Type right, SyntaxNode usageSite) {
            if (!(left instanceof JavaTypes.DeclaredType) || !(right instanceof JavaTypes.DeclaredType))
                return null;
            String leftName = resolveQualifiedTypeName(left.displayName(), usageSite);
            String rightName = resolveQualifiedTypeName(right.displayName(), usageSite);
            if (leftName == null || rightName == null)
                return null;

            Set<String> leftHierarchy = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(leftName);
            while (!queue.isEmpty()) {
                context.poll();
                String current = queue.removeFirst();
                if (!leftHierarchy.add(current))
                    continue;
                queue.addAll(directSuperTypeNames(current));
            }

            Set<String> visited = new HashSet<>();
            queue.add(rightName);
            while (!queue.isEmpty()) {
                context.poll();
                String current = queue.removeFirst();
                if (!visited.add(current))
                    continue;
                if (leftHierarchy.contains(current))
                    return current;
                queue.addAll(directSuperTypeNames(current));
            }
            return null;
        }

        private Type inferSwitchExpressionTypeForResolution(SyntaxNode switchExpression) {
            List<Type> resultTypes = new ArrayList<>();
            collectSwitchResultTypesForResolution(switchExpression, resultTypes);
            return mergeExpressionTypes(resultTypes, projectIndex);
        }

        private void collectSwitchResultTypesForResolution(SyntaxNode node, List<Type> out) {
            if (JavaSyntaxKinds.SWITCH_RULE.id().equals(node.kind().id())) {
                List<SyntaxNode> expressions = new ArrayList<>();
                for (SyntaxNode child : node.children()) {
                    context.poll();
                    if (isExpressionNode(child)) {
                        expressions.add(child);
                    }
                }
                if (!expressions.isEmpty()) {
                    out.add(inferExpressionTypeForResolution(expressions.getLast()));
                }
                return;
            }
            for (SyntaxNode child : node.children()) {
                context.poll();
                collectSwitchResultTypesForResolution(child, out);
            }
        }

        private Type projectTypeFromTypeReference(JavaRuleContext sourceContext, @Nullable SyntaxNode typeRef) {
            if (typeRef == null)
                return new JavaTypes.UnknownType("<unknown>");

            String text = canonicalTypeText(typeRef);
            if (text == null || text.isBlank())
                return new JavaTypes.UnknownType("<unknown>");

            return projectTypeFromText(sourceContext, text);
        }

        private Type projectTypeFromText(JavaRuleContext sourceContext, String text) {
            text = text.trim();
            if (text.isBlank())
                return new JavaTypes.UnknownType("<unknown>");
            if ("void".equals(text))
                return new JavaTypes.VoidType();
            if (text.startsWith("?extends"))
                return new JavaTypes.WildcardType(projectTypeFromText(sourceContext, text.substring(8)), null);
            if (text.startsWith("?super"))
                return new JavaTypes.WildcardType(null, projectTypeFromText(sourceContext, text.substring(6)));
            if ("?".equals(text))
                return new JavaTypes.WildcardType(new JavaTypes.DeclaredType("java.lang.Object", List.of()), null);

            int arrayDimensions = 0;
            while (text.endsWith("[]")) {
                context.poll();
                arrayDimensions++;
                text = text.substring(0, text.length() - 2);
            }

            int typeArgumentsStart = findTopLevelTypeArgumentsStart(text);
            String rawType = typeArgumentsStart > 0 && text.endsWith(">")
                ? text.substring(0, typeArgumentsStart).trim()
                : text;
            List<Type> typeArguments = new ArrayList<>();
            if (typeArgumentsStart > 0 && text.endsWith(">")) {
                String argumentsText = text.substring(typeArgumentsStart + 1, text.length() - 1);
                for (String argument : splitTopLevelTypeArguments(argumentsText)) {
                    context.poll();
                    if (!argument.isBlank()) {
                        typeArguments.add(projectTypeFromText(sourceContext, argument));
                    }
                }
            }

            String qualifiedName = sourceContext.resolveQualifiedTypeName(rawType);
            Type resolved = switch (rawType) {
                case "boolean", "byte", "short", "char", "int", "long", "float", "double" ->
                    new JavaTypes.PrimitiveType(rawType);
                default -> qualifiedName == null || qualifiedName.isBlank()
                    || isLikelyTypeVariableName(rawType) && qualifiedName.equals(rawType)
                        ? isLikelyTypeVariableName(rawType)
                            ? new JavaTypes.TypeVariableType(rawType)
                            : new JavaTypes.UnknownType(text)
                        : new JavaTypes.DeclaredType(qualifiedName, typeArguments);
            };

            for (int index = 0; index < arrayDimensions; index++) {
                context.poll();
                resolved = new JavaTypes.ArrayType(resolved);
            }
            return resolved;
        }

        private @Nullable SyntaxNode nearestTypeReferenceForProjectField(SyntaxNode node) {
            SyntaxNode current = node;
            while (current != null) {
                context.poll();
                SyntaxNode typeRef = directChild(current, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return typeRef;
                current = current.parent().orElse(null);
            }
            return null;
        }

        private boolean matchesProjectMemberSymbol(
            JavaProjectSemanticIndex.SymbolDescriptor target,
            Symbol declared,
            SyntaxNode node
        ) {
            if (target.descriptor() != null && declared.id().isPresent())
                return target.descriptor().id().equals(declared.id().orElseThrow());
            if (declared.kind() != target.kind())
                return false;
            if (!Objects.equals(declared.simpleName(), target.simpleName()))
                return false;

            String ownerQualifiedName = ownerQualifiedName(declared);
            if (!Objects.equals(ownerQualifiedName, target.ownerQualifiedName()))
                return false;

            if (target.kind() == JavaSymbolKind.METHOD || target.kind() == JavaSymbolKind.CONSTRUCTOR)
                return Objects.equals(target.signature(), signatureFromCallableDeclaration(node));
            return true;
        }

        private @Nullable String signatureFromCallableDeclaration(SyntaxNode declaration) {
            SyntaxNode parameterList = JavaSourceSymbolIds.parameterList(declaration);
            if (parameterList == null)
                return "()";

            List<String> parameterTypes = new ArrayList<>();
            for (SyntaxNode child : parameterList.children()) {
                context.poll();
                String kindId = child.kind().id();
                if (!JavaSyntaxKinds.PARAMETER.id().equals(kindId)
                    && !JavaSyntaxKinds.RECORD_COMPONENT.id().equals(kindId))
                    continue;

                SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef == null)
                    continue;

                String text = canonicalTypeText(typeRef);
                if (text != null && !text.isBlank()) {
                    if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                        text += "[]";
                    }
                    parameterTypes.add(text);
                }
            }
            return "(" + String.join(",", parameterTypes) + ")";
        }

        private void collectDirectSuperTypes(SyntaxNode declarationNode, String clauseKindId, List<String> out) {
            SyntaxNode clause = directChild(declarationNode, clauseKindId);
            if (clause == null)
                return;

            for (SyntaxNode typeReference : clause.children()) {
                context.poll();
                if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(typeReference.kind().id()))
                    continue;

                String qualified = resolveQualifiedTypeName(typeReference, declarationNode);
                if (qualified != null && !qualified.isBlank()) {
                    out.add(qualified);
                }
            }
        }

        private void collectDirectSuperTypes(
            JavaRuleContext sourceContext,
            SyntaxNode declarationNode,
            String clauseKindId,
            List<String> out
        ) {
            SyntaxNode clause = directChild(declarationNode, clauseKindId);
            if (clause == null)
                return;

            for (SyntaxNode typeReference : clause.children()) {
                context.poll();
                if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(typeReference.kind().id()))
                    continue;

                String qualified = sourceContext.resolveQualifiedTypeName(typeReference);
                if (qualified != null && !qualified.isBlank()) {
                    out.add(qualified);
                }
            }
        }

        private static int compareCost(List<Integer> left, List<Integer> right) {
            int size = Math.min(left.size(), right.size());
            for (int index = 0; index < size; index++) {
                int comparison = Integer.compare(left.get(index), right.get(index));
                if (comparison != 0)
                    return comparison;
            }
            return Integer.compare(left.size(), right.size());
        }

        private List<MemberCandidate> staticImportedMethodCandidates(String methodName, List<Type> argumentTypes) {
            int argumentCount = argumentTypes.size();
            List<MemberCandidate> candidates = new ArrayList<>();
            List<ImportSpec> singleStaticImports = staticSingleImportsByMemberName.get(methodName);
            if (singleStaticImports != null) {
                for (ImportSpec importSpec : singleStaticImports) {
                    context.poll();
                    for (MemberCandidate candidate : findMethodCandidates(importSpec.ownerName(), methodName, true)) {
                        context.poll();
                        if (isArityCompatible(candidate, argumentCount)) {
                            candidates.add(candidate);
                        }
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                context.poll();
                for (MemberCandidate candidate : findMethodCandidates(onDemandImport.ownerName(), methodName, true)) {
                    context.poll();
                    if (isArityCompatible(candidate, argumentCount)) {
                        candidates.add(candidate);
                    }
                }
            }

            return List.copyOf(candidates);
        }

        private @Nullable MemberLookup resolveMemberLookup(SyntaxNode targetNode, SyntaxNode usageSite) {
            Symbol targetSymbol = context.resolvedSymbol(targetNode);
            if (targetSymbol != null && isTypeSymbol(targetSymbol.kind()))
                return new MemberLookup(targetSymbol.qualifiedName().orElse(null), true);

            if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(targetNode.kind().id())
                || JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(targetNode.kind().id())) {
                String typeLikeName = canonicalQualifiedName(targetNode);
                String qualifiedTypeName = resolveQualifiedTypeName(typeLikeName, usageSite);
                if (qualifiedTypeName != null && isResolvableType(qualifiedTypeName))
                    return new MemberLookup(qualifiedTypeName, true);
            }

            if (JavaSyntaxKinds.THIS_EXPRESSION.id().equals(targetNode.kind().id())
                || JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(targetNode.kind().id())) {
                String qualifiedOwner = qualifiedEnclosingInstanceOwner(targetNode, usageSite);
                if (qualifiedOwner != null)
                    return new MemberLookup(qualifiedOwner, false);
                Symbol enclosingType = nearestEnclosingTypeSymbol(usageSite);
                String owner = enclosingType == null ? null : enclosingType.qualifiedName().orElse(null);
                if (owner != null && JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(targetNode.kind().id())) {
                    owner = directSuperclassName(enclosingType);
                }
                return new MemberLookup(owner, false);
            }

            return new MemberLookup(qualifiedTypeNameOfExpression(targetNode, usageSite), false);
        }

        private @Nullable String qualifiedEnclosingInstanceOwner(
            SyntaxNode targetNode,
            SyntaxNode usageSite
        ) {
            String resolvedQualifier = resolvedTypeQualifier(targetNode);
            if (resolvedQualifier != null)
                return resolvedQualifier;

            var qualifier = new StringBuilder();
            for (SyntaxToken token : leafTokens(targetNode)) {
                context.poll();
                if (isTriviaToken(token) || isMissingTokenKind(token.kind().id()))
                    continue;
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.THIS_KEYWORD).id().equals(token.kind().id())
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.SUPER_KEYWORD).id().equals(token.kind().id()))
                    break;
                qualifier.append(JavaUnicodeEscapes.translate(token.text()));
            }
            String text = qualifier.toString();
            while (text.endsWith(".")) {
                context.poll();
                text = text.substring(0, text.length() - 1);
            }
            if (text.isBlank())
                return null;
            return resolveQualifiedTypeName(text, usageSite);
        }

        private @Nullable String resolvedTypeQualifier(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                context.poll();
                Symbol qualifierSymbol = context.resolvedSymbol(child);
                if (qualifierSymbol != null && isTypeSymbol(qualifierSymbol.kind())) {
                    String qualifiedName = qualifierSymbol.qualifiedName().orElse(null);
                    if (qualifiedName != null && !qualifiedName.isBlank())
                        return qualifiedName;
                }
                String nested = resolvedTypeQualifier(child);
                if (nested != null)
                    return nested;
            }
            return null;
        }

        private @Nullable String qualifiedTypeNameOfExpression(SyntaxNode expressionNode, SyntaxNode usageSite) {
            if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(expressionNode.kind().id())) {
                Type lambdaParameterType = contextualLambdaReferenceType(expressionNode);
                String lambdaOwner = qualifiedMemberOwnerType(lambdaParameterType, usageSite);
                if (lambdaOwner != null)
                    return lambdaOwner;
            }
            if (JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(expressionNode.kind().id())) {
                Type inferred = inferExpressionTypeForResolution(expressionNode);
                String inferredOwner = qualifiedMemberOwnerType(inferred, usageSite);
                if (inferredOwner != null)
                    return inferredOwner;
            }

            Symbol resolved = context.resolvedSymbol(expressionNode);
            if (resolved != null) {
                if (isTypeSymbol(resolved.kind()))
                    return resolved.qualifiedName().orElse(null);
                if (resolved.kind() == JavaSymbolKind.CONSTRUCTOR)
                    return ownerQualifiedName(resolved);
                return qualifiedValueTypeNameOfSymbol(resolved, usageSite);
            }

            if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(expressionNode.kind().id())) {
                SyntaxNode typeRef = directChild(expressionNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
                return typeRef == null ? null : resolveQualifiedTypeName(typeRef, usageSite);
            }

            Type inferred = inferExpressionTypeForResolution(expressionNode);
            return qualifiedMemberOwnerType(inferred, usageSite);
        }

        private @Nullable String qualifiedValueTypeNameOfSymbol(Symbol symbol, SyntaxNode usageSite) {
            if (isTypeSymbol(symbol.kind()))
                return symbol.qualifiedName().orElse(null);

            Type valueType = typeOfResolvedSymbol(symbol);
            return qualifiedMemberOwnerType(valueType, usageSite);
        }

        private @Nullable String qualifiedMemberOwnerType(Type type, SyntaxNode usageSite) {
            if (type.kind() == JavaTypes.Kind.DECLARED)
                return resolveQualifiedTypeName(type.displayName(), usageSite);
            if (type.kind() == JavaTypes.Kind.TYPE_VARIABLE) {
                String bound = qualifiedTypeVariableBound(type.displayName(), usageSite);
                return bound == null ? "java.lang.Object" : bound;
            }
            if (type instanceof JavaTypes.WildcardType wildcard) {
                Type bound = wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound();
                return bound == null ? "java.lang.Object" : qualifiedMemberOwnerType(bound, usageSite);
            }
            return null;
        }

        private @Nullable String qualifiedTypeVariableBound(String variableName, SyntaxNode usageSite) {
            List<String> bounds = qualifiedTypeVariableBounds(variableName, usageSite);
            return bounds.isEmpty() ? null : bounds.getFirst();
        }

        private List<String> qualifiedTypeVariableBounds(String variableName, SyntaxNode usageSite) {
            SyntaxNode current = usageSite;
            while (current != null) {
                context.poll();
                SyntaxNode typeParameters = directChild(current, JavaSyntaxKinds.TYPE_PARAMETERS.id());
                if (typeParameters != null) {
                    for (SyntaxNode typeParameter : typeParameters.children()) {
                        context.poll();
                        if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(typeParameter.kind().id())
                            || !variableName.equals(firstIdentifierLikeTokenText(typeParameter)))
                            continue;
                        List<SyntaxNode> boundTypes = new ArrayList<>();
                        collectTopLevelDescendantsOfKind(
                            typeParameter, JavaSyntaxKinds.TYPE_REFERENCE.id(), boundTypes);
                        List<String> bounds = new ArrayList<>();
                        for (SyntaxNode boundType : boundTypes) {
                            context.poll();
                            String qualified = resolveQualifiedTypeName(boundType, usageSite);
                            if (qualified != null && !qualified.isBlank()) {
                                bounds.add(qualified);
                            }
                        }
                        return bounds.isEmpty() ? List.of("java.lang.Object") : List.copyOf(bounds);
                    }
                }
                current = current.parent().orElse(null);
            }
            return List.of();
        }

        private List<String> qualifiedResolvedReceiverTypeVariableBounds(
            SyntaxNode receiver,
            SyntaxNode usageSite
        ) {
            Symbol symbol = context.resolvedSymbol(receiver);
            if (symbol == null)
                return List.of();
            Type rawType = typeOfResolvedSymbol(symbol);
            if (rawType.kind() != JavaTypes.Kind.TYPE_VARIABLE)
                return List.of();
            String owner = ownerQualifiedName(symbol);
            if (owner == null)
                return List.of();
            SourceTypeParameterInfo parameter = sourceTypeParameterInfo(owner, rawType.displayName());
            if (parameter == null)
                return List.of();

            List<String> bounds = new ArrayList<>();
            for (Type bound : parameter.bounds()) {
                context.poll();
                String qualified = resolveQualifiedTypeName(bound.displayName(), usageSite);
                if (qualified != null && !qualified.isBlank()) {
                    bounds.add(qualified);
                }
            }
            return List.copyOf(bounds);
        }

        private void collectTopLevelDescendantsOfKind(
            SyntaxNode node,
            String kindId,
            List<SyntaxNode> out
        ) {
            for (SyntaxNode child : node.children()) {
                context.poll();
                if (kindId.equals(child.kind().id())) {
                    out.add(child);
                } else {
                    collectTopLevelDescendantsOfKind(child, kindId, out);
                }
            }
        }

        private boolean isLexicallyDeclaredTypeVariable(String variableName, SyntaxNode usageSite) {
            SyntaxNode current = usageSite;
            while (current != null) {
                context.poll();
                SyntaxNode typeParameters = directChild(current, JavaSyntaxKinds.TYPE_PARAMETERS.id());
                if (typeParameters != null && typeParameters.children().stream()
                    .filter(child -> JavaSyntaxKinds.TYPE_PARAMETER.id().equals(child.kind().id()))
                    .map(JavaSemanticAnalyzer::firstIdentifierLikeTokenText)
                    .anyMatch(variableName::equals))
                    return true;
                current = current.parent().orElse(null);
            }
            return false;
        }

        private @Nullable String resolveQualifiedTypeName(SyntaxNode typeNode, SyntaxNode usageSite) {
            return resolveQualifiedTypeName(canonicalTypeText(typeNode), usageSite);
        }

        private @Nullable String resolveQualifiedTypeName(@Nullable String text, SyntaxNode usageSite) {
            if (text == null || text.isBlank())
                return null;

            text = eraseTypeArguments(text);
            while (text.endsWith("[]")) {
                context.poll();
                text = text.substring(0, text.length() - 2);
            }
            if (text.isBlank())
                return null;
            if ("void".equals(text)
                || Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return text;
            String directType = resolvableQualifiedTypeName(text);
            if (directType != null)
                return directType;
            String nestedType = resolveNestedQualifiedTypeName(text, usageSite);
            if (nestedType != null)
                return nestedType;
            if (text.indexOf('.') > 0 && context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String inCurrentPackage = context.currentPackageName + "." + text;
                String currentPackageType = resolvableQualifiedTypeName(inCurrentPackage);
                if (currentPackageType != null)
                    return currentPackageType;
            }

            String simpleName = simpleTypeName(text);
            for (String localQualifiedTypeName : localQualifiedTypeNames) {
                context.poll();
                if (simpleTypeName(localQualifiedTypeName).equals(simpleName))
                    return localQualifiedTypeName;
            }
            if (singleTypeImportsBySimpleName.containsKey(simpleName)) {
                String imported = resolvableQualifiedTypeName(
                    singleTypeImportsBySimpleName.get(simpleName).qualifiedTarget());
                if (imported != null)
                    return imported;
            }
            if (context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String inCurrentPackage = context.currentPackageName + "." + simpleName;
                String currentPackageType = resolvableQualifiedTypeName(inCurrentPackage);
                if (currentPackageType != null)
                    return currentPackageType;
            }
            String javaLangType = "java.lang." + simpleName;
            String javaLangResolved = resolvableQualifiedTypeName(javaLangType);
            if (javaLangResolved != null)
                return javaLangResolved;
            String inheritedMemberType = resolveInheritedMemberType(simpleName, usageSite);
            if (inheritedMemberType != null)
                return inheritedMemberType;
            for (ImportSpec onDemandImport : onDemandTypeImports) {
                context.poll();
                String imported = onDemandImport.ownerName() + "." + simpleName;
                String importedType = resolvableQualifiedTypeName(imported);
                if (importedType != null)
                    return importedType;
            }
            String projectQualifiedType = uniqueProjectQualifiedTypeName(simpleName);
            if (projectQualifiedType != null)
                return projectQualifiedType;
            return text;
        }

        private @Nullable String resolvableQualifiedTypeName(String qualifiedTypeName) {
            if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
                return null;
            if (isResolvableType(qualifiedTypeName))
                return qualifiedTypeName;

            String candidate = qualifiedTypeName;
            int dot = candidate.lastIndexOf('.');
            while (dot > 0) {
                context.poll();
                candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
                if (isResolvableType(candidate))
                    return candidate;
                dot = candidate.lastIndexOf('.', dot - 1);
            }
            return null;
        }

        private @Nullable String resolveNestedQualifiedTypeName(String text, SyntaxNode usageSite) {
            int dot = text.lastIndexOf('.');
            if (dot <= 0 || dot >= text.length() - 1)
                return null;

            String owner = resolveQualifiedTypeName(text.substring(0, dot), usageSite);
            if (owner == null || owner.isBlank())
                return null;

            return resolvableQualifiedTypeName(owner + "." + text.substring(dot + 1));
        }

        private @Nullable String resolveInheritedMemberType(String simpleName, SyntaxNode usageSite) {
            Symbol enclosingType = context.enclosingTypeSymbol(usageSite);
            while (enclosingType != null) {
                context.poll();
                String owner = enclosingType.qualifiedName().orElse(null);
                if (owner != null) {
                    for (String directSuper : directSuperTypeNames(owner)) {
                        context.poll();
                        String resolved = resolveMemberTypeInHierarchy(directSuper, simpleName, new HashSet<>());
                        if (resolved != null)
                            return resolved;
                    }
                }
                SyntaxNode declaration = enclosingType.declaration().orElse(null);
                enclosingType = declaration == null ? null : context.enclosingTypeSymbol(declaration);
            }
            return null;
        }

        private @Nullable String resolveMemberTypeInHierarchy(
            String ownerQualifiedName,
            String simpleName,
            Set<String> visited
        ) {
            if (!visited.add(ownerQualifiedName))
                return null;

            String sourceNested = ownerQualifiedName + "." + simpleName;
            if (isResolvableType(sourceNested))
                return sourceNested;

            String binaryNested = ownerQualifiedName + "$" + simpleName;
            if (isResolvableType(binaryNested))
                return binaryNested;

            for (String directSuper : directSuperTypeNames(ownerQualifiedName)) {
                context.poll();
                String resolved = resolveMemberTypeInHierarchy(directSuper, simpleName, visited);
                if (resolved != null)
                    return resolved;
            }
            return null;
        }

        private Type typeFromTypeReferenceForResolution(SyntaxNode typeNode) {
            String text = canonicalTypeText(typeNode);
            if (text == null || text.isBlank())
                return new JavaTypes.UnknownType("<unknown>");
            return typeFromTypeText(text, typeNode);
        }

        private boolean hasResolvableStaticMember(String ownerQualifiedName, String memberName) {
            return hasResolvableStaticField(ownerQualifiedName, memberName)
                || hasResolvableStaticMethod(ownerQualifiedName, memberName, -1);
        }

        private boolean hasResolvableStaticField(String ownerQualifiedName, String fieldName) {
            Set<String> localFields = localStaticFieldsByOwner.get(ownerQualifiedName);
            if (localFields != null && localFields.contains(fieldName))
                return true;
            if (projectIndex != null) {
                boolean projectMatch = projectIndex.lookupMember(ownerQualifiedName, fieldName).stream()
                    .anyMatch(symbol -> symbol.kind() == JavaSymbolKind.FIELD && symbol.isStatic());
                if (projectMatch)
                    return true;
            }

            ClassStub jdkStub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            if (jdkStub == null)
                return false;

            return jdkStub.fields().stream()
                .anyMatch(
                    field -> field.name().equals(fieldName) && Modifier.isStatic(field.modifiers()));
        }

        private boolean hasResolvableStaticMethod(
            String ownerQualifiedName,
            String methodName,
            int argumentCountOrUnknown
        ) {
            Map<String, Set<Integer>> localMethods = localStaticMethodAritiesByOwner.get(ownerQualifiedName);
            if (localMethods != null) {
                Set<Integer> arities = localMethods.get(methodName);
                if (arities != null && !arities.isEmpty()) {
                    if (argumentCountOrUnknown < 0 || arities.contains(argumentCountOrUnknown))
                        return true;
                }
            }
            if (projectIndex != null) {
                boolean projectMatch = projectIndex.lookupMember(ownerQualifiedName, methodName).stream()
                    .filter(symbol -> symbol.kind() == JavaSymbolKind.METHOD && symbol.isStatic())
                    .anyMatch(symbol -> argumentCountOrUnknown < 0
                        || parameterTypesFromProjectSignature(symbol).size() == argumentCountOrUnknown);
                if (projectMatch)
                    return true;
            }

            ClassStub jdkStub = binaryClassStubsByQualifiedName.get(ownerQualifiedName);
            if (jdkStub == null)
                return false;

            return jdkStub.methods().stream()
                .anyMatch(method -> method.name().equals(methodName)
                    && Modifier.isStatic(method.modifiers())
                    && (argumentCountOrUnknown < 0 || method.parameters().size() == argumentCountOrUnknown));
        }

        private boolean isStaticMemberSymbol(Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return false;

            if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(declaration.kind().id()))
                return true;

            if (hasDirectTokenKind(declaration, JavaTokenType.STATIC_KEYWORD))
                return true;

            return declaration.parent()
                .filter(parent -> JavaSyntaxKinds.FIELD_DECLARATION.id().equals(parent.kind().id()))
                .map(parent -> hasDirectTokenKind(parent, JavaTokenType.STATIC_KEYWORD))
                .orElse(false);
        }

        private static int methodDeclarationArity(Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return -1;
            SyntaxNode parameterList = JavaSourceSymbolIds.parameterList(declaration);
            if (parameterList == null)
                return -1;

            int count = 0;
            for (SyntaxNode child : parameterList.children()) {
                if (JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id())
                    || JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id())) {
                    count++;
                }
            }
            return count;
        }

        private static int countInvocationArguments(SyntaxNode invocationNode) {
            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList == null)
                return -1;

            int count = 0;
            for (SyntaxNode child : argumentList.children()) {
                if (isExpressionNode(child)) {
                    count++;
                }
            }
            return count;
        }

        private Symbol selectWithPrecedence(String simpleName, List<Symbol> candidates) {
            List<Symbol> inCurrentPackage = new ArrayList<>();
            List<Symbol> inSingleImports = new ArrayList<>();
            List<Symbol> inJavaLang = new ArrayList<>();
            List<Symbol> inOnDemandImports = new ArrayList<>();
            List<Symbol> other = new ArrayList<>();

            for (Symbol candidate : candidates) {
                context.poll();
                String qualifiedName = candidate.qualifiedName().orElse("");
                if (!qualifiedName.isBlank() && context.currentPackageName != null
                    && !context.currentPackageName.isBlank()
                    && qualifiedName.startsWith(context.currentPackageName + ".")) {
                    inCurrentPackage.add(candidate);
                } else if (!qualifiedName.isBlank() && singleTypeImportsBySimpleName.containsKey(simpleName)
                    && qualifiedName.equals(singleTypeImportsBySimpleName.get(simpleName).qualifiedTarget())) {
                    inSingleImports.add(candidate);
                } else if (!qualifiedName.isBlank() && qualifiedName.startsWith("java.lang.")) {
                    inJavaLang.add(candidate);
                } else if (isFromOnDemandImport(qualifiedName)) {
                    inOnDemandImports.add(candidate);
                } else {
                    other.add(candidate);
                }
            }

            List<Symbol> level = firstNonEmpty(inCurrentPackage, inSingleImports, inJavaLang, inOnDemandImports, other);
            if (level == null || level.isEmpty())
                return null;

            return level.getFirst();
        }

        private boolean isFromOnDemandImport(String qualifiedName) {
            for (ImportSpec importSpec : onDemandTypeImports) {
                context.poll();
                if (qualifiedName.startsWith(importSpec.ownerName() + "."))
                    return true;
            }
            return false;
        }

        @SafeVarargs
        private static List<Symbol> firstNonEmpty(List<Symbol>... levels) {
            for (List<Symbol> level : levels) {
                if (!level.isEmpty())
                    return level;
            }
            return null;
        }

        private static boolean isMethodNameReference(SyntaxNode node) {
            var parent = node.parent();
            if (parent.isEmpty())
                return false;
            if (!JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(parent.get().kind().id()))
                return false;
            SyntaxNode parentNode = parent.get();
            for (SyntaxNode child : parentNode.children()) {
                if (JavaSyntaxKinds.ARGUMENT_LIST.id().equals(child.kind().id()))
                    return false;
                if (child == node)
                    return true;
            }
            return false;
        }

        private boolean isResolvableType(String qualifiedTypeName) {
            if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
                return false;
            if (localQualifiedTypeNames.contains(qualifiedTypeName))
                return true;
            if (projectIndex != null && !projectIndex.lookupQualifiedName(qualifiedTypeName).isEmpty())
                return true;
            return availableQualifiedTypeNames.contains(qualifiedTypeName);
        }

        private boolean isResolvablePackagePrefix(String packagePrefix) {
            if (packagePrefix == null || packagePrefix.isBlank())
                return false;

            if (projectIndex != null && projectIndex.containsPackage(packagePrefix))
                return true;
            for (String qualifiedType : availableQualifiedTypeNames) {
                context.poll();
                if (qualifiedType.startsWith(packagePrefix + "."))
                    return true;
            }
            return false;
        }

        private Symbol typeSymbolForQualifiedName(
            String simpleName,
            String qualifiedName,
            SyntaxNode declarationOrUsageSite
        ) {
            if (projectIndex != null) {
                List<JavaProjectSemanticIndex.SymbolDescriptor> projectMatches = projectIndex
                    .lookupQualifiedName(qualifiedName).stream()
                    .filter(symbol -> isTypeSymbol(symbol.kind()))
                    .toList();
                if (!projectMatches.isEmpty()) {
                    JavaProjectSemanticIndex.SymbolDescriptor match = projectMatches.getFirst();
                    return new SimpleSymbol(
                        match.descriptor() == null ? JavaSymbolIds.type(qualifiedName) : match.descriptor().id(),
                        match.kind(), match.simpleName(), match.qualifiedName(), null);
                }
            }
            return new SimpleSymbol(JavaSymbolIds.type(qualifiedName), JavaSymbolKind.CLASS, simpleName, qualifiedName,
                null);
        }

        private SyntheticMemberSymbol syntheticProjectMemberSymbol(
            JavaProjectSemanticIndex.SymbolDescriptor symbol,
            Type valueType,
            List<Type> parameterTypes
        ) {
            String qualifiedName = symbol.qualifiedName();
            if (qualifiedName != null && symbol.signature() != null && !qualifiedName.endsWith(symbol.signature())) {
                qualifiedName = qualifiedName + symbol.signature();
            }

            return new SyntheticMemberSymbol(
                symbol.descriptor() == null ? null : symbol.descriptor().id(),
                symbol.kind(),
                symbol.simpleName(),
                qualifiedName,
                null,
                valueType,
                parameterTypes,
                symbol.isStatic());
        }

        private List<Type> parameterTypesFromProjectSignature(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            SymbolSignature structured = symbol.structuredSignature();
            if (structured != null)
                return structured.parameters().stream().map(parameter -> typeFromSignatureText(
                    parameter.type().displayName() + (parameter.varargs() ? "[]" : ""))).toList();
            String signature = symbol.signature();
            if (signature == null || signature.isBlank() || "()".equals(signature))
                return List.of();

            if (!signature.startsWith("(") || !signature.endsWith(")"))
                return List.of();

            String content = signature.substring(1, signature.length() - 1).trim();
            if (content.isEmpty())
                return List.of();

            List<Type> result = new ArrayList<>();
            for (String part : splitSignatureTypes(content)) {
                context.poll();
                String text = part.trim();
                if (text.isEmpty())
                    continue;
                result.add(typeFromSignatureText(text));
            }
            return List.copyOf(result);
        }

        private List<String> splitSignatureTypes(String content) {
            List<String> parts = new ArrayList<>();
            var current = new StringBuilder();
            int genericDepth = 0;
            for (int index = 0; index < content.length(); index++) {
                context.poll();
                char ch = content.charAt(index);
                if (ch == '<') {
                    genericDepth++;
                } else if (ch == '>') {
                    genericDepth = Math.max(0, genericDepth - 1);
                } else if (ch == ',' && genericDepth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
            }
            parts.add(current.toString());
            return List.copyOf(parts);
        }

        private Type typeFromSignatureText(String text) {
            return typeFromTypeText(text, null);
        }

        private Type typeFromTypeText(String text, @Nullable SyntaxNode usageSite) {
            text = text.trim();
            if (text.isBlank())
                return new JavaTypes.UnknownType("<unknown>");
            if ("?".equals(text))
                return new JavaTypes.WildcardType(new JavaTypes.DeclaredType("java.lang.Object", List.of()), null);
            if ("void".equals(text))
                return new JavaTypes.VoidType();
            if (text.startsWith("? extends "))
                return new JavaTypes.WildcardType(typeFromTypeText(text.substring(10), usageSite), null);
            if (text.startsWith("? super "))
                return new JavaTypes.WildcardType(null, typeFromTypeText(text.substring(8), usageSite));
            if (text.startsWith("?extends"))
                return new JavaTypes.WildcardType(typeFromTypeText(text.substring(8), usageSite), null);
            if (text.startsWith("?super"))
                return new JavaTypes.WildcardType(null, typeFromTypeText(text.substring(6), usageSite));
            if (text.endsWith("[]"))
                return new JavaTypes.ArrayType(typeFromTypeText(text.substring(0, text.length() - 2), usageSite));

            int typeArgsStart = findTopLevelTypeArgumentsStart(text);
            if (typeArgsStart > 0 && text.endsWith(">")) {
                String rawText = text.substring(0, typeArgsStart).trim();
                String argsText = text.substring(typeArgsStart + 1, text.length() - 1).trim();
                List<Type> typeArguments = new ArrayList<>();
                for (String part : splitTopLevelTypeArguments(argsText)) {
                    context.poll();
                    String argumentText = part.trim();
                    if (!argumentText.isEmpty()) {
                        typeArguments.add(typeFromTypeText(argumentText, usageSite));
                    }
                }
                String qualifiedRaw = resolveQualifiedTypeName(rawText, usageSite);
                String declaredName = qualifiedRaw == null || qualifiedRaw.isBlank() ? rawText : qualifiedRaw;
                if (declaredName.isBlank())
                    return new JavaTypes.UnknownType("<unknown>");
                return new JavaTypes.DeclaredType(declaredName, typeArguments);
            }

            if (Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return new JavaTypes.PrimitiveType(text);
            if (usageSite != null && isLexicallyDeclaredTypeVariable(text, usageSite))
                return new JavaTypes.TypeVariableType(text);
            if (isTypeVariableNameInScope(text))
                return new JavaTypes.TypeVariableType(text);

            String qualified = resolveQualifiedTypeName(text, usageSite);
            if (isLikelyTypeVariableName(text)
                && (qualified == null || qualified.isBlank() || Objects.equals(qualified, text)))
                return new JavaTypes.TypeVariableType(text);
            String declaredName = qualified == null || qualified.isBlank() ? text : qualified;
            if (declaredName.isBlank())
                return new JavaTypes.UnknownType("<unknown>");
            return new JavaTypes.DeclaredType(declaredName, List.of());
        }

        private int findTopLevelTypeArgumentsStart(String text) {
            int depth = 0;
            for (int index = 0; index < text.length(); index++) {
                context.poll();
                char ch = text.charAt(index);
                if (ch == '<') {
                    if (depth == 0)
                        return index;
                    depth++;
                } else if (ch == '>') {
                    depth = Math.max(0, depth - 1);
                }
            }
            return -1;
        }

        private List<String> splitTopLevelTypeArguments(String text) {
            List<String> parts = new ArrayList<>();
            var current = new StringBuilder();
            int depth = 0;
            for (int index = 0; index < text.length(); index++) {
                context.poll();
                char ch = text.charAt(index);
                if (ch == '<') {
                    depth++;
                } else if (ch == '>') {
                    depth = Math.max(0, depth - 1);
                } else if (ch == ',' && depth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return List.copyOf(parts);
        }

        private boolean isTypeVariableNameInScope(String name) {
            for (Symbol symbol : context.allDeclaredSymbols()) {
                context.poll();
                if (symbol.kind() == JavaSymbolKind.TYPE_PARAMETER && name.equals(symbol.simpleName()))
                    return true;
            }
            return false;
        }

        private static boolean isSelectorNameExpression(SyntaxNode node) {
            var parent = node.parent();
            if (parent.isEmpty())
                return false;
            String parentKindId = parent.get().kind().id();
            if (!JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(parentKindId)
                && !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(parentKindId))
                return false;
            return selectorNameNode(parent.get()) == node;
        }

        private static @Nullable SyntaxNode selectorNameNode(SyntaxNode node) {
            for (int index = node.children().size() - 1; index >= 0; index--) {
                SyntaxNode child = node.children().get(index);
                if (JavaSyntaxKinds.ARGUMENT_LIST.id().equals(child.kind().id()))
                    continue;
                if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(child.kind().id()))
                    return child;
            }
            return null;
        }

        private static @Nullable SyntaxNode explicitReceiver(SyntaxNode node) {
            boolean sawDot = false;
            for (SyntaxNode child : node.children()) {
                if (child instanceof SyntaxToken token
                    && JavaSyntaxKinds.tokenKind(JavaTokenType.DOT).id().equals(token.kind().id())) {
                    sawDot = true;
                    break;
                }
                if (JavaSyntaxKinds.ARGUMENT_LIST.id().equals(child.kind().id()))
                    break;
            }
            if (!sawDot)
                return null;

            for (SyntaxNode child : node.children()) {
                if (isExpressionNode(child))
                    return child;
            }
            return null;
        }

        private static boolean isNumericType(Type type) {
            String simple = simpleTypeName(type.displayName());
            return type.kind() == JavaTypes.Kind.PRIMITIVE
                && Set.of("byte", "short", "char", "int", "long", "float", "double").contains(simple);
        }

        private static boolean isStringLike(Type type) {
            return type.kind() == JavaTypes.Kind.DECLARED && "String".equals(simpleTypeName(type.displayName()));
        }

        private static int numericRank(String primitive) {
            return switch (primitive) {
                case "byte" -> 0;
                case "short", "char" -> 1;
                case "int" -> 2;
                case "long" -> 3;
                case "float" -> 4;
                case "double" -> 5;
                default -> -1;
            };
        }

        private static Type promoteNumeric(Type left, Type right) {
            int rank = Math.max(numericRank(simpleTypeName(left.displayName())),
                numericRank(simpleTypeName(right.displayName())));
            return switch (rank) {
                case 5 -> new JavaTypes.PrimitiveType("double");
                case 4 -> new JavaTypes.PrimitiveType("float");
                case 3 -> new JavaTypes.PrimitiveType("long");
                default -> new JavaTypes.PrimitiveType("int");
            };
        }

        private static List<Symbol> uniqueByQualifiedName(List<Symbol> symbols) {
            Map<String, Symbol> deduped = new LinkedHashMap<>();
            for (Symbol symbol : symbols) {
                String key = symbol.qualifiedName().orElse(symbol.simpleName());
                deduped.putIfAbsent(key, symbol);
            }
            return List.copyOf(deduped.values());
        }

        private record SourceTypeParameterInfo(int index, List<Type> bounds) {
            private Type bound() {
                return bounds.isEmpty()
                    ? new JavaTypes.DeclaredType("java.lang.Object", List.of())
                    : bounds.getFirst();
            }
        }

        private record MemberLookup(@Nullable String ownerQualifiedName, boolean staticAccess) {
        }

        private enum CallableKind {
            METHOD,
            CONSTRUCTOR
        }

        private record MemberCandidate(
            Symbol symbol,
            String ownerQualifiedName,
            boolean staticMember,
            Type valueType,
            List<Type> parameterTypes,
            boolean variableArity
        ) {
            private MemberCandidate(
                Symbol symbol,
                String ownerQualifiedName,
                boolean staticMember,
                Type valueType,
                List<Type> parameterTypes
            ) {
                this(symbol, ownerQualifiedName, staticMember, valueType, parameterTypes, false);
            }

        }
    }

    private static final class TypeResolver {
        private static final Type UNKNOWN_TYPE = new JavaTypes.UnknownType("<unknown>");
        private static final Type BOOLEAN_TYPE = new JavaTypes.PrimitiveType("boolean");
        private static final Set<String> NUMERIC_PRIMITIVES = Set.of("byte", "short", "char", "int", "long", "float",
            "double");

        private final JavaDeclarationAnalysis.Context context;
        private final @Nullable JavaSymbolIndex projectIndex;
        private final Set<String> localQualifiedTypeNames;
        private final Set<String> availableQualifiedTypeNames;
        private final Map<String, ImportSpec> singleTypeImportsBySimpleName = new LinkedHashMap<>();
        private final List<ImportSpec> onDemandTypeImports = new ArrayList<>();
        private final Map<SyntaxNode, Type> cache = new IdentityHashMap<>();
        private final Set<SyntaxNode> contextualInferenceInProgress = Collections
            .newSetFromMap(new IdentityHashMap<>());
        private final Map<String, List<String>> indexedTypeParameterNames = new HashMap<>();
        private final Map<String, List<JavaTypes.DeclaredType>> indexedDirectSuperTypes = new HashMap<>();

        private TypeResolver(JavaDeclarationAnalysis.Context context) {
            this.context = context;
            this.projectIndex = context.projectIndex;

            Set<String> qualified = new HashSet<>();
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                symbol.qualifiedName().ifPresent(qualified::add);
            }
            this.localQualifiedTypeNames = Set.copyOf(qualified);

            this.availableQualifiedTypeNames = projectIndex != null
                ? projectIndex.declaredQualifiedNames()
                : loadJdkQualifiedTypeNames(context.activityCheck);
            collectImportsFromRootScope();
        }

        private void resolveCompilationUnit(SyntaxNode root) {
            visit(root);
        }

        private void visit(SyntaxNode node) {
            String kindId = node.kind().id();

            if (JavaSyntaxKinds.TYPE_REFERENCE.id().equals(kindId)
                || JavaSyntaxKinds.INTERSECTION_TYPE_REFERENCE.id().equals(kindId)
                || JavaSyntaxKinds.UNION_TYPE_REFERENCE.id().equals(kindId)) {
                resolveTypeReference(node);
            }

            if (EXPRESSION_KIND_IDS.contains(kindId)) {
                inferType(node);
            }

            for (SyntaxNode child : node.children()) {
                context.poll();
                visit(child);
            }
        }

        private void resolveTypeReference(SyntaxNode typeRefNode) {
            if (JavaSyntaxKinds.INTERSECTION_TYPE_REFERENCE.id().equals(typeRefNode.kind().id())
                || JavaSyntaxKinds.UNION_TYPE_REFERENCE.id().equals(typeRefNode.kind().id())) {
                context.type(typeRefNode, UNKNOWN_TYPE);
                return;
            }
            Type type = typeFromTypeReference(typeRefNode);
            context.type(typeRefNode, type);
        }

        private Type inferType(SyntaxNode node) {
            Type cached = cache.get(node);
            if (cached != null)
                return cached;

            Type type = switch (node.kind().id()) {
                case "JAVA_LITERAL_EXPRESSION" -> inferLiteralType(node);
                case "JAVA_NAME_EXPRESSION", "JAVA_FIELD_ACCESS_EXPRESSION" -> inferReferenceType(node);
                case "JAVA_ASSIGNMENT_EXPRESSION" -> inferAssignmentType(node);
                case "JAVA_BINARY_EXPRESSION" -> inferBinaryType(node);
                case "JAVA_METHOD_INVOCATION_EXPRESSION" -> inferMethodInvocationType(node);
                case "JAVA_CLASS_INSTANCE_CREATION_EXPRESSION" -> inferClassInstanceCreationType(node);
                case "JAVA_ARRAY_CREATION_EXPRESSION" -> inferArrayCreationType(node);
                case "JAVA_CLASS_LITERAL_EXPRESSION" -> inferClassLiteralType(node);
                case "JAVA_ARRAY_ACCESS_EXPRESSION" -> inferArrayAccessType(node);
                case "JAVA_CAST_EXPRESSION" -> inferCastType(node);
                case "JAVA_CONDITIONAL_EXPRESSION" -> inferConditionalType(node);
                case "JAVA_SWITCH_EXPRESSION" -> inferSwitchExpressionType(node);
                case "JAVA_ARRAY_INITIALIZER_EXPRESSION" -> UNKNOWN_TYPE;
                default -> inferFromChildren(node);
            };

            cache.put(node, type);
            context.type(node, type);
            return type;
        }

        private Type inferLiteralType(SyntaxNode literalExpression) {
            List<SyntaxToken> tokens = leafTokens(literalExpression);
            for (SyntaxToken token : tokens) {
                context.poll();
                String kindId = token.kind().id();
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.BOOLEAN_LITERAL).id().equals(kindId))
                    return BOOLEAN_TYPE;
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_INT_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_HEXADECIMAL_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_BINARY_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_OCTAL_LITERAL).id().equals(kindId))
                    return numericLiteralType(token.text(), false);
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_FLOATING_POINT_LITERAL).id().equals(kindId))
                    return numericLiteralType(token.text(), true);
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.CHARACTER_LITERAL).id().equals(kindId))
                    return new JavaTypes.PrimitiveType("char");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.STRING_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.TEXT_BLOCK_LITERAL).id().equals(kindId))
                    return new JavaTypes.DeclaredType("java.lang.String", List.of());
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NULL_LITERAL).id().equals(kindId))
                    return UNKNOWN_TYPE;
            }
            return UNKNOWN_TYPE;
        }

        private Type inferReferenceType(SyntaxNode referenceNode) {
            Symbol symbol = context.resolvedSymbol(referenceNode);
            if (symbol == null)
                return UNKNOWN_TYPE;
            Type contextualLambdaType = contextualLambdaReferenceType(referenceNode, symbol);
            if (contextualLambdaType.kind() != JavaTypes.Kind.UNKNOWN)
                return contextualLambdaType;
            return typeOfSymbol(symbol);
        }

        private Type contextualLambdaReferenceType(SyntaxNode referenceNode, Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null
                || !isLambdaParameterNode(declaration))
                return UNKNOWN_TYPE;
            SyntaxNode explicitTypeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (explicitTypeRef != null && !"var".equals(canonicalTypeText(explicitTypeRef)))
                return typeFromTypeReference(explicitTypeRef);
            SyntaxNode lambda = enclosingNode(declaration, JavaSyntaxKinds.LAMBDA_EXPRESSION.id());
            if (lambda == null)
                return UNKNOWN_TYPE;
            SyntaxNode parameterList = directChild(lambda, JavaSyntaxKinds.LAMBDA_PARAMETERS.id());
            if (parameterList == null)
                return UNKNOWN_TYPE;
            List<SyntaxNode> lambdaParameters = parameterList.children().stream()
                .filter(this::isLambdaParameterNode)
                .toList();
            int lambdaParameterIndex = lambdaParameters.indexOf(declaration);
            if (lambdaParameterIndex < 0)
                return UNKNOWN_TYPE;

            FunctionalSignature directSignature = functionalSignature(directContextualTargetType(lambda));
            if (directSignature != null && lambdaParameterIndex < directSignature.parameterTypes().size())
                return unwrapWildcard(directSignature.parameterTypes().get(lambdaParameterIndex));

            SyntaxNode contextualExpression = lambda;
            SyntaxNode castParent = contextualExpression.parent().orElse(null);
            while (castParent != null
                && JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(castParent.kind().id())) {
                context.poll();
                contextualExpression = castParent;
                castParent = contextualExpression.parent().orElse(null);
            }
            if (castParent != null && JavaSyntaxKinds.CAST_EXPRESSION.id().equals(castParent.kind().id())) {
                SyntaxNode typeRef = directChild(castParent, JavaSyntaxKinds.TYPE_REFERENCE.id());
                FunctionalSignature signature = typeRef == null
                    ? null
                    : functionalSignature(typeFromTypeReference(typeRef));
                if (signature != null && lambdaParameterIndex < signature.parameterTypes().size())
                    return unwrapWildcard(signature.parameterTypes().get(lambdaParameterIndex));
            }

            SyntaxNode argumentList = lambda.parent().orElse(null);
            while (argumentList != null && !JavaSyntaxKinds.ARGUMENT_LIST.id().equals(argumentList.kind().id())) {
                context.poll();
                argumentList = argumentList.parent().orElse(null);
            }
            if (argumentList == null)
                return UNKNOWN_TYPE;
            SyntaxNode invocation = argumentList.parent().orElse(null);
            Symbol callable = invocation == null ? null : context.resolvedSymbol(invocation);
            if (callable == null
                || callable.kind() != JavaSymbolKind.METHOD
                    && callable.kind() != JavaSymbolKind.CONSTRUCTOR)
                return UNKNOWN_TYPE;

            int argumentIndex = expressionChildIndex(argumentList, lambda);
            List<Type> parameterTypes = specializedCallableParameterTypes(
                invocation, argumentList, lambda, callable);
            if (argumentIndex < 0 || argumentIndex >= parameterTypes.size())
                return UNKNOWN_TYPE;
            FunctionalSignature signature = functionalSignature(parameterTypes.get(argumentIndex));
            if (signature == null || lambdaParameterIndex >= signature.parameterTypes().size())
                return UNKNOWN_TYPE;
            return unwrapWildcard(signature.parameterTypes().get(lambdaParameterIndex));
        }

        private boolean isLambdaParameterNode(SyntaxNode node) {
            String kindId = node.kind().id();
            return JavaSyntaxKinds.LAMBDA_PARAMETER.id().equals(kindId)
                || JavaSyntaxKinds.PARAMETER.id().equals(kindId)
                    && enclosingNode(node, JavaSyntaxKinds.LAMBDA_EXPRESSION.id()) != null;
        }

        private List<Type> specializedCallableParameterTypes(
            SyntaxNode invocation,
            SyntaxNode argumentList,
            SyntaxNode contextualArgument,
            Symbol callable
        ) {
            List<Type> parameterTypes = methodParameterTypes(callable);
            if (parameterTypes.isEmpty())
                return parameterTypes;
            Map<String, Type> substitutions = new LinkedHashMap<>();
            Type receiverType = invocationReceiverType(invocation, callable);
            bindOwnerTypeArguments(callable, receiverType, substitutions);

            int argumentIndex = 0;
            for (SyntaxNode argument : argumentList.children()) {
                context.poll();
                if (!isExpressionNode(argument))
                    continue;
                if (argument != contextualArgument && argumentIndex < parameterTypes.size()) {
                    bindTypeVariables(
                        parameterTypes.get(argumentIndex), inferType(argument), substitutions);
                }
                argumentIndex++;
            }
            Type contextualTarget = contextualInvocationTargetType(invocation);
            if (contextualTarget.kind() != JavaTypes.Kind.UNKNOWN) {
                bindTypeVariables(typeOfSymbol(callable), contextualTarget, substitutions);
            }
            return parameterTypes.stream()
                .map(type -> substituteTypeVariables(type, substitutions))
                .toList();
        }

        private Type contextualInvocationTargetType(SyntaxNode invocation) {
            if (!contextualInferenceInProgress.add(invocation))
                return UNKNOWN_TYPE;
            try {
                Type directTarget = directContextualTargetType(invocation);
                if (directTarget.kind() != JavaTypes.Kind.UNKNOWN)
                    return directTarget;
                SyntaxNode outerArgumentList = invocation.parent().orElse(null);
                if (outerArgumentList == null
                    || !JavaSyntaxKinds.ARGUMENT_LIST.id().equals(outerArgumentList.kind().id()))
                    return UNKNOWN_TYPE;
                SyntaxNode outerInvocation = outerArgumentList.parent().orElse(null);
                Symbol outerCallable = outerInvocation == null ? null : context.resolvedSymbol(outerInvocation);
                if (outerCallable == null)
                    return UNKNOWN_TYPE;

                int argumentIndex = expressionChildIndex(outerArgumentList, invocation);
                List<Type> outerParameters = specializedCallableParameterTypes(
                    outerInvocation, outerArgumentList, invocation, outerCallable);
                return argumentIndex < 0 || argumentIndex >= outerParameters.size()
                    ? UNKNOWN_TYPE
                    : outerParameters.get(argumentIndex);
            } finally {
                contextualInferenceInProgress.remove(invocation);
            }
        }

        private int expressionChildIndex(SyntaxNode parent, SyntaxNode target) {
            int index = 0;
            for (SyntaxNode child : parent.children()) {
                context.poll();
                if (!isExpressionNode(child))
                    continue;
                if (child == target)
                    return index;
                index++;
            }
            return -1;
        }

        private @Nullable SyntaxNode enclosingNode(SyntaxNode node, String kindId) {
            SyntaxNode current = node.parent().orElse(null);
            while (current != null) {
                context.poll();
                if (kindId.equals(current.kind().id()))
                    return current;
                current = current.parent().orElse(null);
            }
            return null;
        }

        private Type inferClassLiteralType(SyntaxNode classLiteralExpression) {
            SyntaxNode typeRef = directChild(classLiteralExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type literalType = typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
            return new JavaTypes.DeclaredType("java.lang.Class", List.of(literalType));
        }

        private Type inferArrayCreationType(SyntaxNode arrayCreationExpression) {
            SyntaxNode typeRef = directChild(arrayCreationExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type type = typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
            for (SyntaxNode child : arrayCreationExpression.children()) {
                context.poll();
                if (child instanceof SyntaxToken token
                    && JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_BRACKET).id().equals(token.kind().id())) {
                    type = new JavaTypes.ArrayType(type);
                }
            }
            return type;
        }

        private Type inferArrayAccessType(SyntaxNode arrayAccessExpression) {
            for (SyntaxNode child : arrayAccessExpression.children()) {
                context.poll();
                if (!isExpressionNode(child))
                    continue;
                Type receiverType = inferType(child);
                return receiverType instanceof JavaTypes.ArrayType array ? array.componentType() : UNKNOWN_TYPE;
            }
            return UNKNOWN_TYPE;
        }

        private Type inferMethodInvocationType(SyntaxNode invocationNode) {
            Symbol resolved = context.resolvedSymbol(invocationNode);
            if (resolved != null && resolved.kind() == JavaSymbolKind.METHOD) {
                Type returnType = typeOfSymbol(resolved);
                return specializeMethodReturnType(invocationNode, resolved, returnType);
            }
            if (resolved != null && resolved.kind() == JavaSymbolKind.CONSTRUCTOR)
                return typeOfSymbol(resolved);
            return UNKNOWN_TYPE;
        }

        private Type specializeMethodReturnType(SyntaxNode invocationNode, Symbol methodSymbol, Type rawReturnType) {
            Map<String, Type> substitutions = new LinkedHashMap<>();
            Type receiverType = invocationReceiverType(invocationNode, methodSymbol);
            if (receiverType.kind() != JavaTypes.Kind.UNKNOWN) {
                bindOwnerTypeArguments(methodSymbol, receiverType, substitutions);
                bindMethodTypeArguments(invocationNode, methodSymbol, substitutions);
                Type partiallySpecialized = substituteTypeVariables(rawReturnType, substitutions);
                Map<String, Type> contextualSubstitutions = new LinkedHashMap<>();
                bindContextualReturnType(invocationNode, partiallySpecialized, contextualSubstitutions);
                Type specialized = substituteTypeVariables(partiallySpecialized, contextualSubstitutions);
                if ((rawReturnType.kind() == JavaTypes.Kind.TYPE_VARIABLE
                    || rawReturnType.kind() == JavaTypes.Kind.DECLARED
                        && isLikelyTypeVariableName(rawReturnType.displayName()))
                    && (specialized.kind() == JavaTypes.Kind.TYPE_VARIABLE
                        || specialized.kind() == JavaTypes.Kind.WILDCARD
                        || specialized.kind() == JavaTypes.Kind.DECLARED
                            && isLikelyTypeVariableName(specialized.displayName()))) {
                    String owner = ownerQualifiedName(methodSymbol).orElse("");
                    if (isSelfBoundOwnerTypeParameter(owner, rawReturnType.displayName()))
                        return receiverType;
                }
                return specialized;
            }
            bindMethodTypeArguments(invocationNode, methodSymbol, substitutions);
            Type partiallySpecialized = substituteTypeVariables(rawReturnType, substitutions);
            Map<String, Type> contextualSubstitutions = new LinkedHashMap<>();
            bindContextualReturnType(invocationNode, partiallySpecialized, contextualSubstitutions);
            return substituteTypeVariables(partiallySpecialized, contextualSubstitutions);
        }

        private void bindContextualReturnType(
            SyntaxNode invocationNode,
            Type rawReturnType,
            Map<String, Type> substitutions
        ) {
            Type contextualTarget = directContextualTargetType(invocationNode);
            if (contextualTarget.kind() == JavaTypes.Kind.UNKNOWN) {
                contextualTarget = contextualInvocationTargetType(invocationNode);
            }
            if (contextualTarget.kind() != JavaTypes.Kind.UNKNOWN) {
                bindTypeVariables(rawReturnType, contextualTarget, substitutions);
            }
        }

        private Type directContextualTargetType(SyntaxNode expression) {
            SyntaxNode contextualExpression = expression;
            SyntaxNode parent = contextualExpression.parent().orElse(null);
            while (parent != null && (JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(parent.kind().id())
                || JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id().equals(parent.kind().id()))) {
                context.poll();
                contextualExpression = parent;
                parent = contextualExpression.parent().orElse(null);
            }
            if (parent == null)
                return UNKNOWN_TYPE;

            if (JavaSyntaxKinds.LAMBDA_BODY.id().equals(parent.kind().id())) {
                SyntaxNode lambda = parent.parent().orElse(null);
                FunctionalSignature signature = lambda == null
                    ? null
                    : functionalSignature(contextualInvocationTargetType(lambda));
                return signature == null ? UNKNOWN_TYPE : unwrapWildcard(signature.returnType());
            }

            if (JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id().equals(parent.kind().id())) {
                for (SyntaxNode child : parent.children()) {
                    context.poll();
                    if (!isExpressionNode(child))
                        continue;
                    if (child == contextualExpression)
                        break;
                    return inferType(child);
                }
            }

            if (JavaSyntaxKinds.CAST_EXPRESSION.id().equals(parent.kind().id())) {
                SyntaxNode typeRef = directChild(parent, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return typeFromTypeReference(typeRef);
            }

            if (JavaSyntaxKinds.RETURN_STATEMENT.id().equals(parent.kind().id())) {
                SyntaxNode method = enclosingNode(parent, JavaSyntaxKinds.METHOD_DECLARATION.id());
                SyntaxNode typeRef = method == null
                    ? null
                    : directChild(method, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return typeFromTypeReference(typeRef);
            }

            SyntaxNode declaration = enclosingNode(contextualExpression, JavaSyntaxKinds.VARIABLE_DECLARATOR.id());
            SyntaxNode initializerExpression = contextualExpression;
            if (declaration != null && declaration.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .anyMatch(child -> child == initializerExpression)) {
                SyntaxNode current = declaration.parent().orElse(null);
                while (current != null) {
                    context.poll();
                    SyntaxNode typeRef = directChild(current, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null)
                        return "var".equals(canonicalTypeText(typeRef))
                            ? UNKNOWN_TYPE
                            : typeFromTypeReference(typeRef);
                    if (JavaSyntaxKinds.BLOCK.id().equals(current.kind().id()))
                        break;
                    current = current.parent().orElse(null);
                }
            }
            return UNKNOWN_TYPE;
        }

        private Type invocationReceiverType(SyntaxNode invocationNode, Symbol methodSymbol) {
            if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(invocationNode.kind().id())) {
                SyntaxNode typeRef = directChild(invocationNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null) {
                    Type createdType = typeFromTypeReference(typeRef);
                    if (createdType instanceof JavaTypes.DeclaredType declaredType
                        && declaredType.typeArguments().isEmpty()) {
                        Type contextualType = contextualInvocationTargetType(invocationNode);
                        if (contextualType instanceof JavaTypes.DeclaredType contextualDeclared
                            && sameRawType(declaredType.displayName(), contextualDeclared.displayName()))
                            return contextualDeclared;
                    }
                    return createdType;
                }
            }
            SyntaxNode receiverNode = explicitReceiver(invocationNode);
            if (receiverNode != null)
                return inferType(receiverNode);
            if (isStaticCallable(methodSymbol))
                return UNKNOWN_TYPE;
            Symbol enclosingType = nearestEnclosingTypeSymbol(invocationNode);
            if (enclosingType == null)
                return UNKNOWN_TYPE;
            String qualifiedName = enclosingType.qualifiedName().orElse(null);
            if (qualifiedName == null || qualifiedName.isBlank())
                return UNKNOWN_TYPE;
            List<Type> typeArguments = ownerTypeParameterNames(qualifiedName).stream()
                .map(name -> (Type) new JavaTypes.TypeVariableType(name))
                .toList();
            return new JavaTypes.DeclaredType(qualifiedName, typeArguments);
        }

        private @Nullable Symbol nearestEnclosingTypeSymbol(SyntaxNode usageSite) {
            SyntaxNode current = usageSite.parent().orElse(null);
            while (current != null) {
                context.poll();
                Symbol declared = context.declaredSymbol(current);
                if (declared != null && isTypeSymbol(declared.kind()))
                    return declared;
                current = current.parent().orElse(null);
            }
            return context.enclosingTypeSymbol(usageSite);
        }

        private boolean isStaticCallable(Symbol symbol) {
            if (symbol instanceof SyntheticMemberSymbol synthetic)
                return synthetic.staticMember();
            SyntaxNode declaration = symbol.declaration().orElse(null);
            return declaration != null && hasTokenKind(declaration, JavaTokenType.STATIC_KEYWORD);
        }

        private void bindMethodTypeArguments(
            SyntaxNode invocationNode,
            Symbol methodSymbol,
            Map<String, Type> substitutions
        ) {
            bindExplicitMethodTypeArguments(invocationNode, methodSymbol, substitutions);

            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList == null)
                return;

            List<Type> parameterTypes = methodParameterTypes(methodSymbol);
            if (parameterTypes.isEmpty())
                return;

            int argumentIndex = 0;
            for (SyntaxNode child : argumentList.children()) {
                context.poll();
                if (!isExpressionNode(child))
                    continue;
                int parameterIndex = Math.min(argumentIndex, parameterTypes.size() - 1);
                Type parameterType = parameterTypes.get(parameterIndex);
                Type argumentType = inferType(child);
                if (isVarargsMethod(methodSymbol)
                    && parameterIndex == parameterTypes.size() - 1
                    && parameterType instanceof JavaTypes.ArrayType array
                    && (argumentIndex >= parameterTypes.size()
                        || !(argumentType instanceof JavaTypes.ArrayType))) {
                    parameterType = array.componentType();
                }
                if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(child.kind().id())
                    || JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(child.kind().id())) {
                    bindFunctionalArgumentType(parameterType, child, substitutions);
                } else {
                    bindTypeVariables(parameterType, argumentType, substitutions);
                }
                argumentIndex++;
            }
        }

        private void bindExplicitMethodTypeArguments(
            SyntaxNode invocationNode,
            Symbol methodSymbol,
            Map<String, Type> substitutions
        ) {
            SyntaxNode typeArguments = directChild(invocationNode, JavaSyntaxKinds.TYPE_ARGUMENTS.id());
            if (typeArguments == null)
                return;

            List<Type> explicitTypes = typeArguments.children().stream()
                .filter(child -> JavaSyntaxKinds.TYPE_REFERENCE.id().equals(child.kind().id()))
                .map(this::typeFromTypeReference)
                .toList();
            if (explicitTypes.isEmpty())
                return;

            List<String> parameterNames = methodTypeParameterNames(methodSymbol);
            int count = Math.min(parameterNames.size(), explicitTypes.size());
            for (int index = 0; index < count; index++) {
                context.poll();
                substitutions.put(parameterNames.get(index), explicitTypes.get(index));
            }
        }

        private List<String> methodTypeParameterNames(Symbol methodSymbol) {
            SyntaxNode declaration = methodSymbol.declaration().orElse(null);
            if (declaration != null) {
                List<String> declared = declaredTypeParameterNames(declaration);
                if (!declared.isEmpty())
                    return declared;
            }

            if (methodSymbol instanceof SyntheticMemberSymbol synthetic) {
                LinkedHashSet<String> names = new LinkedHashSet<>();
                collectTypeVariableNames(synthetic.valueType(), names);
                synthetic.parameterTypes().forEach(type -> collectTypeVariableNames(type, names));
                return List.copyOf(names);
            }
            return List.of();
        }

        private void collectTypeVariableNames(Type type, Set<String> out) {
            if (type instanceof JavaTypes.TypeVariableType variable) {
                out.add(variable.displayName());
            } else if (type instanceof JavaTypes.DeclaredType declared) {
                declared.typeArguments().forEach(argument -> collectTypeVariableNames(argument, out));
            } else if (type instanceof JavaTypes.ArrayType array) {
                collectTypeVariableNames(array.componentType(), out);
            } else if (type instanceof JavaTypes.WildcardType wildcard) {
                if (wildcard.upperBound() != null) {
                    collectTypeVariableNames(wildcard.upperBound(), out);
                }
                if (wildcard.lowerBound() != null) {
                    collectTypeVariableNames(wildcard.lowerBound(), out);
                }
            }
        }

        private boolean isVarargsMethod(Symbol methodSymbol) {
            SyntaxNode declaration = methodSymbol.declaration().orElse(null);
            if (declaration != null)
                return hasTokenKind(declaration, JavaTokenType.ELLIPSIS);
            if (!(methodSymbol instanceof SyntheticMemberSymbol synthetic))
                return false;

            String owner = ownerQualifiedName(methodSymbol).orElse(null);
            ClassStub stub = classStub(owner);
            if (stub == null)
                return false;
            String qualifiedName = methodSymbol.qualifiedName().orElse("");
            for (MethodStub method : stub.methods()) {
                context.poll();
                if (!method.name().equals(methodSymbol.simpleName()))
                    continue;
                List<Type> parameterTypes = method.parameters().stream()
                    .map(parameter -> toSemanticType(parameter.type()))
                    .toList();
                if (!qualifiedName.endsWith(method.name() + signatureSuffix(parameterTypes)))
                    continue;
                return (method.modifiers() & Opcodes.ACC_VARARGS) != 0;
            }
            return false;
        }

        private void bindFunctionalArgumentType(
            Type parameterType,
            SyntaxNode argumentNode,
            Map<String, Type> substitutions
        ) {
            FunctionalSignature signature = functionalSignature(
                substituteTypeVariables(parameterType, substitutions));
            if (signature == null)
                return;

            Type resultType = JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(argumentNode.kind().id())
                ? lambdaResultType(argumentNode)
                : methodReferenceResultType(argumentNode, signature);
            if (resultType.kind() != JavaTypes.Kind.UNKNOWN) {
                bindTypeVariables(signature.returnType(), resultType, substitutions);
            }
        }

        private @Nullable FunctionalSignature functionalSignature(Type functionalType) {
            if (!(functionalType instanceof JavaTypes.DeclaredType declared))
                return null;

            String qualifiedName = resolveQualifiedTypeName(eraseTypeArguments(declared.displayName()));
            ClassStub stub = classStub(qualifiedName);
            if (stub == null)
                return null;

            Map<String, Type> functionalSubstitutions = new LinkedHashMap<>();
            int count = Math.min(stub.typeParameters().size(), declared.typeArguments().size());
            for (int index = 0; index < count; index++) {
                context.poll();
                functionalSubstitutions.put(
                    stub.typeParameters().get(index).name(),
                    declared.typeArguments().get(index));
            }

            List<MethodStub> abstractMethods = stub.methods().stream()
                .filter(method -> Modifier.isAbstract(method.modifiers()))
                .filter(method -> !Modifier.isStatic(method.modifiers()))
                .toList();
            if (abstractMethods.size() != 1)
                return null;

            MethodStub method = abstractMethods.getFirst();
            List<Type> parameterTypes = method.parameters().stream()
                .map(parameter -> substituteTypeVariables(
                    toSemanticType(parameter.type()), functionalSubstitutions))
                .toList();
            Type returnType = substituteTypeVariables(
                toSemanticType(method.returnType()), functionalSubstitutions);
            return new FunctionalSignature(parameterTypes, returnType);
        }

        private Type lambdaResultType(SyntaxNode lambda) {
            return lambdaResultType(lambda, this::inferType);
        }

        private Type lambdaResultType(SyntaxNode lambda, Function<SyntaxNode, Type> resolver) {
            SyntaxNode body = directChild(lambda, JavaSyntaxKinds.LAMBDA_BODY.id());
            if (body == null)
                return UNKNOWN_TYPE;
            for (SyntaxNode child : body.children()) {
                context.poll();
                if (isExpressionNode(child))
                    return resolver.apply(child);
            }
            List<Type> results = new ArrayList<>();
            collectLambdaReturnTypes(body, results, resolver);
            return mergeExpressionTypes(results, projectIndex);
        }

        private void collectLambdaReturnTypes(
            SyntaxNode node,
            List<Type> results,
            Function<SyntaxNode, Type> resolver
        ) {
            if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(node.kind().id())
                || isTypeDeclarationSyntaxKind(node.kind().id())
                || JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id().equals(node.kind().id()))
                return;
            if (JavaSyntaxKinds.RETURN_STATEMENT.id().equals(node.kind().id())) {
                directExpressionChildren(node).stream().map(resolver).forEach(results::add);
                return;
            }
            for (SyntaxNode child : node.children()) {
                context.poll();
                collectLambdaReturnTypes(child, results, resolver);
            }
        }

        private Type methodReferenceResultType(SyntaxNode methodReference, FunctionalSignature signature) {
            String ownerText = methodReferenceOwnerText(methodReference);
            if (ownerText == null || ownerText.isBlank())
                return UNKNOWN_TYPE;

            if (hasTokenKind(methodReference, JavaTokenType.NEW_KEYWORD))
                return typeFromTypeReferenceText(ownerText);

            String methodName = methodReferenceMemberName(methodReference);
            if (methodName == null)
                return UNKNOWN_TYPE;

            Symbol resolvedMethod = context.resolvedSymbol(methodReference);
            if (resolvedMethod != null && resolvedMethod.kind() == JavaSymbolKind.METHOD) {
                Type resolvedReturnType = typeOfSymbol(resolvedMethod);
                if (resolvedReturnType.kind() != JavaTypes.Kind.UNKNOWN
                    && !containsTypeVariable(resolvedReturnType))
                    return resolvedReturnType;
            }

            SyntaxNode receiverNode = methodReference.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .findFirst()
                .orElse(null);
            Symbol receiverSymbol = receiverNode == null ? null : context.resolvedSymbol(receiverNode);
            boolean typeReceiver = receiverSymbol != null && isTypeSymbol(receiverSymbol.kind());
            Type receiverType = receiverNode == null ? UNKNOWN_TYPE : inferType(receiverNode);
            String ownerQualifiedName = typeReceiver
                ? receiverSymbol.qualifiedName().orElse(resolveQualifiedTypeName(ownerText))
                : receiverType.kind() == JavaTypes.Kind.DECLARED
                    ? resolveQualifiedTypeName(receiverType.displayName())
                    : resolveQualifiedTypeName(ownerText);
            ClassStub ownerStub = classStub(ownerQualifiedName);
            if (ownerStub == null)
                return UNKNOWN_TYPE;

            for (MethodStub method : ownerStub.methods()) {
                context.poll();
                if (!method.name().equals(methodName))
                    continue;
                boolean isStatic = Modifier.isStatic(method.modifiers());
                int expectedArity = method.parameters().size() + (typeReceiver && !isStatic ? 1 : 0);
                if (expectedArity != signature.parameterTypes().size())
                    continue;

                Map<String, Type> ownerSubstitutions = new LinkedHashMap<>();
                Type actualOwnerType = receiverType;
                if (typeReceiver && !isStatic && !signature.parameterTypes().isEmpty()) {
                    actualOwnerType = unwrapWildcard(signature.parameterTypes().getFirst());
                }
                if (actualOwnerType instanceof JavaTypes.DeclaredType actualDeclared) {
                    int count = Math.min(ownerStub.typeParameters().size(), actualDeclared.typeArguments().size());
                    for (int index = 0; index < count; index++) {
                        context.poll();
                        ownerSubstitutions.put(
                            ownerStub.typeParameters().get(index).name(),
                            actualDeclared.typeArguments().get(index));
                    }
                }
                return substituteTypeVariables(toSemanticType(method.returnType()), ownerSubstitutions);
            }
            return UNKNOWN_TYPE;
        }

        private boolean containsTypeVariable(Type type) {
            return switch (type) {
                case JavaTypes.TypeVariableType ignored -> true;
                case JavaTypes.ArrayType array -> containsTypeVariable(array.componentType());
                case JavaTypes.WildcardType wildcard ->
                    wildcard.upperBound() != null && containsTypeVariable(wildcard.upperBound())
                        || wildcard.lowerBound() != null && containsTypeVariable(wildcard.lowerBound());
                case JavaTypes.DeclaredType declared -> declared.typeArguments().stream()
                    .anyMatch(this::containsTypeVariable);
                default -> false;
            };
        }

        private @Nullable ClassStub classStub(@Nullable String qualifiedName) {
            if (qualifiedName == null)
                return null;
            Map<String, ClassStub> stubs = projectIndex == null
                ? loadJdkClassStubsByQualifiedName(context.activityCheck)
                : projectIndex.classStubsByQualifiedName();
            return stubs.get(qualifiedName);
        }

        private Type unwrapWildcard(Type type) {
            Type current = type;
            while (current instanceof JavaTypes.WildcardType wildcard) {
                context.poll();
                if (wildcard.lowerBound() != null) {
                    current = wildcard.lowerBound();
                } else if (wildcard.upperBound() != null) {
                    current = wildcard.upperBound();
                } else
                    return UNKNOWN_TYPE;
            }
            return current;
        }

        private @Nullable String methodReferenceOwnerText(SyntaxNode methodReference) {
            var builder = new StringBuilder();
            for (SyntaxToken token : leafTokens(methodReference)) {
                context.poll();
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.DOUBLE_COLON).id().equals(token.kind().id()))
                    break;
                if (!isTriviaToken(token) && !isMissingTokenKind(token.kind().id())) {
                    builder.append(JavaUnicodeEscapes.translate(token.text()));
                }
            }
            return builder.isEmpty() ? null : builder.toString();
        }

        private @Nullable String methodReferenceMemberName(SyntaxNode methodReference) {
            boolean afterSeparator = false;
            String name = null;
            for (SyntaxToken token : leafTokens(methodReference)) {
                context.poll();
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.DOUBLE_COLON).id().equals(token.kind().id())) {
                    afterSeparator = true;
                    continue;
                }
                if (afterSeparator && !isTriviaToken(token) && !isMissingTokenKind(token.kind().id())) {
                    name = JavaUnicodeEscapes.translate(token.text());
                }
            }
            return name;
        }

        private List<Type> methodParameterTypes(Symbol methodSymbol) {
            if (methodSymbol instanceof SyntheticMemberSymbol synthetic)
                return synthetic.parameterTypes();

            SyntaxNode declaration = methodSymbol.declaration().orElse(null);
            SyntaxNode parameterList = declaration == null
                ? null
                : JavaSourceSymbolIds.parameterList(declaration);
            if (parameterList == null)
                return List.of();

            List<Type> parameterTypes = new ArrayList<>();
            for (SyntaxNode child : parameterList.children()) {
                context.poll();
                if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id())
                    && !JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                    continue;
                SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                Type parameterType = typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
                if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                    parameterType = new JavaTypes.ArrayType(parameterType);
                }
                parameterTypes.add(parameterType);
            }
            return List.copyOf(parameterTypes);
        }

        private void bindTypeVariables(Type parameterType, Type argumentType, Map<String, Type> substitutions) {
            if (parameterType instanceof JavaTypes.TypeVariableType variable) {
                Type boundArgument = argumentType instanceof JavaTypes.WildcardType wildcard
                    ? wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound()
                    : argumentType;
                if (boundArgument != null && boundArgument.kind() != JavaTypes.Kind.UNKNOWN) {
                    Type existing = substitutions.get(variable.displayName());
                    if (existing == null || isSelfTypeVariable(existing, variable.displayName())) {
                        substitutions.put(variable.displayName(), boundArgument);
                    }
                }
                return;
            }
            if (parameterType instanceof JavaTypes.WildcardType wildcard) {
                Type bound = wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound();
                if (bound != null) {
                    bindTypeVariables(bound, argumentType, substitutions);
                }
                return;
            }
            if (parameterType instanceof JavaTypes.ArrayType parameterArray
                && argumentType instanceof JavaTypes.ArrayType argumentArray) {
                bindTypeVariables(parameterArray.componentType(), argumentArray.componentType(), substitutions);
                return;
            }
            if (parameterType instanceof JavaTypes.DeclaredType parameterDeclared
                && argumentType instanceof JavaTypes.DeclaredType argumentDeclared) {
                if (parameterDeclared.typeArguments().isEmpty())
                    return;
                if (!sameRawType(parameterDeclared.displayName(), argumentDeclared.displayName())) {
                    String parameterName = resolveQualifiedTypeName(parameterDeclared.displayName());
                    argumentDeclared = declaredViewAs(argumentDeclared,
                        parameterName == null ? parameterDeclared.displayName() : parameterName, new HashSet<>());
                    if (argumentDeclared == null)
                        return;
                }
                int count = Math.min(parameterDeclared.typeArguments().size(), argumentDeclared.typeArguments().size());
                for (int index = 0; index < count; index++) {
                    context.poll();
                    bindTypeVariables(
                        parameterDeclared.typeArguments().get(index),
                        argumentDeclared.typeArguments().get(index),
                        substitutions);
                }
            }
        }

        private void bindOwnerTypeArguments(Symbol methodSymbol, Type receiverType, Map<String, Type> substitutions) {
            if (!(receiverType instanceof JavaTypes.DeclaredType receiverDeclared))
                return;

            String ownerQualifiedName = ownerQualifiedName(methodSymbol).orElse(null);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return;

            List<String> ownerTypeParameters = ownerTypeParameterNames(ownerQualifiedName);
            JavaTypes.DeclaredType ownerView = declaredViewAs(
                receiverDeclared, ownerQualifiedName, new HashSet<>());
            List<Type> actualTypeArguments = ownerView == null
                ? declaredTypeArguments(receiverType)
                : ownerView.typeArguments();
            int count = Math.min(ownerTypeParameters.size(), actualTypeArguments.size());
            for (int index = 0; index < count; index++) {
                context.poll();
                Type actualTypeArgument = effectiveOwnerTypeArgument(
                    ownerQualifiedName, index, actualTypeArguments.get(index));
                substitutions.putIfAbsent(ownerTypeParameters.get(index), actualTypeArgument);
            }
        }

        private Type effectiveOwnerTypeArgument(String ownerQualifiedName, int index, Type actualTypeArgument) {
            if (!(actualTypeArgument instanceof JavaTypes.WildcardType wildcard))
                return actualTypeArgument;

            Type explicitUpperBound = wildcard.upperBound();
            if (explicitUpperBound != null
                && !"java.lang.Object".equals(explicitUpperBound.displayName())
                && !"Object".equals(explicitUpperBound.displayName()))
                return explicitUpperBound;

            ClassStub ownerStub = classStub(ownerQualifiedName);
            if (ownerStub != null && index < ownerStub.typeParameters().size()) {
                TypeParameter parameter = ownerStub.typeParameters().get(index);
                if (!parameter.bounds().isEmpty())
                    return toSemanticType(parameter.bounds().getFirst());
            }
            Type sourceBound = sourceOwnerTypeParameterBound(ownerQualifiedName, index);
            if (sourceBound != null)
                return sourceBound;
            return explicitUpperBound == null ? UNKNOWN_TYPE : explicitUpperBound;
        }

        private @Nullable Type sourceOwnerTypeParameterBound(String ownerQualifiedName, int index) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!ownerQualifiedName.equals(symbol.qualifiedName().orElse(null)))
                    continue;
                SyntaxNode declaration = symbol.declaration().orElse(null);
                SyntaxNode typeParameters = declaration == null
                    ? null
                    : directChild(declaration, JavaSyntaxKinds.TYPE_PARAMETERS.id());
                if (typeParameters == null)
                    return null;

                int currentIndex = 0;
                for (SyntaxNode child : typeParameters.children()) {
                    context.poll();
                    if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(child.kind().id()))
                        continue;
                    if (currentIndex++ != index)
                        continue;
                    SyntaxNode boundNode = directChild(child, JavaSyntaxKinds.TYPE_BOUND.id());
                    SyntaxNode bound = boundNode == null
                        ? null
                        : directChild(boundNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    return bound == null
                        ? new JavaTypes.DeclaredType("java.lang.Object", List.of())
                        : typeFromTypeReference(bound);
                }
            }
            return null;
        }

        private @Nullable JavaTypes.DeclaredType declaredViewAs(
            JavaTypes.DeclaredType candidate,
            String targetQualifiedName,
            Set<String> visited
        ) {
            String candidateQualifiedName = resolveQualifiedTypeName(candidate.displayName());
            if (candidateQualifiedName == null) {
                candidateQualifiedName = candidate.displayName();
            }
            if (candidateQualifiedName.replace('$', '.').equals(targetQualifiedName.replace('$', '.')))
                return candidate;
            if (!visited.add(candidateQualifiedName))
                return null;

            Map<String, Type> substitutions = new LinkedHashMap<>();
            List<String> candidateTypeParameters = ownerTypeParameterNames(candidateQualifiedName);
            int count = Math.min(candidateTypeParameters.size(), candidate.typeArguments().size());
            for (int index = 0; index < count; index++) {
                context.poll();
                substitutions.put(
                    candidateTypeParameters.get(index),
                    candidate.typeArguments().get(index));
            }

            List<Type> directSupers = new ArrayList<>();
            ClassStub stub = classStub(candidateQualifiedName);
            if (stub != null) {
                if (stub.superClass() != null) {
                    directSupers.add(toSemanticType(stub.superClass()));
                }
                stub.interfaces().stream().map(JavaSemanticAnalyzer::toSemanticType).forEach(directSupers::add);
            } else {
                directSupers.addAll(sourceDirectSuperTypes(candidateQualifiedName));
            }
            for (Type directSuper : directSupers) {
                context.poll();
                Type semanticSuper = substituteTypeVariables(directSuper, substitutions);
                if (!(semanticSuper instanceof JavaTypes.DeclaredType declaredSuper))
                    continue;
                JavaTypes.DeclaredType match = declaredViewAs(declaredSuper, targetQualifiedName, visited);
                if (match != null)
                    return match;
            }
            return null;
        }

        private List<JavaTypes.DeclaredType> sourceDirectSuperTypes(String ownerQualifiedName) {
            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!ownerQualifiedName.equals(symbol.qualifiedName().orElse(null)))
                    continue;
                SyntaxNode declaration = symbol.declaration().orElse(null);
                return declaration == null
                    ? List.of()
                    : declaredDirectSuperTypes(declaration, this::typeFromTypeReference);
            }
            if (projectIndex == null)
                return List.of();
            return indexedDirectSuperTypes.computeIfAbsent(
                ownerQualifiedName, this::loadIndexedDirectSuperTypes);
        }

        private List<JavaTypes.DeclaredType> loadIndexedDirectSuperTypes(String ownerQualifiedName) {
            JavaProjectSemanticIndex.SymbolDescriptor descriptor = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> symbol.sourceFile() != null && isTypeSymbol(symbol.kind()))
                .findFirst()
                .orElse(null);
            if (descriptor == null)
                return List.of();
            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, descriptor.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                var sourceContext = new JavaRuleContext(
                    descriptor.sourceFile(), source, model, projectIndex);
                return findIndexedDirectSuperTypes(
                    model.syntaxTree().root(), model, sourceContext, descriptor.qualifiedName());
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return List.of();
            }
        }

        private List<JavaTypes.DeclaredType> findIndexedDirectSuperTypes(
            SyntaxNode node,
            SemanticModel model,
            JavaRuleContext sourceContext,
            String ownerQualifiedName
        ) {
            Symbol symbol = model.declaredSymbol(node).orElse(null);
            if (symbol != null && isTypeSymbol(symbol.kind())
                && ownerQualifiedName.equals(symbol.qualifiedName().orElse(null)))
                return declaredDirectSuperTypes(
                    node, typeRef -> indexedProjectTypeFromTypeReference(sourceContext, typeRef));
            for (SyntaxNode child : node.children()) {
                context.poll();
                List<JavaTypes.DeclaredType> result = findIndexedDirectSuperTypes(
                    child, model, sourceContext, ownerQualifiedName);
                if (!result.isEmpty())
                    return result;
            }
            return List.of();
        }

        private Type indexedProjectTypeFromTypeReference(JavaRuleContext sourceContext, SyntaxNode typeRef) {
            String text = canonicalTypeText(typeRef);
            return text == null || text.isBlank()
                ? UNKNOWN_TYPE
                : indexedProjectTypeFromText(sourceContext, text.trim());
        }

        private Type indexedProjectTypeFromText(JavaRuleContext sourceContext, String text) {
            if (text.isBlank())
                return UNKNOWN_TYPE;
            if ("void".equals(text))
                return new JavaTypes.VoidType();
            if (text.startsWith("?extends"))
                return new JavaTypes.WildcardType(indexedProjectTypeFromText(sourceContext, text.substring(8)), null);
            if (text.startsWith("?super"))
                return new JavaTypes.WildcardType(null, indexedProjectTypeFromText(sourceContext, text.substring(6)));
            if ("?".equals(text))
                return new JavaTypes.WildcardType(new JavaTypes.DeclaredType("java.lang.Object", List.of()), null);

            int arrayDimensions = 0;
            while (text.endsWith("[]")) {
                context.poll();
                arrayDimensions++;
                text = text.substring(0, text.length() - 2);
            }
            int typeArgumentsStart = findTopLevelTypeArgumentsStart(text);
            String rawType = typeArgumentsStart > 0 && text.endsWith(">")
                ? text.substring(0, typeArgumentsStart).trim()
                : text;
            List<Type> typeArguments = new ArrayList<>();
            if (typeArgumentsStart > 0 && text.endsWith(">")) {
                String argumentsText = text.substring(typeArgumentsStart + 1, text.length() - 1);
                for (String argument : splitTopLevelTypeArguments(argumentsText)) {
                    context.poll();
                    if (!argument.isBlank()) {
                        typeArguments.add(indexedProjectTypeFromText(sourceContext, argument.trim()));
                    }
                }
            }

            String qualifiedName = sourceContext.resolveQualifiedTypeName(rawType);
            Type resolved = switch (rawType) {
                case "boolean", "byte", "short", "char", "int", "long", "float", "double" ->
                    new JavaTypes.PrimitiveType(rawType);
                default -> qualifiedName == null || qualifiedName.isBlank()
                    || isLikelyTypeVariableName(rawType) && qualifiedName.equals(rawType)
                        ? isLikelyTypeVariableName(rawType)
                            ? new JavaTypes.TypeVariableType(rawType)
                            : new JavaTypes.UnknownType(text)
                        : new JavaTypes.DeclaredType(qualifiedName, typeArguments);
            };
            for (int index = 0; index < arrayDimensions; index++) {
                context.poll();
                resolved = new JavaTypes.ArrayType(resolved);
            }
            return resolved;
        }

        private List<JavaTypes.DeclaredType> declaredDirectSuperTypes(
            SyntaxNode declaration,
            Function<SyntaxNode, Type> typeResolver
        ) {
            if (JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id().equals(declaration.kind().id())) {
                SyntaxNode creation = declaration.parent().orElse(null);
                SyntaxNode baseType = creation == null
                    ? null
                    : directChild(creation, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (baseType == null)
                    return List.of();
                Type resolved = typeResolver.apply(baseType);
                if (resolved instanceof JavaTypes.DeclaredType declared) {
                    if (declared.typeArguments().isEmpty()) {
                        Type contextual = directContextualTargetType(creation);
                        if (contextual instanceof JavaTypes.DeclaredType target
                            && sameRawType(declared.displayName(), target.displayName()))
                            return List.of(target);
                    }
                    return List.of(declared);
                }
                return List.of();
            }
            List<JavaTypes.DeclaredType> directSupers = new ArrayList<>();
            for (SyntaxNode child : declaration.children()) {
                context.poll();
                if (!JavaSyntaxKinds.EXTENDS_CLAUSE.id().equals(child.kind().id())
                    && !JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id().equals(child.kind().id()))
                    continue;
                for (SyntaxNode typeRef : child.children()) {
                    context.poll();
                    if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(typeRef.kind().id()))
                        continue;
                    Type resolved = typeResolver.apply(typeRef);
                    if (resolved instanceof JavaTypes.DeclaredType declared) {
                        directSupers.add(declared);
                    }
                }
            }
            return List.copyOf(directSupers);
        }

        private Type substituteTypeVariables(Type type, Map<String, Type> substitutions) {
            return switch (type) {
                case JavaTypes.UnknownType unknown -> unknown;
                case JavaTypes.VoidType voidType -> voidType;
                case JavaTypes.PrimitiveType primitive -> primitive;
                case JavaTypes.TypeVariableType variable ->
                    substitutions.getOrDefault(variable.displayName(), variable);
                case JavaTypes.ArrayType array ->
                    new JavaTypes.ArrayType(substituteTypeVariables(array.componentType(), substitutions));
                case JavaTypes.WildcardType wildcard -> new JavaTypes.WildcardType(
                    wildcard.upperBound() == null
                        ? null
                        : substituteTypeVariables(wildcard.upperBound(), substitutions),
                    wildcard.lowerBound() == null
                        ? null
                        : substituteTypeVariables(wildcard.lowerBound(), substitutions));
                case JavaTypes.DeclaredType declared -> new JavaTypes.DeclaredType(
                    declared.displayName(),
                    declaredTypeArguments(declared).stream()
                        .map(argument -> substituteTypeVariables(argument, substitutions))
                        .toList());
                default -> type;
            };
        }

        private List<Type> declaredTypeArguments(Type type) {
            if (type instanceof JavaTypes.DeclaredType declared)
                return declared.typeArguments();
            return List.of();
        }

        private List<String> ownerTypeParameterNames(String ownerQualifiedName) {
            Map<String, ClassStub> stubs = projectIndex == null
                ? loadJdkClassStubsByQualifiedName(context.activityCheck)
                : projectIndex.classStubsByQualifiedName();
            ClassStub stub = stubs.get(ownerQualifiedName);
            if (stub != null && !stub.typeParameters().isEmpty())
                return stub.typeParameters().stream().map(TypeParameter::name).toList();

            for (Symbol symbol : context.allTypeSymbols()) {
                context.poll();
                if (!ownerQualifiedName.equals(symbol.qualifiedName().orElse(null)))
                    continue;
                SyntaxNode declaration = symbol.declaration().orElse(null);
                return declaration == null ? List.of() : declaredTypeParameterNames(declaration);
            }

            if (projectIndex != null)
                return indexedTypeParameterNames.computeIfAbsent(
                    ownerQualifiedName, this::loadIndexedTypeParameterNames);

            return List.of();
        }

        private List<String> loadIndexedTypeParameterNames(String ownerQualifiedName) {
            JavaProjectSemanticIndex.SymbolDescriptor descriptor = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> symbol.sourceFile() != null && isTypeSymbol(symbol.kind()))
                .findFirst()
                .orElse(null);
            if (descriptor == null)
                return List.of();

            try {
                String source = JavaSourceSnapshotIndex.readSource(projectIndex, descriptor.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(
                    JavaSyntaxParser.parse(source, context.activityCheck), projectIndex, context.activityCheck);
                return findDeclaredTypeParameterNames(
                    model.syntaxTree().root(), model, descriptor.qualifiedName());
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception _) {
                return List.of();
            }
        }

        private List<String> findDeclaredTypeParameterNames(
            SyntaxNode node,
            SemanticModel model,
            String ownerQualifiedName
        ) {
            Symbol symbol = model.declaredSymbol(node).orElse(null);
            if (symbol != null && isTypeSymbol(symbol.kind())
                && ownerQualifiedName.equals(symbol.qualifiedName().orElse(null)))
                return declaredTypeParameterNames(node);
            for (SyntaxNode child : node.children()) {
                context.poll();
                List<String> names = findDeclaredTypeParameterNames(child, model, ownerQualifiedName);
                if (!names.isEmpty())
                    return names;
            }
            return List.of();
        }

        private List<String> declaredTypeParameterNames(SyntaxNode declaration) {
            SyntaxNode typeParameters = directChild(declaration, JavaSyntaxKinds.TYPE_PARAMETERS.id());
            if (typeParameters == null)
                return List.of();
            List<String> names = new ArrayList<>();
            for (SyntaxNode child : typeParameters.children()) {
                context.poll();
                if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(child.kind().id()))
                    continue;
                String name = firstIdentifierLikeTokenText(child);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return List.copyOf(names);
        }

        private boolean isSelfBoundOwnerTypeParameter(String ownerQualifiedName, String variableName) {
            Map<String, ClassStub> stubs = projectIndex == null
                ? loadJdkClassStubsByQualifiedName(context.activityCheck)
                : projectIndex.classStubsByQualifiedName();
            ClassStub stub = stubs.get(ownerQualifiedName);
            if (stub == null)
                return false;

            for (TypeParameter parameter : stub.typeParameters()) {
                context.poll();
                if (!parameter.name().equals(variableName))
                    continue;
                for (dev.railroadide.railroad.ide.classparser.Type bound : parameter.bounds()) {
                    context.poll();
                    if (bound instanceof dev.railroadide.railroad.ide.classparser.Type.ClassType classType
                        && sameRawType(ownerQualifiedName, classType.name()))
                        return true;
                }
            }
            return false;
        }

        private Type inferClassInstanceCreationType(SyntaxNode creationNode) {
            SyntaxNode typeRef = directChild(creationNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef != null) {
                Type createdType = typeFromTypeReference(typeRef);
                if (createdType instanceof JavaTypes.DeclaredType declaredType
                    && declaredType.typeArguments().isEmpty()) {
                    Type contextualType = contextualInvocationTargetType(creationNode);
                    if (contextualType instanceof JavaTypes.DeclaredType contextualDeclared
                        && sameRawType(declaredType.displayName(), contextualDeclared.displayName()))
                        return contextualDeclared;
                    SyntaxNode arguments = directChild(typeRef, JavaSyntaxKinds.DIAMOND_TYPE_ARGUMENTS.id());
                    Symbol constructor = context.resolvedSymbol(creationNode);
                    if (arguments != null && constructor != null) {
                        Map<String, Type> substitutions = new LinkedHashMap<>();
                        bindMethodTypeArguments(creationNode, constructor, substitutions);
                        List<Type> inferredArguments = ownerTypeParameterNames(declaredType.displayName()).stream()
                            .map(name -> substitutions.getOrDefault(name, new JavaTypes.TypeVariableType(name)))
                            .toList();
                        return new JavaTypes.DeclaredType(declaredType.displayName(), inferredArguments);
                    }
                }
                return createdType;
            }

            Symbol resolved = context.resolvedSymbol(creationNode);
            return resolved != null && resolved.kind() == JavaSymbolKind.CONSTRUCTOR
                ? typeOfSymbol(resolved)
                : UNKNOWN_TYPE;
        }

        private Type inferAssignmentType(SyntaxNode assignmentExpression) {
            List<SyntaxNode> expressionChildren = directExpressionChildren(assignmentExpression);
            if (expressionChildren.isEmpty())
                return UNKNOWN_TYPE;
            return inferType(expressionChildren.getFirst());
        }

        private Type inferBinaryType(SyntaxNode binaryExpression) {
            Type left = UNKNOWN_TYPE;
            Type right = UNKNOWN_TYPE;
            String operator = null;
            for (SyntaxNode child : binaryExpression.children()) {
                context.poll();
                if (child instanceof SyntaxToken token) {
                    if (isTriviaToken(token) || isMissingTokenKind(token.kind().id()))
                        continue;
                    operator = token.text();
                } else if (isExpressionNode(child)) {
                    if (left == UNKNOWN_TYPE) {
                        left = inferType(child);
                    } else {
                        right = inferType(child);
                    }
                }
            }

            if ("&&".equals(operator) || "||".equals(operator)
                || "==".equals(operator) || "!=".equals(operator)
                || "<".equals(operator) || "<=".equals(operator)
                || ">".equals(operator) || ">=".equals(operator))
                return BOOLEAN_TYPE;

            if ("+".equals(operator) && (isStringType(left) || isStringType(right)))
                return new JavaTypes.DeclaredType("String", List.of());

            if (isNumericType(left) && isNumericType(right))
                return promoteNumeric(left, right);

            return UNKNOWN_TYPE;
        }

        private Type inferCastType(SyntaxNode castExpression) {
            SyntaxNode typeRef = directChild(castExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
        }

        private Type inferConditionalType(SyntaxNode conditionalExpression) {
            List<SyntaxNode> expressions = directExpressionChildren(conditionalExpression);
            if (expressions.size() < 3)
                return UNKNOWN_TYPE;
            return commonConditionalType(inferType(expressions.get(1)), inferType(expressions.get(2)), projectIndex);
        }

        private Type inferSwitchExpressionType(SyntaxNode switchExpression) {
            List<Type> resultTypes = new ArrayList<>();
            collectSwitchResultTypes(switchExpression, resultTypes);
            return mergeExpressionTypes(resultTypes, projectIndex);
        }

        private void collectSwitchResultTypes(SyntaxNode node, List<Type> out) {
            if (JavaSyntaxKinds.SWITCH_RULE.id().equals(node.kind().id())) {
                List<SyntaxNode> expressions = directExpressionChildren(node);
                if (!expressions.isEmpty()) {
                    out.add(inferType(expressions.getLast()));
                }
                return;
            }
            for (SyntaxNode child : node.children()) {
                context.poll();
                collectSwitchResultTypes(child, out);
            }
        }

        private Type inferFromChildren(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                context.poll();
                if (isExpressionNode(child)) {
                    Type type = inferType(child);
                    if (type.kind() != JavaTypes.Kind.UNKNOWN)
                        return type;
                }
            }
            return UNKNOWN_TYPE;
        }

        private Type typeOfSymbol(Symbol symbol) {
            if (symbol instanceof SyntheticMemberSymbol synthetic)
                return synthetic.valueType();

            return switch (symbol.kind()) {
                case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
                    JavaSymbolKind.RECORD ->
                    new JavaTypes.DeclaredType(symbol.simpleName(), List.of());
                case JavaSymbolKind.METHOD -> methodReturnType(symbol);
                case JavaSymbolKind.PARAMETER, JavaSymbolKind.LOCAL_VARIABLE, JavaSymbolKind.FIELD ->
                    variableLikeType(symbol);
                case JavaSymbolKind.CONSTRUCTOR ->
                    new JavaTypes.DeclaredType(ownerQualifiedName(symbol).orElse(symbol.simpleName()), List.of());
                default -> UNKNOWN_TYPE;
            };
        }

        private Type methodReturnType(Symbol methodSymbol) {
            SyntaxNode declaration = methodSymbol.declaration().orElse(null);
            if (declaration == null)
                return UNKNOWN_TYPE;

            SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef == null)
                return UNKNOWN_TYPE;
            return typeFromTypeReference(typeRef);
        }

        private Type variableLikeType(Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return UNKNOWN_TYPE;

            if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(declaration.kind().id())) {
                Symbol enclosingEnum = nearestEnclosingTypeSymbol(declaration);
                if (enclosingEnum != null)
                    return new JavaTypes.DeclaredType(
                        enclosingEnum.qualifiedName().orElse(enclosingEnum.simpleName()), List.of());
            }

            if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                || JavaSyntaxKinds.RECORD_COMPONENT.id().equals(declaration.kind().id())
                || JavaSyntaxKinds.PATTERN.id().equals(declaration.kind().id())) {
                SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef == null)
                    return UNKNOWN_TYPE;
                Type parameterType = typeFromTypeReference(typeRef);
                if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                    && "var".equals(canonicalTypeText(typeRef))) {
                    Type elementType = enhancedForElementType(declaration);
                    if (elementType.kind() != JavaTypes.Kind.UNKNOWN) {
                        parameterType = elementType;
                    }
                }
                return hasTokenKind(declaration, JavaTokenType.ELLIPSIS)
                    ? new JavaTypes.ArrayType(parameterType)
                    : parameterType;
            }

            if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id()))
                return variableDeclaredType(declaration);

            return UNKNOWN_TYPE;
        }

        private Type enhancedForElementType(SyntaxNode parameterDeclaration) {
            SyntaxNode enhancedFor = parameterDeclaration.parent().orElse(null);
            if (enhancedFor == null
                || !JavaSyntaxKinds.ENHANCED_FOR_STATEMENT.id().equals(enhancedFor.kind().id()))
                return UNKNOWN_TYPE;
            SyntaxNode iterableExpression = enhancedFor.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .findFirst()
                .orElse(null);
            if (iterableExpression == null)
                return UNKNOWN_TYPE;

            Type iterableType = inferType(iterableExpression);
            if (iterableType instanceof JavaTypes.ArrayType arrayType)
                return arrayType.componentType();
            if (!(iterableType instanceof JavaTypes.DeclaredType declaredType))
                return UNKNOWN_TYPE;

            JavaTypes.DeclaredType iterableView = declaredViewAs(
                declaredType, "java.lang.Iterable", new HashSet<>());
            if (iterableView == null || iterableView.typeArguments().isEmpty())
                return UNKNOWN_TYPE;
            return unwrapWildcard(iterableView.typeArguments().getFirst());
        }

        private Optional<String> ownerQualifiedName(Symbol symbol) {
            String qualifiedName = symbol.qualifiedName().orElse(null);
            if (qualifiedName == null || qualifiedName.isBlank())
                return Optional.empty();
            int separator = qualifiedName.indexOf('#');
            if (separator <= 0)
                return Optional.empty();
            return Optional.of(qualifiedName.substring(0, separator));
        }

        private Type variableDeclaredType(SyntaxNode variableDeclarator) {
            var parent = variableDeclarator.parent();
            while (parent.isPresent()) {
                context.poll();
                SyntaxNode candidate = parent.get();
                if (JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.id().equals(candidate.kind().id())
                    && hasTokenKind(candidate, JavaTokenType.VAR_KEYWORD)) {
                    List<SyntaxNode> expressions = directExpressionChildren(variableDeclarator);
                    SyntaxNode initializer = expressions.isEmpty() ? null : expressions.getFirst();
                    return initializer == null ? UNKNOWN_TYPE : inferType(initializer);
                }

                SyntaxNode typeRef = directChild(candidate, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null) {
                    if ("var".equals(canonicalTypeText(typeRef))) {
                        List<SyntaxNode> expressions = directExpressionChildren(variableDeclarator);
                        SyntaxNode initializer = expressions.isEmpty() ? null : expressions.getFirst();
                        return initializer == null ? UNKNOWN_TYPE : inferType(initializer);
                    }
                    return typeFromTypeReference(typeRef);
                }
                if (JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.id().equals(candidate.kind().id()))
                    return UNKNOWN_TYPE;

                parent = candidate.parent();
            }
            return UNKNOWN_TYPE;
        }

        private Type typeFromTypeReference(SyntaxNode typeNode) {
            String text = canonicalTypeText(typeNode);
            if (text == null || text.isBlank())
                return UNKNOWN_TYPE;

            return typeFromTypeReferenceText(text, typeNode);
        }

        private Type typeFromTypeReferenceText(String text) {
            return typeFromTypeReferenceText(text, null);
        }

        private Type typeFromTypeReferenceText(String text, @Nullable SyntaxNode usageSite) {
            if (text == null)
                return UNKNOWN_TYPE;
            text = text.trim();
            if (text.isBlank())
                return UNKNOWN_TYPE;
            if ("void".equals(text))
                return new JavaTypes.VoidType();
            if (text.startsWith("? extends "))
                return new JavaTypes.WildcardType(typeFromTypeReferenceText(text.substring(10), usageSite), null);
            if (text.startsWith("? super "))
                return new JavaTypes.WildcardType(null, typeFromTypeReferenceText(text.substring(8), usageSite));
            if (text.startsWith("?extends"))
                return new JavaTypes.WildcardType(typeFromTypeReferenceText(text.substring(8), usageSite), null);
            if (text.startsWith("?super"))
                return new JavaTypes.WildcardType(null, typeFromTypeReferenceText(text.substring(6), usageSite));
            if ("?".equals(text))
                return new JavaTypes.WildcardType(new JavaTypes.DeclaredType("java.lang.Object", List.of()), null);
            if (text.endsWith("[]"))
                return new JavaTypes.ArrayType(
                    typeFromTypeReferenceText(text.substring(0, text.length() - 2), usageSite));

            int typeArgsStart = findTopLevelTypeArgumentsStart(text);
            if (typeArgsStart > 0 && text.endsWith(">")) {
                String rawText = text.substring(0, typeArgsStart).trim();
                String argsText = text.substring(typeArgsStart + 1, text.length() - 1).trim();
                List<Type> typeArguments = new ArrayList<>();
                for (String part : splitTopLevelTypeArguments(argsText)) {
                    context.poll();
                    String argumentText = part.trim();
                    if (!argumentText.isEmpty()) {
                        typeArguments.add(typeFromTypeReferenceText(argumentText, usageSite));
                    }
                }
                String qualifiedRaw = resolveQualifiedTypeName(rawText);
                String declaredName = qualifiedRaw == null || qualifiedRaw.isBlank() ? rawText : qualifiedRaw;
                return declaredName.isBlank()
                    ? UNKNOWN_TYPE
                    : new JavaTypes.DeclaredType(declaredName, typeArguments);
            }

            if (NUMERIC_PRIMITIVES.contains(text) || "boolean".equals(text))
                return new JavaTypes.PrimitiveType(text);
            if (usageSite != null && isLexicallyDeclaredTypeVariable(text, usageSite))
                return new JavaTypes.TypeVariableType(text);
            if (isTypeVariableNameInScope(text))
                return new JavaTypes.TypeVariableType(text);
            String qualified = resolveQualifiedTypeName(text);
            if (isLikelyTypeVariableName(text)
                && (qualified == null || qualified.isBlank() || Objects.equals(qualified, text)))
                return new JavaTypes.TypeVariableType(text);
            String declaredName = qualified == null || qualified.isBlank() ? simpleTypeName(text) : qualified;
            return declaredName.isBlank() ? UNKNOWN_TYPE : new JavaTypes.DeclaredType(declaredName, List.of());
        }

        private boolean isLexicallyDeclaredTypeVariable(String variableName, SyntaxNode usageSite) {
            SyntaxNode current = usageSite;
            while (current != null) {
                context.poll();
                SyntaxNode typeParameters = directChild(current, JavaSyntaxKinds.TYPE_PARAMETERS.id());
                if (typeParameters != null && typeParameters.children().stream()
                    .filter(child -> JavaSyntaxKinds.TYPE_PARAMETER.id().equals(child.kind().id()))
                    .map(JavaSemanticAnalyzer::firstIdentifierLikeTokenText)
                    .anyMatch(variableName::equals))
                    return true;
                current = current.parent().orElse(null);
            }
            return false;
        }

        private int findTopLevelTypeArgumentsStart(String text) {
            int depth = 0;
            for (int index = 0; index < text.length(); index++) {
                context.poll();
                char ch = text.charAt(index);
                if (ch == '<') {
                    if (depth == 0)
                        return index;
                    depth++;
                } else if (ch == '>') {
                    depth = Math.max(0, depth - 1);
                }
            }
            return -1;
        }

        private List<String> splitTopLevelTypeArguments(String text) {
            List<String> parts = new ArrayList<>();
            var current = new StringBuilder();
            int depth = 0;
            for (int index = 0; index < text.length(); index++) {
                context.poll();
                char ch = text.charAt(index);
                if (ch == '<') {
                    depth++;
                } else if (ch == '>') {
                    depth = Math.max(0, depth - 1);
                } else if (ch == ',' && depth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return List.copyOf(parts);
        }

        private boolean isTypeVariableNameInScope(String name) {
            for (Symbol symbol : context.allDeclaredSymbols()) {
                context.poll();
                if (symbol.kind() == JavaSymbolKind.TYPE_PARAMETER && name.equals(symbol.simpleName()))
                    return true;
            }
            return false;
        }

        private void collectImportsFromRootScope() {
            Map<String, List<Symbol>> rootDeclarations = context.rootScope.snapshotDeclarations();
            for (List<Symbol> symbols : rootDeclarations.values()) {
                context.poll();
                for (Symbol symbol : symbols) {
                    context.poll();
                    if (symbol.kind() != JavaSymbolKind.IMPORT)
                        continue;

                    SyntaxNode declarationNode = symbol.declaration().orElse(null);
                    if (declarationNode == null)
                        continue;

                    SyntaxNode targetNode = directChild(declarationNode, JavaSyntaxKinds.IMPORT_TARGET.id());
                    if (targetNode == null)
                        continue;

                    String qualifiedTarget = canonicalQualifiedName(targetNode);
                    if (qualifiedTarget == null || qualifiedTarget.isBlank())
                        continue;

                    boolean isStatic = hasTokenKind(declarationNode, JavaTokenType.STATIC_KEYWORD);
                    boolean isWildcard = qualifiedTarget.endsWith(".*");
                    if (isStatic)
                        continue;

                    String ownerName = isWildcard
                        ? qualifiedTarget.substring(0, qualifiedTarget.length() - 2)
                        : packagePrefix(qualifiedTarget);
                    String importedName = isWildcard ? "*" : lastSegment(qualifiedTarget);
                    var importSpec = new ImportSpec(
                        declarationNode,
                        targetNode,
                        qualifiedTarget,
                        ownerName,
                        importedName,
                        false,
                        isWildcard);

                    if (isWildcard) {
                        onDemandTypeImports.add(importSpec);
                    } else {
                        singleTypeImportsBySimpleName.putIfAbsent(importSpec.importedName(), importSpec);
                    }
                }
            }
        }

        private record FunctionalSignature(List<Type> parameterTypes, Type returnType) {
        }

        private @Nullable String resolveQualifiedTypeName(@Nullable String text) {
            if (text == null || text.isBlank())
                return null;

            text = eraseTypeArguments(text);
            while (text.endsWith("[]")) {
                context.poll();
                text = text.substring(0, text.length() - 2);
            }
            if (text.isBlank())
                return null;
            if ("void".equals(text)
                || Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return text;
            String directType = resolvableQualifiedTypeName(text);
            if (directType != null)
                return directType;
            String nestedType = resolveNestedQualifiedTypeName(text);
            if (nestedType != null)
                return nestedType;
            if (text.indexOf('.') > 0 && context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String inCurrentPackage = context.currentPackageName + "." + text;
                String currentPackageType = resolvableQualifiedTypeName(inCurrentPackage);
                if (currentPackageType != null)
                    return currentPackageType;
            }

            String simpleName = simpleTypeName(text);
            for (String localQualifiedTypeName : localQualifiedTypeNames) {
                context.poll();
                if (simpleTypeName(localQualifiedTypeName).equals(simpleName))
                    return localQualifiedTypeName;
            }
            if (singleTypeImportsBySimpleName.containsKey(simpleName)) {
                String imported = resolvableQualifiedTypeName(
                    singleTypeImportsBySimpleName.get(simpleName).qualifiedTarget());
                if (imported != null)
                    return imported;
            }
            if (context.currentPackageName != null && !context.currentPackageName.isBlank()) {
                String inCurrentPackage = context.currentPackageName + "." + simpleName;
                String currentPackageType = resolvableQualifiedTypeName(inCurrentPackage);
                if (currentPackageType != null)
                    return currentPackageType;
            }
            String javaLangType = "java.lang." + simpleName;
            String javaLangResolved = resolvableQualifiedTypeName(javaLangType);
            if (javaLangResolved != null)
                return javaLangResolved;
            for (ImportSpec onDemandImport : onDemandTypeImports) {
                context.poll();
                String imported = onDemandImport.ownerName() + "." + simpleName;
                String importedType = resolvableQualifiedTypeName(imported);
                if (importedType != null)
                    return importedType;
            }
            String projectQualifiedType = uniqueProjectQualifiedTypeName(simpleName);
            if (projectQualifiedType != null)
                return projectQualifiedType;
            return null;
        }

        private @Nullable String resolvableQualifiedTypeName(String qualifiedTypeName) {
            if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
                return null;
            if (isResolvableType(qualifiedTypeName))
                return qualifiedTypeName;

            String candidate = qualifiedTypeName;
            int dot = candidate.lastIndexOf('.');
            while (dot > 0) {
                context.poll();
                candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
                if (isResolvableType(candidate))
                    return candidate;
                dot = candidate.lastIndexOf('.', dot - 1);
            }
            return null;
        }

        private @Nullable String resolveNestedQualifiedTypeName(String text) {
            int dot = text.lastIndexOf('.');
            if (dot <= 0 || dot >= text.length() - 1)
                return null;

            String owner = resolveQualifiedTypeName(text.substring(0, dot));
            if (owner == null || owner.isBlank())
                return null;

            return resolvableQualifiedTypeName(owner + "." + text.substring(dot + 1));
        }

        private @Nullable String uniqueProjectQualifiedTypeName(String simpleName) {
            if (projectIndex == null)
                return null;

            String match = null;
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupSimpleName(simpleName)) {
                context.poll();
                if (!isTypeSymbol(symbol.kind()))
                    continue;
                String qualifiedName = symbol.qualifiedName();
                if (qualifiedName == null || qualifiedName.isBlank())
                    continue;
                if (match != null && !match.equals(qualifiedName))
                    return null;
                match = qualifiedName;
            }
            return match;
        }

        private boolean isResolvableType(String qualifiedTypeName) {
            if (qualifiedTypeName == null || qualifiedTypeName.isBlank())
                return false;
            if (localQualifiedTypeNames.contains(qualifiedTypeName))
                return true;
            if (projectIndex != null && !projectIndex.lookupQualifiedName(qualifiedTypeName).isEmpty())
                return true;
            return availableQualifiedTypeNames.contains(qualifiedTypeName);
        }

        private static List<SyntaxNode> directExpressionChildren(SyntaxNode node) {
            List<SyntaxNode> result = new ArrayList<>();
            for (SyntaxNode child : node.children()) {
                if (isExpressionNode(child)) {
                    result.add(child);
                }
            }
            return List.copyOf(result);
        }

        private static Type promoteNumeric(Type left, Type right) {
            int rank = Math.max(numericRank(left.displayName()), numericRank(right.displayName()));
            return switch (rank) {
                case 5 -> new JavaTypes.PrimitiveType("double");
                case 4 -> new JavaTypes.PrimitiveType("float");
                case 3 -> new JavaTypes.PrimitiveType("long");
                default -> new JavaTypes.PrimitiveType("int");
            };
        }

        private static int numericRank(String primitive) {
            return switch (primitive) {
                case "byte" -> 0;
                case "short", "char" -> 1;
                case "int" -> 2;
                case "long" -> 3;
                case "float" -> 4;
                case "double" -> 5;
                default -> -1;
            };
        }

        private static boolean isNumericType(Type type) {
            return type.kind() == JavaTypes.Kind.PRIMITIVE && NUMERIC_PRIMITIVES.contains(type.displayName());
        }

        private static boolean isStringType(Type type) {
            return type.kind() == JavaTypes.Kind.DECLARED && "String".equals(simpleTypeName(type.displayName()));
        }
    }

    private static final class SyntheticMemberSymbol implements Symbol {
        private final @Nullable SymbolId id;
        private final SymbolKind kind;
        private final String simpleName;
        private final @Nullable String qualifiedName;
        private final @Nullable SyntaxNode declaration;
        private final Type valueType;
        private final List<Type> parameterTypes;
        private final boolean staticMember;

        private SyntheticMemberSymbol(
            SymbolKind kind,
            String simpleName,
            @Nullable String qualifiedName,
            @Nullable SyntaxNode declaration,
            Type valueType,
            List<Type> parameterTypes,
            boolean staticMember
        ) {
            this(null, kind, simpleName, qualifiedName, declaration, valueType, parameterTypes, staticMember);
        }

        private SyntheticMemberSymbol(
            @Nullable SymbolId id,
            SymbolKind kind,
            String simpleName,
            @Nullable String qualifiedName,
            @Nullable SyntaxNode declaration,
            Type valueType,
            List<Type> parameterTypes,
            boolean staticMember
        ) {
            this.id = id;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
            this.qualifiedName = qualifiedName;
            this.declaration = declaration;
            this.valueType = Objects.requireNonNull(valueType, "valueType");
            this.parameterTypes = List.copyOf(Objects.requireNonNull(parameterTypes, "parameterTypes"));
            this.staticMember = staticMember;
        }

        @Override
        public Optional<SymbolId> id() {
            if (id != null)
                return Optional.of(id);
            if (qualifiedName == null || !qualifiedName.contains("#"))
                return Optional.empty();
            SymbolId owner = JavaSymbolIds.type(qualifiedName.substring(0, qualifiedName.indexOf('#')));
            if (kind == JavaSymbolKind.FIELD)
                return Optional.of(JavaSymbolIds.field(owner, simpleName));
            try {
                List<String> parameters = parameterTypes.stream().map(SyntheticMemberSymbol::identityParameterType)
                    .toList();
                return Optional.of(kind == JavaSymbolKind.CONSTRUCTOR
                    ? JavaSymbolIds.constructor(owner, parameters)
                    : JavaSymbolIds.method(owner, simpleName, parameters));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        private static String identityParameterType(Type type) {
            return switch (type) {
                case JavaTypes.PrimitiveType primitive -> primitive.displayName();
                case JavaTypes.DeclaredType declared -> declared.displayName();
                case JavaTypes.ArrayType array -> identityParameterType(array.componentType()) + "[]";
                default ->
                    throw new IllegalArgumentException("A synthetic identity requires concrete declaration types");
            };
        }

        @Override
        public SymbolKind kind() {
            return kind;
        }

        @Override
        public String simpleName() {
            return simpleName;
        }

        @Override
        public Optional<String> qualifiedName() {
            return Optional.ofNullable(qualifiedName);
        }

        @Override
        public Optional<SyntaxNode> declaration() {
            return Optional.ofNullable(declaration);
        }

        private Type valueType() {
            return valueType;
        }

        private List<Type> parameterTypes() {
            return parameterTypes;
        }

        private boolean staticMember() {
            return staticMember;
        }
    }

    private static Type mergeExpressionTypes(List<Type> types, @Nullable JavaSymbolIndex projectIndex) {
        Type result = new JavaTypes.UnknownType("<unknown>");
        for (Type type : types) {
            result = commonConditionalType(result, type, projectIndex);
        }
        return result;
    }

    private static Type commonConditionalType(
        Type whenTrue,
        Type whenFalse,
        @Nullable JavaSymbolIndex projectIndex
    ) {
        if (whenTrue.kind() == JavaTypes.Kind.UNKNOWN)
            return whenFalse;
        if (whenFalse.kind() == JavaTypes.Kind.UNKNOWN)
            return whenTrue;
        if (whenTrue.equals(whenFalse) || whenTrue.displayName().equals(whenFalse.displayName()))
            return whenTrue;
        if (whenTrue.kind() == JavaTypes.Kind.DECLARED && whenFalse.kind() == JavaTypes.Kind.DECLARED) {
            Map<String, ClassStub> stubs = projectIndex == null
                ? loadJdkClassStubsByQualifiedName()
                : projectIndex.classStubsByQualifiedName();
            String trueName = qualifiedStubName(whenTrue.displayName(), stubs);
            String falseName = qualifiedStubName(whenFalse.displayName(), stubs);
            if (isStubSubtype(trueName, falseName, stubs, new HashSet<>()))
                return whenFalse;
            if (isStubSubtype(falseName, trueName, stubs, new HashSet<>()))
                return whenTrue;
        }
        return new JavaTypes.UnknownType("<unknown>");
    }

    private static @Nullable String qualifiedStubName(String displayName, Map<String, ClassStub> stubs) {
        String rawName = eraseTypeArguments(displayName);
        if (stubs.containsKey(rawName))
            return rawName;

        String match = null;
        String simpleName = simpleTypeName(rawName);
        for (String qualifiedName : stubs.keySet()) {
            if (!simpleTypeName(qualifiedName).equals(simpleName))
                continue;
            if (match != null)
                return null;
            match = qualifiedName;
        }
        return match;
    }

    private static boolean isStubSubtype(
        @Nullable String candidate,
        @Nullable String target,
        Map<String, ClassStub> stubs,
        Set<String> visited
    ) {
        if (candidate == null || target == null)
            return false;
        if (candidate.equals(target))
            return true;
        if (!visited.add(candidate))
            return false;

        ClassStub stub = stubs.get(candidate);
        if (stub == null)
            return false;
        List<dev.railroadide.railroad.ide.classparser.Type> directSupers = new ArrayList<>();
        if (stub.superClass() != null) {
            directSupers.add(stub.superClass());
        }
        directSupers.addAll(stub.interfaces());
        for (dev.railroadide.railroad.ide.classparser.Type directSuper : directSupers) {
            Type semanticSuper = toSemanticType(directSuper);
            String superName = qualifiedStubName(semanticSuper.displayName(), stubs);
            if (target.equals(superName) || isStubSubtype(superName, target, stubs, visited))
                return true;
        }
        return false;
    }

    private static boolean sameRawType(String left, String right) {
        String leftRaw = eraseTypeArguments(left);
        String rightRaw = eraseTypeArguments(right);
        return leftRaw.equals(rightRaw) || simpleTypeName(leftRaw).equals(simpleTypeName(rightRaw));
    }

    private static boolean isLikelyTypeVariableName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.indexOf('.') >= 0)
            return false;
        if (!Character.isUpperCase(displayName.charAt(0)))
            return false;
        for (int index = 1; index < displayName.length(); index++) {
            if (!Character.isUpperCase(displayName.charAt(index))
                && !Character.isDigit(displayName.charAt(index))
                && displayName.charAt(index) != '_')
                return false;
        }
        return true;
    }

    private static boolean isSelfTypeVariable(Type type, String variableName) {
        return type instanceof JavaTypes.TypeVariableType variable
            && variableName.equals(variable.displayName())
            || type.kind() == JavaTypes.Kind.DECLARED
                && variableName.equals(type.displayName())
                && isLikelyTypeVariableName(type.displayName());
    }

    private static Type toSemanticType(dev.railroadide.railroad.ide.classparser.Type type) {
        if (type == null)
            return new JavaTypes.UnknownType("<unknown>");

        return switch (type) {
            case dev.railroadide.railroad.ide.classparser.Type.PrimitiveType primitive ->
                "void".equals(primitive.name())
                    ? new JavaTypes.VoidType()
                    : new JavaTypes.PrimitiveType(primitive.name());
            case dev.railroadide.railroad.ide.classparser.Type.ArrayType array ->
                new JavaTypes.ArrayType(toSemanticType(array.componentType()));
            case dev.railroadide.railroad.ide.classparser.Type.ClassType clazz ->
                new JavaTypes.DeclaredType(
                    clazz.name(),
                    clazz.typeArguments().stream().map(JavaSemanticAnalyzer::toSemanticType).toList());
            case dev.railroadide.railroad.ide.classparser.Type.TypeVariable variable ->
                new JavaTypes.TypeVariableType(variable.name());
            case dev.railroadide.railroad.ide.classparser.Type.WildcardType wildcard -> {
                // JVM signature '*' is the same unbounded wildcard as source '?'.
                if (wildcard.bound() == null && wildcard.isUpperBound())
                    yield new JavaTypes.WildcardType(new JavaTypes.DeclaredType("java.lang.Object", List.of()), null);
                Type bound = wildcard.bound() == null
                    ? new JavaTypes.UnknownType("<unknown>")
                    : toSemanticType(wildcard.bound());
                yield wildcard.isUpperBound()
                    ? new JavaTypes.WildcardType(bound, null)
                    : new JavaTypes.WildcardType(null, bound);
            }
        };
    }

    private static Type numericLiteralType(String text, boolean floatingPoint) {
        String normalized = text == null ? "" : text.replace("_", "").toLowerCase(Locale.ROOT);
        if (floatingPoint)
            return new JavaTypes.PrimitiveType(normalized.endsWith("f") ? "float" : "double");
        return new JavaTypes.PrimitiveType(normalized.endsWith("l") ? "long" : "int");
    }

    private static String signatureSuffix(List<Type> parameterTypes) {
        if (parameterTypes.isEmpty())
            return "()";
        var builder = new StringBuilder("(");
        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(parameterTypes.get(index).displayName());
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Returns the cached qualified names of classes discovered in the standard library.
     *
     * @return an immutable set of standard-library class names
     */
    public static Set<String> loadJdkQualifiedTypeNames() {
        return loadJdkQualifiedTypeNames(ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Returns runtime type names, observing request controls while scanning or waiting for the cache.
     *
     * @param activityCheck request activity check
     * @return immutable runtime type names
     */
    public static Set<String> loadJdkQualifiedTypeNames(Runnable activityCheck) {
        activityCheck.run();
        Set<String> cached = cachedJdkQualifiedTypeNames;
        if (cached != null)
            return cached;

        lockJdkCache(activityCheck);
        try {
            activityCheck.run();
            if (cachedJdkQualifiedTypeNames != null)
                return cachedJdkQualifiedTypeNames;

            Set<String> names = Set.copyOf(loadJdkClassStubsByQualifiedName(activityCheck).keySet());
            activityCheck.run();
            cachedJdkQualifiedTypeNames = names;
            return cachedJdkQualifiedTypeNames;
        } finally {
            JDK_CACHE_LOCK.unlock();
        }
    }

    /**
     * Scans the standard library on first use and caches class stubs by their nonblank qualified names.
     *
     * @return an immutable map from qualified class names to standard-library stubs
     */
    public static Map<String, ClassStub> loadJdkClassStubsByQualifiedName() {
        return loadJdkClassStubsByQualifiedName(ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Scans or reuses the runtime library without publishing an incomplete or cancelled cache.
     *
     * @param activityCheck request activity check
     * @return immutable runtime stubs
     */
    public static Map<String, ClassStub> loadJdkClassStubsByQualifiedName(Runnable activityCheck) {
        activityCheck.run();
        Map<String, ClassStub> cached = cachedJdkClassStubsByQualifiedName;
        if (cached != null)
            return cached;

        lockJdkCache(activityCheck);
        try {
            activityCheck.run();
            if (cachedJdkClassStubsByQualifiedName != null)
                return cachedJdkClassStubsByQualifiedName;

            Map<String, ClassStub> byQualifiedName = new LinkedHashMap<>();
            for (ClassStub stub : Indexes.scanStandardLibrary(activityCheck)) {
                ActivityCheckpoint.poll(activityCheck);
                String fullName = stub.getFullName();
                if (fullName == null || fullName.isBlank())
                    continue;
                byQualifiedName.put(fullName,
                    BinaryStubPublication.copy(stub));
            }

            Map<String, ClassStub> result = Map.copyOf(byQualifiedName);
            activityCheck.run();
            cachedJdkClassStubsByQualifiedName = result;
            return cachedJdkClassStubsByQualifiedName;
        } finally {
            JDK_CACHE_LOCK.unlock();
        }
    }

    private static void lockJdkCache(Runnable activityCheck) {
        while (true) {
            activityCheck.run();
            try {
                if (JDK_CACHE_LOCK.tryLock(10, TimeUnit.MILLISECONDS))
                    return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Runtime library indexing interrupted");
            }
        }
    }

    /**
     * Finds the first immediate child with the requested syntax kind.
     *
     * @param node the parent node to inspect
     * @param kindId the syntax kind identifier to match
     * @return the matching child, or {@code null} if none exists
     */
    public static @Nullable SyntaxNode directChild(SyntaxNode node, String kindId) {
        for (SyntaxNode child : node.children()) {
            if (kindId.equals(child.kind().id()))
                return child;
        }
        return null;
    }

    /**
     * Tests whether a node or its descendants contain a nonmissing token of the requested Java token type.
     *
     * @param node the subtree to inspect
     * @param tokenType the Java token type to find
     * @return whether a matching token is present
     */
    public static boolean hasTokenKind(SyntaxNode node, JavaTokenType tokenType) {
        String tokenKindId = JavaSyntaxKinds.tokenKind(tokenType).id();
        return containsTokenKind(node, tokenKindId);
    }

    private static boolean containsTokenKind(SyntaxNode node, String tokenKindId) {
        if (node instanceof SyntaxToken token)
            return tokenKindId.equals(token.kind().id()) && !isMissingTokenKind(token.kind().id());
        for (SyntaxNode child : node.children()) {
            if (containsTokenKind(child, tokenKindId))
                return true;
        }
        return false;
    }

    /**
     * Finds the first identifier-like direct token child after the specified keyword token.
     *
     * @param node the node whose immediate children should be inspected
     * @param keywordTokenType the keyword after which to search
     * @return the identifier text, or {@code null} if the keyword or a following identifier is absent
     */
    public static @Nullable String identifierAfterKeyword(SyntaxNode node, JavaTokenType keywordTokenType) {
        String keywordKindId = JavaSyntaxKinds.tokenKind(keywordTokenType).id();
        boolean foundKeyword = false;
        for (SyntaxNode child : node.children()) {
            if (!(child instanceof SyntaxToken token))
                continue;

            String tokenKindId = token.kind().id();
            if (!foundKeyword) {
                if (keywordKindId.equals(tokenKindId)) {
                    foundKeyword = true;
                }
                continue;
            }

            if (isIdentifierLikeToken(token))
                return JavaUnicodeEscapes.translate(token.text());
        }
        return null;
    }

    /**
     * Finds the last identifier-like direct token child preceding the first child of the requested kind.
     * If that kind is absent, the last identifier-like direct token child is used.
     *
     * @param node the node whose immediate children should be inspected
     * @param childKindId the syntax kind identifier at which to stop searching
     * @return the identifier text, or {@code null} if no preceding identifier exists
     */
    public static @Nullable String identifierBeforeChildKind(SyntaxNode node, String childKindId) {
        String lastIdentifier = null;
        for (SyntaxNode child : node.children()) {
            if (childKindId.equals(child.kind().id()))
                return lastIdentifier;

            if (child instanceof SyntaxToken token && isIdentifierLikeToken(token)) {
                lastIdentifier = JavaUnicodeEscapes.translate(token.text());
            }
        }
        return lastIdentifier;
    }

    /**
     * Finds the first identifier or contextual keyword token in a subtree, ignoring missing tokens.
     *
     * @param node the subtree to search in child order
     * @return the first identifier-like token's text, or {@code null} if none exists
     */
    public static @Nullable String firstIdentifierLikeTokenText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return isIdentifierLikeToken(token) ? JavaUnicodeEscapes.translate(token.text()) : null;

        for (SyntaxNode child : node.children()) {
            String identifier = firstIdentifierLikeTokenText(child);
            if (identifier != null)
                return identifier;
        }

        return null;
    }

    /**
     * Finds the last identifier or contextual keyword token in a subtree, ignoring missing tokens.
     *
     * @param node the subtree to search in reverse child order
     * @return the last identifier-like token's text, or {@code null} if none exists
     */
    public static @Nullable String lastIdentifierLikeTokenText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return isIdentifierLikeToken(token) ? JavaUnicodeEscapes.translate(token.text()) : null;
        for (int index = node.children().size() - 1; index >= 0; index--) {
            String identifier = lastIdentifierLikeTokenText(node.children().get(index));
            if (identifier != null)
                return identifier;
        }
        return null;
    }

    /**
     * Collects all tokens in a subtree in child order, including trivia and missing tokens.
     *
     * @param node the subtree whose tokens should be collected
     * @return an immutable list of the subtree's tokens
     */
    public static List<SyntaxToken> leafTokens(SyntaxNode node) {
        List<SyntaxToken> tokens = new ArrayList<>();
        collectLeafTokens(node, tokens);
        return List.copyOf(tokens);
    }

    private static void collectLeafTokens(SyntaxNode node, List<SyntaxToken> out) {
        if (node instanceof SyntaxToken token) {
            out.add(token);
            return;
        }

        for (SyntaxNode child : node.children()) {
            collectLeafTokens(child, out);
        }
    }

    private static boolean isIdentifierLikeToken(SyntaxToken token) {
        String kindId = token.kind().id();
        return IDENTIFIER_LIKE_TOKEN_KIND_IDS.contains(kindId) && !isMissingTokenKind(kindId);
    }

    /**
     * Tests whether a syntax kind identifier denotes a token inserted by parser recovery.
     *
     * @param kindId the syntax kind identifier to inspect
     * @return whether the identifier starts with {@code JAVA_MISSING_}
     */
    public static boolean isMissingTokenKind(String kindId) {
        return kindId.startsWith("JAVA_MISSING_");
    }

    /**
     * Tests whether a token represents whitespace, a line terminator, or a Java comment.
     *
     * @param token the token to classify
     * @return whether the token is trivia
     */
    public static boolean isTriviaToken(SyntaxToken token) {
        return TRIVIA_TOKEN_KIND_IDS.contains(token.kind().id());
    }

    /**
     * Joins identifier-like tokens, dots, and wildcard stars into a qualified name.
     * Trivia, missing tokens, and other token kinds are omitted.
     *
     * @param node the qualified-name subtree to read
     * @return the concatenated name, or {@code null} if no name tokens exist
     */
    public static @Nullable String canonicalQualifiedName(SyntaxNode node) {
        var builder = new StringBuilder();
        appendCanonicalQualifiedNameTokens(node, builder);
        if (builder.isEmpty())
            return null;
        return builder.toString();
    }

    private static void appendCanonicalQualifiedNameTokens(SyntaxNode node, StringBuilder builder) {
        if (node instanceof SyntaxToken token) {
            if (isTriviaToken(token) || isMissingTokenKind(token.kind().id()))
                return;

            String kindId = token.kind().id();
            if (isIdentifierLikeToken(token)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.DOT).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.STAR).id().equals(kindId)) {
                builder.append(JavaUnicodeEscapes.translate(token.text()));
            }
            return;
        }

        for (SyntaxNode child : node.children()) {
            appendCanonicalQualifiedNameTokens(child, builder);
        }
    }

    /**
     * Joins tokens describing a Java type, including generic and array syntax, into compact text.
     * Annotations, trivia, missing tokens, and unrelated token kinds are omitted.
     *
     * @param node the type subtree to read
     * @return the concatenated type text, or {@code null} if no type tokens exist
     */
    public static @Nullable String canonicalTypeText(SyntaxNode node) {
        var builder = new StringBuilder();
        appendCanonicalTypeTokens(node, builder);
        if (builder.isEmpty())
            return null;
        return builder.toString();
    }

    private static void appendCanonicalTypeTokens(SyntaxNode node, StringBuilder builder) {
        if (JavaSyntaxKinds.ANNOTATION.id().equals(node.kind().id()))
            return;

        if (node instanceof SyntaxToken token) {
            String kindId = token.kind().id();
            if (isTriviaToken(token) || isMissingTokenKind(kindId))
                return;

            if (isIdentifierLikeToken(token)
                || PRIMITIVE_TOKEN_KIND_IDS.contains(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.VOID_KEYWORD).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.DOT).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.LEFT_ANGLED_BRACKET).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.RIGHT_ANGLED_BRACKET).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.RIGHT_SHIFT).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.UNSIGNED_RIGHT_SHIFT).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.COMMA).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.QUESTION_MARK).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.EXTENDS_KEYWORD).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.SUPER_KEYWORD).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.AMPERSAND).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.PIPE).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_BRACKET).id().equals(kindId)
                || JavaSyntaxKinds.tokenKind(JavaTokenType.CLOSE_BRACKET).id().equals(kindId)) {
                builder.append(JavaUnicodeEscapes.translate(token.text()));
            }
            return;
        }

        for (SyntaxNode child : node.children()) {
            appendCanonicalTypeTokens(child, builder);
        }
    }

    /**
     * Tests whether a symbol kind represents a class, interface, enum, annotation, or record.
     *
     * @param symbolKind the symbol kind to classify
     * @return whether the kind represents a declared Java type
     */
    public static boolean isTypeSymbol(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
                JavaSymbolKind.RECORD -> true;
            default -> false;
        };
    }

    private static boolean isTypeDeclarationSyntaxKind(String kindId) {
        return JavaSyntaxKinds.CLASS_DECLARATION.id().equals(kindId)
            || JavaSyntaxKinds.INTERFACE_DECLARATION.id().equals(kindId)
            || JavaSyntaxKinds.ENUM_DECLARATION.id().equals(kindId)
            || JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id().equals(kindId)
            || JavaSyntaxKinds.RECORD_DECLARATION.id().equals(kindId);
    }

    /**
     * Tests whether a node is the selected name of its enclosing field access or method invocation.
     *
     * @param node the possible selector name expression
     * @return whether the node is the selector name of a supported parent expression
     */
    public static boolean isSelectorNameExpression(SyntaxNode node) {
        var parent = node.parent();
        if (parent.isEmpty())
            return false;
        String parentKindId = parent.get().kind().id();
        if (!JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(parentKindId)
            && !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(parentKindId))
            return false;
        return selectorNameNode(parent.get()) == node;
    }

    /**
     * Finds the last direct name-expression child, skipping argument lists.
     *
     * @param node the field access or method invocation to inspect
     * @return the selector name node, or {@code null} if no direct name expression exists
     */
    public static @Nullable SyntaxNode selectorNameNode(SyntaxNode node) {
        for (int index = node.children().size() - 1; index >= 0; index--) {
            SyntaxNode child = node.children().get(index);
            if (JavaSyntaxKinds.ARGUMENT_LIST.id().equals(child.kind().id()))
                continue;
            if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(child.kind().id()))
                return child;
        }
        return null;
    }

    /**
     * Finds the receiver expression of a qualified member access or invocation.
     * A dot must appear among the direct children before any argument list.
     *
     * @param node the member access or invocation to inspect
     * @return its first expression child when explicitly qualified, or {@code null} if none is found
     */
    public static @Nullable SyntaxNode explicitReceiver(SyntaxNode node) {
        boolean sawDot = false;
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token
                && JavaSyntaxKinds.tokenKind(JavaTokenType.DOT).id().equals(token.kind().id())) {
                sawDot = true;
                break;
            }
            if (JavaSyntaxKinds.ARGUMENT_LIST.id().equals(child.kind().id()))
                break;
        }
        if (!sawDot)
            return null;

        for (SyntaxNode child : node.children()) {
            if (isExpressionNode(child))
                return child;
        }
        return null;
    }

    /**
     * Tests whether a node has one of the Java expression syntax kinds recognized by semantic analysis.
     *
     * @param node the syntax node to classify
     * @return whether the node represents an expression
     */
    public static boolean isExpressionNode(SyntaxNode node) {
        return EXPRESSION_KIND_IDS.contains(node.kind().id());
    }

    /**
     * Extracts the text after the last dot, retaining names with no dot or a trailing dot unchanged.
     *
     * @param qualifiedName the dotted name to shorten
     * @return the final nonempty segment, or the original name if it has no such suffix
     */
    public static String lastSegment(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        if (index < 0 || index == qualifiedName.length() - 1)
            return qualifiedName;
        return qualifiedName.substring(index + 1);
    }

    /**
     * Removes generic arguments, array suffixes, and package or enclosing-type qualifiers from a type name.
     * Both dotted and dollar-separated enclosing-type names are supported.
     *
     * @param displayName the type's display name
     * @return the simple name of the underlying type
     */
    public static String simpleTypeName(String displayName) {
        String text = eraseTypeArguments(displayName);

        while (text.endsWith("[]")) {
            text = text.substring(0, text.length() - 2);
        }

        int nestedIndex = text.lastIndexOf('$');
        if (nestedIndex >= 0 && nestedIndex < text.length() - 1) {
            text = text.substring(nestedIndex + 1);
        }

        return lastSegment(text);
    }

    /**
     * Removes angle-bracketed generic arguments, including nested arguments, and trims the remaining text.
     * Null and blank input are returned unchanged.
     *
     * @param displayName the type text to erase, which may be {@code null}
     * @return the text without generic arguments, or the original null or blank input
     */
    public static String eraseTypeArguments(String displayName) {
        if (displayName == null || displayName.isBlank())
            return displayName;

        var builder = new StringBuilder(displayName.length());
        int depth = 0;
        for (int index = 0; index < displayName.length(); index++) {
            char ch = displayName.charAt(index);
            if (ch == '<') {
                depth++;
                continue;
            }
            if (ch == '>') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0) {
                builder.append(ch);
            }
        }
        return builder.toString().trim();
    }

    /**
     * Extracts the prefix before the last dot in a qualified name.
     *
     * @param qualifiedName the dotted name to inspect
     * @return the prefix, or an empty string if no dot follows the first character
     */
    public static String packagePrefix(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        if (index <= 0)
            return "";
        return qualifiedName.substring(0, index);
    }

    private record ImportSpec(
        SyntaxNode declarationNode,
        SyntaxNode targetNode,
        String qualifiedTarget,
        String ownerName,
        String importedName,
        boolean isStatic,
        boolean isWildcard
    ) {
    }
}
