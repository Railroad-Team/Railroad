package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Extracts package, import, type, and member descriptors from Java declaration facts.
 */
public final class JavaProjectSemanticExtractor {
    private final Runnable activityCheck;
    private final JavaSymbolIndex symbolIndex;

    /** Creates a source extractor that observes worker interruption. */
    public JavaProjectSemanticExtractor() {
        this(ActivityCheckpoint::checkInterrupted);
    }

    private JavaProjectSemanticExtractor(Runnable activityCheck) {
        this(activityCheck, null);
    }

    private JavaProjectSemanticExtractor(Runnable activityCheck, JavaSymbolIndex symbolIndex) {
        this.activityCheck = activityCheck;
        this.symbolIndex = symbolIndex;
    }

    /**
     * Extracts one source file with invocation-local cancellation controls.
     *
     * @param path source file identity
     * @param source captured source text
     * @param activityCheck cooperative request activity check
     * @return complete immutable source entry
     */
    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, CharSequence source, Runnable activityCheck) {
        return new JavaProjectSemanticExtractor(
            new ActivityCheckpoint(activityCheck)).extract(path, source);
    }
    /**
     * Extracts package, import, type, and member descriptors for one Java source file.
     *
     * @param path the source file path attached to extracted descriptors
     * @param source the Java source to parse for declaration facts
     * @return the source file's immutable index entry
     */
    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, CharSequence source) {
        DocumentUri uri = DocumentUri.fromPath(path);
        return extract(path, new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), source, StandardCharsets.UTF_8), activityCheck);
    }

    /** Extracts declarations retaining the exact document identity, version, URI and origin. */
    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, TextDocumentSnapshot snapshot, Runnable check) {
        return new JavaProjectSemanticExtractor(new ActivityCheckpoint(check)).extractSnapshot(path, snapshot);
    }

    private JavaProjectSemanticIndex.SourceFileIndex extractSnapshot(Path path, TextDocumentSnapshot snapshot) {
        activityCheck.run();
        String parsedSource = snapshot.text();
        SemanticModel model = JavaSemanticAnalyzer
            .analyzeDeclarationsFacts(JavaSyntaxParser.parse(snapshot, activityCheck), activityCheck);
        JavaProjectSemanticIndex.SourceFileIndex indexed = extract(path, model);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(parsedSource.getBytes(StandardCharsets.UTF_8)));
            activityCheck.run();
            return new JavaProjectSemanticIndex.SourceFileIndex(indexed.path(), indexed.packageName(),
                indexed.imports(), indexed.declaredSymbols(), hash, indexed.semanticShard());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /**
     * Extracts package, import, type, and member descriptors for one Java source file.
     *
     * @param path the source file path attached to extracted descriptors
     * @param model the semantic model containing the file's declaration facts
     * @return the source file's immutable index entry
     */
    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, SemanticModel model) {
        activityCheck.run();
        SyntaxNode root = model.syntaxTree().root();

        String packageName = extractPackageName(root);
        List<JavaProjectSemanticIndex.ImportDescriptor> imports = extractImports(root);
        SemanticIndexShard shard = JavaSemanticGraphExtractor.extract(model, symbolIndex, activityCheck);
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols = extractSymbols(path, root, model, shard);
        activityCheck.run();

        return new JavaProjectSemanticIndex.SourceFileIndex(path, packageName, imports, symbols, null, shard);
    }

    /** Copies an already resolved semantic model with invocation-local cancellation. */
    public JavaProjectSemanticIndex.SourceFileIndex extract(Path path, SemanticModel model, Runnable check) {
        return new JavaProjectSemanticExtractor(new ActivityCheckpoint(check)).extract(path, model);
    }

    /** Extracts resolved facts and documentation links using the model's captured external symbols. */
    public JavaProjectSemanticIndex.SourceFileIndex extract(
        Path path,
        SemanticModel model,
        JavaSymbolIndex index,
        Runnable check
    ) {
        return new JavaProjectSemanticExtractor(new ActivityCheckpoint(check), index).extract(path, model);
    }

    private List<JavaProjectSemanticIndex.SymbolDescriptor> extractSymbols(
        Path path,
        SyntaxNode root,
        SemanticModel model,
        SemanticIndexShard shard
    ) {
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols = new ArrayList<>();
        collectSymbols(path, root, model, symbols, shard);
        return List.copyOf(symbols);
    }

    private void collectSymbols(
        Path path,
        SyntaxNode node,
        SemanticModel model,
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols,
        SemanticIndexShard shard
    ) {
        model.declaredSymbol(node)
            .filter(symbol -> isIndexedKind(symbol.kind()) && isProjectDeclaration(node))
            .ifPresent(symbol -> symbols.add(toDescriptor(path, node, symbol, shard)));

        for (SyntaxNode child : node.children()) {
            ActivityCheckpoint.poll(activityCheck);
            collectSymbols(path, child, model, symbols, shard);
        }
    }

    private boolean isIndexedKind(SymbolKind symbolKind) {
        return switch (symbolKind) {
            case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
                JavaSymbolKind.RECORD, JavaSymbolKind.METHOD, JavaSymbolKind.FIELD, JavaSymbolKind.CONSTRUCTOR -> true;
            default -> false;
        };
    }

    private static boolean isProjectDeclaration(SyntaxNode node) {
        if (node.kind().id().equals("JAVA_ANONYMOUS_CLASS_BODY"))
            return false;
        for (SyntaxNode owner = node.parent().orElse(null); owner != null; owner = owner.parent().orElse(null)) {
            if (switch (owner.kind().id()) {
                case "JAVA_METHOD_DECLARATION", "JAVA_CONSTRUCTOR_DECLARATION", "JAVA_RECORD_COMPACT_CONSTRUCTOR",
                    "JAVA_ANONYMOUS_CLASS_BODY" -> true;
                default -> false;
            })
                return false;
        }
        return true;
    }

    private JavaProjectSemanticIndex.SymbolDescriptor toDescriptor(
        Path path,
        SyntaxNode node,
        Symbol symbol,
        SemanticIndexShard shard
    ) {
        SyntaxNode container = declarationContainer(node);
        String qualifiedName = symbol.qualifiedName().orElse(null);
        var descriptor = JavaSourceSymbolIds.deriveSymbolId(symbol).flatMap(id -> shard.declarations().stream()
            .filter(candidate -> candidate.id().equals(id)).findFirst()).orElse(null);

        return new JavaProjectSemanticIndex.SymbolDescriptor(
            symbol.kind(),
            symbol.simpleName(),
            qualifiedName,
            ownerQualifiedName(symbol.kind(), qualifiedName),
            descriptor == null ? extractSignature(container, symbol.kind()) : null,
            path,
            descriptor == null && isStaticDeclaration(container),
            isTopLevelType(container, symbol.kind()),
            descriptor);
    }

    private @Nullable String extractSignature(SyntaxNode node, SymbolKind kind) {
        if (kind != JavaSymbolKind.METHOD && kind != JavaSymbolKind.CONSTRUCTOR)
            return null;

        SyntaxNode parameterList = directChild(node, JavaSyntaxKinds.PARAMETER_LIST.id());
        if (parameterList == null)
            return "()";

        List<String> parameterTypes = new ArrayList<>();
        for (SyntaxNode child : parameterList.children()) {
            ActivityCheckpoint.poll(activityCheck);
            String kindId = child.kind().id();

            if (!JavaSyntaxKinds.PARAMETER.id().equals(kindId))
                continue;

            SyntaxNode typeRef = directChild(child, JavaSyntaxKinds.TYPE_REFERENCE.id());
            if (typeRef != null) {
                String text = JavaSemanticAnalyzer.canonicalTypeText(typeRef);
                if (text != null && !text.isBlank()) {
                    if (hasTokenKind(child, JavaTokenType.ELLIPSIS)) {
                        text += "[]";
                    }
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

        if (kind == JavaSymbolKind.FIELD || kind == JavaSymbolKind.METHOD || kind == JavaSymbolKind.CONSTRUCTOR) {
            int hash = qualifiedName.indexOf('#');
            if (hash > 0)
                return qualifiedName.substring(0, hash);
        }

        return null;
    }

    private boolean isStaticDeclaration(SyntaxNode node) {
        if (JavaSyntaxKinds.ENUM_CONSTANT.id().equals(node.kind().id()))
            return true;
        return hasTokenKind(node, JavaTokenType.STATIC_KEYWORD);
    }

    private boolean isTopLevelType(SyntaxNode node, SymbolKind kind) {
        if (!isTypeKind(kind))
            return false;

        SyntaxNode current = node.parent().orElse(null);
        while (current != null) {
            ActivityCheckpoint.poll(activityCheck);
            if (isTypeDeclaration(current))
                return false;

            current = current.parent().orElse(null);
        }

        return true;
    }

    private boolean isTypeKind(SymbolKind kind) {
        return switch (kind) {
            case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
                JavaSymbolKind.RECORD -> true;
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
            ActivityCheckpoint.poll(activityCheck);
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
                isWildcard));
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
            ActivityCheckpoint.poll(activityCheck);
            if (containsTokenKind(child, tokenKindId))
                return true;
        }

        return false;
    }

    private String extractPackageName(SyntaxNode root) {
        for (SyntaxNode child : root.children()) {
            ActivityCheckpoint.poll(activityCheck);
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
            ActivityCheckpoint.poll(activityCheck);
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
            ActivityCheckpoint.poll(activityCheck);
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
