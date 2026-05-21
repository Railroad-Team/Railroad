package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.ConstructorStub;
import dev.railroadide.railroad.ide.classparser.stub.FieldStub;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.indexing.Indexes;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

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
            JavaSyntaxKinds.tokenKind(JavaTokenType.WHEN_KEYWORD).id()
    );

    private static final Set<String> TRIVIA_TOKEN_KIND_IDS = Set.of(
            JavaSyntaxKinds.tokenKind(JavaTokenType.WHITESPACE).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.LINE_TERMINATOR).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.LINE_COMMENT).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.BLOCK_COMMENT).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.JAVADOC_COMMENT).id()
    );

    private static final Set<String> PRIMITIVE_TOKEN_KIND_IDS = Set.of(
            JavaSyntaxKinds.tokenKind(JavaTokenType.BOOLEAN_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.BYTE_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.SHORT_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.INT_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.LONG_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.CHAR_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.FLOAT_KEYWORD).id(),
            JavaSyntaxKinds.tokenKind(JavaTokenType.DOUBLE_KEYWORD).id()
    );

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
            JavaSyntaxKinds.LITERAL_EXPRESSION.id()
    );

    private JavaSemanticAnalyzer() {
    }

    public static SemanticModel analyze(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyze(JavaSyntaxParser.parse(source));
    }

    public static SemanticModel analyze(CharSequence source, JavaProjectSemanticIndex projectIndex) {
        Objects.requireNonNull(source, "source");
        return analyze(JavaSyntaxParser.parse(source), projectIndex);
    }

    public static SemanticModel analyze(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeFacts(syntaxTree));
    }

    public static SemanticModel analyze(SyntaxTree syntaxTree, JavaProjectSemanticIndex projectIndex) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return withCoreDiagnostics(analyzeFacts(syntaxTree, projectIndex));
    }

    public static SemanticModel analyzeFacts(CharSequence source) {
        Objects.requireNonNull(source, "source");
        return analyzeFacts(JavaSyntaxParser.parse(source));
    }

    public static SemanticModel analyzeFacts(CharSequence source, JavaProjectSemanticIndex projectIndex) {
        Objects.requireNonNull(source, "source");
        return analyzeFacts(JavaSyntaxParser.parse(source), projectIndex);
    }

    public static SemanticModel analyzeFacts(SyntaxTree syntaxTree) {
        Objects.requireNonNull(syntaxTree, "syntaxTree");
        return performAnalysis(syntaxTree, true);
    }

    public static SemanticModel analyzeFacts(SyntaxTree syntaxTree, JavaProjectSemanticIndex projectIndex) {
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
        JavaRuleContext context = new JavaRuleContext(Path.of("memory.java"), "", facts);
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
            @Nullable JavaProjectSemanticIndex projectIndex
    ) {
        Scope rootScope = Scope.root();
        SemanticModel.Builder builder = SemanticModel.builder(syntaxTree, rootScope);

        AnalysisContext context = new AnalysisContext(rootScope, builder, projectIndex);
        new DeclarationCollector(context).visitCompilationUnit(syntaxTree.root());

        if (includeResolutionAndTypes) {
            new NameResolver(context).resolveCompilationUnit(syntaxTree.root());
            new TypeResolver(context).resolveCompilationUnit(syntaxTree.root());
        }

        return builder.build();
    }

    private static final class AnalysisContext {
        private final Scope rootScope;
        private final SemanticModel.Builder builder;
        private final Map<SyntaxNode, Scope> scopeByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> declaredSymbolByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> resolvedSymbolByNode = new IdentityHashMap<>();
        private final @Nullable JavaProjectSemanticIndex projectIndex;
        private @Nullable String currentPackageName;

        private AnalysisContext(Scope rootScope, SemanticModel.Builder builder, @Nullable JavaProjectSemanticIndex projectIndex) {
            this.rootScope = rootScope;
            this.builder = builder;
            this.projectIndex = projectIndex;
        }

        private void attachScope(SyntaxNode node, Scope scope) {
            scopeByNode.put(node, scope);
        }

        private Scope scopeFor(SyntaxNode node) {
            Scope scope = scopeByNode.get(node);
            if (scope != null)
                return scope;

            SyntaxNode current = node;
            while (true) {
                var parent = current.parent();
                if (parent.isEmpty())
                    return rootScope;

                current = parent.get();
                scope = scopeByNode.get(current);
                if (scope != null)
                    return scope;
            }
        }

        private void declare(SyntaxNode declarationNode, Symbol symbol) {
            declaredSymbolByNode.put(declarationNode, symbol);
            builder.declare(declarationNode, symbol);
        }

        private void resolve(SyntaxNode referenceNode, Symbol symbol) {
            resolvedSymbolByNode.put(referenceNode, symbol);
            builder.resolve(referenceNode, symbol);
        }

        private @Nullable Symbol resolvedSymbol(SyntaxNode node) {
            return resolvedSymbolByNode.get(node);
        }

        private @Nullable Symbol declaredSymbol(SyntaxNode node) {
            return declaredSymbolByNode.get(node);
        }

        private void type(SyntaxNode node, Type type) {
            builder.type(node, type);
        }

        private List<Symbol> allTypeSymbols() {
            List<Symbol> symbols = new ArrayList<>();
            for (Symbol symbol : declaredSymbolByNode.values()) {
                if (isTypeSymbol(symbol.kind()))
                    symbols.add(symbol);
            }
            return List.copyOf(symbols);
        }

        private List<Symbol> allDeclaredSymbols() {
            return List.copyOf(declaredSymbolByNode.values());
        }

        private @Nullable Symbol enclosingTypeSymbol(SyntaxNode node) {
            SyntaxNode current = node;
            while (true) {
                var parent = current.parent();
                if (parent.isEmpty())
                    return null;

                current = parent.get();
                Symbol declared = declaredSymbol(current);
                if (declared != null && isTypeSymbol(declared.kind()))
                    return declared;
            }
        }
    }

    private static final class DeclarationCollector {
        private final AnalysisContext context;

        private DeclarationCollector(AnalysisContext context) {
            this.context = context;
        }

        private void visitCompilationUnit(SyntaxNode compilationUnit) {
            visitNode(compilationUnit, context.rootScope, null);
        }

        private void visitNode(SyntaxNode node, Scope scope, @Nullable String currentTypeQualifiedName) {
            context.attachScope(node, scope);

            String kindId = node.kind().id();

            if (JavaSyntaxKinds.BLOCK.id().equals(kindId)) {
                Scope blockScope = scope.child();
                for (SyntaxNode child : node.children())
                    visitNode(child, blockScope, currentTypeQualifiedName);
                return;
            }
            if (JavaSyntaxKinds.TRY_STATEMENT.id().equals(kindId)) {
                Scope tryScope = scope.child();
                for (SyntaxNode child : node.children())
                    visitNode(child, tryScope, currentTypeQualifiedName);
                return;
            }
            if (JavaSyntaxKinds.CATCH_CLAUSE.id().equals(kindId)) {
                Scope catchScope = scope.child();
                for (SyntaxNode child : node.children())
                    visitNode(child, catchScope, currentTypeQualifiedName);
                return;
            }

            if (JavaSyntaxKinds.PACKAGE_DECLARATION.id().equals(kindId)) {
                declarePackage(node, scope);
            } else if (JavaSyntaxKinds.IMPORT_DECLARATION.id().equals(kindId)) {
                declareImport(node, scope);
            } else if (JavaSyntaxKinds.MODULE_DECLARATION.id().equals(kindId)) {
                declareModule(node, scope);
            } else if (JavaSyntaxKinds.CLASS_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.CLASS, JavaTokenType.CLASS_KEYWORD, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.INTERFACE_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.INTERFACE, JavaTokenType.INTERFACE_KEYWORD, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.ENUM_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.ENUM, JavaTokenType.ENUM_KEYWORD, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.ANNOTATION, JavaTokenType.AT_INTERFACE_KEYWORD, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.RECORD_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.RECORD, JavaTokenType.RECORD_KEYWORD, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.FIELD_DECLARATION.id().equals(kindId)) {
                declareFields(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.id().equals(kindId)) {
                declareLocalVariables(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(kindId)) {
                declareEnumConstant(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.METHOD_DECLARATION.id().equals(kindId)) {
                Scope methodScope = declareMethod(node, scope, currentTypeQualifiedName);
                for (SyntaxNode child : node.children())
                    visitNode(child, methodScope, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id().equals(kindId)) {
                Scope constructorScope = declareConstructor(node, scope, currentTypeQualifiedName);
                for (SyntaxNode child : node.children())
                    visitNode(child, constructorScope, currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.PARAMETER.id().equals(kindId)) {
                declareParameter(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.RECORD_COMPONENT.id().equals(kindId)) {
                declareRecordComponent(node, scope, currentTypeQualifiedName);
            }

            for (SyntaxNode child : node.children())
                visitNode(child, scope, currentTypeQualifiedName);
        }

        private void visitTypeDeclaration(
                SyntaxNode declarationNode,
                Scope scope,
                SymbolKind symbolKind,
                JavaTokenType declarationKeyword,
                @Nullable String enclosingTypeQualifiedName
        ) {
            String simpleName = identifierAfterKeyword(declarationNode, declarationKeyword);
            if (simpleName == null || simpleName.isBlank()) {
                for (SyntaxNode child : declarationNode.children())
                    visitNode(child, scope, enclosingTypeQualifiedName);
                return;
            }

            String qualifiedName = qualifyTypeName(simpleName, enclosingTypeQualifiedName);
            declareSymbol(scope, declarationNode, symbolKind, simpleName, qualifiedName);

            Scope typeScope = scope.child();
            for (SyntaxNode child : declarationNode.children())
                visitNode(child, typeScope, qualifiedName);
        }

        private void declarePackage(SyntaxNode packageDeclaration, Scope scope) {
            SyntaxNode qualifiedNameNode = directChild(packageDeclaration, JavaSyntaxKinds.QUALIFIED_NAME.id());
            if (qualifiedNameNode == null)
                return;

            String qualifiedName = canonicalQualifiedName(qualifiedNameNode);
            if (qualifiedName == null || qualifiedName.isBlank())
                return;

            String simpleName = lastSegment(qualifiedName);
            context.currentPackageName = qualifiedName;
            declareSymbol(scope, packageDeclaration, SymbolKind.PACKAGE, simpleName, qualifiedName);
        }

        private void declareImport(SyntaxNode importDeclaration, Scope scope) {
            SyntaxNode importTarget = directChild(importDeclaration, JavaSyntaxKinds.IMPORT_TARGET.id());
            if (importTarget == null)
                return;

            String importName = canonicalQualifiedName(importTarget);
            if (importName == null || importName.isBlank())
                return;

            declareSymbol(scope, importDeclaration, SymbolKind.IMPORT, importName, importName);
        }

        private void declareModule(SyntaxNode moduleDeclaration, Scope scope) {
            SyntaxNode qualifiedNameNode = directChild(moduleDeclaration, JavaSyntaxKinds.QUALIFIED_NAME.id());
            if (qualifiedNameNode == null)
                return;

            String moduleName = canonicalQualifiedName(qualifiedNameNode);
            if (moduleName == null || moduleName.isBlank())
                return;

            declareSymbol(scope, moduleDeclaration, SymbolKind.MODULE, moduleName, moduleName);
        }

        private void declareFields(SyntaxNode fieldDeclaration, Scope scope, @Nullable String ownerQualifiedName) {
            for (SyntaxNode child : fieldDeclaration.children()) {
                if (!JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(child.kind().id()))
                    continue;

                String fieldName = firstIdentifierLikeTokenText(child);
                if (fieldName == null || fieldName.isBlank())
                    continue;

                String qualifiedName = qualifyMemberName(ownerQualifiedName, fieldName);
                declareSymbol(scope, child, SymbolKind.FIELD, fieldName, qualifiedName);
            }
        }

        private void declareLocalVariables(SyntaxNode localVariableDeclaration, Scope scope, @Nullable String ownerQualifiedName) {
            for (SyntaxNode child : localVariableDeclaration.children()) {
                if (!JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(child.kind().id()))
                    continue;

                String variableName = firstIdentifierLikeTokenText(child);
                if (variableName == null || variableName.isBlank())
                    continue;

                String qualifiedName = qualifyMemberName(ownerQualifiedName, variableName);
                declareSymbol(scope, child, SymbolKind.LOCAL_VARIABLE, variableName, qualifiedName);
            }
        }

        private void declareEnumConstant(SyntaxNode enumConstant, Scope scope, @Nullable String ownerQualifiedName) {
            String constantName = firstIdentifierLikeTokenText(enumConstant);
            if (constantName == null || constantName.isBlank())
                return;

            String qualifiedName = qualifyMemberName(ownerQualifiedName, constantName);
            declareSymbol(scope, enumConstant, SymbolKind.FIELD, constantName, qualifiedName);
        }

        private Scope declareMethod(SyntaxNode methodDeclaration, Scope scope, @Nullable String ownerQualifiedName) {
            String methodName = identifierBeforeChildKind(methodDeclaration, JavaSyntaxKinds.PARAMETER_LIST.id());
            if (methodName == null || methodName.isBlank())
                return scope.child();

            String qualifiedName = qualifyMemberName(ownerQualifiedName, methodName);
            declareSymbol(scope, methodDeclaration, SymbolKind.METHOD, methodName, qualifiedName);
            return scope.child();
        }

        private Scope declareConstructor(SyntaxNode constructorDeclaration, Scope scope, @Nullable String ownerQualifiedName) {
            String constructorName = identifierBeforeChildKind(constructorDeclaration, JavaSyntaxKinds.PARAMETER_LIST.id());
            if (constructorName == null || constructorName.isBlank())
                constructorName = "<init>";

            String qualifiedName = qualifyMemberName(ownerQualifiedName, constructorName);
            declareSymbol(scope, constructorDeclaration, SymbolKind.CONSTRUCTOR, constructorName, qualifiedName);
            return scope.child();
        }

        private void declareParameter(SyntaxNode parameterNode, Scope scope, @Nullable String ownerQualifiedName) {
            String parameterName = lastIdentifierLikeTokenText(parameterNode);
            if (parameterName == null || parameterName.isBlank())
                return;

            String qualifiedName = qualifyMemberName(ownerQualifiedName, parameterName);
            declareSymbol(scope, parameterNode, SymbolKind.PARAMETER, parameterName, qualifiedName);
        }

        private void declareRecordComponent(SyntaxNode recordComponentNode, Scope scope, @Nullable String ownerQualifiedName) {
            String componentName = lastIdentifierLikeTokenText(recordComponentNode);
            if (componentName == null || componentName.isBlank())
                return;

            String qualifiedName = qualifyMemberName(ownerQualifiedName, componentName);
            declareSymbol(scope, recordComponentNode, SymbolKind.PARAMETER, componentName, qualifiedName);
        }

        private void declareSymbol(
                Scope scope,
                SyntaxNode declarationNode,
                SymbolKind kind,
                String simpleName,
                @Nullable String qualifiedName
        ) {
            Symbol symbol = new SimpleSymbol(kind, simpleName, qualifiedName, declarationNode);
            scope.declare(symbol);
            context.declare(declarationNode, symbol);
        }

        private String qualifyTypeName(String simpleName, @Nullable String enclosingTypeQualifiedName) {
            if (enclosingTypeQualifiedName != null && !enclosingTypeQualifiedName.isBlank())
                return enclosingTypeQualifiedName + "." + simpleName;
            if (context.currentPackageName != null && !context.currentPackageName.isBlank())
                return context.currentPackageName + "." + simpleName;
            return simpleName;
        }

        private static @Nullable String qualifyMemberName(@Nullable String ownerQualifiedName, String simpleName) {
            if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
                return simpleName;
            return ownerQualifiedName + "#" + simpleName;
        }
    }

    private static final class NameResolver {
        private final AnalysisContext context;
        private final @Nullable JavaProjectSemanticIndex projectIndex;
        private final Set<String> localQualifiedTypeNames;
        private final Set<String> availableQualifiedTypeNames;
        private final Map<String, ClassStub> jdkClassStubsByQualifiedName;
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

        private NameResolver(AnalysisContext context) {
            this.context = context;
            this.projectIndex = context.projectIndex;
            Set<String> qualified = new HashSet<>();
            for (Symbol symbol : context.allTypeSymbols()) {
                symbol.qualifiedName().ifPresent(qualified::add);
            }

            this.localQualifiedTypeNames = Set.copyOf(qualified);
            Set<String> available = new HashSet<>(localQualifiedTypeNames);
            if (projectIndex != null) {
                for (JavaProjectSemanticIndex.SourceFileIndex file : projectIndex.files().values()) {
                    available.addAll(file.declaredQualifiedNames());
                }
            }
            available.addAll(loadJdkQualifiedTypeNames());
            this.availableQualifiedTypeNames = Set.copyOf(available);
            this.jdkClassStubsByQualifiedName = loadJdkClassStubsByQualifiedName();
            collectImportsFromRootScope();
            classifyImports();
            indexLocalStaticMembers();
            indexLocalMembers();
        }

        private void resolveCompilationUnit(SyntaxNode root) {
            resolveNode(root);
        }

        private void resolveNode(SyntaxNode node) {
            for (SyntaxNode child : node.children())
                resolveNode(child);

            String kindId = node.kind().id();

            if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(kindId)) {
                resolveNameExpression(node);
            } else if (JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(kindId)) {
                resolveFieldAccess(node);
            } else if (JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(kindId)) {
                resolveMethodInvocation(node);
            } else if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(kindId)) {
                resolveClassInstanceCreation(node);
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
            if (matches.isEmpty()) {
                resolveNameFromImports(expressionNode, simpleName, name);
                return;
            }

            context.resolve(expressionNode, matches.getFirst());
        }

        private void resolveFieldAccess(SyntaxNode expressionNode) {
            SyntaxNode memberNode = selectorNameNode(expressionNode);
            SyntaxNode targetNode = explicitReceiver(expressionNode);
            if (memberNode == null || targetNode == null)
                return;

            String fieldName = canonicalQualifiedName(memberNode);
            if (fieldName == null || fieldName.isBlank())
                return;

            MemberLookup lookup = resolveMemberLookup(targetNode, expressionNode);
            if (lookup.ownerQualifiedName() == null || lookup.ownerQualifiedName().isBlank())
                return;

            MemberCandidate chosen = chooseFieldCandidate(findFieldCandidates(
                    lookup.ownerQualifiedName(),
                    fieldName,
                    lookup.staticAccess()
            ));
            if (chosen == null)
                return;

            context.resolve(expressionNode, chosen.symbol());
            context.resolve(memberNode, chosen.symbol());
        }

        private void resolveMethodInvocation(SyntaxNode invocationNode) {
            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList == null)
                return;

            SyntaxNode memberNode = selectorNameNode(invocationNode);
            String methodName = memberNode == null
                    ? identifierBeforeChildKind(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id())
                    : canonicalQualifiedName(memberNode);
            if (methodName == null || methodName.isBlank())
                return;

            List<Type> argumentTypes = inferArgumentTypes(argumentList);
            SyntaxNode targetNode = explicitReceiver(invocationNode);
            if (targetNode != null) {
                MemberLookup lookup = resolveMemberLookup(targetNode, invocationNode);
                if (lookup.ownerQualifiedName() == null || lookup.ownerQualifiedName().isBlank())
                    return;

                MemberCandidate chosen = selectBestCallable(
                        findMethodCandidates(lookup.ownerQualifiedName(), methodName, lookup.staticAccess()),
                        argumentTypes
                );
                if (chosen == null)
                    return;

                context.resolve(invocationNode, chosen.symbol());
                if (memberNode != null)
                    context.resolve(memberNode, chosen.symbol());
                return;
            }

            Scope scope = context.scopeFor(invocationNode);
            MemberCandidate localChosen = selectBestCallable(localMethodCandidates(scope.lookupAll(methodName)), argumentTypes);
            if (localChosen != null) {
                context.resolve(invocationNode, localChosen.symbol());
                if (memberNode != null)
                    context.resolve(memberNode, localChosen.symbol());
                return;
            }

            Symbol enclosingType = context.enclosingTypeSymbol(invocationNode);
            if (enclosingType != null) {
                String ownerQualifiedName = enclosingType.qualifiedName().orElse(null);
                if (ownerQualifiedName != null) {
                    MemberCandidate ownerChosen = selectBestCallable(
                            findMethodCandidates(ownerQualifiedName, methodName, false),
                            argumentTypes
                    );
                    if (ownerChosen != null) {
                        context.resolve(invocationNode, ownerChosen.symbol());
                        if (memberNode != null)
                            context.resolve(memberNode, ownerChosen.symbol());
                        return;
                    }
                }
            }

            MemberCandidate importedChosen = selectBestCallable(staticImportedMethodCandidates(methodName, argumentTypes), argumentTypes);
            if (importedChosen != null) {
                context.resolve(invocationNode, importedChosen.symbol());
                if (memberNode != null)
                    context.resolve(memberNode, importedChosen.symbol());
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

            MemberCandidate chosen = selectBestCallable(findConstructorCandidates(ownerQualifiedName), argumentTypes);
            if (chosen != null)
                context.resolve(creationNode, chosen.symbol());
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
                    String ownerName = isWildcard ? qualifiedTarget.substring(0, qualifiedTarget.length() - 2) : packagePrefix(qualifiedTarget);
                    String importedName = isWildcard ? "*" : lastSegment(qualifiedTarget);

                    imports.add(new ImportSpec(
                            declarationNode,
                            targetNode,
                            qualifiedTarget,
                            ownerName,
                            importedName,
                            isStatic,
                            isWildcard
                    ));
                }
            }
        }

        private void classifyImports() {
            for (ImportSpec importSpec : imports) {
                if (importSpec.isWildcard()) {
                    if (importSpec.isStatic())
                        onDemandStaticImports.add(importSpec);
                    else
                        onDemandTypeImports.add(importSpec);
                    continue;
                }

                if (importSpec.isStatic()) {
                    staticSingleImportsByMemberName
                            .computeIfAbsent(importSpec.importedName(), ignored -> new ArrayList<>())
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
                            .computeIfAbsent(ownerName, ignored -> new HashSet<>())
                            .add(memberName);
                } else {
                    int arity = methodDeclarationArity(symbol);
                    localStaticMethodAritiesByOwner
                            .computeIfAbsent(ownerName, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(memberName, ignored -> new HashSet<>())
                            .add(arity);
                }
            }
        }

        private void resolveNameFromImports(SyntaxNode referenceNode, String simpleName, String fullName) {
            List<Symbol> candidates = new ArrayList<>();

            if (singleTypeImportsBySimpleName.containsKey(simpleName)) {
                ImportSpec importSpec = singleTypeImportsBySimpleName.get(simpleName);
                if (isResolvableType(importSpec.qualifiedTarget())) {
                    candidates.add(typeSymbolForQualifiedName(simpleName, importSpec.qualifiedTarget(), importSpec.targetNode()));
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

            for (ImportSpec onDemandImport : onDemandTypeImports) {
                String qualified = onDemandImport.ownerName() + "." + simpleName;
                if (isResolvableType(qualified)) {
                    candidates.add(typeSymbolForQualifiedName(simpleName, qualified, onDemandImport.targetNode()));
                }
            }

            List<Symbol> importedStaticFields = resolveStaticImportedFields(simpleName, referenceNode);
            if (!importedStaticFields.isEmpty())
                candidates.addAll(importedStaticFields);

            if (isMethodNameReference(referenceNode)) {
                List<Symbol> importedStaticMethods = resolveStaticImportedMethods(simpleName, referenceNode, -1);
                if (!importedStaticMethods.isEmpty())
                    candidates.addAll(importedStaticMethods);
            }

            List<Symbol> uniqueCandidates = uniqueByQualifiedName(candidates);
            if (uniqueCandidates.isEmpty()) {
                return;
            }

            Symbol resolved = selectWithPrecedence(simpleName, uniqueCandidates);
            if (resolved != null)
                context.resolve(referenceNode, resolved);
        }

        private List<Symbol> resolveStaticImportedFields(String fieldName, SyntaxNode referenceNode) {
            List<Symbol> resolved = new ArrayList<>();

            List<ImportSpec> singleStaticImports = staticSingleImportsByMemberName.get(fieldName);
            if (singleStaticImports != null) {
                for (ImportSpec importSpec : singleStaticImports) {
                    if (hasResolvableStaticField(importSpec.ownerName(), fieldName)) {
                        resolved.add(new SimpleSymbol(
                                SymbolKind.FIELD,
                                fieldName,
                                importSpec.ownerName() + "#" + fieldName,
                                importSpec.targetNode()
                        ));
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                if (hasResolvableStaticField(onDemandImport.ownerName(), fieldName)) {
                    resolved.add(new SimpleSymbol(
                            SymbolKind.FIELD,
                            fieldName,
                            onDemandImport.ownerName() + "#" + fieldName,
                            referenceNode
                    ));
                }
            }

            return uniqueByQualifiedName(resolved);
        }

        private List<Symbol> resolveStaticImportedMethods(String methodName, SyntaxNode invocationNode, int argumentCountOrUnknown) {
            List<Symbol> resolved = new ArrayList<>();

            List<ImportSpec> singleStaticImports = staticSingleImportsByMemberName.get(methodName);
            if (singleStaticImports != null) {
                for (ImportSpec importSpec : singleStaticImports) {
                    if (hasResolvableStaticMethod(importSpec.ownerName(), methodName, argumentCountOrUnknown)) {
                        resolved.add(new SimpleSymbol(
                                SymbolKind.METHOD,
                                methodName,
                                importSpec.ownerName() + "#" + methodName,
                                importSpec.targetNode()
                        ));
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                if (hasResolvableStaticMethod(onDemandImport.ownerName(), methodName, argumentCountOrUnknown)) {
                    resolved.add(new SimpleSymbol(
                            SymbolKind.METHOD,
                            methodName,
                            onDemandImport.ownerName() + "#" + methodName,
                            invocationNode
                    ));
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
                                .computeIfAbsent(ownerQualifiedName, ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(symbol.simpleName(), ignored -> new ArrayList<>())
                                .add(candidate);
                    }
                } else if (symbol.kind() == SymbolKind.METHOD) {
                    MemberCandidate candidate = localMethodCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localMethodsByOwner
                                .computeIfAbsent(ownerQualifiedName, ignored -> new LinkedHashMap<>())
                                .computeIfAbsent(symbol.simpleName(), ignored -> new ArrayList<>())
                                .add(candidate);
                    }
                } else if (symbol.kind() == SymbolKind.CONSTRUCTOR) {
                    MemberCandidate candidate = localConstructorCandidate(symbol, ownerQualifiedName);
                    if (candidate != null) {
                        localConstructorsByOwner
                                .computeIfAbsent(ownerQualifiedName, ignored -> new ArrayList<>())
                                .add(candidate);
                        localTypesWithExplicitConstructors.add(ownerQualifiedName);
                    }
                }
            }
        }

        private @Nullable MemberCandidate localFieldCandidate(Symbol symbol, String ownerQualifiedName) {
            return new MemberCandidate(symbol, ownerQualifiedName, isStaticMemberSymbol(symbol), typeOfFieldDeclaration(symbol), List.of());
        }

        private @Nullable MemberCandidate localMethodCandidate(Symbol symbol, String ownerQualifiedName) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return null;
            return new MemberCandidate(symbol, ownerQualifiedName, isStaticMemberSymbol(symbol), typeOfMethodDeclaration(symbol), parameterTypes(declaration));
        }

        private @Nullable MemberCandidate localConstructorCandidate(Symbol symbol, String ownerQualifiedName) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return null;
            return new MemberCandidate(symbol, ownerQualifiedName, false, new Type.DeclaredType(ownerQualifiedName, List.of()), parameterTypes(declaration));
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
                types.add(typeRef == null ? new Type.UnknownType("<unknown>") : typeFromTypeReferenceForResolution(typeRef));
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

            if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                var parent = declaration.parent();
                while (parent.isPresent()) {
                    SyntaxNode candidate = parent.get();
                    SyntaxNode typeRef = directChild(candidate, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null)
                        return typeFromTypeReferenceForResolution(typeRef);
                    parent = candidate.parent();
                }
            }

            if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                    || JavaSyntaxKinds.RECORD_COMPONENT.id().equals(declaration.kind().id())) {
                SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return typeFromTypeReferenceForResolution(typeRef);
            }

            return new Type.UnknownType("<unknown>");
        }

        private List<Type> inferArgumentTypes(SyntaxNode argumentList) {
            List<Type> types = new ArrayList<>();
            for (SyntaxNode child : argumentList.children()) {
                if (isExpressionNode(child))
                    types.add(inferExpressionTypeForResolution(child));
            }
            return List.copyOf(types);
        }

        private Type inferExpressionTypeForResolution(SyntaxNode node) {
            return switch (node.kind().id()) {
                case "JAVA_LITERAL_EXPRESSION" -> inferLiteralTypeForResolution(node);
                case "JAVA_NAME_EXPRESSION", "JAVA_FIELD_ACCESS_EXPRESSION", "JAVA_METHOD_INVOCATION_EXPRESSION" -> inferredTypeForResolvedSymbol(node);
                case "JAVA_CLASS_INSTANCE_CREATION_EXPRESSION" -> createdTypeForResolution(node);
                case "JAVA_ASSIGNMENT_EXPRESSION" -> inferAssignmentTypeForResolution(node);
                case "JAVA_BINARY_EXPRESSION" -> inferBinaryTypeForResolution(node);
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
                    return new Type.PrimitiveType("double");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.CHARACTER_LITERAL).id().equals(kindId))
                    return new Type.PrimitiveType("char");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.STRING_LITERAL).id().equals(kindId)
                        || JavaSyntaxKinds.tokenKind(JavaTokenType.TEXT_BLOCK_LITERAL).id().equals(kindId))
                    return new Type.DeclaredType("java.lang.String", List.of());
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_INT_LITERAL).id().equals(kindId)
                        || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_HEXADECIMAL_LITERAL).id().equals(kindId)
                        || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_BINARY_LITERAL).id().equals(kindId)
                        || JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_OCTAL_LITERAL).id().equals(kindId))
                    return new Type.PrimitiveType("int");
            }
            return new Type.UnknownType("<unknown>");
        }

        private Type inferredTypeForResolvedSymbol(SyntaxNode node) {
            Symbol symbol = context.resolvedSymbol(node);
            return symbol == null ? new Type.UnknownType("<unknown>") : typeOfResolvedSymbol(symbol);
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
            return firstExpression == null ? new Type.UnknownType("<unknown>") : inferExpressionTypeForResolution(firstExpression);
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
                    || ">".equals(operator) || ">=".equals(operator)) {
                return new Type.PrimitiveType("boolean");
            }
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
                if (candidate != null)
                    candidates.add(candidate);
            }
            return List.copyOf(candidates);
        }

        private List<MemberCandidate> findFieldCandidates(String ownerQualifiedName, String fieldName, boolean staticAccess) {
            List<MemberCandidate> candidates = new ArrayList<>();
            collectSourceFieldCandidates(ownerQualifiedName, fieldName, staticAccess, candidates, new HashSet<>());
            collectProjectFieldCandidates(ownerQualifiedName, fieldName, staticAccess, candidates, new HashSet<>());
            collectJdkFieldCandidates(ownerQualifiedName, fieldName, staticAccess, candidates, new HashSet<>());
            return List.copyOf(candidates);
        }

        private List<MemberCandidate> findMethodCandidates(String ownerQualifiedName, String methodName, boolean staticAccess) {
            List<MemberCandidate> candidates = new ArrayList<>();
            collectSourceMethodCandidates(ownerQualifiedName, methodName, staticAccess, candidates, new HashSet<>());
            collectProjectMethodCandidates(ownerQualifiedName, methodName, staticAccess, candidates, new HashSet<>());
            collectJdkMethodCandidates(ownerQualifiedName, methodName, staticAccess, candidates, new HashSet<>());
            return dedupeCallableCandidates(candidates);
        }

        private List<MemberCandidate> findConstructorCandidates(String ownerQualifiedName) {
            List<MemberCandidate> candidates = new ArrayList<>();
            List<MemberCandidate> local = localConstructorsByOwner.get(ownerQualifiedName);
            if (local != null)
                candidates.addAll(local);
            if (!localTypesWithExplicitConstructors.contains(ownerQualifiedName) && localQualifiedTypeNames.contains(ownerQualifiedName)) {
                candidates.add(new MemberCandidate(
                        new SyntheticMemberSymbol(
                                SymbolKind.CONSTRUCTOR,
                                "<init>",
                                ownerQualifiedName + "#<init>()",
                                null,
                                new Type.DeclaredType(ownerQualifiedName, List.of()),
                                List.of(),
                                false
                        ),
                        ownerQualifiedName,
                        false,
                        new Type.DeclaredType(ownerQualifiedName, List.of()),
                        List.of()
                ));
            }
            collectProjectConstructorCandidates(ownerQualifiedName, candidates);

            ClassStub stub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
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
                                    false
                            ),
                            ownerQualifiedName,
                            false,
                            constructedType,
                            parameterTypes
                    ));
                }
            }

            return List.copyOf(candidates);
        }

        private void collectSourceFieldCandidates(
                String ownerQualifiedName,
                String fieldName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners
        ) {
            if (!visitedOwners.add(ownerQualifiedName))
                return;
            Map<String, List<MemberCandidate>> fields = localFieldsByOwner.get(ownerQualifiedName);
            if (fields == null)
                return;
            for (MemberCandidate candidate : fields.getOrDefault(fieldName, List.of())) {
                if (candidate.staticMember() == staticAccess)
                    out.add(candidate);
            }
        }

        private void collectSourceMethodCandidates(
                String ownerQualifiedName,
                String methodName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners
        ) {
            if (!visitedOwners.add(ownerQualifiedName))
                return;
            Map<String, List<MemberCandidate>> methods = localMethodsByOwner.get(ownerQualifiedName);
            if (methods == null)
                return;
            for (MemberCandidate candidate : methods.getOrDefault(methodName, List.of())) {
                if (candidate.staticMember() == staticAccess)
                    out.add(candidate);
            }
        }

        private void collectProjectFieldCandidates(
                String ownerQualifiedName,
                String fieldName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners
        ) {
            if (projectIndex == null || !visitedOwners.add(ownerQualifiedName))
                return;

            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMember(ownerQualifiedName, fieldName)) {
                if (symbol.kind() != SymbolKind.FIELD || symbol.isStatic() != staticAccess)
                    continue;

                out.add(new MemberCandidate(
                        syntheticProjectMemberSymbol(symbol, new Type.UnknownType("<unknown>"), List.of()),
                        ownerQualifiedName,
                        staticAccess,
                        new Type.UnknownType("<unknown>"),
                        List.of()
                ));
            }
        }

        private void collectProjectMethodCandidates(
                String ownerQualifiedName,
                String methodName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners
        ) {
            if (projectIndex == null || !visitedOwners.add(ownerQualifiedName))
                return;

            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMember(ownerQualifiedName, methodName)) {
                if (symbol.kind() != SymbolKind.METHOD || symbol.isStatic() != staticAccess)
                    continue;

                List<Type> parameterTypes = parameterTypesFromProjectSignature(symbol.signature());
                out.add(new MemberCandidate(
                        syntheticProjectMemberSymbol(symbol, new Type.UnknownType("<unknown>"), parameterTypes),
                        ownerQualifiedName,
                        staticAccess,
                        new Type.UnknownType("<unknown>"),
                        parameterTypes
                ));
            }
        }

        private void collectProjectConstructorCandidates(String ownerQualifiedName, List<MemberCandidate> out) {
            if (projectIndex == null)
                return;

            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMembers(ownerQualifiedName)) {
                if (symbol.kind() != SymbolKind.CONSTRUCTOR)
                    continue;

                List<Type> parameterTypes = parameterTypesFromProjectSignature(symbol.signature());
                Type constructedType = new Type.DeclaredType(ownerQualifiedName, List.of());
                out.add(new MemberCandidate(
                        syntheticProjectMemberSymbol(symbol, constructedType, parameterTypes),
                        ownerQualifiedName,
                        false,
                        constructedType,
                        parameterTypes
                ));
            }
        }

        private void collectJdkFieldCandidates(
                String ownerQualifiedName,
                String fieldName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners
        ) {
            if (!visitedOwners.add(ownerQualifiedName))
                return;
            ClassStub stub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (stub == null)
                return;

            for (FieldStub field : stub.fields()) {
                if (!field.name().equals(fieldName))
                    continue;
                if (java.lang.reflect.Modifier.isStatic(field.modifiers()) != staticAccess)
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
                                staticAccess
                        ),
                        ownerQualifiedName,
                        staticAccess,
                        valueType,
                        List.of()
                ));
            }

            collectJdkInheritedCandidates(ownerQualifiedName, fieldName, staticAccess, out, visitedOwners, true);
        }

        private void collectJdkMethodCandidates(
                String ownerQualifiedName,
                String methodName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners
        ) {
            if (!visitedOwners.add(ownerQualifiedName))
                return;
            ClassStub stub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (stub == null)
                return;

            for (MethodStub method : stub.methods()) {
                if (!method.name().equals(methodName))
                    continue;
                if (java.lang.reflect.Modifier.isStatic(method.modifiers()) != staticAccess)
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
                                staticAccess
                        ),
                        ownerQualifiedName,
                        staticAccess,
                        valueType,
                        parameterTypes
                ));
            }

            collectJdkInheritedCandidates(ownerQualifiedName, methodName, staticAccess, out, visitedOwners, false);
        }

        private void collectJdkInheritedCandidates(
                String ownerQualifiedName,
                String memberName,
                boolean staticAccess,
                List<MemberCandidate> out,
                Set<String> visitedOwners,
                boolean fieldLookup
        ) {
            ClassStub stub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (stub == null)
                return;

            Type superClass = toSemanticType(stub.superClass());
            if (superClass.kind() == Type.Kind.DECLARED) {
                String superOwner = superClass.displayName();
                if (fieldLookup) {
                    collectJdkFieldCandidates(superOwner, memberName, staticAccess, out, visitedOwners);
                } else {
                    collectJdkMethodCandidates(superOwner, memberName, staticAccess, out, visitedOwners);
                }
            }

            for (dev.railroadide.railroad.ide.classparser.Type iface : stub.interfaces()) {
                Type ifaceType = toSemanticType(iface);
                if (ifaceType.kind() != Type.Kind.DECLARED)
                    continue;
                String ifaceOwner = ifaceType.displayName();
                if (fieldLookup) {
                    collectJdkFieldCandidates(ifaceOwner, memberName, staticAccess, out, visitedOwners);
                } else {
                    collectJdkMethodCandidates(ifaceOwner, memberName, staticAccess, out, visitedOwners);
                }
            }
        }

        private @Nullable MemberCandidate chooseFieldCandidate(List<MemberCandidate> candidates) {
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        private @Nullable MemberCandidate selectBestCallable(List<MemberCandidate> candidates, List<Type> argumentTypes) {
            MemberCandidate best = null;
            List<Integer> bestCost = null;
            boolean ambiguous = false;

            for (MemberCandidate candidate : candidates) {
                List<Integer> cost = applicabilityCost(candidate.parameterTypes(), argumentTypes);
                if (cost == null)
                    continue;
                if (best == null) {
                    best = candidate;
                    bestCost = cost;
                    ambiguous = false;
                    continue;
                }

                int comparison = compareCost(cost, bestCost);
                if (comparison < 0) {
                    best = candidate;
                    bestCost = cost;
                    ambiguous = false;
                } else if (comparison == 0) {
                    ambiguous = true;
                }
            }

            return ambiguous ? null : best;
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
            if (parameterTypes.size() != argumentTypes.size())
                return null;

            List<Integer> cost = new ArrayList<>(parameterTypes.size());
            for (int index = 0; index < parameterTypes.size(); index++) {
                Integer conversionCost = conversionCost(parameterTypes.get(index), argumentTypes.get(index));
                if (conversionCost == null)
                    return null;
                cost.add(conversionCost);
            }
            return List.copyOf(cost);
        }

        private @Nullable Integer conversionCost(Type parameterType, Type argumentType) {
            if (argumentType.kind() == Type.Kind.UNKNOWN || parameterType.kind() == Type.Kind.UNKNOWN)
                return 0;
            if (parameterType.displayName().equals(argumentType.displayName()))
                return 0;
            if (isNumericType(parameterType) && isNumericType(argumentType)) {
                int targetRank = numericRank(simpleTypeName(parameterType.displayName()));
                int sourceRank = numericRank(simpleTypeName(argumentType.displayName()));
                if (targetRank < 0 || sourceRank < 0 || targetRank < sourceRank)
                    return null;
                return targetRank - sourceRank;
            }
            if (parameterType.kind() == Type.Kind.DECLARED && argumentType.kind() == Type.Kind.DECLARED) {
                String parameterName = simpleTypeName(parameterType.displayName());
                String argumentName = simpleTypeName(argumentType.displayName());
                if (parameterName.equals(argumentName))
                    return 0;
                if ("Object".equals(parameterName))
                    return 100;
            }
            return null;
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
                        if (candidate.parameterTypes().size() == argumentCount)
                            candidates.add(candidate);
                    }
                }
            }

            for (ImportSpec onDemandImport : onDemandStaticImports) {
                for (MemberCandidate candidate : findMethodCandidates(onDemandImport.ownerName(), methodName, true)) {
                    if (candidate.parameterTypes().size() == argumentCount)
                        candidates.add(candidate);
                }
            }

            return List.copyOf(candidates);
        }

        private @Nullable MemberLookup resolveMemberLookup(SyntaxNode targetNode, SyntaxNode usageSite) {
            Symbol targetSymbol = context.resolvedSymbol(targetNode);
            if (targetSymbol != null && isTypeSymbol(targetSymbol.kind()))
                return new MemberLookup(targetSymbol.qualifiedName().orElse(null), true);

            if (JavaSyntaxKinds.THIS_EXPRESSION.id().equals(targetNode.kind().id())
                    || JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(targetNode.kind().id())) {
                Symbol enclosingType = context.enclosingTypeSymbol(usageSite);
                return new MemberLookup(enclosingType == null ? null : enclosingType.qualifiedName().orElse(null), false);
            }

            return new MemberLookup(qualifiedTypeNameOfExpression(targetNode, usageSite), false);
        }

        private @Nullable String qualifiedTypeNameOfExpression(SyntaxNode expressionNode, SyntaxNode usageSite) {
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
            if (inferred.kind() == Type.Kind.DECLARED)
                return resolveQualifiedTypeName(inferred.displayName(), usageSite);
            return null;
        }

        private @Nullable String qualifiedValueTypeNameOfSymbol(Symbol symbol, SyntaxNode usageSite) {
            if (symbol instanceof SyntheticMemberSymbol synthetic) {
                Type valueType = synthetic.valueType();
                return valueType.kind() == Type.Kind.DECLARED ? resolveQualifiedTypeName(valueType.displayName(), usageSite) : null;
            }
            if (isTypeSymbol(symbol.kind()))
                return symbol.qualifiedName().orElse(null);

            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return null;

            SyntaxNode typeRef = null;
            if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                    || JavaSyntaxKinds.RECORD_COMPONENT.id().equals(declaration.kind().id())
                    || JavaSyntaxKinds.METHOD_DECLARATION.id().equals(declaration.kind().id())) {
                typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
            } else if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                var parent = declaration.parent();
                while (parent.isPresent()) {
                    SyntaxNode candidate = parent.get();
                    typeRef = directChild(candidate, JavaSyntaxKinds.TYPE_REFERENCE.id());
                    if (typeRef != null)
                        break;
                    parent = candidate.parent();
                }
            }

            return typeRef == null ? null : resolveQualifiedTypeName(typeRef, usageSite);
        }

        private @Nullable String resolveQualifiedTypeName(SyntaxNode typeNode, SyntaxNode usageSite) {
            return resolveQualifiedTypeName(canonicalTypeText(typeNode), usageSite);
        }

        private @Nullable String resolveQualifiedTypeName(@Nullable String text, SyntaxNode usageSite) {
            if (text == null || text.isBlank())
                return null;

            while (text.endsWith("[]"))
                text = text.substring(0, text.length() - 2);
            if ("void".equals(text) || Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return text;
            if (text.indexOf('.') > 0 && isResolvableType(text))
                return text;

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
            return text;
        }

        private Type typeFromTypeReferenceForResolution(SyntaxNode typeNode) {
            String text = canonicalTypeText(typeNode);
            if (text == null || text.isBlank())
                return new Type.UnknownType("<unknown>");
            if ("void".equals(text))
                return new Type.VoidType();
            String simple = simpleTypeName(text);
            if (Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(simple))
                return new Type.PrimitiveType(simple);
            if (text.endsWith("[]")) {
                String component = text.substring(0, text.length() - 2);
                String qualifiedComponent = resolveQualifiedTypeName(component, typeNode);
                Type componentType = qualifiedComponent == null
                        ? new Type.UnknownType("<unknown>")
                        : new Type.DeclaredType(qualifiedComponent, List.of());
                return new Type.ArrayType(componentType);
            }
            String qualified = resolveQualifiedTypeName(text, typeNode);
            return new Type.DeclaredType(qualified == null ? simple : qualified, List.of());
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

            ClassStub jdkStub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (jdkStub == null)
                return false;

            return jdkStub.fields().stream()
                    .anyMatch(field -> field.name().equals(fieldName) && java.lang.reflect.Modifier.isStatic(field.modifiers()));
        }

        private boolean hasResolvableStaticMethod(String ownerQualifiedName, String methodName, int argumentCountOrUnknown) {
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

            ClassStub jdkStub = jdkClassStubsByQualifiedName.get(ownerQualifiedName);
            if (jdkStub == null)
                return false;

            return jdkStub.methods().stream()
                    .anyMatch(method ->
                            method.name().equals(methodName)
                                    && java.lang.reflect.Modifier.isStatic(method.modifiers())
                                    && (argumentCountOrUnknown < 0 || method.parameters().size() == argumentCountOrUnknown)
                    );
        }

        private static boolean isStaticMemberSymbol(Symbol symbol) {
            SyntaxNode declaration = symbol.declaration().orElse(null);
            if (declaration == null)
                return false;

            if (hasTokenKind(declaration, JavaTokenType.STATIC_KEYWORD))
                return true;

            return declaration.parent()
                    .map(parent -> hasTokenKind(parent, JavaTokenType.STATIC_KEYWORD))
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
                if (JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id()))
                    count++;
            }
            return count;
        }

        private static int countInvocationArguments(SyntaxNode invocationNode) {
            SyntaxNode argumentList = directChild(invocationNode, JavaSyntaxKinds.ARGUMENT_LIST.id());
            if (argumentList == null)
                return -1;

            int count = 0;
            for (SyntaxNode child : argumentList.children()) {
                if (isExpressionNode(child))
                    count++;
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
                if (!qualifiedName.isBlank() && context.currentPackageName != null && !context.currentPackageName.isBlank()
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
            if (level == null || level.isEmpty()) {
                return null;
            }

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

            if (projectIndex != null && !projectIndex.getFilesByPackage(packagePrefix).isEmpty())
                return true;
            for (String qualifiedType : availableQualifiedTypeNames) {
                if (qualifiedType.startsWith(packagePrefix + "."))
                    return true;
            }
            return false;
        }

        private Symbol typeSymbolForQualifiedName(String simpleName, String qualifiedName, SyntaxNode declarationOrUsageSite) {
            if (projectIndex != null) {
                List<JavaProjectSemanticIndex.SymbolDescriptor> projectMatches = projectIndex.lookupQualifiedName(qualifiedName).stream()
                        .filter(symbol -> isTypeSymbol(symbol.kind()))
                        .toList();
                if (!projectMatches.isEmpty()) {
                    JavaProjectSemanticIndex.SymbolDescriptor match = projectMatches.getFirst();
                    return new SimpleSymbol(match.kind(), match.simpleName(), match.qualifiedName(), declarationOrUsageSite);
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
            if (qualifiedName != null && symbol.signature() != null && !qualifiedName.endsWith(symbol.signature()))
                qualifiedName = qualifiedName + symbol.signature();

            return new SyntheticMemberSymbol(
                    symbol.kind(),
                    symbol.simpleName(),
                    qualifiedName,
                    null,
                    valueType,
                    parameterTypes,
                    symbol.isStatic()
            );
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
            StringBuilder current = new StringBuilder();
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
            if (Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return new Type.PrimitiveType(text);
            if (text.endsWith("[]"))
                return new Type.ArrayType(typeFromSignatureText(text.substring(0, text.length() - 2)));
            return new Type.DeclaredType(text, List.of());
        }

        private static boolean isSelectorNameExpression(SyntaxNode node) {
            var parent = node.parent();
            if (parent.isEmpty())
                return false;
            String parentKindId = parent.get().kind().id();
            if (!JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(parentKindId)
                    && !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(parentKindId)) {
                return false;
            }
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
            int rank = Math.max(numericRank(simpleTypeName(left.displayName())), numericRank(simpleTypeName(right.displayName())));
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

        private record MemberLookup(@Nullable String ownerQualifiedName, boolean staticAccess) {
        }

        private record MemberCandidate(
                Symbol symbol,
                String ownerQualifiedName,
                boolean staticMember,
                Type valueType,
                List<Type> parameterTypes
        ) {
        }
    }

    private static final class TypeResolver {
        private static final Type UNKNOWN_TYPE = new Type.UnknownType("<unknown>");
        private static final Type BOOLEAN_TYPE = new Type.PrimitiveType("boolean");
        private static final Set<String> NUMERIC_PRIMITIVES = Set.of("byte", "short", "char", "int", "long", "float", "double");

        private final AnalysisContext context;
        private final @Nullable JavaProjectSemanticIndex projectIndex;
        private final Set<String> localQualifiedTypeNames;
        private final Set<String> availableQualifiedTypeNames;
        private final Map<String, ImportSpec> singleTypeImportsBySimpleName = new LinkedHashMap<>();
        private final List<ImportSpec> onDemandTypeImports = new ArrayList<>();
        private final Map<SyntaxNode, Type> cache = new IdentityHashMap<>();

        private TypeResolver(AnalysisContext context) {
            this.context = context;
            this.projectIndex = context.projectIndex;

            Set<String> qualified = new HashSet<>();
            for (Symbol symbol : context.allTypeSymbols()) {
                symbol.qualifiedName().ifPresent(qualified::add);
            }
            this.localQualifiedTypeNames = Set.copyOf(qualified);

            Set<String> available = new HashSet<>(localQualifiedTypeNames);
            if (projectIndex != null) {
                for (JavaProjectSemanticIndex.SourceFileIndex file : projectIndex.files().values()) {
                    available.addAll(file.declaredQualifiedNames());
                }
            }
            available.addAll(loadJdkQualifiedTypeNames());
            this.availableQualifiedTypeNames = Set.copyOf(available);
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

            if (EXPRESSION_KIND_IDS.contains(kindId))
                inferType(node);

            for (SyntaxNode child : node.children())
                visit(child);
        }

        private void resolveTypeReference(SyntaxNode typeRefNode) {
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
                    return new Type.PrimitiveType("int");
                if (JavaSyntaxKinds.tokenKind(JavaTokenType.NUMBER_FLOATING_POINT_LITERAL).id().equals(kindId))
                    return new Type.PrimitiveType("double");
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
            return typeOfSymbol(symbol);
        }

        private Type inferMethodInvocationType(SyntaxNode invocationNode) {
            Symbol resolved = context.resolvedSymbol(invocationNode);
            if (resolved != null && (resolved.kind() == SymbolKind.METHOD || resolved.kind() == SymbolKind.CONSTRUCTOR))
                return typeOfSymbol(resolved);
            return UNKNOWN_TYPE;
        }

        private Type inferClassInstanceCreationType(SyntaxNode creationNode) {
            Symbol resolved = context.resolvedSymbol(creationNode);
            if (resolved != null && resolved.kind() == SymbolKind.CONSTRUCTOR)
                return typeOfSymbol(resolved);

            SyntaxNode typeRef = directChild(creationNode, JavaSyntaxKinds.TYPE_REFERENCE.id());
            return typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
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
                    || ">".equals(operator) || ">=".equals(operator)) {
                return BOOLEAN_TYPE;
            }

            if ("+".equals(operator) && (isStringType(left) || isStringType(right)))
                return new Type.DeclaredType("String", List.of());

            if (isNumericType(left) && isNumericType(right))
                return promoteNumeric(left, right);

            return UNKNOWN_TYPE;
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
                case CONSTRUCTOR -> new Type.DeclaredType(simpleTypeName(ownerQualifiedName(symbol).orElse(symbol.simpleName())), List.of());
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

            if (JavaSyntaxKinds.PARAMETER.id().equals(declaration.kind().id())
                    || JavaSyntaxKinds.RECORD_COMPONENT.id().equals(declaration.kind().id())) {
                SyntaxNode typeRef = directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
                return typeRef == null ? UNKNOWN_TYPE : typeFromTypeReference(typeRef);
            }

            if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(declaration.kind().id())) {
                return variableDeclaredType(declaration);
            }

            return UNKNOWN_TYPE;
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
                SyntaxNode typeRef = directChild(candidate, JavaSyntaxKinds.TYPE_REFERENCE.id());
                if (typeRef != null)
                    return typeFromTypeReference(typeRef);

                parent = candidate.parent();
            }
            return UNKNOWN_TYPE;
        }

        private Type typeFromTypeReference(SyntaxNode typeNode) {
            String text = canonicalTypeText(typeNode);
            if (text == null || text.isBlank())
                return UNKNOWN_TYPE;

            if ("void".equals(text))
                return new Type.VoidType();
            if (NUMERIC_PRIMITIVES.contains(text) || "boolean".equals(text))
                return new Type.PrimitiveType(text);
            if (text.endsWith("[]")) {
                String component = text.substring(0, text.length() - 2);
                Type componentType = typeFromTypeReferenceText(component);
                return new Type.ArrayType(componentType);
            }

            String qualified = resolveQualifiedTypeName(text);
            return new Type.DeclaredType(qualified == null ? simpleTypeName(text) : qualified, List.of());
        }

        private Type typeFromTypeReferenceText(String text) {
            if (text == null || text.isBlank())
                return UNKNOWN_TYPE;
            if ("void".equals(text))
                return new Type.VoidType();
            if (NUMERIC_PRIMITIVES.contains(text) || "boolean".equals(text))
                return new Type.PrimitiveType(text);
            String qualified = resolveQualifiedTypeName(text);
            return new Type.DeclaredType(qualified == null ? simpleTypeName(text) : qualified, List.of());
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

                    String ownerName = isWildcard ? qualifiedTarget.substring(0, qualifiedTarget.length() - 2) : packagePrefix(qualifiedTarget);
                    String importedName = isWildcard ? "*" : lastSegment(qualifiedTarget);
                    ImportSpec importSpec = new ImportSpec(
                            declarationNode,
                            targetNode,
                            qualifiedTarget,
                            ownerName,
                            importedName,
                            false,
                            isWildcard
                    );

                    if (isWildcard) {
                        onDemandTypeImports.add(importSpec);
                    } else {
                        singleTypeImportsBySimpleName.putIfAbsent(importSpec.importedName(), importSpec);
                    }
                }
            }
        }

        private @Nullable String resolveQualifiedTypeName(@Nullable String text) {
            if (text == null || text.isBlank())
                return null;

            while (text.endsWith("[]"))
                text = text.substring(0, text.length() - 2);
            if ("void".equals(text) || Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double").contains(text))
                return text;
            if (text.indexOf('.') > 0 && isResolvableType(text))
                return text;

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
            return null;
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
                if (isExpressionNode(child))
                    result.add(child);
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
                    new Type.DeclaredType(clazz.name(), List.of());
            case dev.railroadide.railroad.ide.classparser.Type.TypeVariable variable ->
                    new Type.TypeVariableType(variable.name());
            case dev.railroadide.railroad.ide.classparser.Type.WildcardType wildcard -> {
                Type bound = wildcard.bound() == null ? new Type.UnknownType("<unknown>") : toSemanticType(wildcard.bound());
                yield wildcard.isUpperBound()
                        ? new Type.WildcardType(bound, null)
                        : new Type.WildcardType(null, bound);
            }
        };
    }

    private static String signatureSuffix(List<Type> parameterTypes) {
        if (parameterTypes.isEmpty())
            return "()";
        StringBuilder builder = new StringBuilder("(");
        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0)
                builder.append(',');
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
        for (SyntaxToken token : leafTokens(node)) {
            if (tokenKindId.equals(token.kind().id()) && !isMissingTokenKind(token.kind().id()))
                return true;
        }
        return false;
    }

    private static @Nullable String identifierAfterKeyword(SyntaxNode node, JavaTokenType keywordTokenType) {
        String keywordKindId = JavaSyntaxKinds.tokenKind(keywordTokenType).id();
        boolean foundKeyword = false;
        for (SyntaxNode child : node.children()) {
            if (!(child instanceof SyntaxToken token))
                continue;

            String tokenKindId = token.kind().id();
            if (!foundKeyword) {
                if (keywordKindId.equals(tokenKindId))
                    foundKeyword = true;
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

            if (child instanceof SyntaxToken token && isIdentifierLikeToken(token))
                lastIdentifier = token.text();
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
        List<SyntaxToken> tokens = leafTokens(node);
        for (int index = tokens.size() - 1; index >= 0; index--) {
            SyntaxToken token = tokens.get(index);
            if (isIdentifierLikeToken(token))
                return token.text();
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

        for (SyntaxNode child : node.children())
            collectLeafTokens(child, out);
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
        StringBuilder builder = new StringBuilder();
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

        for (SyntaxNode child : node.children())
            appendCanonicalQualifiedNameTokens(child, builder);
    }

    public static @Nullable String canonicalTypeText(SyntaxNode node) {
        StringBuilder builder = new StringBuilder();
        appendCanonicalTypeTokens(node, builder);
        if (builder.isEmpty())
            return null;
        return builder.toString();
    }

    private static void appendCanonicalTypeTokens(SyntaxNode node, StringBuilder builder) {
        if (node instanceof SyntaxToken token) {
            String kindId = token.kind().id();
            if (isTriviaToken(token) || isMissingTokenKind(kindId))
                return;

            if (isIdentifierLikeToken(token)
                    || PRIMITIVE_TOKEN_KIND_IDS.contains(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.VOID_KEYWORD).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.DOT).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_BRACKET).id().equals(kindId)
                    || JavaSyntaxKinds.tokenKind(JavaTokenType.CLOSE_BRACKET).id().equals(kindId)) {
                builder.append(token.text());
            }
            return;
        }

        for (SyntaxNode child : node.children())
            appendCanonicalTypeTokens(child, builder);
    }

    private static boolean isTypeSymbol(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> true;
            default -> false;
        };
    }

    public static boolean isSelectorNameExpression(SyntaxNode node) {
        var parent = node.parent();
        if (parent.isEmpty())
            return false;
        String parentKindId = parent.get().kind().id();
        if (!JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION.id().equals(parentKindId)
                && !JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(parentKindId)) {
            return false;
        }
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
        String text = displayName;
        int genericIndex = text.indexOf('<');
        if (genericIndex >= 0)
            text = text.substring(0, genericIndex);

        while (text.endsWith("[]"))
            text = text.substring(0, text.length() - 2);

        return lastSegment(text);
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
            boolean isWildcard
    ) {
    }
}
