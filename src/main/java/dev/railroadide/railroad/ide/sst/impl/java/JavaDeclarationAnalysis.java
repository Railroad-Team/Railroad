package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer.*;

/** Declaration collection and mutable state shared by semantic resolution passes. */
public final class JavaDeclarationAnalysis {
    private JavaDeclarationAnalysis() {
    }

    /**
     * Collects compilation-unit declarations and scopes into the shared analysis context.
     *
     * @param context the mutable analysis state receiving declarations
     * @param compilationUnit the compilation-unit syntax root
     */
    public static void collect(Context context, SyntaxNode compilationUnit) {
        new DeclarationCollector(context).visitCompilationUnit(compilationUnit);
    }

    /**
     * Mutable declaration, scope, symbol-resolution, and inferred-type state shared by Java analysis passes.
     */
    public static final class Context {
        public final SyntaxNode syntaxRoot;
        public final Scope rootScope;
        private final SemanticModel.Builder builder;
        private final Map<SyntaxNode, Scope> scopeByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> declaredSymbolByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> resolvedSymbolByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Type> inferredTypeByNode = new IdentityHashMap<>();
        public final @Nullable JavaSymbolIndex projectIndex;
        @Nullable
        public String currentPackageName;

        /**
         * Creates shared analysis state backed by the supplied semantic-model builder.
         *
         * @param syntaxRoot the syntax-tree root being analyzed
         * @param rootScope the compilation unit's root scope
         * @param builder the builder receiving semantic facts
         * @param projectIndex the external project symbol index, or {@code null} to use standard-library resolution
         */
        public Context(
            SyntaxNode syntaxRoot,
            Scope rootScope,
            SemanticModel.Builder builder,
            @Nullable JavaSymbolIndex projectIndex
        ) {
            this.syntaxRoot = syntaxRoot;
            this.rootScope = rootScope;
            this.builder = builder;
            this.projectIndex = projectIndex;
        }

        private void attachScope(SyntaxNode node, Scope scope) {
            scopeByNode.put(node, scope);
        }

        /**
         * Finds the scope attached to a node or its nearest ancestor, falling back to the root scope.
         *
         * @param node the syntax node whose scope is needed
         * @return the nearest enclosing scope, or the root scope
         */
        public Scope scopeFor(SyntaxNode node) {
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

        /**
         * Records a resolved symbol in this context and the semantic-model builder.
         *
         * @param referenceNode the syntax reference being resolved
         * @param symbol the symbol identified by the reference
         */
        public void resolve(SyntaxNode referenceNode, Symbol symbol) {
            resolvedSymbolByNode.put(referenceNode, symbol);
            builder.resolve(referenceNode, symbol);
        }

        /**
         * Looks up the symbol previously resolved for a syntax node.
         *
         * @param node the reference node to query
         * @return the resolved symbol, or {@code null} if none was recorded
         */
        @Nullable
        public Symbol resolvedSymbol(SyntaxNode node) {
            return resolvedSymbolByNode.get(node);
        }

        /**
         * Looks up the symbol declared by a syntax node.
         *
         * @param node the declaration node to query
         * @return the declared symbol, or {@code null} if none was recorded
         */
        @Nullable
        public Symbol declaredSymbol(SyntaxNode node) {
            return declaredSymbolByNode.get(node);
        }

        /**
         * Records an inferred type in this context and the semantic-model builder.
         *
         * @param node the syntax node receiving a type
         * @param type the inferred semantic type
         */
        public void type(SyntaxNode node, Type type) {
            inferredTypeByNode.put(node, type);
            builder.type(node, type);
        }

        /**
         * Looks up the inferred type previously recorded for a syntax node.
         *
         * @param node the syntax node to query
         * @return the inferred type, or {@code null} if none was recorded
         */
        @Nullable
        public Type inferredType(SyntaxNode node) {
            return inferredTypeByNode.get(node);
        }

        /**
         * Collects the declared class, interface, enum, annotation, and record symbols.
         *
         * @return an immutable snapshot of declared type symbols
         */
        public List<Symbol> allTypeSymbols() {
            List<Symbol> symbols = new ArrayList<>();
            for (Symbol symbol : declaredSymbolByNode.values()) {
                if (isTypeSymbol(symbol.kind())) {
                    symbols.add(symbol);
                }
            }
            return List.copyOf(symbols);
        }

        /**
         * Collects every symbol recorded by declaration analysis.
         *
         * @return an immutable snapshot of declared symbols
         */
        public List<Symbol> allDeclaredSymbols() {
            return List.copyOf(declaredSymbolByNode.values());
        }

        /**
         * Walks the node's ancestors to find the nearest enclosing declared type.
         *
         * @param node the syntax node to start from, or {@code null}
         * @return the enclosing type symbol, or {@code null} if none exists
         */
        @Nullable
        public Symbol enclosingTypeSymbol(SyntaxNode node) {
            if (node == null)
                return null;
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

        /**
         * Walks the node's ancestors to find the outermost enclosing declared type.
         *
         * @param node the syntax node to start from, or {@code null}
         * @return the outermost enclosing type symbol, or {@code null} if none exists
         */
        @Nullable
        public Symbol topLevelEnclosingTypeSymbol(SyntaxNode node) {
            if (node == null)
                return null;
            SyntaxNode current = node;
            Symbol topLevel = null;
            while (true) {
                var parent = current.parent();
                if (parent.isEmpty())
                    return topLevel;

                current = parent.get();
                Symbol declared = declaredSymbol(current);
                if (declared != null && isTypeSymbol(declared.kind())) {
                    topLevel = declared;
                }
            }
        }
    }

    private static final class DeclarationCollector {
        private final Context context;

        private DeclarationCollector(Context context) {
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
                for (SyntaxNode child : node.children()) {
                    visitNode(child, blockScope, currentTypeQualifiedName);
                }
                return;
            }
            if (JavaSyntaxKinds.FOR_STATEMENT.id().equals(kindId)) {
                Scope loopScope = scope.child();
                for (SyntaxNode child : node.children()) {
                    visitNode(child, loopScope, currentTypeQualifiedName);
                }
                return;
            }
            if (JavaSyntaxKinds.TRY_STATEMENT.id().equals(kindId)) {
                Scope tryScope = scope.child();
                for (SyntaxNode child : node.children()) {
                    visitNode(child, tryScope, currentTypeQualifiedName);
                }
                return;
            }
            if (JavaSyntaxKinds.CATCH_CLAUSE.id().equals(kindId)) {
                Scope catchScope = scope.child();
                for (SyntaxNode child : node.children()) {
                    visitNode(child, catchScope, currentTypeQualifiedName);
                }
                return;
            }
            if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(kindId)) {
                Scope lambdaScope = scope.child();
                for (SyntaxNode child : node.children()) {
                    visitNode(child, lambdaScope, currentTypeQualifiedName);
                }
                return;
            }

            if (JavaSyntaxKinds.PACKAGE_DECLARATION.id().equals(kindId)) {
                declarePackage(node, scope);
            } else if (JavaSyntaxKinds.IMPORT_DECLARATION.id().equals(kindId)) {
                declareImport(node, scope);
            } else if (JavaSyntaxKinds.MODULE_DECLARATION.id().equals(kindId)) {
                declareModule(node, scope);
            } else if (JavaSyntaxKinds.CLASS_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.CLASS, JavaTokenType.CLASS_KEYWORD,
                    currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.INTERFACE_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.INTERFACE, JavaTokenType.INTERFACE_KEYWORD,
                    currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.ENUM_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.ENUM, JavaTokenType.ENUM_KEYWORD,
                    currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.ANNOTATION, JavaTokenType.AT_INTERFACE_KEYWORD,
                    currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.RECORD_DECLARATION.id().equals(kindId)) {
                visitTypeDeclaration(node, scope, SymbolKind.RECORD, JavaTokenType.RECORD_KEYWORD,
                    currentTypeQualifiedName);
                return;
            } else if (JavaSyntaxKinds.FIELD_DECLARATION.id().equals(kindId)) {
                declareFields(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT.id().equals(kindId)) {
                declareLocalVariables(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(kindId)) {
                declareEnumConstant(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.METHOD_DECLARATION.id().equals(kindId)) {
                Scope methodScope = declareMethod(node, scope, currentTypeQualifiedName);
                for (SyntaxNode child : node.children()) {
                    visitNode(child, methodScope, currentTypeQualifiedName);
                }
                return;
            } else if (JavaSyntaxKinds.CONSTRUCTOR_DECLARATION.id().equals(kindId)) {
                Scope constructorScope = declareConstructor(node, scope, currentTypeQualifiedName);
                for (SyntaxNode child : node.children()) {
                    visitNode(child, constructorScope, currentTypeQualifiedName);
                }
                return;
            } else if (JavaSyntaxKinds.PARAMETER.id().equals(kindId)) {
                declareParameter(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.LAMBDA_PARAMETER.id().equals(kindId)) {
                declareParameter(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.RECORD_COMPONENT.id().equals(kindId)) {
                declareRecordComponent(node, scope, currentTypeQualifiedName);
            } else if (JavaSyntaxKinds.PATTERN.id().equals(kindId)) {
                declarePatternVariable(node, scope, currentTypeQualifiedName);
            }

            for (SyntaxNode child : node.children()) {
                visitNode(child, scope, currentTypeQualifiedName);
            }
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
                for (SyntaxNode child : declarationNode.children()) {
                    visitNode(child, scope, enclosingTypeQualifiedName);
                }
                return;
            }

            String qualifiedName = qualifyTypeName(simpleName, enclosingTypeQualifiedName);
            declareSymbol(scope, declarationNode, symbolKind, simpleName, qualifiedName);

            Scope typeScope = scope.child();
            for (SyntaxNode child : declarationNode.children()) {
                visitNode(child, typeScope, qualifiedName);
            }
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

        private void declareLocalVariables(
            SyntaxNode localVariableDeclaration,
            Scope scope,
            @Nullable String ownerQualifiedName
        ) {
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

        private Scope declareConstructor(
            SyntaxNode constructorDeclaration,
            Scope scope,
            @Nullable String ownerQualifiedName
        ) {
            String constructorName = identifierBeforeChildKind(constructorDeclaration,
                JavaSyntaxKinds.PARAMETER_LIST.id());
            if (constructorName == null || constructorName.isBlank()) {
                constructorName = "<init>";
            }

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

        private void declareRecordComponent(
            SyntaxNode recordComponentNode,
            Scope scope,
            @Nullable String ownerQualifiedName
        ) {
            String componentName = lastIdentifierLikeTokenText(recordComponentNode);
            if (componentName == null || componentName.isBlank())
                return;

            String qualifiedName = qualifyMemberName(ownerQualifiedName, componentName);
            declareSymbol(scope, recordComponentNode, SymbolKind.PARAMETER, componentName, qualifiedName);
        }

        private void declarePatternVariable(SyntaxNode patternNode, Scope scope, @Nullable String ownerQualifiedName) {
            boolean hasNestedPattern = patternNode.children().stream()
                .anyMatch(child -> JavaSyntaxKinds.PATTERN.id().equals(child.kind().id()));
            if (hasNestedPattern)
                return;

            String variableName = lastIdentifierLikeTokenText(patternNode);
            if (variableName == null || variableName.isBlank())
                return;

            String typeName = Optional.ofNullable(directChild(patternNode, JavaSyntaxKinds.TYPE_REFERENCE.id()))
                .map(JavaSemanticAnalyzer::canonicalTypeText)
                .orElse(null);
            if (variableName.equals(typeName) || variableName.equals(simpleTypeName(typeName == null ? "" : typeName)))
                return;

            String qualifiedName = qualifyMemberName(ownerQualifiedName, variableName);
            declareSymbol(scope, patternNode, SymbolKind.LOCAL_VARIABLE, variableName, qualifiedName);
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

}
