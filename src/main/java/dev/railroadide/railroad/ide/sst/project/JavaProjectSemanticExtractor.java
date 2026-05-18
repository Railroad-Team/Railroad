package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// TODO: Improve signature extraction to handle generics, varargs, and other complex parameter types.
// TODO: Improve signature extraction to include return type for methods.
public final class JavaProjectSemanticExtractor {
    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, CharSequence source) {
        SemanticModel model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
        return extract(path, model);
    }

    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, SemanticModel model) {
        SyntaxNode root = model.syntaxTree().root();

        String packageName = extractPackageName(root);
        List<JavaProjectSemanticIndex.ImportDescriptor> imports = extractImports(root);
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols = extractSymbols(path, root, model);

        return new JavaProjectSemanticIndex.SourceFileIndex(path, packageName, imports, symbols);
    }

    private List<JavaProjectSemanticIndex.SymbolDescriptor> extractSymbols(Path path, SyntaxNode root, SemanticModel model) {
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols = new ArrayList<>();
        collectSymbols(path, root, model, symbols);
        return List.copyOf(symbols);
    }

    private void collectSymbols(Path path, SyntaxNode node, SemanticModel model, List<JavaProjectSemanticIndex.SymbolDescriptor> symbols) {
        model.declaredSymbol(node)
            .filter(symbol -> isIndexedKind(symbol.kind()))
            .ifPresent(symbol -> symbols.add(toDescriptor(path, node, symbol)));

        for (SyntaxNode child : node.children()) {
            collectSymbols(path, child, model, symbols);
        }
    }

    private boolean isIndexedKind(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD,
                 METHOD, FIELD, CONSTRUCTOR -> true;
            default -> false;
        };
    }

    private JavaProjectSemanticIndex.SymbolDescriptor toDescriptor(Path path, SyntaxNode node, Symbol symbol) {
        SyntaxNode container = declarationContainer(node);
        String qualifiedName = symbol.qualifiedName().orElse(null);

        return new JavaProjectSemanticIndex.SymbolDescriptor(
            symbol.kind(),
            symbol.simpleName(),
            qualifiedName,
            ownerQualifiedName(symbol.kind(), qualifiedName),
            extractSignature(container, symbol.kind()),
            path,
            isStaticDeclaration(container),
            isTopLevelType(container, symbol.kind())
        );
    }

    private @Nullable String extractSignature(SyntaxNode node, SymbolKind kind) {
        if (kind != SymbolKind.METHOD && kind != SymbolKind.CONSTRUCTOR)
            return null;

        SyntaxNode parameterList = directChild(node, JavaSyntaxKinds.PARAMETER_LIST.id());
        if (parameterList == null)
            return "()";

        List<String> parameterTypes = new ArrayList<>();
        for (SyntaxNode child : parameterList.children()) {
            String kindId = child.kind().id();

            if (!JavaSyntaxKinds.PARAMETER.id().equals(kindId))
                continue;

            SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef != null) {
                String text = JavaSemanticAnalyzer.canonicalTypeText(typeRef);
                if (text != null && !text.isBlank()) {
                    parameterTypes.add(text);
                }
            }
        }

        return "(" + String.join(",", parameterTypes) + ")";
    }

    private SyntaxNode declarationContainer(SyntaxNode node) {
        if (JavaSyntaxKinds.VARIABLE_DECLARATOR.id().equals(node.kind().id()))
            return node.parent().orElse(node);

        return node;
    }

    private String ownerQualifiedName(SymbolKind kind, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank())
            return null;

        if (kind == SymbolKind.FIELD || kind == SymbolKind.METHOD || kind == SymbolKind.CONSTRUCTOR) {
            int hash = qualifiedName.indexOf('#');
            if (hash > 0)
                return qualifiedName.substring(0, hash);
        }

        return null;
    }

    private boolean isStaticDeclaration(SyntaxNode node) {
        return hasTokenKind(node, JavaTokenType.STATIC_KEYWORD);
    }

    private boolean isTopLevelType(SyntaxNode node, SymbolKind kind) {
        if (!isTypeKind(kind))
            return false;

        SyntaxNode current = node.parent().orElse(null);
        while (current != null) {
            if (isTypeDeclaration(current))
                return false;

            current = current.parent().orElse(null);
        }

        return true;
    }

    private boolean isTypeKind(SymbolKind kind) {
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> true;
            default -> false;
        };
    }

    private boolean isTypeDeclaration(SyntaxNode node) {
        return switch (node.kind().id()) {
            case "JAVA_CLASS_DECLARATION",
                 "JAVA_INTERFACE_DECLARATION",
                 "JAVA_ENUM_DECLARATION",
                 "JAVA_ANNOTATION_TYPE_DECLARATION",
                 "JAVA_RECORD_DECLARATION" -> true;
            default -> false;
        };
    }

    private List<JavaProjectSemanticIndex.ImportDescriptor> extractImports(SyntaxNode root) {
        List<JavaProjectSemanticIndex.ImportDescriptor> imports = new ArrayList<>();

        for (SyntaxNode child : root.children()) {
            if (!JavaSyntaxKinds.IMPORT_DECLARATION.id().equals(child.kind().id()))
                continue;

            SyntaxNode importTarget = directChild(child, JavaSyntaxKinds.IMPORT_TARGET.id());
            if (importTarget == null)
                continue;

            String qualifiedName = flattenQualifiedName(importTarget);
            if (qualifiedName == null || qualifiedName.isBlank())
                continue;

            boolean isStatic = hasTokenKind(child, JavaTokenType.STATIC_KEYWORD);
            boolean isWildcard = hasTokenKind(child, JavaTokenType.STAR);

            imports.add(new JavaProjectSemanticIndex.ImportDescriptor(
                qualifiedName,
                isStatic,
                isWildcard
            ));
        }

        return List.copyOf(imports);
    }

    private boolean hasTokenKind(SyntaxNode node, JavaTokenType tokenType) {
        String tokenKindId = JavaSyntaxKinds.tokenKind(tokenType).id();
        return containsTokenKind(node, tokenKindId);
    }

    private boolean containsTokenKind(SyntaxNode node, String tokenKindId) {
        if (node instanceof SyntaxToken token)
            return token.kind().id().equals(tokenKindId);

        for (SyntaxNode child : node.children()) {
            if (containsTokenKind(child, tokenKindId))
                return true;
        }

        return false;
    }

    private String extractPackageName(SyntaxNode root) {
        for (SyntaxNode child : root.children()) {
            if (!JavaSyntaxKinds.PACKAGE_DECLARATION.id().equals(child.kind().id()))
                continue;

            SyntaxNode qualifiedName = directChild(child, JavaSyntaxKinds.QUALIFIED_NAME.id());
            if (qualifiedName == null)
                return null;

            String text = flattenQualifiedName(qualifiedName);
            return text == null || text.isBlank() ? null : text;
        }

        return null;
    }

    private @Nullable SyntaxNode directChild(SyntaxNode node, String kindId) {
        for (SyntaxNode child : node.children()) {
            if (child.kind().id().equals(kindId))
                return child;
        }

        return null;
    }

    private @Nullable String flattenQualifiedName(SyntaxNode node) {
        var builder = new StringBuilder();
        appendQualifiedName(node, builder);

        String text = builder.toString().trim();
        return text.isBlank() ? null : text;
    }

    private void appendQualifiedName(SyntaxNode node, StringBuilder builder) {
        if (node instanceof SyntaxToken token && isQualifiedNameToken(token)) {
            builder.append(token.text());
            return;
        }

        for (SyntaxNode child : node.children()) {
            appendQualifiedName(child, builder);
        }
    }

    private boolean isQualifiedNameToken(SyntaxToken token) {
        String kindId = token.kind().id();
        return isIdentifierLikeToken(kindId)
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.DOT).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.STAR).id());
    }

    private boolean isIdentifierLikeToken(String kindId) {
        return kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.IDENTIFIER).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.UNDERSCORE_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.EXPORTS_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.MODULE_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.OPENS_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.PERMITS_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.PROVIDES_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.RECORD_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.REQUIRES_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.SEALED_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.TO_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.TRANSITIVE_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.USES_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.VAR_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.WITH_KEYWORD).id())
            || kindId.equals(JavaSyntaxKinds.tokenKind(JavaTokenType.WHEN_KEYWORD).id());
    }
}
