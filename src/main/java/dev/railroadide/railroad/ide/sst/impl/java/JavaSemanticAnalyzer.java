package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.classparser.stub.*;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.indexing.Indexes;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

/**
 * Java semantic analysis entry point.
 * <p>
 * Current pipeline:
 * 1) declaration collection
 * 2) name resolution
 * 3) baseline type inference/checking
 */
public final class JavaSemanticAnalyzer {
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

    public static SemanticModel analyze(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyze(JavaSyntaxParser.parse(source));
    }

    public static SemanticModel analyze(CharSequence source, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(source, "source");
        return analyze(JavaSyntaxParser.parse(source), projectIndex);
    }

    public static SemanticModel analyze(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeFacts(syntaxTree));
    }

    public static SemanticModel analyze(SyntaxTree syntaxTree, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeFacts(syntaxTree, projectIndex));
    }

    public static SemanticModel analyzeFacts(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeFacts(JavaSyntaxParser.parse(source));
    }

    public static SemanticModel analyzeFacts(CharSequence source, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(source, "source");
        return analyzeFacts(JavaSyntaxParser.parse(source), projectIndex);
    }

    public static SemanticModel analyzeFacts(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return performAnalysis(syntaxTree, true);
    }

    public static SemanticModel analyzeFacts(SyntaxTree syntaxTree, JavaSymbolIndex projectIndex) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        Objects.requireNonNull(projectIndex, "projectIndex");
        return performAnalysis(syntaxTree, true, projectIndex);
    }

    public static SemanticModel analyzeDeclarations(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeDeclarations(JavaSyntaxParser.parse(source));
    }

    public static SemanticModel analyzeDeclarations(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeDeclarationsFacts(syntaxTree));
    }

    public static SemanticModel analyzeDeclarationsFacts(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeDeclarationsFacts(JavaSyntaxParser.parse(source));
    }

    public static SemanticModel analyzeDeclarationsFacts(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return performAnalysis(syntaxTree, false);
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
        Scope rootScope = Scope.root();
        SemanticModel.Builder builder = SemanticModel.builder(syntaxTree, rootScope);

        var context = new JavaDeclarationAnalysis.Context(syntaxTree.root(), rootScope, builder, projectIndex);
        JavaDeclarationAnalysis.collect(context, syntaxTree.root());

        if (includeResolutionAndTypes) {
            var nameResolver = new NameResolver(context);
            nameResolver.resolveCompilationUnit(syntaxTree.root());
            new TypeResolver(context).resolveCompilationUnit(syntaxTree.root());
            nameResolver.resolveDeferredCallables(syntaxTree.root());
        }

        return builder.build();
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
                symbol.qualifiedName().ifPresent(qualified::add);
            }

            this.localQualifiedTypeNames = Set.copyOf(qualified);
            if (projectIndex != null) {
                this.availableQualifiedTypeNames = projectIndex.declaredQualifiedNames();
                this.binaryClassStubsByQualifiedName = projectIndex.classStubsByQualifiedName();
            } else {
                this.availableQualifiedTypeNames = loadJdkQualifiedTypeNames();
                this.binaryClassStubsByQualifiedName = loadJdkClassStubsByQualifiedName();
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
                String ownerQualifiedName = enclosingType == null
                    ? null
                    : enclosingType.qualifiedName().orElse(null);
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
                resolveNameFromImports(expressionNode, simpleName, name);
                return;
            }

            context.resolve(expressionNode, matches.getFirst());
        }

        private boolean isStaticContext(SyntaxNode usageSite) {
            SyntaxNode current = usageSite.parent().orElse(null);
            while (current != null) {
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
            return kind == SymbolKind.FIELD
                || kind == SymbolKind.PARAMETER
                || kind == SymbolKind.LOCAL_VARIABLE;
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
                    if (targetType.kind() == Type.Kind.TYPE_VARIABLE) {
                        for (String bound : qualifiedTypeVariableBounds(targetType.displayName(), invocationNode)) {
                            chosen = resolveCallableOnOwner(
                                bound, methodName, false, argumentTypes, CallableKind.METHOD, false);
                            if (chosen != null)
                                break;
                        }
                    }
                }
                if (chosen == null) {
                    for (String bound : qualifiedResolvedReceiverTypeVariableBounds(targetNode, invocationNode)) {
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
                if (inferredType.kind() != Type.Kind.UNKNOWN) {
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
            if (enclosingType != null) {
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
            List<Type.DeclaredType> semanticSupers = resolvedExpressionTypeResolver
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
                if (inferredType.kind() != Type.Kind.UNKNOWN) {
                    context.type(invocationNode, inferredType);
                }
                return;
            }

            Symbol enclosingType = nearestEnclosingTypeSymbol(invocationNode);
            if (enclosingType == null)
                return;

            String ownerQualifiedName = enclosingType.qualifiedName().orElse(null);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return;

            MemberCandidate chosen = resolveCallableOnOwner(
                ownerQualifiedName,
                methodName,
                false,
                argumentTypes,
                CallableKind.METHOD,
                true);
            if (chosen == null && hasComplexArgumentShape(argumentList)) {
                List<MemberCandidate> candidates = collectCallableCandidates(ownerQualifiedName, methodName, false,
                    CallableKind.METHOD);
                if (candidates.size() == 1) {
                    chosen = candidates.getFirst();
                }
            }
            if (chosen == null)
                return;

            context.resolve(invocationNode, chosen.symbol());
            if (memberNode != null) {
                context.resolve(memberNode, chosen.symbol());
            }
            Type inferredType = inferMethodInvocationTypeForResolution(invocationNode);
            if (inferredType.kind() != Type.Kind.UNKNOWN) {
                context.type(invocationNode, inferredType);
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
                    if (isArityCompatible(candidate.parameterTypes(), argumentTypes.size())) {
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
                if (isArityCompatible(candidate.parameterTypes(), argumentCount)) {
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
                if (isArityCompatible(candidate.parameterTypes(), argumentTypes.size())) {
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1)
                return matches.getFirst();
            return allCandidates.size() == 1 ? allCandidates.getFirst() : null;
        }

        private @Nullable String genericReceiverOwnerQualifiedName(SyntaxNode targetNode) {
            Type inferred = context.inferredType(targetNode);
            if (inferred != null && inferred.kind() == Type.Kind.DECLARED && inferred.displayName().contains("<"))
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
                if (resolved.kind() == SymbolKind.CONSTRUCTOR)
                    return ownerQualifiedName(resolved);
            }

            Type inferred = context.inferredType(expressionNode);
            if (inferred != null && inferred.kind() == Type.Kind.DECLARED)
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
                for (Symbol symbol : symbols) {
                    if (symbol.kind() != SymbolKind.IMPORT)
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
                if (symbol.kind() != SymbolKind.FIELD && symbol.kind() != SymbolKind.METHOD)
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
                if (symbol.kind() == SymbolKind.FIELD) {
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
                    findFieldCandidates(importSpec.ownerName(), fieldName, true).stream()
                        .map(MemberCandidate::symbol)
                        .forEach(resolved::add);
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
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
                    if (hasResolvableStaticMethod(importSpec.ownerName(), methodName, argumentCountOrUnknown)) {
                        resolved.add(new SimpleSymbol(
                            SymbolKind.METHOD,
                            methodName,
                            importSpec.ownerName() + "#" + methodName,
                            importSpec.targetNode()));
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                if (hasResolvableStaticMethod(onDemandImport.ownerName(), methodName, argumentCountOrUnknown)) {
                    resolved.add(new SimpleSymbol(
                        SymbolKind.METHOD,
                        methodName,
                        onDemandImport.ownerName() + "#" + methodName,
                        invocationNode));
                }
            }

            return uniqueByQualifiedName(resolved);
        }

        private void indexLocalMembers() {
            for (Symbol symbol : context.allDeclaredSymbols()) {
                String ownerQualifiedName = ownerQualifiedName(symbol);
                if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                    continue;

                if (symbol.kind() == SymbolKind.FIELD) {
                    MemberCandidate candidate = localFieldCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localFieldsByOwner
                            .computeIfAbsent(ownerQualifiedName, _ -> new LinkedHashMap<>())
                            .computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>())
                            .add(candidate);
                    }
                } else if (symbol.kind() == SymbolKind.METHOD) {
                    MemberCandidate candidate = localMethodCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localMethodsByOwner
                            .computeIfAbsent(ownerQualifiedName, _ -> new LinkedHashMap<>())
                            .computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>())
                            .add(candidate);
                    }
                } else if (symbol.kind() == SymbolKind.CONSTRUCTOR) {
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
                typeOfMethodDeclaration(symbol), parameterTypes(declaration));
        }

        private @Nullable MemberCandidate localConstructorCandidate(Symbol symbol, String ownerQualifiedName) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return null;
            return new MemberCandidate(symbol, ownerQualifiedName, false,
                new Type.DeclaredType(ownerQualifiedName, List.of()), parameterTypes(declaration));
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

        private List<Type> parameterTypes(SyntaxNode declarationNode) {
            SyntaxNode parameterList = directChild(declarationNode, JavaSyntaxKinds.PARAMETER_LIST.id());
            if (parameterList == null)
                return List.of();

            List<Type> types = new ArrayList<>();
            for (SyntaxNode child : parameterList.children()) {
                if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id()))
                    continue;
                SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                Type parameterType = typeRef == null
                    ? new Type.UnknownType("<unknown>")
                    : typeFromTypeReferenceForResolution(typeRef);
                if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                    parameterType = new Type.ArrayType(parameterType);
                }
                types.add(parameterType);
            }
            return List.copyOf(types);
        }

        private Type typeOfMethodDeclaration(Symbol methodSymbol) {
            SyntaxNode declaration = methodSymbol.declaration().orElse(null);
            if (declaration == null)
                return new Type.UnknownType("<unknown>");
            SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null ? new Type.UnknownType("<unknown>") : typeFromTypeReferenceForResolution(typeRef);
        }

        private Type typeOfFieldDeclaration(Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return new Type.UnknownType("<unknown>");

            if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(declaration.kind().id())) {
                Symbol enclosingEnum = nearestEnclosingTypeSymbol(declaration);
                if (enclosingEnum != null)
                    return new Type.DeclaredType(
                        enclosingEnum.qualifiedName().orElse(enclosingEnum.simpleName()), List.of());
            }

            if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                var parent = declaration.parent();
                while (parent.isPresent()) {
                    SyntaxNode candidate = parent.get();
                    if (hasDirectTokenKind(candidate, JavaTokenType.VAR_KEYWORD)) {
                        List<SyntaxNode> expressions = new ArrayList<>();
                        for (SyntaxNode child : declaration.children()) {
                            if (isExpressionNode(child)) {
                                expressions.add(child);
                            }
                        }
                        return expressions.isEmpty()
                            ? new Type.UnknownType("<unknown>")
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
                        if (elementType.kind() != Type.Kind.UNKNOWN) {
                            parameterType = elementType;
                        }
                    }
                    return hasTokenKind(declaration, JavaTokenType.ELLIPSIS)
                        ? new Type.ArrayType(parameterType)
                        : parameterType;
                }
                if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                    && enclosingNode(declaration, JavaSyntaxKinds.LAMBDA_EXPRESSION.id()) != null)
                    return contextualLambdaParameterType(declaration);
            }
            if (JavaSyntaxKinds.LAMBDA_PARAMETER.id().equals(declaration.kind().id()))
                return contextualLambdaParameterType(declaration);

            return new Type.UnknownType("<unknown>");
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
                return new Type.UnknownType("<unknown>");
            int parameterIndex = lambdaParameterNodes(lambda).indexOf(parameterDeclaration);
            return parameterIndex < 0
                ? new Type.UnknownType("<unknown>")
                : contextualLambdaParameterType(lambda, parameterIndex);
        }

        private Type contextualLambdaParameterType(SyntaxNode lambda, int parameterIndex) {
            SyntaxNode contextualExpression = lambda;
            SyntaxNode castParent = contextualExpression.parent().orElse(null);
            while (castParent != null
                && JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(castParent.kind().id())) {
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
            if (directTargetType.kind() != Type.Kind.UNKNOWN)
                return functionalParameterType(directTargetType, parameterIndex);

            SyntaxNode argumentList = lambda.parent().orElse(null);
            while (argumentList != null && !JavaSyntaxKinds.ARGUMENT_LIST.id().equals(argumentList.kind().id())) {
                argumentList = argumentList.parent().orElse(null);
            }
            if (argumentList == null)
                return new Type.UnknownType("<unknown>");

            SyntaxNode invocation = argumentList.parent().orElse(null);
            if (invocation == null
                || !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(invocation.kind().id())
                    && !JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(invocation.kind().id()))
                return new Type.UnknownType("<unknown>");

            Symbol callable = context.resolvedSymbol(invocation);
            if (callable == null) {
                resolveMethodInvocation(invocation);
                callable = context.resolvedSymbol(invocation);
            }
            if (callable == null)
                return new Type.UnknownType("<unknown>");

            int argumentIndex = expressionChildIndex(argumentList, lambda);
            List<Type> callableParameters = specializedCallableParameterTypes(invocation, argumentList, lambda,
                callable);
            if (argumentIndex < 0 || argumentIndex >= callableParameters.size())
                return new Type.UnknownType("<unknown>");

            return functionalParameterType(callableParameters.get(argumentIndex), parameterIndex);
        }

        private Type directlyContextualFunctionalType(SyntaxNode lambda) {
            SyntaxNode contextualExpression = lambda;
            SyntaxNode parent = contextualExpression.parent().orElse(null);
            while (parent != null && (JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(parent.kind().id())
                || JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id().equals(parent.kind().id()))) {
                contextualExpression = parent;
                parent = contextualExpression.parent().orElse(null);
            }
            if (parent == null)
                return new Type.UnknownType("<unknown>");

            if (JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id().equals(parent.kind().id())) {
                for (SyntaxNode child : parent.children()) {
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
                    SyntaxNode typeRef = directChild(current, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null)
                        return typeFromTypeReferenceForResolution(typeRef);
                    if (JavaSyntaxKinds.BLOCK.id().equals(current.kind().id()))
                        break;
                    current = current.parent().orElse(null);
                }
            }
            return new Type.UnknownType("<unknown>");
        }

        private Type enhancedForElementType(SyntaxNode parameterDeclaration) {
            SyntaxNode enhancedFor = parameterDeclaration.parent().orElse(null);
            if (enhancedFor == null
                || !JavaSyntaxKinds.ENHANCED_FOR_STATEMENT.id().equals(enhancedFor.kind().id()))
                return new Type.UnknownType("<unknown>");
            SyntaxNode iterableExpression = enhancedFor.children().stream()
                .filter(JavaSemanticAnalyzer::isExpressionNode)
                .findFirst()
                .orElse(null);
            if (iterableExpression == null)
                return new Type.UnknownType("<unknown>");

            Type iterableType = inferExpressionTypeForResolution(iterableExpression);
            if (iterableType instanceof Type.ArrayType arrayType)
                return arrayType.componentType();
            if (!(iterableType instanceof Type.DeclaredType declaredType))
                return new Type.UnknownType("<unknown>");

            Type.DeclaredType iterableView = resolvedExpressionTypeResolver.declaredViewAs(
                declaredType, "java.lang.Iterable", new HashSet<>());
            if (iterableView == null || iterableView.typeArguments().isEmpty())
                return new Type.UnknownType("<unknown>");
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
            if (contextualTarget.kind() != Type.Kind.UNKNOWN) {
                bindTypeVariables(typeOfResolvedSymbol(callable), contextualTarget, substitutions);
            }

            return parameterTypes.stream()
                .map(parameterType -> substituteFunctionalType(parameterType, substitutions))
                .toList();
        }

        private Type contextualInvocationTargetType(SyntaxNode invocation) {
            if (!contextualInferenceInProgress.add(invocation))
                return new Type.UnknownType("<unknown>");
            try {
                Type directTarget = directlyContextualFunctionalType(invocation);
                if (directTarget.kind() != Type.Kind.UNKNOWN)
                    return directTarget;

                SyntaxNode outerArgumentList = invocation.parent().orElse(null);
                if (outerArgumentList == null
                    || !JavaSyntaxKinds.ARGUMENT_LIST.id().equals(outerArgumentList.kind().id()))
                    return new Type.UnknownType("<unknown>");
                SyntaxNode outerInvocation = outerArgumentList.parent().orElse(null);
                if (outerInvocation == null
                    || !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(outerInvocation.kind().id())
                        && !JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(outerInvocation.kind().id()))
                    return new Type.UnknownType("<unknown>");

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
                    return new Type.UnknownType("<unknown>");

                int argumentIndex = expressionChildIndex(outerArgumentList, invocation);
                List<Type> outerParameters = specializedCallableParameterTypes(
                    outerInvocation, outerArgumentList, invocation, outerCallable);
                return argumentIndex < 0 || argumentIndex >= outerParameters.size()
                    ? new Type.UnknownType("<unknown>")
                    : outerParameters.get(argumentIndex);
            } finally {
                contextualInferenceInProgress.remove(invocation);
            }
        }

        private @Nullable SyntaxNode enclosingNode(SyntaxNode node, String kindId) {
            SyntaxNode current = node.parent().orElse(null);
            while (current != null) {
                if (kindId.equals(current.kind().id()))
                    return current;
                current = current.parent().orElse(null);
            }
            return null;
        }

        private int expressionChildIndex(SyntaxNode parent, SyntaxNode target) {
            int index = 0;
            for (SyntaxNode child : parent.children()) {
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
            if (!(functionalType instanceof Type.DeclaredType declared))
                return new Type.UnknownType("<unknown>");

            String rawType = eraseTypeArguments(declared.displayName());
            String qualifiedType = resolveQualifiedTypeName(rawType, null);
            if (qualifiedType == null)
                return new Type.UnknownType("<unknown>");

            return functionalParameterType(declared, qualifiedType, parameterIndex, new HashSet<>());
        }

        private Type functionalParameterType(
            Type.DeclaredType functionalType,
            String qualifiedType,
            int parameterIndex,
            Set<String> visited
        ) {
            if (!visited.add(qualifiedType))
                return new Type.UnknownType("<unknown>");
            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedType);
            if (stub == null)
                return sourceFunctionalParameterType(qualifiedType, parameterIndex);

            Map<String, Type> substitutions = new LinkedHashMap<>();
            int typeArgumentCount = Math.min(stub.typeParameters().size(), functionalType.typeArguments().size());
            for (int index = 0; index < typeArgumentCount; index++) {
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
                    return new Type.UnknownType("<unknown>");
                Type parameterType = substituteFunctionalType(
                    toSemanticType(sam.parameters().get(parameterIndex).type()), substitutions);
                return effectiveWildcardBound(parameterType);
            }

            for (dev.railroadide.railroad.ide.classparser.Type parentInterface : stub.interfaces()) {
                Type specializedParent = substituteFunctionalType(toSemanticType(parentInterface), substitutions);
                if (!(specializedParent instanceof Type.DeclaredType parentDeclared))
                    continue;
                String parentQualifiedType = resolveFunctionalSuperTypeName(parentDeclared, qualifiedType);
                if (parentQualifiedType == null)
                    continue;
                Type inheritedParameter = functionalParameterType(
                    parentDeclared, parentQualifiedType, parameterIndex, visited);
                if (inheritedParameter.kind() != Type.Kind.UNKNOWN)
                    return inheritedParameter;
            }
            return new Type.UnknownType("<unknown>");
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
                    : new Type.UnknownType("<unknown>");
            }

            List<JavaRuleContext.MethodDescriptor> projectMethods = projectSourceMethodDescriptors(qualifiedType)
                .stream()
                .filter(method -> method.isAbstract()
                    && !Modifier.isStatic(method.modifiers()))
                .filter(method -> !isObjectMethodSignature(method.name(), method.parameterTypes().size()))
                .toList();
            if (projectMethods.size() != 1)
                return new Type.UnknownType("<unknown>");
            List<Type> parameterTypes = projectMethods.getFirst().parameterTypes();
            return parameterIndex < parameterTypes.size()
                ? effectiveWildcardBound(parameterTypes.get(parameterIndex))
                : new Type.UnknownType("<unknown>");
        }

        private @Nullable String resolveFunctionalSuperTypeName(
            Type.DeclaredType parentType,
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
            while (current instanceof Type.WildcardType wildcard) {
                if (wildcard.lowerBound() != null) {
                    current = wildcard.lowerBound();
                } else if (wildcard.upperBound() != null) {
                    current = wildcard.upperBound();
                } else
                    return new Type.UnknownType("<unknown>");
            }
            return current;
        }

        private Type substituteFunctionalType(Type type, Map<String, Type> substitutions) {
            return switch (type) {
                case Type.TypeVariableType variable -> substitutions.getOrDefault(variable.displayName(), variable);
                case Type.ArrayType array ->
                    new Type.ArrayType(substituteFunctionalType(array.componentType(), substitutions));
                case Type.WildcardType wildcard -> new Type.WildcardType(
                    wildcard.upperBound() == null
                        ? null
                        : substituteFunctionalType(wildcard.upperBound(), substitutions),
                    wildcard.lowerBound() == null
                        ? null
                        : substituteFunctionalType(wildcard.lowerBound(), substitutions));
                case Type.DeclaredType declared -> new Type.DeclaredType(
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
                if (!isExpressionNode(child))
                    continue;
                if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(child.kind().id())
                    || JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(child.kind().id())) {
                    String marker = JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(child.kind().id())
                        ? POLY_FUNCTIONAL_ARGUMENT + ":" + lambdaParameterNodes(child).size() + ">"
                        : POLY_FUNCTIONAL_ARGUMENT + ">";
                    types.add(new Type.UnknownType(marker));
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
                String kindId = token.kind().id();
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.BOOLEAN_LITERAL).id().equals(kindId))
                    return new Type.PrimitiveType("boolean");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_FLOATING_POINT_LITERAL).id().equals(kindId))
                    return numericLiteralType(token.text(), true);
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.CHARACTER_LITERAL).id().equals(kindId))
                    return new Type.PrimitiveType("char");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.STRING_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.TEXT_BLOCK_LITERAL).id().equals(kindId))
                    return new Type.DeclaredType("java.lang.String", List.of());
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_INT_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_HEXADECIMAL_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_BINARY_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_OCTAL_LITERAL).id().equals(kindId))
                    return numericLiteralType(token.text(), false);
            }
            return new Type.UnknownType("<unknown>");
        }

        private Type inferClassLiteralTypeForResolution(SyntaxNode classLiteralExpression) {
            SyntaxNode typeRef = directChild(classLiteralExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type literalType = typeRef == null
                ? new Type.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
            return new Type.DeclaredType("java.lang.Class", List.of(literalType));
        }

        private Type inferArrayCreationTypeForResolution(SyntaxNode arrayCreationExpression) {
            SyntaxNode typeRef = directChild(arrayCreationExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type type = typeRef == null
                ? new Type.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
            for (SyntaxNode child : arrayCreationExpression.children()) {
                if (child instanceof SyntaxToken token
                    && JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_BRACKET).id().equals(token.kind().id())) {
                    type = new Type.ArrayType(type);
                }
            }
            return type;
        }

        private Type inferArrayAccessTypeForResolution(SyntaxNode arrayAccessExpression) {
            Type receiverType = firstExpressionChildType(arrayAccessExpression);
            return receiverType instanceof Type.ArrayType array
                ? array.componentType()
                : new Type.UnknownType("<unknown>");
        }

        private Type inferredTypeForResolvedSymbol(SyntaxNode node) {
            Type lambdaParameterType = contextualLambdaReferenceType(node);
            if (lambdaParameterType.kind() != Type.Kind.UNKNOWN)
                return lambdaParameterType;
            Symbol symbol = context.resolvedSymbol(node);
            return symbol == null ? new Type.UnknownType("<unknown>") : typeOfResolvedSymbol(symbol);
        }

        private Type contextualLambdaReferenceType(SyntaxNode referenceNode) {
            String referenceName = canonicalQualifiedName(referenceNode);
            if (referenceName == null || referenceName.indexOf('.') >= 0)
                return new Type.UnknownType("<unknown>");

            SyntaxNode lambda = containingLambda(referenceNode);
            if (lambda == null)
                return new Type.UnknownType("<unknown>");

            List<SyntaxNode> parameters = lambdaParameterNodes(lambda);
            for (int index = 0; index < parameters.size(); index++) {
                SyntaxNode parameter = parameters.get(index);
                if (!referenceName.equals(lastIdentifierLikeTokenText(parameter)))
                    continue;
                SyntaxNode typeRef = directChild(parameter, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null && !"var".equals(canonicalTypeText(typeRef)))
                    return typeFromTypeReferenceForResolution(typeRef);
                return contextualLambdaParameterType(lambda, index);
            }
            return new Type.UnknownType("<unknown>");
        }

        private @Nullable SyntaxNode containingLambda(SyntaxNode node) {
            return deepestContainingLambda(context.syntaxRoot, node.start(), node.end());
        }

        private @Nullable SyntaxNode deepestContainingLambda(SyntaxNode node, int start, int end) {
            if (node.start() > start || node.end() < end)
                return null;

            for (SyntaxNode child : node.children()) {
                SyntaxNode nested = deepestContainingLambda(child, start, end);
                if (nested != null)
                    return nested;
            }
            return JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(node.kind().id()) ? node : null;
        }

        private Type inferMethodInvocationTypeForResolution(SyntaxNode invocationNode) {
            Symbol symbol = context.resolvedSymbol(invocationNode);
            if (symbol == null)
                return new Type.UnknownType("<unknown>");

            Type rawType = typeOfResolvedSymbol(symbol);
            if (symbol.kind() != SymbolKind.METHOD)
                return rawType;

            SyntaxNode receiverNode = explicitReceiver(invocationNode);
            Type receiverType = receiverNode == null
                ? resolvedExpressionTypeResolver.invocationReceiverType(invocationNode, symbol)
                : contextualLambdaReferenceType(receiverNode);
            if (receiverNode != null && receiverType.kind() == Type.Kind.UNKNOWN) {
                Type inferredReceiver = context.inferredType(receiverNode);
                if (inferredReceiver != null) {
                    receiverType = inferredReceiver;
                }
            }
            if (receiverType.kind() == Type.Kind.UNKNOWN) {
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
            if (parameterType instanceof Type.TypeVariableType variable) {
                Type boundArgument = argumentType instanceof Type.WildcardType wildcard
                    ? wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound()
                    : argumentType;
                if (boundArgument != null) {
                    Type existing = substitutions.get(variable.displayName());
                    if (existing == null || isSelfTypeVariable(existing, variable.displayName())) {
                        substitutions.put(variable.displayName(), boundArgument);
                    }
                }
                return;
            }
            if (parameterType instanceof Type.WildcardType wildcard) {
                Type bound = wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound();
                if (bound != null) {
                    bindTypeVariables(bound, argumentType, substitutions);
                }
                return;
            }
            if (parameterType instanceof Type.ArrayType parameterArray
                && argumentType instanceof Type.ArrayType argumentArray) {
                bindTypeVariables(parameterArray.componentType(), argumentArray.componentType(), substitutions);
                return;
            }
            if (parameterType instanceof Type.DeclaredType parameterDeclared
                && argumentType instanceof Type.DeclaredType argumentDeclared
                && sameRawType(parameterDeclared.displayName(), argumentDeclared.displayName())) {
                int count = Math.min(parameterDeclared.typeArguments().size(), argumentDeclared.typeArguments().size());
                for (int index = 0; index < count; index++) {
                    bindTypeVariables(
                        parameterDeclared.typeArguments().get(index),
                        argumentDeclared.typeArguments().get(index),
                        substitutions);
                }
            }
        }

        private void bindFunctionalResultType(
            Type parameterType,
            SyntaxNode argument,
            Map<String, Type> substitutions
        ) {
            Type expectedResult = functionalReturnType(substituteFunctionalType(parameterType, substitutions));
            if (expectedResult.kind() == Type.Kind.UNKNOWN)
                return;

            Type actualResult;
            if (JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id().equals(argument.kind().id())) {
                Type specializedParameter = substituteFunctionalType(parameterType, substitutions);
                TypeResolver.FunctionalSignature signature = resolvedExpressionTypeResolver
                    .functionalSignature(specializedParameter);
                actualResult = signature == null
                    ? new Type.UnknownType("<unknown>")
                    : resolvedExpressionTypeResolver.methodReferenceResultType(argument, signature);
            } else {
                SyntaxNode body = directChild(argument, JavaSyntaxKinds.LAMBDA_BODY.id());
                SyntaxNode expression = body == null
                    ? null
                    : body.children().stream()
                        .filter(JavaSemanticAnalyzer::isExpressionNode)
                        .findFirst()
                        .orElse(null);
                actualResult = expression == null
                    ? new Type.UnknownType("<unknown>")
                    : inferExpressionTypeForResolution(expression);
            }
            if (actualResult.kind() != Type.Kind.UNKNOWN) {
                bindTypeVariables(expectedResult, actualResult, substitutions);
            }
        }

        private Type functionalReturnType(Type functionalType) {
            if (!(functionalType instanceof Type.DeclaredType declared))
                return new Type.UnknownType("<unknown>");
            String qualifiedType = resolveQualifiedTypeNameForCallMatching(declared.displayName());
            if (qualifiedType == null)
                return new Type.UnknownType("<unknown>");
            return functionalReturnType(declared, qualifiedType, new HashSet<>());
        }

        private Type functionalReturnType(
            Type.DeclaredType functionalType,
            String qualifiedType,
            Set<String> visited
        ) {
            if (!visited.add(qualifiedType))
                return new Type.UnknownType("<unknown>");
            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedType);
            if (stub == null)
                return new Type.UnknownType("<unknown>");

            Map<String, Type> substitutions = new LinkedHashMap<>();
            int count = Math.min(stub.typeParameters().size(), functionalType.typeArguments().size());
            for (int index = 0; index < count; index++) {
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
                Type specializedParent = substituteFunctionalType(toSemanticType(parentInterface), substitutions);
                if (!(specializedParent instanceof Type.DeclaredType parentDeclared))
                    continue;
                String parentQualifiedType = resolveFunctionalSuperTypeName(parentDeclared, qualifiedType);
                if (parentQualifiedType == null)
                    continue;
                Type inheritedReturn = functionalReturnType(parentDeclared, parentQualifiedType, visited);
                if (inheritedReturn.kind() != Type.Kind.UNKNOWN)
                    return inheritedReturn;
            }
            return new Type.UnknownType("<unknown>");
        }

        private Type specializeReceiverTypeVariable(Symbol methodSymbol, Type rawType, Type receiverType) {
            Type declaredReturnType = typeOfResolvedSymbol(methodSymbol);
            String variableName = declaredReturnType instanceof Type.TypeVariableType variable
                ? variable.displayName()
                : declaredReturnType.kind() == Type.Kind.DECLARED
                    && isLikelyTypeVariableName(declaredReturnType.displayName())
                        ? declaredReturnType.displayName()
                        : null;
            if (variableName == null || !(receiverType instanceof Type.DeclaredType receiverDeclared))
                return rawType;
            if (rawType.kind() != Type.Kind.TYPE_VARIABLE
                && rawType.kind() != Type.Kind.WILDCARD
                && !(rawType.kind() == Type.Kind.DECLARED
                    && isLikelyTypeVariableName(rawType.displayName())))
                return rawType;

            String ownerQualifiedName = ownerQualifiedName(methodSymbol);
            if (ownerQualifiedName == null)
                return rawType;

            ClassStub ownerStub = classStub(ownerQualifiedName);
            if (ownerStub != null) {
                for (int index = 0; index < ownerStub.typeParameters().size(); index++) {
                    TypeParameter parameter = ownerStub.typeParameters().get(index);
                    if (!parameter.name().equals(variableName))
                        continue;

                    Type declaredUpperBound;
                    if (isSelfBoundTypeParameter(ownerQualifiedName, parameter)) {
                        declaredUpperBound = receiverType;
                    } else if (!parameter.bounds().isEmpty()) {
                        declaredUpperBound = toSemanticType(parameter.bounds().getFirst());
                    } else {
                        declaredUpperBound = new Type.DeclaredType("java.lang.Object", List.of());
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
                if (actual instanceof Type.WildcardType wildcard) {
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
            if (!(actual instanceof Type.WildcardType wildcard))
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
                String source = Files.readString(owner.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(
                    owner.sourceFile(), source, model, projectIndex);
                return findProjectSourceTypeParameterInfo(
                    model.syntaxTree().root(), model, sourceContext,
                    ownerQualifiedName, variableName);
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
                if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(parameter.kind().id()))
                    continue;
                if (variableName.equals(firstIdentifierLikeTokenText(parameter))) {
                    SyntaxNode bound = directChild(parameter, JavaSyntaxKinds.TYPE_BOUND.id());
                    List<Type> boundTypes = new ArrayList<>();
                    if (bound != null) {
                        for (SyntaxNode child : bound.children()) {
                            if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(child.kind().id()))
                                continue;
                            boundTypes.add(sourceContext == null
                                ? typeFromTypeReferenceForResolution(child)
                                : projectTypeFromTypeReference(sourceContext, child));
                        }
                    }
                    if (boundTypes.isEmpty()) {
                        boundTypes.add(new Type.DeclaredType("java.lang.Object", List.of()));
                    }
                    return new SourceTypeParameterInfo(index, List.copyOf(boundTypes));
                }
                index++;
            }
            return null;
        }

        private boolean isSelfBoundTypeParameter(String ownerQualifiedName, TypeParameter parameter) {
            for (dev.railroadide.railroad.ide.classparser.Type bound : parameter.bounds()) {
                if (bound instanceof dev.railroadide.railroad.ide.classparser.Type.ClassType classType
                    && ownerQualifiedName.equals(classType.name()))
                    return true;
            }
            return false;
        }

        private Type createdTypeForResolution(SyntaxNode creationNode) {
            SyntaxNode typeRef = directChild(creationNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null ? new Type.UnknownType("<unknown>") : typeFromTypeReferenceForResolution(typeRef);
        }

        private Type inferAssignmentTypeForResolution(SyntaxNode assignmentExpression) {
            SyntaxNode firstExpression = null;
            for (SyntaxNode child : assignmentExpression.children()) {
                if (!isExpressionNode(child))
                    continue;
                firstExpression = child;
                break;
            }
            return firstExpression == null
                ? new Type.UnknownType("<unknown>")
                : inferExpressionTypeForResolution(firstExpression);
        }

        private Type inferBinaryTypeForResolution(SyntaxNode binaryExpression) {
            Type left = new Type.UnknownType("<unknown>");
            Type right = new Type.UnknownType("<unknown>");
            String operator = null;
            for (SyntaxNode child : binaryExpression.children()) {
                if (child instanceof SyntaxToken token) {
                    if (isTriviaToken(token) || isMissingTokenKind(token.kind().id()))
                        continue;
                    operator = token.text();
                } else if (isExpressionNode(child)) {
                    if (left.kind() == Type.Kind.UNKNOWN) {
                        left = inferExpressionTypeForResolution(child);
                    } else {
                        right = inferExpressionTypeForResolution(child);
                    }
                }
            }

            if ("+".equals(operator) && (isStringLike(left) || isStringLike(right)))
                return new Type.DeclaredType("java.lang.String", List.of());
            if ("&&".equals(operator) || "||".equals(operator)
                || "==".equals(operator) || "!=".equals(operator)
                || "<".equals(operator) || "<=".equals(operator)
                || ">".equals(operator) || ">=".equals(operator))
                return new Type.PrimitiveType("boolean");
            if (isNumericType(left) && isNumericType(right))
                return promoteNumeric(left, right);
            return new Type.UnknownType("<unknown>");
        }

        private Type firstExpressionChildType(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                if (isExpressionNode(child))
                    return inferExpressionTypeForResolution(child);
            }
            return new Type.UnknownType("<unknown>");
        }

        private Type typeOfResolvedSymbol(Symbol symbol) {
            if (symbol instanceof SyntheticMemberSymbol synthetic)
                return synthetic.valueType();

            return switch (symbol.kind()) {
                case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD ->
                    new Type.DeclaredType(symbol.qualifiedName().orElse(symbol.simpleName()), List.of());
                case METHOD -> typeOfMethodDeclaration(symbol);
                case FIELD, PARAMETER, LOCAL_VARIABLE -> typeOfFieldDeclaration(symbol);
                case CONSTRUCTOR -> new Type.DeclaredType(ownerQualifiedName(symbol), List.of());
                default -> new Type.UnknownType("<unknown>");
            };
        }

        private List<MemberCandidate> localMethodCandidates(List<Symbol> symbols) {
            List<MemberCandidate> candidates = new ArrayList<>();
            for (Symbol symbol : symbols) {
                if (symbol.kind() != SymbolKind.METHOD)
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

            Type enumType = new Type.DeclaredType(ownerQualifiedName, List.of());
            Type returnType;
            List<Type> parameterTypes;
            if ("values".equals(methodName)) {
                returnType = new Type.ArrayType(enumType);
                parameterTypes = List.of();
            } else if ("valueOf".equals(methodName)) {
                returnType = enumType;
                parameterTypes = List.of(new Type.DeclaredType("java.lang.String", List.of()));
            } else
                return null;

            Symbol symbol = new SyntheticMemberSymbol(
                SymbolKind.METHOD,
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
                if (symbol.kind() == SymbolKind.ENUM
                    && Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    return true;
            }
            return projectIndex != null && projectIndex.lookupQualifiedName(ownerQualifiedName).stream()
                .anyMatch(symbol -> isProjectSourceSymbol(symbol) && symbol.kind() == SymbolKind.ENUM);
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
                    List<Type> parameterTypes = constructor.parameters().stream()
                        .map(parameter -> toSemanticType(parameter.type()))
                        .toList();
                    Type constructedType = new Type.DeclaredType(ownerQualifiedName, List.of());
                    candidates.add(new MemberCandidate(
                        new SyntheticMemberSymbol(
                            SymbolKind.CONSTRUCTOR,
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
                if (!isProjectSourceSymbol(symbol) || symbol.kind() != SymbolKind.FIELD
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
                        SymbolKind.FIELD,
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
                    boolean methodStatic = Modifier.isStatic(method.modifiers());
                    if (!method.name().equals(methodName) || methodStatic != staticAccess)
                        continue;
                    List<Type> parameterTypes = method.parameterTypes();
                    out.add(new MemberCandidate(
                        new SyntheticMemberSymbol(
                            SymbolKind.METHOD,
                            method.name(),
                            ownerQualifiedName + "#" + method.name() + signatureSuffix(parameterTypes),
                            null,
                            method.returnType(),
                            parameterTypes,
                            methodStatic),
                        ownerQualifiedName,
                        methodStatic,
                        method.returnType(),
                        parameterTypes));
                }
                return;
            }

            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMember(ownerQualifiedName,
                methodName)) {
                if (!isProjectSourceSymbol(symbol) || symbol.kind() != SymbolKind.METHOD
                    || symbol.isStatic() != staticAccess)
                    continue;

                List<Type> parameterTypes = projectMethodParameterTypes(symbol);
                Type valueType = projectMemberValueType(symbol);
                out.add(new MemberCandidate(
                    syntheticProjectMemberSymbol(symbol, valueType, parameterTypes),
                    ownerQualifiedName,
                    staticAccess,
                    valueType,
                    parameterTypes));
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
                if (!isProjectSourceSymbol(symbol) || symbol.kind() != SymbolKind.CONSTRUCTOR)
                    continue;
                hasExplicitConstructors = true;

                List<Type> parameterTypes = parameterTypesFromProjectSignature(symbol.signature());
                Type constructedType = new Type.DeclaredType(ownerQualifiedName, List.of());
                out.add(new MemberCandidate(
                    syntheticProjectMemberSymbol(symbol, constructedType, parameterTypes),
                    ownerQualifiedName,
                    false,
                    constructedType,
                    parameterTypes));
            }

            JavaProjectSemanticIndex.SymbolDescriptor ownerType = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> isProjectSourceSymbol(symbol)
                    && (symbol.kind() == SymbolKind.CLASS || symbol.kind() == SymbolKind.RECORD))
                .findFirst()
                .orElse(null);
            if (ownerType == null)
                return;

            if (hasExplicitConstructors && ownerType.kind() != SymbolKind.RECORD)
                return;

            List<Type> parameterTypes = ownerType.kind() == SymbolKind.RECORD
                ? projectRecordCanonicalConstructorParameterTypes(ownerQualifiedName, ownerType.sourceFile())
                : List.of();
            if (out.stream().anyMatch(candidate -> ownerQualifiedName.equals(candidate.ownerQualifiedName())
                && candidate.parameterTypes().equals(parameterTypes)))
                return;
            Type constructedType = new Type.DeclaredType(ownerQualifiedName, List.of());
            out.add(new MemberCandidate(
                new SyntheticMemberSymbol(
                    SymbolKind.CONSTRUCTOR,
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
                if (!field.name().equals(fieldName))
                    continue;
                if (Modifier.isStatic(field.modifiers()) != staticAccess)
                    continue;
                Type valueType = toSemanticType(field.type());
                out.add(new MemberCandidate(
                    new SyntheticMemberSymbol(
                        SymbolKind.FIELD,
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
                        SymbolKind.METHOD,
                        method.name(),
                        ownerQualifiedName + "#" + method.name() + signatureSuffix(parameterTypes),
                        null,
                        valueType,
                        parameterTypes,
                        staticAccess),
                    ownerQualifiedName,
                    staticAccess,
                    valueType,
                    parameterTypes));
            }

        }

        private @Nullable MemberCandidate chooseFieldCandidate(List<MemberCandidate> candidates) {
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        private @Nullable MemberCandidate selectBestCallable(
            List<MemberCandidate> candidates,
            List<Type> argumentTypes
        ) {
            return selectBestCallable(candidates, argumentTypes, new Type.UnknownType("<unknown>"));
        }

        private @Nullable MemberCandidate selectBestCallable(
            List<MemberCandidate> candidates,
            List<Type> argumentTypes,
            Type contextualReturnType
        ) {
            MemberCandidate best = null;
            List<Integer> bestCost = null;
            int bestReturnCost = Integer.MAX_VALUE;

            for (MemberCandidate candidate : candidates) {
                List<Integer> cost = applicabilityCost(candidate.parameterTypes(), argumentTypes);
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

            return best;
        }

        private int contextualReturnCost(Type targetType, Type returnType) {
            if (targetType.kind() == Type.Kind.UNKNOWN)
                return 0;
            if (returnType.kind() == Type.Kind.VOID)
                return targetType.kind() == Type.Kind.VOID ? 0 : 100;
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
                if (isArityCompatible(candidate.parameterTypes(), argumentCount))
                    return candidate;
            }
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        private boolean containsUnknownLikeArgument(List<Type> argumentTypes) {
            for (Type argumentType : argumentTypes) {
                if (argumentType.kind() == Type.Kind.UNKNOWN
                    || argumentType.kind() == Type.Kind.TYPE_VARIABLE
                    || argumentType.kind() == Type.Kind.WILDCARD)
                    return true;
            }
            return false;
        }

        private boolean hasComplexArgumentShape(SyntaxNode argumentList) {
            for (SyntaxNode child : argumentList.children()) {
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

        private boolean isArityCompatible(List<Type> parameterTypes, int argumentCount) {
            boolean varargs = !parameterTypes.isEmpty() && parameterTypes.getLast().kind() == Type.Kind.ARRAY;
            if (!varargs)
                return parameterTypes.size() == argumentCount;
            return argumentCount >= parameterTypes.size() - 1;
        }

        private List<MemberCandidate> dedupeCallableCandidates(List<MemberCandidate> candidates) {
            Map<String, MemberCandidate> deduped = new LinkedHashMap<>();
            for (MemberCandidate candidate : candidates) {
                String key = candidate.symbol().simpleName() + signatureSuffix(candidate.parameterTypes());
                deduped.putIfAbsent(key, candidate);
            }
            return List.copyOf(deduped.values());
        }

        private @Nullable List<Integer> applicabilityCost(List<Type> parameterTypes, List<Type> argumentTypes) {
            boolean varargs = !parameterTypes.isEmpty() && parameterTypes.getLast().kind() == Type.Kind.ARRAY;
            if (!varargs && parameterTypes.size() != argumentTypes.size())
                return null;
            if (varargs && argumentTypes.size() < parameterTypes.size() - 1)
                return null;

            List<Integer> cost = new ArrayList<>(Math.max(parameterTypes.size(), argumentTypes.size()));
            int fixedCount = varargs ? parameterTypes.size() - 1 : parameterTypes.size();
            for (int index = 0; index < fixedCount; index++) {
                Integer conversionCost = conversionCost(parameterTypes.get(index), argumentTypes.get(index));
                if (conversionCost == null)
                    return null;
                cost.add(conversionCost);
            }

            if (!varargs)
                return List.copyOf(cost);

            Type.ArrayType varargsArray = (Type.ArrayType) parameterTypes.getLast();
            Type componentType = varargsArray.componentType();
            if (argumentTypes.size() == parameterTypes.size()) {
                Integer directArrayCost = conversionCost(varargsArray, argumentTypes.getLast());
                if (directArrayCost != null) {
                    cost.add(directArrayCost);
                    return List.copyOf(cost);
                }
            }

            for (int index = fixedCount; index < argumentTypes.size(); index++) {
                Integer conversionCost = conversionCost(componentType, argumentTypes.get(index));
                if (conversionCost == null)
                    return null;
                cost.add(conversionCost + 1);
            }
            return List.copyOf(cost);
        }

        private @Nullable Integer conversionCost(Type parameterType, Type argumentType) {
            if (argumentType.kind() == Type.Kind.UNKNOWN
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
            if (argumentType.kind() == Type.Kind.UNKNOWN || parameterType.kind() == Type.Kind.UNKNOWN)
                return 0;
            if (parameterType.kind() == Type.Kind.TYPE_VARIABLE || parameterType.kind() == Type.Kind.WILDCARD)
                return 20;
            if (argumentType.kind() == Type.Kind.TYPE_VARIABLE || argumentType.kind() == Type.Kind.WILDCARD)
                return 0;
            if (parameterType.displayName().equals(argumentType.displayName()))
                return 0;
            if (parameterType.kind() == Type.Kind.PRIMITIVE && argumentType.kind() == Type.Kind.DECLARED) {
                String wrapper = boxedQualifiedName(parameterType.displayName());
                if (wrapper != null
                    && wrapper.equals(resolveQualifiedTypeNameForCallMatching(argumentType.displayName())))
                    return 10;
            }
            if (parameterType.kind() == Type.Kind.DECLARED && argumentType.kind() == Type.Kind.PRIMITIVE) {
                String wrapper = boxedQualifiedName(argumentType.displayName());
                String parameterQualifiedName = resolveQualifiedTypeNameForCallMatching(parameterType.displayName());
                if (wrapper != null) {
                    if (wrapper.equals(parameterQualifiedName))
                        return 10;
                    if (parameterQualifiedName != null && isSubtype(wrapper, parameterQualifiedName))
                        return 60;
                }
            }
            if (parameterType.kind() == Type.Kind.ARRAY && argumentType.kind() == Type.Kind.ARRAY) {
                Type.ArrayType parameterArray = (Type.ArrayType) parameterType;
                Type.ArrayType argumentArray = (Type.ArrayType) argumentType;
                return conversionCost(parameterArray.componentType(), argumentArray.componentType());
            }
            if (isNumericType(parameterType) && isNumericType(argumentType)) {
                int targetRank = numericRank(simpleTypeName(parameterType.displayName()));
                int sourceRank = numericRank(simpleTypeName(argumentType.displayName()));
                if (targetRank < 0 || sourceRank < 0 || targetRank < sourceRank)
                    return null;
                return targetRank - sourceRank;
            }
            if (parameterType.kind() == Type.Kind.DECLARED && argumentType.kind() == Type.Kind.ARRAY) {
                String parameterQualifiedName = resolveQualifiedTypeNameForCallMatching(parameterType.displayName());
                return "java.lang.Object".equals(parameterQualifiedName) ? 100 : null;
            }
            if (parameterType.kind() == Type.Kind.DECLARED && argumentType.kind() == Type.Kind.DECLARED) {
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
                if (!Objects.equals(symbol.qualifiedName().orElse(null), qualifiedTypeName))
                    continue;

                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    return List.of();

                List<String> directSupers = new ArrayList<>();
                collectDirectSuperTypes(declaration, JavaSyntaxKinds.EXTENDS_CLAUSE.id(), directSupers);
                collectDirectSuperTypes(declaration, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id(), directSupers);
                if (symbol.kind() == SymbolKind.ENUM) {
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
                String source = Files.readString(sourceType.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(sourceType.sourceFile(), source, model, projectIndex);
                Map<String, List<String>> collected = new LinkedHashMap<>();
                collectDirectSuperTypesFromSourceFile(sourceContext, model.syntaxTree().root(), collected);
                collected
                    .forEach((name, supers) -> directSuperTypesByQualifiedName.putIfAbsent(name, List.copyOf(supers)));
                return directSuperTypesByQualifiedName.getOrDefault(qualifiedTypeName, List.of());
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
            if (superClass.kind() == Type.Kind.DECLARED) {
                directSupers.add(superClass.displayName());
            }
            for (dev.railroadide.railroad.ide.classparser.Type iface : stub.interfaces()) {
                Type ifaceType = toSemanticType(iface);
                if (ifaceType.kind() == Type.Kind.DECLARED) {
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
                collectDirectSuperTypes(sourceContext, node, JavaSyntaxKinds.EXTENDS_CLAUSE.id(), directSupers);
                collectDirectSuperTypes(sourceContext, node, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id(), directSupers);
                if (symbol.kind() == SymbolKind.ENUM) {
                    directSupers.addFirst("java.lang.Enum");
                }
                out.put(qualifiedName, List.copyOf(directSupers));
            });

            for (SyntaxNode child : node.children()) {
                collectDirectSuperTypesFromSourceFile(sourceContext, child, out);
            }
        }

        private boolean isProjectSourceSymbol(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            Path sourceFile = symbol.sourceFile();
            return sourceFile != null && sourceFile.getFileName() != null
                && sourceFile.getFileName().toString().endsWith(".java");
        }

        private @Nullable MemberCandidate implicitLocalConstructorCandidate(String ownerQualifiedName) {
            Type constructedType = new Type.DeclaredType(ownerQualifiedName, List.of());
            List<Type> parameterTypes = localRecordCanonicalConstructorParameterTypes(ownerQualifiedName);
            return new MemberCandidate(
                new SyntheticMemberSymbol(
                    SymbolKind.CONSTRUCTOR,
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
                if (!Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    continue;
                if (symbol.kind() != SymbolKind.RECORD)
                    return List.of();

                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    return List.of();

                SyntaxNode recordHeader = directChild(declaration, JavaSyntaxKinds.RECORD_HEADER.id());
                if (recordHeader == null)
                    return List.of();

                List<Type> parameterTypes = new ArrayList<>();
                for (SyntaxNode child : recordHeader.children()) {
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
                if (!Objects.equals(symbol.qualifiedName().orElse(null), ownerQualifiedName))
                    continue;
                if (symbol.kind() != SymbolKind.RECORD)
                    return null;

                SyntaxNode declaration = symbol.declaration().orElse(null);
                if (declaration == null)
                    return null;

                SyntaxNode recordHeader = directChild(declaration, JavaSyntaxKinds.RECORD_HEADER.id());
                if (recordHeader == null)
                    return null;

                for (SyntaxNode child : recordHeader.children()) {
                    if (!JavaSyntaxKinds.RECORD_COMPONENT.id().equals(child.kind().id()))
                        continue;
                    String componentName = lastIdentifierLikeTokenText(child);
                    if (!Objects.equals(componentName, methodName))
                        continue;
                    SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    Type valueType = typeRef == null
                        ? new Type.UnknownType("<unknown>")
                        : typeFromTypeReferenceForResolution(typeRef);
                    return new MemberCandidate(
                        new SyntheticMemberSymbol(
                            SymbolKind.METHOD,
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
                    SymbolKind.METHOD,
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
                .filter(symbol -> isProjectSourceSymbol(symbol) && symbol.kind() == SymbolKind.RECORD)
                .findFirst()
                .orElse(null);
            if (ownerType == null) {
                projectRecordAccessorTypesByOwner.put(ownerQualifiedName, Map.of());
                return Map.of();
            }

            try {
                String source = Files.readString(ownerType.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(
                    ownerType.sourceFile(), source, model, projectIndex);
                Map<String, Type> accessorTypes = new LinkedHashMap<>();
                collectProjectRecordAccessorTypes(
                    model.syntaxTree().root(), model, sourceContext, ownerQualifiedName, accessorTypes);
                Map<String, Type> copy = Collections.unmodifiableMap(new LinkedHashMap<>(accessorTypes));
                projectRecordAccessorTypesByOwner.put(ownerQualifiedName, copy);
                return copy;
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
                && declared.kind() == SymbolKind.RECORD
                && Objects.equals(declared.qualifiedName().orElse(null), ownerQualifiedName)) {
                SyntaxNode recordHeader = directChild(node, JavaSyntaxKinds.RECORD_HEADER.id());
                if (recordHeader == null)
                    return;
                for (SyntaxNode child : recordHeader.children()) {
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
                SymbolKind.FIELD,
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
                if (symbol.kind() == SymbolKind.RECORD
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
                resolved = parameterTypesFromProjectSignature(symbol.signature());
            }
            List<Type> copy = List.copyOf(resolved);
            projectMethodParameterTypesByKey.put(key, copy);
            return copy;
        }

        private List<Type> computeProjectMethodParameterTypes(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            if (symbol.kind() != SymbolKind.METHOD)
                return List.of();
            try {
                String source = Files.readString(symbol.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(symbol.sourceFile(), source, model, projectIndex);
                List<Type> match = findProjectMethodParameterTypes(
                    symbol, model.syntaxTree().root(), model, sourceContext);
                return match == null ? List.of() : match;
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
                String source = Files.readString(owner.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(
                    owner.sourceFile(), source, model, projectIndex);
                return List.copyOf(sourceContext.declaredMethodDescriptors(ownerQualifiedName));
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
                SyntaxNode parameterList = directChild(node, JavaSyntaxKinds.PARAMETER_LIST.id());
                if (parameterList == null)
                    return List.of();
                List<Type> parameterTypes = new ArrayList<>();
                for (SyntaxNode child : parameterList.children()) {
                    if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id()))
                        continue;
                    SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    Type parameterType = projectTypeFromTypeReference(sourceContext, typeRef);
                    if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                        parameterType = new Type.ArrayType(parameterType);
                    }
                    parameterTypes.add(parameterType);
                }
                return List.copyOf(parameterTypes);
            }

            for (SyntaxNode child : node.children()) {
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
            if (!(parameterType instanceof Type.DeclaredType declared))
                return null;
            String qualifiedTypeName = resolveQualifiedTypeNameForCallMatching(declared.displayName());
            if (qualifiedTypeName == null || !visited.add(qualifiedTypeName))
                return null;

            ClassStub stub = binaryClassStubsByQualifiedName.get(qualifiedTypeName);
            if (stub != null) {
                Map<String, Integer> abstractMethods = new LinkedHashMap<>();
                for (MethodStub method : stub.methods()) {
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
                        substitutions.put(stub.typeParameters().get(index).name(), declared.typeArguments().get(index));
                    }
                    for (dev.railroadide.railroad.ide.classparser.Type parentInterface : stub.interfaces()) {
                        Type specializedParent = substituteFunctionalType(toSemanticType(parentInterface),
                            substitutions);
                        if (!(specializedParent instanceof Type.DeclaredType parentDeclared))
                            continue;
                        String parentQualifiedType = resolveFunctionalSuperTypeName(parentDeclared, qualifiedTypeName);
                        Type qualifiedParent = parentQualifiedType == null
                            ? parentDeclared
                            : new Type.DeclaredType(parentQualifiedType, parentDeclared.typeArguments());
                        Integer parentArity = functionalInterfaceArity(qualifiedParent, visited);
                        if (parentArity != null)
                            return parentArity;
                    }
                }
            }

            if (projectIndex == null)
                return null;
            boolean sourceInterface = projectIndex.lookupQualifiedName(qualifiedTypeName).stream()
                .anyMatch(symbol -> isProjectSourceSymbol(symbol) && symbol.kind() == SymbolKind.INTERFACE);
            if (!sourceInterface)
                return null;
            Map<String, Integer> sourceMethods = new LinkedHashMap<>();
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMembers(qualifiedTypeName)) {
                if (isProjectSourceSymbol(symbol) && symbol.kind() == SymbolKind.METHOD && !symbol.isStatic()) {
                    sourceMethods.put(
                        symbol.simpleName() + Objects.toString(symbol.signature(), ""),
                        parameterTypesFromProjectSignature(symbol.signature()).size());
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
            return symbol.kind() + "|" + symbol.qualifiedName() + "|" + symbol.signature() + "|" + symbol.sourceFile();
        }

        private Type computeProjectMemberValueType(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
            if (symbol.kind() == SymbolKind.CONSTRUCTOR) {
                String ownerQualifiedName = symbol.ownerQualifiedName();
                return ownerQualifiedName == null
                    ? new Type.UnknownType("<unknown>")
                    : new Type.DeclaredType(ownerQualifiedName, List.of());
            }

            if (symbol.kind() != SymbolKind.METHOD && symbol.kind() != SymbolKind.FIELD)
                return new Type.UnknownType("<unknown>");

            try {
                String source = Files.readString(symbol.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(
                    symbol.sourceFile(),
                    source,
                    model,
                    projectIndex);
                return findProjectMemberValueType(
                    symbol, model.syntaxTree().root(), model, sourceContext);
            } catch (Exception _) {
                return new Type.UnknownType("<unknown>");
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
                if (target.kind() == SymbolKind.FIELD) {
                    if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(node.kind().id())) {
                        String ownerQualifiedName = target.ownerQualifiedName();
                        return ownerQualifiedName == null || ownerQualifiedName.isBlank()
                            ? new Type.UnknownType("<unknown>")
                            : new Type.DeclaredType(ownerQualifiedName, List.of());
                    }
                    SyntaxNode typeRef = nearestTypeReferenceForProjectField(node);
                    return projectTypeFromTypeReference(sourceContext, typeRef);
                }
                SyntaxNode typeRef = directChild(node, JavaSyntaxKinds.TYPE_REFERENCE.id());
                return projectTypeFromTypeReference(sourceContext, typeRef);
            }

            for (SyntaxNode child : node.children()) {
                Type match = findProjectMemberValueType(target, child, model, sourceContext);
                if (match.kind() != Type.Kind.UNKNOWN)
                    return match;
            }
            return new Type.UnknownType("<unknown>");
        }

        private Type inferCastTypeForResolution(SyntaxNode castExpression) {
            SyntaxNode typeRef = directChild(castExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null
                ? new Type.UnknownType("<unknown>")
                : typeFromTypeReferenceForResolution(typeRef);
        }

        private Type inferConditionalTypeForResolution(SyntaxNode conditionalExpression) {
            List<SyntaxNode> expressions = new ArrayList<>();
            for (SyntaxNode child : conditionalExpression.children()) {
                if (isExpressionNode(child)) {
                    expressions.add(child);
                }
            }
            if (expressions.size() < 3)
                return new Type.UnknownType("<unknown>");
            Type whenTrue = inferExpressionTypeForResolution(expressions.get(1));
            Type whenFalse = inferExpressionTypeForResolution(expressions.get(2));
            Type common = commonConditionalType(whenTrue, whenFalse, projectIndex);
            if (common.kind() != Type.Kind.UNKNOWN)
                return common;
            String commonSupertype = nearestCommonSupertype(whenTrue, whenFalse, conditionalExpression);
            return commonSupertype == null
                ? new Type.UnknownType("<unknown>")
                : new Type.DeclaredType(commonSupertype, List.of());
        }

        private @Nullable String nearestCommonSupertype(Type left, Type right, SyntaxNode usageSite) {
            if (!(left instanceof Type.DeclaredType) || !(right instanceof Type.DeclaredType))
                return null;
            String leftName = resolveQualifiedTypeName(left.displayName(), usageSite);
            String rightName = resolveQualifiedTypeName(right.displayName(), usageSite);
            if (leftName == null || rightName == null)
                return null;

            Set<String> leftHierarchy = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(leftName);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!leftHierarchy.add(current))
                    continue;
                queue.addAll(directSuperTypeNames(current));
            }

            Set<String> visited = new HashSet<>();
            queue.add(rightName);
            while (!queue.isEmpty()) {
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
                collectSwitchResultTypesForResolution(child, out);
            }
        }

        private Type projectTypeFromTypeReference(JavaRuleContext sourceContext, @Nullable SyntaxNode typeRef) {
            if (typeRef == null)
                return new Type.UnknownType("<unknown>");

            String text = canonicalTypeText(typeRef);
            if (text == null || text.isBlank())
                return new Type.UnknownType("<unknown>");

            return projectTypeFromText(sourceContext, text);
        }

        private Type projectTypeFromText(JavaRuleContext sourceContext, String text) {
            text = text.trim();
            if (text.isBlank())
                return new Type.UnknownType("<unknown>");
            if ("void".equals(text))
                return new Type.VoidType();
            if (text.startsWith("?extends"))
                return new Type.WildcardType(projectTypeFromText(sourceContext, text.substring(8)), null);
            if (text.startsWith("?super"))
                return new Type.WildcardType(null, projectTypeFromText(sourceContext, text.substring(6)));
            if ("?".equals(text))
                return new Type.WildcardType(new Type.DeclaredType("java.lang.Object", List.of()), null);

            int arrayDimensions = 0;
            while (text.endsWith("[]")) {
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
                    if (!argument.isBlank()) {
                        typeArguments.add(projectTypeFromText(sourceContext, argument));
                    }
                }
            }

            String qualifiedName = sourceContext.resolveQualifiedTypeName(rawType);
            Type resolved = switch (rawType) {
                case "boolean", "byte", "short", "char", "int", "long", "float", "double" ->
                    new Type.PrimitiveType(rawType);
                default -> qualifiedName == null || qualifiedName.isBlank()
                    || isLikelyTypeVariableName(rawType) && qualifiedName.equals(rawType)
                        ? isLikelyTypeVariableName(rawType)
                            ? new Type.TypeVariableType(rawType)
                            : new Type.UnknownType(text)
                        : new Type.DeclaredType(qualifiedName, typeArguments);
            };

            for (int index = 0; index < arrayDimensions; index++) {
                resolved = new Type.ArrayType(resolved);
            }
            return resolved;
        }

        private @Nullable SyntaxNode nearestTypeReferenceForProjectField(SyntaxNode node) {
            SyntaxNode current = node;
            while (current != null) {
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
            if (declared.kind() != target.kind())
                return false;
            if (!Objects.equals(declared.simpleName(), target.simpleName()))
                return false;

            String ownerQualifiedName = ownerQualifiedName(declared);
            if (!Objects.equals(ownerQualifiedName, target.ownerQualifiedName()))
                return false;

            if (target.kind() == SymbolKind.METHOD)
                return Objects.equals(target.signature(), signatureFromCallableDeclaration(node));
            return true;
        }

        private @Nullable String signatureFromCallableDeclaration(SyntaxNode declaration) {
            SyntaxNode parameterList = directChild(declaration, JavaSyntaxKinds.PARAMETER_LIST.id());
            if (parameterList == null)
                return "()";

            List<String> parameterTypes = new ArrayList<>();
            for (SyntaxNode child : parameterList.children()) {
                String kindId = child.kind().id();
                if (!JavaSyntaxKinds.PARAMETER.id().equals(kindId))
                    continue;

                SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef == null)
                    continue;

                String text = canonicalTypeText(typeRef);
                if (text != null && !text.isBlank()) {
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
                    for (MemberCandidate candidate : findMethodCandidates(importSpec.ownerName(), methodName, true)) {
                        if (candidate.parameterTypes().size() == argumentCount) {
                            candidates.add(candidate);
                        }
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                for (MemberCandidate candidate : findMethodCandidates(onDemandImport.ownerName(), methodName, true)) {
                    if (candidate.parameterTypes().size() == argumentCount) {
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
                if (isTriviaToken(token) || isMissingTokenKind(token.kind().id()))
                    continue;
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.THIS_KEYWORD).id().equals(token.kind().id())
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.SUPER_KEYWORD).id().equals(token.kind().id()))
                    break;
                qualifier.append(token.text());
            }
            String text = qualifier.toString();
            while (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.isBlank())
                return null;
            return resolveQualifiedTypeName(text, usageSite);
        }

        private @Nullable String resolvedTypeQualifier(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
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
                if (resolved.kind() == SymbolKind.CONSTRUCTOR)
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
            if (type.kind() == Type.Kind.DECLARED)
                return resolveQualifiedTypeName(type.displayName(), usageSite);
            if (type.kind() == Type.Kind.TYPE_VARIABLE) {
                String bound = qualifiedTypeVariableBound(type.displayName(), usageSite);
                return bound == null ? "java.lang.Object" : bound;
            }
            if (type instanceof Type.WildcardType wildcard) {
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
                SyntaxNode typeParameters = directChild(current, JavaSyntaxKinds.TYPE_PARAMETERS.id());
                if (typeParameters != null) {
                    for (SyntaxNode typeParameter : typeParameters.children()) {
                        if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(typeParameter.kind().id())
                            || !variableName.equals(firstIdentifierLikeTokenText(typeParameter)))
                            continue;
                        List<SyntaxNode> boundTypes = new ArrayList<>();
                        collectTopLevelDescendantsOfKind(
                            typeParameter, JavaSyntaxKinds.TYPE_REFERENCE.id(), boundTypes);
                        List<String> bounds = new ArrayList<>();
                        for (SyntaxNode boundType : boundTypes) {
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
            if (rawType.kind() != Type.Kind.TYPE_VARIABLE)
                return List.of();
            String owner = ownerQualifiedName(symbol);
            if (owner == null)
                return List.of();
            SourceTypeParameterInfo parameter = sourceTypeParameterInfo(owner, rawType.displayName());
            if (parameter == null)
                return List.of();

            List<String> bounds = new ArrayList<>();
            for (Type bound : parameter.bounds()) {
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
            Symbol enclosingType = context.topLevelEnclosingTypeSymbol(usageSite);
            if (enclosingType == null) {
                enclosingType = context.enclosingTypeSymbol(usageSite);
            }
            String ownerQualifiedName = enclosingType == null ? null : enclosingType.qualifiedName().orElse(null);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return null;

            for (String directSuper : directSuperTypeNames(ownerQualifiedName)) {
                String resolved = resolveMemberTypeInHierarchy(directSuper, simpleName, new HashSet<>());
                if (resolved != null)
                    return resolved;
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
                String resolved = resolveMemberTypeInHierarchy(directSuper, simpleName, visited);
                if (resolved != null)
                    return resolved;
            }
            return null;
        }

        private Type typeFromTypeReferenceForResolution(SyntaxNode typeNode) {
            String text = canonicalTypeText(typeNode);
            if (text == null || text.isBlank())
                return new Type.UnknownType("<unknown>");
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
                    .anyMatch(symbol -> symbol.kind() == SymbolKind.FIELD && symbol.isStatic());
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
                    .filter(symbol -> symbol.kind() == SymbolKind.METHOD && symbol.isStatic())
                    .anyMatch(symbol -> argumentCountOrUnknown < 0
                        || parameterTypesFromProjectSignature(symbol.signature()).size() == argumentCountOrUnknown);
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
            SyntaxNode parameterList = directChild(declaration, JavaSyntaxKinds.PARAMETER_LIST.id());
            if (parameterList == null)
                return -1;

            int count = 0;
            for (SyntaxNode child : parameterList.children()) {
                if (JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id())) {
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
                    return new SimpleSymbol(match.kind(), match.simpleName(), match.qualifiedName(),
                        declarationOrUsageSite);
                }
            }
            return new SimpleSymbol(SymbolKind.CLASS, simpleName, qualifiedName, declarationOrUsageSite);
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
                symbol.kind(),
                symbol.simpleName(),
                qualifiedName,
                null,
                valueType,
                parameterTypes,
                symbol.isStatic());
        }

        private List<Type> parameterTypesFromProjectSignature(@Nullable String signature) {
            if (signature == null || signature.isBlank() || "()".equals(signature))
                return List.of();

            if (!signature.startsWith("(") || !signature.endsWith(")"))
                return List.of();

            String content = signature.substring(1, signature.length() - 1).trim();
            if (content.isEmpty())
                return List.of();

            List<Type> result = new ArrayList<>();
            for (String part : splitSignatureTypes(content)) {
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
                return new Type.UnknownType("<unknown>");
            if ("?".equals(text))
                return new Type.WildcardType(new Type.DeclaredType("java.lang.Object", List.of()), null);
            if ("void".equals(text))
                return new Type.VoidType();
            if (text.startsWith("? extends "))
                return new Type.WildcardType(typeFromTypeText(text.substring(10), usageSite), null);
            if (text.startsWith("? super "))
                return new Type.WildcardType(null, typeFromTypeText(text.substring(8), usageSite));
            if (text.startsWith("?extends"))
                return new Type.WildcardType(typeFromTypeText(text.substring(8), usageSite), null);
            if (text.startsWith("?super"))
                return new Type.WildcardType(null, typeFromTypeText(text.substring(6), usageSite));
            if (text.endsWith("[]"))
                return new Type.ArrayType(typeFromTypeText(text.substring(0, text.length() - 2), usageSite));

            int typeArgsStart = findTopLevelTypeArgumentsStart(text);
            if (typeArgsStart > 0 && text.endsWith(">")) {
                String rawText = text.substring(0, typeArgsStart).trim();
                String argsText = text.substring(typeArgsStart + 1, text.length() - 1).trim();
                List<Type> typeArguments = new ArrayList<>();
                for (String part : splitTopLevelTypeArguments(argsText)) {
                    String argumentText = part.trim();
                    if (!argumentText.isEmpty()) {
                        typeArguments.add(typeFromTypeText(argumentText, usageSite));
                    }
                }
                String qualifiedRaw = resolveQualifiedTypeName(rawText, usageSite);
                String declaredName = qualifiedRaw == null || qualifiedRaw.isBlank() ? rawText : qualifiedRaw;
                if (declaredName.isBlank())
                    return new Type.UnknownType("<unknown>");
                return new Type.DeclaredType(declaredName, typeArguments);
            }

            if (Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return new Type.PrimitiveType(text);
            if (usageSite != null && isLexicallyDeclaredTypeVariable(text, usageSite))
                return new Type.TypeVariableType(text);
            if (isTypeVariableNameInScope(text))
                return new Type.TypeVariableType(text);

            String qualified = resolveQualifiedTypeName(text, usageSite);
            if (isLikelyTypeVariableName(text)
                && (qualified == null || qualified.isBlank() || Objects.equals(qualified, text)))
                return new Type.TypeVariableType(text);
            String declaredName = qualified == null || qualified.isBlank() ? text : qualified;
            if (declaredName.isBlank())
                return new Type.UnknownType("<unknown>");
            return new Type.DeclaredType(declaredName, List.of());
        }

        private int findTopLevelTypeArgumentsStart(String text) {
            int depth = 0;
            for (int index = 0; index < text.length(); index++) {
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
                if (symbol.kind() == SymbolKind.TYPE_PARAMETER && name.equals(symbol.simpleName()))
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
            return type.kind() == Type.Kind.PRIMITIVE
                && Set.of("byte", "short", "char", "int", "long", "float", "double").contains(simple);
        }

        private static boolean isStringLike(Type type) {
            return type.kind() == Type.Kind.DECLARED && "String".equals(simpleTypeName(type.displayName()));
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
                case 5 -> new Type.PrimitiveType("double");
                case 4 -> new Type.PrimitiveType("float");
                case 3 -> new Type.PrimitiveType("long");
                default -> new Type.PrimitiveType("int");
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
                    ? new Type.DeclaredType("java.lang.Object", List.of())
                    : bounds.getFirst();
            }
        }

        private record MemberLookup(@Nullable String ownerQualifiedName, boolean staticAccess) {
        }

        private enum CallableKind {
            METHOD, CONSTRUCTOR
        }

        private record MemberCandidate(
            Symbol symbol,
            String ownerQualifiedName,
            boolean staticMember,
            Type valueType,
            List<Type> parameterTypes) {
        }
    }

    private static final class TypeResolver {
        private static final Type UNKNOWN_TYPE = new Type.UnknownType("<unknown>");
        private static final Type BOOLEAN_TYPE = new Type.PrimitiveType("boolean");
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
        private final Map<String, List<Type.DeclaredType>> indexedDirectSuperTypes = new HashMap<>();

        private TypeResolver(JavaDeclarationAnalysis.Context context) {
            this.context = context;
            this.projectIndex = context.projectIndex;

            Set<String> qualified = new HashSet<>();
            for (Symbol symbol : context.allTypeSymbols()) {
                symbol.qualifiedName().ifPresent(qualified::add);
            }
            this.localQualifiedTypeNames = Set.copyOf(qualified);

            this.availableQualifiedTypeNames = projectIndex != null
                ? projectIndex.declaredQualifiedNames()
                : loadJdkQualifiedTypeNames();
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
                    return new Type.PrimitiveType("char");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.STRING_LITERAL).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.TEXT_BLOCK_LITERAL).id().equals(kindId))
                    return new Type.DeclaredType("String", List.of());
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
            if (contextualLambdaType.kind() != Type.Kind.UNKNOWN)
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

            SyntaxNode contextualExpression = lambda;
            SyntaxNode castParent = contextualExpression.parent().orElse(null);
            while (castParent != null
                && JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(castParent.kind().id())) {
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
                argumentList = argumentList.parent().orElse(null);
            }
            if (argumentList == null)
                return UNKNOWN_TYPE;
            SyntaxNode invocation = argumentList.parent().orElse(null);
            Symbol callable = invocation == null ? null : context.resolvedSymbol(invocation);
            if (callable == null
                || callable.kind() != SymbolKind.METHOD
                    && callable.kind() != SymbolKind.CONSTRUCTOR)
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
                if (!isExpressionNode(argument))
                    continue;
                if (argument != contextualArgument && argumentIndex < parameterTypes.size()) {
                    bindTypeVariables(
                        parameterTypes.get(argumentIndex), inferType(argument), substitutions);
                }
                argumentIndex++;
            }
            Type contextualTarget = contextualInvocationTargetType(invocation);
            if (contextualTarget.kind() != Type.Kind.UNKNOWN) {
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
                if (kindId.equals(current.kind().id()))
                    return current;
                current = current.parent().orElse(null);
            }
            return null;
        }

        private Type inferClassLiteralType(SyntaxNode classLiteralExpression) {
            SyntaxNode typeRef = directChild(classLiteralExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type literalType = typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
            return new Type.DeclaredType("java.lang.Class", List.of(literalType));
        }

        private Type inferArrayCreationType(SyntaxNode arrayCreationExpression) {
            SyntaxNode typeRef = directChild(arrayCreationExpression, JavaSyntaxKinds.TYPE_REFERENCE.id());
            Type type = typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
            for (SyntaxNode child : arrayCreationExpression.children()) {
                if (child instanceof SyntaxToken token
                    && JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_BRACKET).id().equals(token.kind().id())) {
                    type = new Type.ArrayType(type);
                }
            }
            return type;
        }

        private Type inferArrayAccessType(SyntaxNode arrayAccessExpression) {
            for (SyntaxNode child : arrayAccessExpression.children()) {
                if (!isExpressionNode(child))
                    continue;
                Type receiverType = inferType(child);
                return receiverType instanceof Type.ArrayType array ? array.componentType() : UNKNOWN_TYPE;
            }
            return UNKNOWN_TYPE;
        }

        private Type inferMethodInvocationType(SyntaxNode invocationNode) {
            Symbol resolved = context.resolvedSymbol(invocationNode);
            if (resolved != null && resolved.kind() == SymbolKind.METHOD) {
                Type returnType = typeOfSymbol(resolved);
                return specializeMethodReturnType(invocationNode, resolved, returnType);
            }
            if (resolved != null && resolved.kind() == SymbolKind.CONSTRUCTOR)
                return typeOfSymbol(resolved);
            return UNKNOWN_TYPE;
        }

        private Type specializeMethodReturnType(SyntaxNode invocationNode, Symbol methodSymbol, Type rawReturnType) {
            Map<String, Type> substitutions = new LinkedHashMap<>();
            Type receiverType = invocationReceiverType(invocationNode, methodSymbol);
            if (receiverType.kind() != Type.Kind.UNKNOWN) {
                bindOwnerTypeArguments(methodSymbol, receiverType, substitutions);
                bindMethodTypeArguments(invocationNode, methodSymbol, substitutions);
                Type partiallySpecialized = substituteTypeVariables(rawReturnType, substitutions);
                Map<String, Type> contextualSubstitutions = new LinkedHashMap<>();
                bindContextualReturnType(invocationNode, partiallySpecialized, contextualSubstitutions);
                Type specialized = substituteTypeVariables(partiallySpecialized, contextualSubstitutions);
                if ((rawReturnType.kind() == Type.Kind.TYPE_VARIABLE
                    || rawReturnType.kind() == Type.Kind.DECLARED
                        && isLikelyTypeVariableName(rawReturnType.displayName()))
                    && (specialized.kind() == Type.Kind.TYPE_VARIABLE
                        || specialized.kind() == Type.Kind.WILDCARD
                        || specialized.kind() == Type.Kind.DECLARED
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
            if (contextualTarget.kind() == Type.Kind.UNKNOWN) {
                contextualTarget = contextualInvocationTargetType(invocationNode);
            }
            if (contextualTarget.kind() != Type.Kind.UNKNOWN) {
                bindTypeVariables(rawReturnType, contextualTarget, substitutions);
            }
        }

        private Type directContextualTargetType(SyntaxNode expression) {
            SyntaxNode contextualExpression = expression;
            SyntaxNode parent = contextualExpression.parent().orElse(null);
            while (parent != null && (JavaSyntaxKinds.PARENTHESIZED_EXPRESSION.id().equals(parent.kind().id())
                || JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id().equals(parent.kind().id()))) {
                contextualExpression = parent;
                parent = contextualExpression.parent().orElse(null);
            }
            if (parent == null)
                return UNKNOWN_TYPE;

            if (JavaSyntaxKinds.ASSIGNMENT_EXPRESSION.id().equals(parent.kind().id())) {
                for (SyntaxNode child : parent.children()) {
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
                    if (createdType instanceof Type.DeclaredType declaredType
                        && declaredType.typeArguments().isEmpty()) {
                        Type contextualType = directContextualTargetType(invocationNode);
                        if (contextualType instanceof Type.DeclaredType contextualDeclared
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
                .map(name -> (Type) new Type.TypeVariableType(name))
                .toList();
            return new Type.DeclaredType(qualifiedName, typeArguments);
        }

        private @Nullable Symbol nearestEnclosingTypeSymbol(SyntaxNode usageSite) {
            SyntaxNode current = usageSite.parent().orElse(null);
            while (current != null) {
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
                if (!isExpressionNode(child))
                    continue;
                int parameterIndex = Math.min(argumentIndex, parameterTypes.size() - 1);
                Type parameterType = parameterTypes.get(parameterIndex);
                Type argumentType = inferType(child);
                if (isVarargsMethod(methodSymbol)
                    && parameterIndex == parameterTypes.size() - 1
                    && parameterType instanceof Type.ArrayType array
                    && (argumentIndex >= parameterTypes.size()
                        || !(argumentType instanceof Type.ArrayType))) {
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
            if (type instanceof Type.TypeVariableType variable) {
                out.add(variable.displayName());
            } else if (type instanceof Type.DeclaredType declared) {
                declared.typeArguments().forEach(argument -> collectTypeVariableNames(argument, out));
            } else if (type instanceof Type.ArrayType array) {
                collectTypeVariableNames(array.componentType(), out);
            } else if (type instanceof Type.WildcardType wildcard) {
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
            if (resultType.kind() != Type.Kind.UNKNOWN) {
                bindTypeVariables(signature.returnType(), resultType, substitutions);
            }
        }

        private @Nullable FunctionalSignature functionalSignature(Type functionalType) {
            if (!(functionalType instanceof Type.DeclaredType declared))
                return null;

            String qualifiedName = resolveQualifiedTypeName(eraseTypeArguments(declared.displayName()));
            ClassStub stub = classStub(qualifiedName);
            if (stub == null)
                return null;

            Map<String, Type> functionalSubstitutions = new LinkedHashMap<>();
            int count = Math.min(stub.typeParameters().size(), declared.typeArguments().size());
            for (int index = 0; index < count; index++) {
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
            SyntaxNode body = directChild(lambda, JavaSyntaxKinds.LAMBDA_BODY.id());
            if (body == null)
                return UNKNOWN_TYPE;
            for (SyntaxNode child : body.children()) {
                if (isExpressionNode(child))
                    return inferType(child);
            }
            return UNKNOWN_TYPE;
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
            if (resolvedMethod != null && resolvedMethod.kind() == SymbolKind.METHOD) {
                Type resolvedReturnType = typeOfSymbol(resolvedMethod);
                if (resolvedReturnType.kind() != Type.Kind.UNKNOWN
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
                : receiverType.kind() == Type.Kind.DECLARED
                    ? resolveQualifiedTypeName(receiverType.displayName())
                    : resolveQualifiedTypeName(ownerText);
            ClassStub ownerStub = classStub(ownerQualifiedName);
            if (ownerStub == null)
                return UNKNOWN_TYPE;

            for (MethodStub method : ownerStub.methods()) {
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
                if (actualOwnerType instanceof Type.DeclaredType actualDeclared) {
                    int count = Math.min(ownerStub.typeParameters().size(), actualDeclared.typeArguments().size());
                    for (int index = 0; index < count; index++) {
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
                case Type.TypeVariableType ignored -> true;
                case Type.ArrayType array -> containsTypeVariable(array.componentType());
                case Type.WildcardType wildcard ->
                    wildcard.upperBound() != null && containsTypeVariable(wildcard.upperBound())
                        || wildcard.lowerBound() != null && containsTypeVariable(wildcard.lowerBound());
                case Type.DeclaredType declared -> declared.typeArguments().stream()
                    .anyMatch(this::containsTypeVariable);
                default -> false;
            };
        }

        private @Nullable ClassStub classStub(@Nullable String qualifiedName) {
            if (qualifiedName == null)
                return null;
            Map<String, ClassStub> stubs = projectIndex == null
                ? loadJdkClassStubsByQualifiedName()
                : projectIndex.classStubsByQualifiedName();
            return stubs.get(qualifiedName);
        }

        private Type unwrapWildcard(Type type) {
            Type current = type;
            while (current instanceof Type.WildcardType wildcard) {
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
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.DOUBLE_COLON).id().equals(token.kind().id()))
                    break;
                if (!isTriviaToken(token) && !isMissingTokenKind(token.kind().id())) {
                    builder.append(token.text());
                }
            }
            return builder.isEmpty() ? null : builder.toString();
        }

        private @Nullable String methodReferenceMemberName(SyntaxNode methodReference) {
            boolean afterSeparator = false;
            String name = null;
            for (SyntaxToken token : leafTokens(methodReference)) {
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.DOUBLE_COLON).id().equals(token.kind().id())) {
                    afterSeparator = true;
                    continue;
                }
                if (afterSeparator && !isTriviaToken(token) && !isMissingTokenKind(token.kind().id())) {
                    name = token.text();
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
                : directChild(declaration, JavaSyntaxKinds.PARAMETER_LIST.id());
            if (parameterList == null)
                return List.of();

            List<Type> parameterTypes = new ArrayList<>();
            for (SyntaxNode child : parameterList.children()) {
                if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id()))
                    continue;
                SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
                Type parameterType = typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
                if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                    parameterType = new Type.ArrayType(parameterType);
                }
                parameterTypes.add(parameterType);
            }
            return List.copyOf(parameterTypes);
        }

        private void bindTypeVariables(Type parameterType, Type argumentType, Map<String, Type> substitutions) {
            if (parameterType instanceof Type.TypeVariableType variable) {
                Type boundArgument = argumentType instanceof Type.WildcardType wildcard
                    ? wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound()
                    : argumentType;
                if (boundArgument != null) {
                    Type existing = substitutions.get(variable.displayName());
                    if (existing == null || isSelfTypeVariable(existing, variable.displayName())) {
                        substitutions.put(variable.displayName(), boundArgument);
                    }
                }
                return;
            }
            if (parameterType instanceof Type.WildcardType wildcard) {
                Type bound = wildcard.upperBound() != null ? wildcard.upperBound() : wildcard.lowerBound();
                if (bound != null) {
                    bindTypeVariables(bound, argumentType, substitutions);
                }
                return;
            }
            if (parameterType instanceof Type.ArrayType parameterArray
                && argumentType instanceof Type.ArrayType argumentArray) {
                bindTypeVariables(parameterArray.componentType(), argumentArray.componentType(), substitutions);
                return;
            }
            if (parameterType instanceof Type.DeclaredType parameterDeclared
                && argumentType instanceof Type.DeclaredType argumentDeclared
                && sameRawType(parameterDeclared.displayName(), argumentDeclared.displayName())) {
                int count = Math.min(parameterDeclared.typeArguments().size(), argumentDeclared.typeArguments().size());
                for (int index = 0; index < count; index++) {
                    bindTypeVariables(
                        parameterDeclared.typeArguments().get(index),
                        argumentDeclared.typeArguments().get(index),
                        substitutions);
                }
            }
        }

        private void bindOwnerTypeArguments(Symbol methodSymbol, Type receiverType, Map<String, Type> substitutions) {
            if (!(receiverType instanceof Type.DeclaredType receiverDeclared))
                return;

            String ownerQualifiedName = ownerQualifiedName(methodSymbol).orElse(null);
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return;

            List<String> ownerTypeParameters = ownerTypeParameterNames(ownerQualifiedName);
            Type.DeclaredType ownerView = declaredViewAs(
                receiverDeclared, ownerQualifiedName, new HashSet<>());
            List<Type> actualTypeArguments = ownerView == null
                ? declaredTypeArguments(receiverType)
                : ownerView.typeArguments();
            int count = Math.min(ownerTypeParameters.size(), actualTypeArguments.size());
            for (int index = 0; index < count; index++) {
                Type actualTypeArgument = effectiveOwnerTypeArgument(
                    ownerQualifiedName, index, actualTypeArguments.get(index));
                substitutions.putIfAbsent(ownerTypeParameters.get(index), actualTypeArgument);
            }
        }

        private Type effectiveOwnerTypeArgument(String ownerQualifiedName, int index, Type actualTypeArgument) {
            if (!(actualTypeArgument instanceof Type.WildcardType wildcard))
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
                    if (!JavaSyntaxKinds.TYPE_PARAMETER.id().equals(child.kind().id()))
                        continue;
                    if (currentIndex++ != index)
                        continue;
                    SyntaxNode boundNode = directChild(child, JavaSyntaxKinds.TYPE_BOUND.id());
                    SyntaxNode bound = boundNode == null
                        ? null
                        : directChild(boundNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    return bound == null
                        ? new Type.DeclaredType("java.lang.Object", List.of())
                        : typeFromTypeReference(bound);
                }
            }
            return null;
        }

        private @Nullable Type.DeclaredType declaredViewAs(
            Type.DeclaredType candidate,
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
                Type semanticSuper = substituteTypeVariables(directSuper, substitutions);
                if (!(semanticSuper instanceof Type.DeclaredType declaredSuper))
                    continue;
                Type.DeclaredType match = declaredViewAs(declaredSuper, targetQualifiedName, visited);
                if (match != null)
                    return match;
            }
            return null;
        }

        private List<Type.DeclaredType> sourceDirectSuperTypes(String ownerQualifiedName) {
            for (Symbol symbol : context.allTypeSymbols()) {
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

        private List<Type.DeclaredType> loadIndexedDirectSuperTypes(String ownerQualifiedName) {
            JavaProjectSemanticIndex.SymbolDescriptor descriptor = projectIndex.lookupQualifiedName(ownerQualifiedName)
                .stream()
                .filter(symbol -> symbol.sourceFile() != null && isTypeSymbol(symbol.kind()))
                .findFirst()
                .orElse(null);
            if (descriptor == null)
                return List.of();
            try {
                String source = Files.readString(descriptor.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                var sourceContext = new JavaRuleContext(
                    descriptor.sourceFile(), source, model, projectIndex);
                return findIndexedDirectSuperTypes(
                    model.syntaxTree().root(), model, sourceContext, ownerQualifiedName);
            } catch (Exception _) {
                return List.of();
            }
        }

        private List<Type.DeclaredType> findIndexedDirectSuperTypes(
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
                List<Type.DeclaredType> result = findIndexedDirectSuperTypes(
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
                return new Type.VoidType();
            if (text.startsWith("?extends"))
                return new Type.WildcardType(indexedProjectTypeFromText(sourceContext, text.substring(8)), null);
            if (text.startsWith("?super"))
                return new Type.WildcardType(null, indexedProjectTypeFromText(sourceContext, text.substring(6)));
            if ("?".equals(text))
                return new Type.WildcardType(new Type.DeclaredType("java.lang.Object", List.of()), null);

            int arrayDimensions = 0;
            while (text.endsWith("[]")) {
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
                    if (!argument.isBlank()) {
                        typeArguments.add(indexedProjectTypeFromText(sourceContext, argument.trim()));
                    }
                }
            }

            String qualifiedName = sourceContext.resolveQualifiedTypeName(rawType);
            Type resolved = switch (rawType) {
                case "boolean", "byte", "short", "char", "int", "long", "float", "double" ->
                    new Type.PrimitiveType(rawType);
                default -> qualifiedName == null || qualifiedName.isBlank()
                    || isLikelyTypeVariableName(rawType) && qualifiedName.equals(rawType)
                        ? isLikelyTypeVariableName(rawType)
                            ? new Type.TypeVariableType(rawType)
                            : new Type.UnknownType(text)
                        : new Type.DeclaredType(qualifiedName, typeArguments);
            };
            for (int index = 0; index < arrayDimensions; index++) {
                resolved = new Type.ArrayType(resolved);
            }
            return resolved;
        }

        private List<Type.DeclaredType> declaredDirectSuperTypes(
            SyntaxNode declaration,
            Function<SyntaxNode, Type> typeResolver
        ) {
            List<Type.DeclaredType> directSupers = new ArrayList<>();
            for (SyntaxNode child : declaration.children()) {
                if (!JavaSyntaxKinds.EXTENDS_CLAUSE.id().equals(child.kind().id())
                    && !JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id().equals(child.kind().id()))
                    continue;
                for (SyntaxNode typeRef : child.children()) {
                    if (!JavaSyntaxKinds.TYPE_REFERENCE.id().equals(typeRef.kind().id()))
                        continue;
                    Type resolved = typeResolver.apply(typeRef);
                    if (resolved instanceof Type.DeclaredType declared) {
                        directSupers.add(declared);
                    }
                }
            }
            return List.copyOf(directSupers);
        }

        private Type substituteTypeVariables(Type type, Map<String, Type> substitutions) {
            return switch (type) {
                case Type.UnknownType unknown -> unknown;
                case Type.VoidType voidType -> voidType;
                case Type.PrimitiveType primitive -> primitive;
                case Type.TypeVariableType variable -> substitutions.getOrDefault(variable.displayName(), variable);
                case Type.ArrayType array ->
                    new Type.ArrayType(substituteTypeVariables(array.componentType(), substitutions));
                case Type.WildcardType wildcard -> new Type.WildcardType(
                    wildcard.upperBound() == null
                        ? null
                        : substituteTypeVariables(wildcard.upperBound(), substitutions),
                    wildcard.lowerBound() == null
                        ? null
                        : substituteTypeVariables(wildcard.lowerBound(), substitutions));
                case Type.DeclaredType declared -> new Type.DeclaredType(
                    declared.displayName(),
                    declaredTypeArguments(declared).stream()
                        .map(argument -> substituteTypeVariables(argument, substitutions))
                        .toList());
            };
        }

        private List<Type> declaredTypeArguments(Type type) {
            if (type instanceof Type.DeclaredType declared)
                return declared.typeArguments();
            return List.of();
        }

        private List<String> ownerTypeParameterNames(String ownerQualifiedName) {
            Map<String, ClassStub> stubs = projectIndex == null
                ? loadJdkClassStubsByQualifiedName()
                : projectIndex.classStubsByQualifiedName();
            ClassStub stub = stubs.get(ownerQualifiedName);
            if (stub != null && !stub.typeParameters().isEmpty())
                return stub.typeParameters().stream().map(TypeParameter::name).toList();

            for (Symbol symbol : context.allTypeSymbols()) {
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
                String source = Files.readString(descriptor.sourceFile());
                SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
                return findDeclaredTypeParameterNames(
                    model.syntaxTree().root(), model, ownerQualifiedName);
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
                ? loadJdkClassStubsByQualifiedName()
                : projectIndex.classStubsByQualifiedName();
            ClassStub stub = stubs.get(ownerQualifiedName);
            if (stub == null)
                return false;

            for (TypeParameter parameter : stub.typeParameters()) {
                if (!parameter.name().equals(variableName))
                    continue;
                for (dev.railroadide.railroad.ide.classparser.Type bound : parameter.bounds()) {
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
                if (createdType instanceof Type.DeclaredType declaredType
                    && declaredType.typeArguments().isEmpty()) {
                    Type contextualType = directContextualTargetType(creationNode);
                    if (contextualType instanceof Type.DeclaredType contextualDeclared
                        && sameRawType(declaredType.displayName(), contextualDeclared.displayName()))
                        return contextualDeclared;
                }
                return createdType;
            }

            Symbol resolved = context.resolvedSymbol(creationNode);
            return resolved != null && resolved.kind() == SymbolKind.CONSTRUCTOR
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
                return new Type.DeclaredType("String", List.of());

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
                collectSwitchResultTypes(child, out);
            }
        }

        private Type inferFromChildren(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                if (isExpressionNode(child)) {
                    Type type = inferType(child);
                    if (type.kind() != Type.Kind.UNKNOWN)
                        return type;
                }
            }
            return UNKNOWN_TYPE;
        }

        private Type typeOfSymbol(Symbol symbol) {
            if (symbol instanceof SyntheticMemberSymbol synthetic)
                return synthetic.valueType();

            return switch (symbol.kind()) {
                case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD ->
                    new Type.DeclaredType(symbol.simpleName(), List.of());
                case METHOD -> methodReturnType(symbol);
                case PARAMETER, LOCAL_VARIABLE, FIELD -> variableLikeType(symbol);
                case CONSTRUCTOR ->
                    new Type.DeclaredType(ownerQualifiedName(symbol).orElse(symbol.simpleName()), List.of());
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
                    return new Type.DeclaredType(
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
                    if (elementType.kind() != Type.Kind.UNKNOWN) {
                        parameterType = elementType;
                    }
                }
                return hasTokenKind(declaration, JavaTokenType.ELLIPSIS)
                    ? new Type.ArrayType(parameterType)
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
            if (iterableType instanceof Type.ArrayType arrayType)
                return arrayType.componentType();
            if (!(iterableType instanceof Type.DeclaredType declaredType))
                return UNKNOWN_TYPE;

            Type.DeclaredType iterableView = declaredViewAs(
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
                return new Type.VoidType();
            if (text.startsWith("? extends "))
                return new Type.WildcardType(typeFromTypeReferenceText(text.substring(10), usageSite), null);
            if (text.startsWith("? super "))
                return new Type.WildcardType(null, typeFromTypeReferenceText(text.substring(8), usageSite));
            if (text.startsWith("?extends"))
                return new Type.WildcardType(typeFromTypeReferenceText(text.substring(8), usageSite), null);
            if (text.startsWith("?super"))
                return new Type.WildcardType(null, typeFromTypeReferenceText(text.substring(6), usageSite));
            if ("?".equals(text))
                return new Type.WildcardType(new Type.DeclaredType("java.lang.Object", List.of()), null);
            if (text.endsWith("[]"))
                return new Type.ArrayType(typeFromTypeReferenceText(text.substring(0, text.length() - 2), usageSite));

            int typeArgsStart = findTopLevelTypeArgumentsStart(text);
            if (typeArgsStart > 0 && text.endsWith(">")) {
                String rawText = text.substring(0, typeArgsStart).trim();
                String argsText = text.substring(typeArgsStart + 1, text.length() - 1).trim();
                List<Type> typeArguments = new ArrayList<>();
                for (String part : splitTopLevelTypeArguments(argsText)) {
                    String argumentText = part.trim();
                    if (!argumentText.isEmpty()) {
                        typeArguments.add(typeFromTypeReferenceText(argumentText, usageSite));
                    }
                }
                String qualifiedRaw = resolveQualifiedTypeName(rawText);
                String declaredName = qualifiedRaw == null || qualifiedRaw.isBlank() ? rawText : qualifiedRaw;
                return declaredName.isBlank()
                    ? UNKNOWN_TYPE
                    : new Type.DeclaredType(declaredName, typeArguments);
            }

            if (NUMERIC_PRIMITIVES.contains(text) || "boolean".equals(text))
                return new Type.PrimitiveType(text);
            if (usageSite != null && isLexicallyDeclaredTypeVariable(text, usageSite))
                return new Type.TypeVariableType(text);
            if (isTypeVariableNameInScope(text))
                return new Type.TypeVariableType(text);
            String qualified = resolveQualifiedTypeName(text);
            if (isLikelyTypeVariableName(text)
                && (qualified == null || qualified.isBlank() || Objects.equals(qualified, text)))
                return new Type.TypeVariableType(text);
            String declaredName = qualified == null || qualified.isBlank() ? simpleTypeName(text) : qualified;
            return declaredName.isBlank() ? UNKNOWN_TYPE : new Type.DeclaredType(declaredName, List.of());
        }

        private boolean isLexicallyDeclaredTypeVariable(String variableName, SyntaxNode usageSite) {
            SyntaxNode current = usageSite;
            while (current != null) {
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
                if (symbol.kind() == SymbolKind.TYPE_PARAMETER && name.equals(symbol.simpleName()))
                    return true;
            }
            return false;
        }

        private void collectImportsFromRootScope() {
            Map<String, List<Symbol>> rootDeclarations = context.rootScope.snapshotDeclarations();
            for (List<Symbol> symbols : rootDeclarations.values()) {
                for (Symbol symbol : symbols) {
                    if (symbol.kind() != SymbolKind.IMPORT)
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
                case 5 -> new Type.PrimitiveType("double");
                case 4 -> new Type.PrimitiveType("float");
                case 3 -> new Type.PrimitiveType("long");
                default -> new Type.PrimitiveType("int");
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
            return type.kind() == Type.Kind.PRIMITIVE && NUMERIC_PRIMITIVES.contains(type.displayName());
        }

        private static boolean isStringType(Type type) {
            return type.kind() == Type.Kind.DECLARED && "String".equals(simpleTypeName(type.displayName()));
        }
    }

    private static final class SyntheticMemberSymbol implements Symbol {
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
            this.kind = Objects.requireNonNull(kind, "kind");
            this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
            this.qualifiedName = qualifiedName;
            this.declaration = declaration;
            this.valueType = Objects.requireNonNull(valueType, "valueType");
            this.parameterTypes = List.copyOf(Objects.requireNonNull(parameterTypes, "parameterTypes"));
            this.staticMember = staticMember;
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
        Type result = new Type.UnknownType("<unknown>");
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
        if (whenTrue.kind() == Type.Kind.UNKNOWN)
            return whenFalse;
        if (whenFalse.kind() == Type.Kind.UNKNOWN)
            return whenTrue;
        if (whenTrue.equals(whenFalse) || whenTrue.displayName().equals(whenFalse.displayName()))
            return whenTrue;
        if (whenTrue.kind() == Type.Kind.DECLARED && whenFalse.kind() == Type.Kind.DECLARED) {
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
        return new Type.UnknownType("<unknown>");
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
        return type instanceof Type.TypeVariableType variable
            && variableName.equals(variable.displayName())
            || type.kind() == Type.Kind.DECLARED
                && variableName.equals(type.displayName())
                && isLikelyTypeVariableName(type.displayName());
    }

    private static Type toSemanticType(dev.railroadide.railroad.ide.classparser.Type type) {
        if (type == null)
            return new Type.UnknownType("<unknown>");

        return switch (type) {
            case dev.railroadide.railroad.ide.classparser.Type.PrimitiveType primitive ->
                "void".equals(primitive.name())
                    ? new Type.VoidType()
                    : new Type.PrimitiveType(primitive.name());
            case dev.railroadide.railroad.ide.classparser.Type.ArrayType array ->
                new Type.ArrayType(toSemanticType(array.componentType()));
            case dev.railroadide.railroad.ide.classparser.Type.ClassType clazz ->
                new Type.DeclaredType(
                    clazz.name(),
                    clazz.typeArguments().stream().map(JavaSemanticAnalyzer::toSemanticType).toList());
            case dev.railroadide.railroad.ide.classparser.Type.TypeVariable variable ->
                new Type.TypeVariableType(variable.name());
            case dev.railroadide.railroad.ide.classparser.Type.WildcardType wildcard -> {
                Type bound = wildcard.bound() == null
                    ? new Type.UnknownType("<unknown>")
                    : toSemanticType(wildcard.bound());
                yield wildcard.isUpperBound()
                    ? new Type.WildcardType(bound, null)
                    : new Type.WildcardType(null, bound);
            }
        };
    }

    private static Type numericLiteralType(String text, boolean floatingPoint) {
        String normalized = text == null ? "" : text.replace("_", "").toLowerCase(Locale.ROOT);
        if (floatingPoint)
            return new Type.PrimitiveType(normalized.endsWith("f") ? "float" : "double");
        return new Type.PrimitiveType(normalized.endsWith("l") ? "long" : "int");
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

    public static Set<String> loadJdkQualifiedTypeNames() {
        Set<String> cached = cachedJdkQualifiedTypeNames;
        if (cached != null)
            return cached;

        synchronized (JavaSemanticAnalyzer.class) {
            if (cachedJdkQualifiedTypeNames != null)
                return cachedJdkQualifiedTypeNames;

            cachedJdkQualifiedTypeNames = Set.copyOf(loadJdkClassStubsByQualifiedName().keySet());
            return cachedJdkQualifiedTypeNames;
        }
    }

    public static Map<String, ClassStub> loadJdkClassStubsByQualifiedName() {
        Map<String, ClassStub> cached = cachedJdkClassStubsByQualifiedName;
        if (cached != null)
            return cached;

        synchronized (JavaSemanticAnalyzer.class) {
            if (cachedJdkClassStubsByQualifiedName != null)
                return cachedJdkClassStubsByQualifiedName;

            Map<String, ClassStub> byQualifiedName = new LinkedHashMap<>();
            for (ClassStub stub : Indexes.scanStandardLibrary()) {
                String fullName = stub.getFullName();
                if (fullName == null || fullName.isBlank())
                    continue;
                byQualifiedName.put(fullName, stub);
            }

            cachedJdkClassStubsByQualifiedName = Map.copyOf(byQualifiedName);
            return cachedJdkClassStubsByQualifiedName;
        }
    }

    public static @Nullable SyntaxNode directChild(SyntaxNode node, String kindId) {
        for (SyntaxNode child : node.children()) {
            if (kindId.equals(child.kind().id()))
                return child;
        }
        return null;
    }

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
                return token.text();
        }
        return null;
    }

    public static @Nullable String identifierBeforeChildKind(SyntaxNode node, String childKindId) {
        String lastIdentifier = null;
        for (SyntaxNode child : node.children()) {
            if (childKindId.equals(child.kind().id()))
                return lastIdentifier;

            if (child instanceof SyntaxToken token && isIdentifierLikeToken(token)) {
                lastIdentifier = token.text();
            }
        }
        return lastIdentifier;
    }

    public static @Nullable String firstIdentifierLikeTokenText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return isIdentifierLikeToken(token) ? token.text() : null;

        for (SyntaxNode child : node.children()) {
            String identifier = firstIdentifierLikeTokenText(child);
            if (identifier != null)
                return identifier;
        }

        return null;
    }

    public static @Nullable String lastIdentifierLikeTokenText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return isIdentifierLikeToken(token) ? token.text() : null;
        for (int index = node.children().size() - 1; index >= 0; index--) {
            String identifier = lastIdentifierLikeTokenText(node.children().get(index));
            if (identifier != null)
                return identifier;
        }
        return null;
    }

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

    public static boolean isMissingTokenKind(String kindId) {
        return kindId.startsWith("JAVA_MISSING_");
    }

    public static boolean isTriviaToken(SyntaxToken token) {
        return TRIVIA_TOKEN_KIND_IDS.contains(token.kind().id());
    }

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
                builder.append(token.text());
            }
            return;
        }

        for (SyntaxNode child : node.children()) {
            appendCanonicalQualifiedNameTokens(child, builder);
        }
    }

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
                builder.append(token.text());
            }
            return;
        }

        for (SyntaxNode child : node.children()) {
            appendCanonicalTypeTokens(child, builder);
        }
    }

    public static boolean isTypeSymbol(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> true;
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

    public static boolean isExpressionNode(SyntaxNode node) {
        return EXPRESSION_KIND_IDS.contains(node.kind().id());
    }

    public static String lastSegment(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        if (index < 0 || index == qualifiedName.length() - 1)
            return qualifiedName;
        return qualifiedName.substring(index + 1);
    }

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
        boolean isWildcard) {
    }
}
