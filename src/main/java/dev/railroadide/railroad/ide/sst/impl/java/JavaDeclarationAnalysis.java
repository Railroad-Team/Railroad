package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer.*;

/** Declaration collection and mutable state shared by semantic resolution passes. */
final class JavaDeclarationAnalysis {
    private JavaDeclarationAnalysis() {
    }

    static void collect(Context context, SyntaxNode compilationUnit) {
        new DeclarationCollector(context).visitCompilationUnit(compilationUnit);
    }

    static final class Context {
        final SyntaxNode syntaxRoot;
        final Scope rootScope;
        private final SemanticModel.Builder builder;
        private final Map<SyntaxNode, Scope> scopeByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> declaredSymbolByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> resolvedSymbolByNode = new IdentityHashMap<>();
        private final Map<SyntaxNode, Type> inferredTypeByNode = new IdentityHashMap<>();
        final @Nullable JavaSymbolIndex projectIndex;
        @Nullable
        String currentPackageName;

        Context(
            SyntaxNode syntaxRoot,
            Scope rootScope,
            SemanticModel.Builder builder,
            @Nullable JavaSymbolIndex projectIndex) {
            this.syntaxRoot = syntaxRoot;
            this.rootScope = rootScope;
            this.builder = builder;
            this.projectIndex = projectIndex;
        }

        void attachScope(SyntaxNode node, Scope scope) {
            scopeByNode.put(node, scope);
        }

        Scope scopeFor(SyntaxNode node) {
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

        void declare(SyntaxNode declarationNode, Symbol symbol) {
            declaredSymbolByNode.put(declarationNode, symbol);
            builder.declare(declarationNode, symbol);
        }

        void resolve(SyntaxNode referenceNode, Symbol symbol) {
            resolvedSymbolByNode.put(referenceNode, symbol);
            builder.resolve(referenceNode, symbol);
        }

        @Nullable
        Symbol resolvedSymbol(SyntaxNode node) {
            return resolvedSymbolByNode.get(node);
        }

        @Nullable
        Symbol declaredSymbol(SyntaxNode node) {
            return declaredSymbolByNode.get(node);
        }

        void type(SyntaxNode node, Type type) {
            inferredTypeByNode.put(node, type);
            builder.type(node, type);
        }

        @Nullable
        Type inferredType(SyntaxNode node) {
            return inferredTypeByNode.get(node);
        }

        List<Symbol> allTypeSymbols() {
            List<Symbol> symbols = new ArrayList<>();
            for (Symbol symbol : declaredSymbolByNode.values()) {
                if (isTypeSymbol(symbol.kind())) {
                    symbols.add(symbol);
                }
            }
            return List.copyOf(symbols);
        }

        List<Symbol> allDeclaredSymbols() {
            return List.copyOf(declaredSymbolByNode.values());
        }

        @Nullable
        Symbol enclosingTypeSymbol(SyntaxNode node) {
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

        @Nullable
        Symbol topLevelEnclosingTypeSymbol(SyntaxNode node) {
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
            @Nullable String enclosingTypeQualifiedName) {
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

        private void declareLocalVariables(SyntaxNode localVariableDeclaration, Scope scope,
            @Nullable String ownerQualifiedName) {
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

        private Scope declareConstructor(SyntaxNode constructorDeclaration, Scope scope,
            @Nullable String ownerQualifiedName) {
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

        private void declareRecordComponent(SyntaxNode recordComponentNode, Scope scope,
            @Nullable String ownerQualifiedName) {
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
            @Nullable String qualifiedName) {
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
