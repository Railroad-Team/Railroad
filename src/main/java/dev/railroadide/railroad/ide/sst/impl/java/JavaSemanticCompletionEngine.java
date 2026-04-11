package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.FieldStub;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import dev.railroadide.railroad.ide.completion.CompletionItem;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.sst.project.ProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SST-backed dot completion for source/project/JDK members.
 */
public final class JavaSemanticCompletionEngine {
    private JavaSemanticCompletionEngine() {
    }

    public static @Nullable CompletionResult compute(
        String document,
        int triggerAt,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        if (document == null || document.isEmpty() || triggerAt < 0 || triggerAt >= document.length())
            return null;
        if (document.charAt(triggerAt) != '.')
            return null;

        SemanticModel model = projectIndex == null
            ? JavaSemanticAnalyzer.analyzeFacts(document)
            : JavaSemanticAnalyzer.analyzeFacts(document, projectIndex);
        SyntaxTree tree = model.syntaxTree();

        SyntaxNode targetNode = findDeepestNodeContaining(tree.root(), triggerAt);
        if (targetNode == null)
            return null;

        CompletionTarget target = resolveCompletionTarget(targetNode, model, projectIndex);
        if (target == null || target.ownerQualifiedName() == null || target.ownerQualifiedName().isBlank())
            return null;

        String currentPackage = currentPackageName(tree.root());
        LinkedHashMap<String, CompletionItem> itemsByKey = new LinkedHashMap<>();
        collectMembersRecursive(
            model,
            projectIndex,
            target.ownerQualifiedName(),
            target.staticContext(),
            currentPackage,
            itemsByKey,
            new LinkedHashSet<>()
        );

        if (itemsByKey.isEmpty())
            return null;

        List<CompletionItem> items = itemsByKey.values().stream()
            .sorted(Comparator.comparing(CompletionItem::displayText, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return new CompletionResult(triggerAt, items);
    }

    private static @Nullable CompletionTarget resolveCompletionTarget(
        SyntaxNode node,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        for (SyntaxNode current = node; current != null; current = current.parent().orElse(null)) {
            SyntaxNode receiver = JavaSemanticAnalyzer.explicitReceiver(current);
            if (receiver != null)
                return completionTargetForReceiver(receiver, current, model, projectIndex);
        }
        return null;
    }

    private static @Nullable CompletionTarget completionTargetForReceiver(
        SyntaxNode receiver,
        SyntaxNode usageSite,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        if (JavaSyntaxKinds.THIS_EXPRESSION.id().equals(receiver.kind().id())) {
            Symbol enclosing = enclosingTypeSymbol(usageSite, model);
            String ownerQualifiedName = enclosing == null ? null : enclosing.qualifiedName().orElse(null);
            return ownerQualifiedName == null ? null : new CompletionTarget(ownerQualifiedName, false);
        }

        if (JavaSyntaxKinds.SUPER_EXPRESSION.id().equals(receiver.kind().id())) {
            Symbol enclosing = enclosingTypeSymbol(usageSite, model);
            String ownerQualifiedName = enclosing == null ? null : enclosing.qualifiedName().orElse(null);
            String superQualifiedName = superTypeQualifiedName(ownerQualifiedName, model, projectIndex);
            return superQualifiedName == null ? null : new CompletionTarget(superQualifiedName, false);
        }

        Symbol resolved = model.resolvedSymbol(receiver).orElse(null);
        if (resolved != null) {
            if (isTypeSymbol(resolved.kind()))
                return completionTarget(resolved.qualifiedName().orElse(null), true);
            if (resolved.kind() == SymbolKind.CONSTRUCTOR)
                return completionTarget(ownerQualifiedName(resolved.qualifiedName().orElse(null)), false);
        }

        Type inferred = model.inferredType(receiver).orElse(null);
        if (inferred != null) {
            String displayName = inferred.displayName();
            if (inferred.kind() == Type.Kind.ARRAY)
                return new CompletionTarget("java.lang.Object", false);
            if (inferred.kind() == Type.Kind.DECLARED)
                return completionTarget(resolveQualifiedTypeName(displayName, usageSite, model, projectIndex), false);
        }

        String canonical = JavaSemanticAnalyzer.canonicalQualifiedName(receiver);
        if (canonical != null) {
            String qualified = resolveQualifiedTypeName(canonical, usageSite, model, projectIndex);
            if (qualified != null)
                return new CompletionTarget(qualified, true);
        }

        return null;
    }

    private static @Nullable CompletionTarget completionTarget(@Nullable String ownerQualifiedName, boolean staticContext) {
        if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
            return null;
        return new CompletionTarget(ownerQualifiedName, staticContext);
    }

    private static void collectMembersRecursive(
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex,
        String ownerQualifiedName,
        boolean staticContext,
        String currentPackage,
        Map<String, CompletionItem> out,
        Set<String> visitedOwners
    ) {
        if (!visitedOwners.add(ownerQualifiedName))
            return;

        collectLocalMembers(model, ownerQualifiedName, staticContext, out);
        collectProjectMembers(projectIndex, ownerQualifiedName, staticContext, out);
        collectJdkMembers(ownerQualifiedName, staticContext, currentPackage, out);

        for (String superType : directSuperTypes(ownerQualifiedName, model, projectIndex)) {
            collectMembersRecursive(model, projectIndex, superType, staticContext, currentPackage, out, visitedOwners);
        }
    }

    private static void collectLocalMembers(
        SemanticModel model,
        String ownerQualifiedName,
        boolean staticContext,
        Map<String, CompletionItem> out
    ) {
        collectLocalMembersRecursive(model.syntaxTree().root(), model, ownerQualifiedName, staticContext, out);
    }

    private static void collectLocalMembersRecursive(
        SyntaxNode node,
        SemanticModel model,
        String ownerQualifiedName,
        boolean staticContext,
        Map<String, CompletionItem> out
    ) {
        Symbol declared = model.declaredSymbol(node).orElse(null);
        if (declared != null) {
            String declarationOwnerQualifiedName = ownerQualifiedName(declared.qualifiedName().orElse(null));
            if (ownerQualifiedName.equals(declarationOwnerQualifiedName)) {
                switch (declared.kind()) {
                    case FIELD -> addFieldItem(
                        out,
                        declared.simpleName(),
                        typeOfFieldSymbol(declared, model).displayName(),
                        isStaticDeclaration(node)
                    );
                    case METHOD -> addMethodItem(
                        out,
                        declared.simpleName(),
                        parameterTypesFromDeclaration(node),
                        returnTypeFromMethodDeclaration(node),
                        isStaticDeclaration(node)
                    );
                    case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> {
                        if (staticContext && isStaticDeclaration(node)) {
                            addTypeItem(out, declared.simpleName(), true);
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        for (SyntaxNode child : node.children())
            collectLocalMembersRecursive(child, model, ownerQualifiedName, staticContext, out);
    }

    private static void collectProjectMembers(
        @Nullable ProjectSemanticIndex projectIndex,
        String ownerQualifiedName,
        boolean staticContext,
        Map<String, CompletionItem> out
    ) {
        if (projectIndex == null)
            return;

        for (ProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupMembers(ownerQualifiedName)) {
            switch (symbol.kind()) {
                case FIELD -> {
                    if (symbol.isStatic() == staticContext)
                        addFieldItem(out, symbol.simpleName(), "?", symbol.isStatic());
                }
                case METHOD -> {
                    if (symbol.isStatic() == staticContext)
                        addMethodItem(
                            out,
                            symbol.simpleName(),
                            parameterDisplaysFromSignature(symbol.signature()),
                            "?",
                            symbol.isStatic()
                        );
                }
                default -> {
                }
            }
        }
    }

    private static void collectJdkMembers(
        String ownerQualifiedName,
        boolean staticContext,
        String currentPackage,
        Map<String, CompletionItem> out
    ) {
        ClassStub stub = JavaSemanticAnalyzer.loadJdkClassStubsByQualifiedName().get(ownerQualifiedName);
        if (stub == null)
            return;

        for (FieldStub field : stub.fields()) {
            boolean isStatic = java.lang.reflect.Modifier.isStatic(field.modifiers());
            if (isStatic != staticContext)
                continue;
            if (!isAccessible(field.modifiers(), stub.packageName(), currentPackage))
                continue;
            addFieldItem(out, field.name(), renderJvmType(field.type()), isStatic);
        }

        for (MethodStub method : stub.methods()) {
            boolean isStatic = java.lang.reflect.Modifier.isStatic(method.modifiers());
            if (isStatic != staticContext)
                continue;
            if (!isAccessible(method.modifiers(), stub.packageName(), currentPackage))
                continue;
            List<String> parameters = method.parameters().stream()
                .map(parameter -> renderJvmType(parameter.type()))
                .toList();
            addMethodItem(out, method.name(), parameters, renderJvmType(method.returnType()), isStatic);
        }
    }

    private static void addFieldItem(Map<String, CompletionItem> out, String name, String type, boolean isStatic) {
        String prefix = isStatic ? "static " : "";
        out.putIfAbsent("F:" + name, new CompletionItem(name, prefix + name + " : " + type));
    }

    private static void addMethodItem(
        Map<String, CompletionItem> out,
        String name,
        List<String> parameterTypes,
        String returnType,
        boolean isStatic
    ) {
        String prefix = isStatic ? "static " : "";
        String parameters = String.join(", ", parameterTypes);
        String display = prefix + name + "(" + parameters + ") : " + returnType;
        out.putIfAbsent("M:" + display, new CompletionItem(name, display));
    }

    private static void addTypeItem(Map<String, CompletionItem> out, String name, boolean isStatic) {
        String prefix = isStatic ? "static " : "";
        out.putIfAbsent("T:" + name, new CompletionItem(name, prefix + name + " (type)"));
    }

    private static Type typeOfFieldSymbol(Symbol symbol, SemanticModel model) {
        SyntaxNode declaration = symbol.declaration().orElse(null);
        if (declaration == null)
            return new Type.UnknownType("?");
        SyntaxNode typeRef = JavaSemanticAnalyzer.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
        if (typeRef == null) {
            SyntaxNode parent = declaration.parent().orElse(null);
            typeRef = parent == null ? null : JavaSemanticAnalyzer.directChild(parent, JavaSyntaxKinds.TYPE_REFERENCE.id());
        }
        return typeRef == null ? new Type.UnknownType("?") : model.inferredType(typeRef).orElse(new Type.UnknownType("?"));
    }

    private static String returnTypeFromMethodDeclaration(SyntaxNode declaration) {
        SyntaxNode typeRef = JavaSemanticAnalyzer.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id());
        String text = typeRef == null ? null : JavaSemanticAnalyzer.canonicalTypeText(typeRef);
        return text == null || text.isBlank() ? "void" : text;
    }

    private static List<String> parameterTypesFromDeclaration(SyntaxNode declaration) {
        SyntaxNode parameterList = JavaSemanticAnalyzer.directChild(declaration, JavaSyntaxKinds.PARAMETER_LIST.id());
        if (parameterList == null)
            return List.of();

        List<String> result = new ArrayList<>();
        for (SyntaxNode child : parameterList.children()) {
            if (!JavaSyntaxKinds.PARAMETER.id().equals(child.kind().id()))
                continue;
            SyntaxNode typeRef = JavaSemanticAnalyzer.directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
            String text = typeRef == null ? null : JavaSemanticAnalyzer.canonicalTypeText(typeRef);
            result.add(text == null || text.isBlank() ? "?" : text);
        }
        return List.copyOf(result);
    }

    private static List<String> parameterDisplaysFromSignature(@Nullable String signature) {
        if (signature == null || signature.isBlank() || "()".equals(signature))
            return List.of();
        int open = signature.indexOf('(');
        int close = signature.lastIndexOf(')');
        if (open < 0 || close <= open + 1)
            return List.of();
        return Arrays.stream(signature.substring(open + 1, close).split(","))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList();
    }

    private static boolean isStaticDeclaration(SyntaxNode declaration) {
        if (JavaSemanticAnalyzer.hasTokenKind(declaration, JavaTokenType.STATIC_KEYWORD))
            return true;
        SyntaxNode parent = declaration.parent().orElse(null);
        return parent != null && JavaSemanticAnalyzer.hasTokenKind(parent, JavaTokenType.STATIC_KEYWORD);
    }

    private static boolean isAccessible(int modifiers, String declaringPackage, String currentPackage) {
        if (java.lang.reflect.Modifier.isPublic(modifiers) || java.lang.reflect.Modifier.isProtected(modifiers))
            return true;
        if (java.lang.reflect.Modifier.isPrivate(modifiers))
            return false;
        return Objects.equals(declaringPackage, currentPackage);
    }

    private static @Nullable Symbol enclosingTypeSymbol(SyntaxNode node, SemanticModel model) {
        for (SyntaxNode current = node; current != null; current = current.parent().orElse(null)) {
            Symbol declared = model.declaredSymbol(current).orElse(null);
            if (declared != null && isTypeSymbol(declared.kind()))
                return declared;
        }
        return null;
    }

    private static boolean isTypeSymbol(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> true;
            default -> false;
        };
    }

    private static @Nullable String superTypeQualifiedName(
        @Nullable String ownerQualifiedName,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        List<String> superTypes = directSuperTypes(ownerQualifiedName, model, projectIndex);
        return superTypes.isEmpty() ? null : superTypes.get(0);
    }

    private static List<String> directSuperTypes(
        @Nullable String ownerQualifiedName,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        if (ownerQualifiedName == null || ownerQualifiedName.isBlank())
            return List.of();

        ClassStub stub = JavaSemanticAnalyzer.loadJdkClassStubsByQualifiedName().get(ownerQualifiedName);
        if (stub != null) {
            List<String> result = new ArrayList<>();
            if (stub.superClass() instanceof dev.railroadide.railroad.ide.classparser.Type.ClassType classType)
                result.add(classType.name());
            for (dev.railroadide.railroad.ide.classparser.Type iface : stub.interfaces()) {
                if (iface instanceof dev.railroadide.railroad.ide.classparser.Type.ClassType classType)
                    result.add(classType.name());
            }
            return List.copyOf(result);
        }

        TypeDeclarationInfo declarationInfo = typeDeclarationInfoForOwner(ownerQualifiedName, model, projectIndex);
        if (declarationInfo == null)
            return List.of();
        if (!declarationInfo.directSupertypes().isEmpty())
            return declarationInfo.directSupertypes();

        return implicitSuperTypes(ownerQualifiedName, declarationInfo.kind());
    }

    private static List<String> implicitSuperTypes(String ownerQualifiedName, SymbolKind kind) {
        return switch (kind) {
            case CLASS -> "java.lang.Object".equals(ownerQualifiedName) ? List.of() : List.of("java.lang.Object");
            case RECORD -> List.of("java.lang.Record");
            case ENUM -> List.of("java.lang.Enum");
            case ANNOTATION -> List.of("java.lang.annotation.Annotation");
            case INTERFACE -> List.of();
            default -> List.of();
        };
    }

    private static @Nullable TypeDeclarationInfo typeDeclarationInfoForOwner(
        String ownerQualifiedName,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        TypeDeclarationInfo inCurrentModel = typeDeclarationInfoInModel(ownerQualifiedName, model, projectIndex);
        if (inCurrentModel != null)
            return inCurrentModel;

        Path sourceFile = sourceFileForType(ownerQualifiedName, projectIndex);
        if (sourceFile == null)
            return null;

        try {
            SemanticModel declarationModel = JavaSemanticAnalyzer.analyzeDeclarationsFacts(Files.readString(sourceFile));
            return typeDeclarationInfoInModel(ownerQualifiedName, declarationModel, projectIndex);
        } catch (IOException exception) {
            return null;
        }
    }

    private static @Nullable TypeDeclarationInfo typeDeclarationInfoInModel(
        String ownerQualifiedName,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        SyntaxNode declarationNode = findTypeDeclarationNodeByQualifiedName(model, ownerQualifiedName);
        if (declarationNode == null)
            return null;

        Symbol symbol = model.declaredSymbol(declarationNode).orElse(null);
        if (symbol == null || !isTypeSymbol(symbol.kind()))
            return null;

        List<String> directSupertypes = new ArrayList<>();
        collectClauseTypes(
            directSupertypes,
            JavaSemanticAnalyzer.directChild(declarationNode, JavaSyntaxKinds.EXTENDS_CLAUSE.id()),
            declarationNode,
            model,
            projectIndex
        );
        collectClauseTypes(
            directSupertypes,
            JavaSemanticAnalyzer.directChild(declarationNode, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id()),
            declarationNode,
            model,
            projectIndex
        );
        return new TypeDeclarationInfo(symbol.kind(), List.copyOf(directSupertypes));
    }

    private static void collectClauseTypes(
        List<String> out,
        @Nullable SyntaxNode clauseNode,
        SyntaxNode usageSite,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        if (clauseNode == null)
            return;

        for (SyntaxNode typeReference : descendantTypeReferences(clauseNode)) {
            String qualifiedName = resolveQualifiedTypeName(
                JavaSemanticAnalyzer.canonicalTypeText(typeReference),
                usageSite,
                model,
                projectIndex
            );
            if (qualifiedName != null && !qualifiedName.isBlank() && !out.contains(qualifiedName))
                out.add(qualifiedName);
        }
    }

    private static List<SyntaxNode> descendantTypeReferences(SyntaxNode root) {
        List<SyntaxNode> result = new ArrayList<>();
        collectDescendantTypeReferences(root, result);
        return List.copyOf(result);
    }

    private static void collectDescendantTypeReferences(SyntaxNode node, List<SyntaxNode> out) {
        if (JavaSyntaxKinds.TYPE_REFERENCE.id().equals(node.kind().id())) {
            out.add(node);
            return;
        }
        for (SyntaxNode child : node.children())
            collectDescendantTypeReferences(child, out);
    }

    private static @Nullable Path sourceFileForType(
        String ownerQualifiedName,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        if (projectIndex == null)
            return null;

        for (ProjectSemanticIndex.SymbolDescriptor symbol : projectIndex.lookupQualifiedName(ownerQualifiedName)) {
            if (isTypeSymbol(symbol.kind()))
                return symbol.sourceFile();
        }
        return null;
    }

    private static @Nullable SyntaxNode findTypeDeclarationNodeByQualifiedName(SemanticModel model, String ownerQualifiedName) {
        ArrayDeque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(model.syntaxTree().root());
        while (!stack.isEmpty()) {
            SyntaxNode node = stack.pop();
            Symbol symbol = model.declaredSymbol(node).orElse(null);
            if (symbol != null
                && isTypeSymbol(symbol.kind())
                && ownerQualifiedName.equals(symbol.qualifiedName().orElse(null))) {
                return node;
            }
            List<SyntaxNode> children = node.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return null;
    }

    private static @Nullable String resolveQualifiedTypeName(
        @Nullable String text,
        SyntaxNode usageSite,
        SemanticModel model,
        @Nullable ProjectSemanticIndex projectIndex
    ) {
        if (text == null || text.isBlank())
            return null;
        while (text.endsWith("[]"))
            text = text.substring(0, text.length() - 2);

        if (Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double", "void").contains(text))
            return text;
        if (text.indexOf('.') > 0)
            return text;

        String simpleName = JavaSemanticAnalyzer.simpleTypeName(text);

        Symbol localType = enclosingOrRootTypeBySimpleName(model, simpleName);
        if (localType != null)
            return localType.qualifiedName().orElse(simpleName);

        SyntaxNode root = model.syntaxTree().root();
        for (SyntaxNode child : root.children()) {
            if (!JavaSyntaxKinds.IMPORT_DECLARATION.id().equals(child.kind().id()))
                continue;
            if (JavaSemanticAnalyzer.hasTokenKind(child, JavaTokenType.STATIC_KEYWORD))
                continue;

            SyntaxNode importTarget = JavaSemanticAnalyzer.directChild(child, JavaSyntaxKinds.IMPORT_TARGET.id());
            String qualified = importTarget == null ? null : JavaSemanticAnalyzer.canonicalQualifiedName(importTarget);
            if (qualified == null || qualified.isBlank())
                continue;
            if (qualified.endsWith(".*")) {
                String wildcardCandidate = qualified.substring(0, qualified.length() - 2) + "." + simpleName;
                if (typeExists(wildcardCandidate, projectIndex))
                    return wildcardCandidate;
            } else if (simpleName.equals(JavaSemanticAnalyzer.lastSegment(qualified))) {
                return qualified;
            }
        }

        String currentPackage = currentPackageName(root);
        if (!currentPackage.isBlank()) {
            String inPackage = currentPackage + "." + simpleName;
            if (typeExists(inPackage, projectIndex))
                return inPackage;
        }

        String javaLang = "java.lang." + simpleName;
        if (typeExists(javaLang, projectIndex))
            return javaLang;

        if (typeExists(text, projectIndex))
            return text;

        return null;
    }

    private static @Nullable Symbol enclosingOrRootTypeBySimpleName(SemanticModel model, String simpleName) {
        ArrayDeque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(model.syntaxTree().root());
        while (!stack.isEmpty()) {
            SyntaxNode node = stack.pop();
            Symbol symbol = model.declaredSymbol(node).orElse(null);
            if (symbol != null && isTypeSymbol(symbol.kind()) && simpleName.equals(symbol.simpleName()))
                return symbol;
            List<SyntaxNode> children = node.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return null;
    }

    private static boolean typeExists(String qualifiedName, @Nullable ProjectSemanticIndex projectIndex) {
        if (qualifiedName == null || qualifiedName.isBlank())
            return false;
        if (projectIndex != null && !projectIndex.lookupQualifiedName(qualifiedName).isEmpty())
            return true;
        return JavaSemanticAnalyzer.loadJdkQualifiedTypeNames().contains(qualifiedName);
    }

    private static @Nullable SyntaxNode findDeepestNodeContaining(SyntaxNode node, int offset) {
        if (offset < node.start() || offset >= node.end())
            return null;
        for (SyntaxNode child : node.children()) {
            SyntaxNode match = findDeepestNodeContaining(child, offset);
            if (match != null)
                return match;
        }
        return node;
    }

    private static String currentPackageName(SyntaxNode root) {
        for (SyntaxNode child : root.children()) {
            if (!JavaSyntaxKinds.PACKAGE_DECLARATION.id().equals(child.kind().id()))
                continue;
            SyntaxNode qualifiedName = JavaSemanticAnalyzer.directChild(child, JavaSyntaxKinds.QUALIFIED_NAME.id());
            String text = qualifiedName == null ? null : JavaSemanticAnalyzer.canonicalQualifiedName(qualifiedName);
            return text == null ? "" : text;
        }
        return "";
    }

    private static @Nullable String ownerQualifiedName(@Nullable String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank())
            return null;
        int separator = qualifiedName.indexOf('#');
        return separator > 0 ? qualifiedName.substring(0, separator) : null;
    }

    private static String renderJvmType(dev.railroadide.railroad.ide.classparser.Type type) {
        return switch (type) {
            case dev.railroadide.railroad.ide.classparser.Type.PrimitiveType primitive -> primitive.name();
            case dev.railroadide.railroad.ide.classparser.Type.ArrayType array -> renderJvmType(array.componentType()) + "[]";
            case dev.railroadide.railroad.ide.classparser.Type.ClassType clazz -> clazz.name();
            case dev.railroadide.railroad.ide.classparser.Type.TypeVariable variable -> variable.name();
            case dev.railroadide.railroad.ide.classparser.Type.WildcardType wildcard ->
                wildcard.bound() == null ? "?" : "? " + renderJvmType(wildcard.bound());
        };
    }

    private record TypeDeclarationInfo(SymbolKind kind, List<String> directSupertypes) {
    }

    private record CompletionTarget(String ownerQualifiedName, boolean staticContext) {
    }
}
