package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.impl.java.JavaDeclarationAnalysis;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaUnicodeEscapes;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import java.util.*;

/** Java source identities. Lexical paths count syntax nodes, never trivia or character offsets. */
public final class JavaSourceSymbolIds {
    private JavaSourceSymbolIds() {
    }

    /**
     * Returns an identity when the symbol carries enough declaration information to identify it uniquely.
     *
     * @param symbol symbol whose declaration is queried
     * @return canonical identity, or an empty optional if insufficient declaration facts are available
     */
    public static Optional<SymbolId> deriveSymbolId(Symbol symbol) {
        return deriveSymbolId(symbol, null);
    }

    /**
     * Resolves source signature aliases against a captured project index.
     *
     * @param symbol symbol whose declaration is queried
     * @param index captured external symbols used for name resolution, or {@code null} for local resolution
     * @return canonical identity, or an empty optional if insufficient declaration facts are available
     */
    public static Optional<SymbolId> deriveSymbolId(Symbol symbol, JavaSymbolIndex index) {
        return deriveSymbolId(symbol, index, null);
    }

    private static Optional<SymbolId> deriveSymbolId(Symbol symbol, JavaSymbolIndex index, Resolver resolver) {
        try {
            return deriveValidSymbolId(symbol, index, resolver);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<SymbolId> deriveValidSymbolId(Symbol symbol, JavaSymbolIndex index, Resolver resolver) {
        if (symbol.id().isPresent())
            return symbol.id();
        SyntaxNode node = symbol.declaration().orElse(null);
        String qualified = symbol.qualifiedName().orElse(symbol.simpleName());
        if (symbol.kind() == JavaSymbolKind.IMPORT || symbol.kind() == JavaSymbolKind.UNKNOWN)
            return Optional.empty();
        if (symbol.kind() == JavaSymbolKind.PACKAGE)
            return Optional.of(JavaSymbolIds.packageId("", qualified));
        if (symbol.kind() == JavaSymbolKind.MODULE)
            return Optional.of(JavaSymbolIds.module(qualified));
        if (isType(symbol.kind().id())) {
            if (node != null && node.kind().id().equals("JAVA_ANONYMOUS_CLASS_BODY")
                && !JavaDeclarationAnalysis.isEnumConstantBody(node)) {
                node = node.parent().orElse(null);
            }
            if (node != null)
                return Optional.of(resolver != null && isTypeNode(node)
                    ? resolver.declarationId(node)
                    : typeId(node, index, resolver));
            return Optional.of(JavaSymbolIds.type(qualified));
        }
        if (node == null)
            return Optional.empty();
        SyntaxNode owner = enclosingDeclaration(node);
        SymbolId ownerId = owner == null
            ? JavaSymbolIds.packageId("", packageName(node))
            : declarationId(owner, index, resolver);
        return switch (symbol.kind()) {
            case JavaSymbolKind.FIELD -> Optional.of(JavaSymbolIds.field(ownerId, symbol.simpleName()));
            case JavaSymbolKind.METHOD ->
                Optional.of(JavaSymbolIds.method(ownerId, symbol.simpleName(), parameterTypes(node, index, resolver)));
            case JavaSymbolKind.CONSTRUCTOR ->
                Optional.of(JavaSymbolIds.constructor(ownerId, parameterTypes(node, index, resolver)));
            case JavaSymbolKind.PARAMETER -> Optional.of(node.kind().id().equals("JAVA_PARAMETER")
                && node.parent().flatMap(SyntaxNode::parent).orElse(null) == owner
                    ? JavaSymbolIds.parameter(ownerId, siblingIndex(node))
                    : JavaSymbolIds.local(ownerId, lexicalPath(node, owner), symbol.simpleName()));
            case JavaSymbolKind.TYPE_PARAMETER -> Optional.of(JavaSymbolIds.typeParameter(ownerId, siblingIndex(node)));
            default -> Optional.of(JavaSymbolIds.local(ownerId, lexicalPath(node, owner), symbol.simpleName()));
        };
    }

    /**
     * Returns the nearest containing type or callable declaration.
     *
     * @param node syntax node to inspect
     * @return nearest containing type or callable, or {@code null}
     */
    public static SyntaxNode enclosingDeclaration(SyntaxNode node) {
        for (SyntaxNode current = node.parent().orElse(null); current != null; current = current.parent()
            .orElse(null)) {
            if (isDeclaration(current))
                return current;
        }
        return null;
    }

    /**
     * Returns the structural declaration identity, including local and anonymous types.
     *
     * @param node syntax node to inspect
     * @return canonical identity of the source declaration
     */
    public static SymbolId declarationId(SyntaxNode node) {
        return declarationId(node, null);
    }

    /**
     * Resolves a source declaration identity using captured external type names.
     *
     * @param node syntax node to inspect
     * @param index captured external symbols used for name resolution, or {@code null} for local resolution
     * @return canonical identity of the source declaration
     */
    public static SymbolId declarationId(SyntaxNode node, JavaSymbolIndex index) {
        return declarationId(node, index, null);
    }

    private static SymbolId declarationId(SyntaxNode node, JavaSymbolIndex index, Resolver resolver) {
        return resolver == null ? computeDeclarationId(node, index, null) : resolver.declarationId(node);
    }

    private static SymbolId computeDeclarationId(SyntaxNode node, JavaSymbolIndex index, Resolver resolver) {
        if (isTypeNode(node))
            return typeId(node, index, resolver);
        SyntaxNode owner = enclosingDeclaration(node);
        SymbolId ownerId = owner == null
            ? JavaSymbolIds.packageId("", packageName(node))
            : declarationId(owner, index, resolver);
        return (node.kind().id().equals("JAVA_CONSTRUCTOR_DECLARATION")
            || node.kind().id().equals("JAVA_RECORD_COMPACT_CONSTRUCTOR"))
                ? JavaSymbolIds.constructor(ownerId, parameterTypes(node, index, resolver))
                : JavaSymbolIds.method(ownerId, declarationName(node), parameterTypes(node, index, resolver));
    }

    private static SymbolId typeId(SyntaxNode node, JavaSymbolIndex index, Resolver resolver) {
        SyntaxNode owner = enclosingDeclaration(node);
        if (node.kind().id().equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION")
            || JavaDeclarationAnalysis.isEnumConstantBody(node))
            return JavaSymbolIds.anonymous(owner == null
                ? JavaSymbolIds.packageId("", packageName(node))
                : declarationId(owner, index, resolver), lexicalPath(node, owner));
        String name = declarationName(node);
        if (owner != null) {
            SymbolId ownerId = declarationId(owner, index, resolver);
            if (!isTypeNode(owner) || ownerId.value().contains("/localType/")
                || ownerId.value().contains("/anonymous/"))
                return JavaSymbolIds.localType(ownerId, lexicalPath(node, owner), name);
        }
        String qualified = typeQualifiedName(node);
        return JavaSymbolIds.type(qualified);
    }

    private static String typeQualifiedName(SyntaxNode node) {
        SyntaxNode owner = enclosingDeclaration(node);
        String prefix = owner != null && isTypeNode(owner) ? typeQualifiedName(owner) : packageName(node);
        return prefix.isEmpty()
            ? declarationName(node)
            : prefix + (owner != null && isTypeNode(owner) ? "$" : ".") + declarationName(node);
    }

    /**
     * Returns canonical erased parameter types, preserving array rank and normalizing varargs.
     *
     * @param callable callable declaration or record node whose parameters are inspected
     * @return canonical erased parameter types in declaration order
     */
    public static List<String> parameterTypes(SyntaxNode callable) {
        return computeParameterTypes(callable, null, null);
    }

    private static List<String> parameterTypes(SyntaxNode callable, JavaSymbolIndex index, Resolver resolver) {
        return resolver == null
            ? computeParameterTypes(callable, index, null)
            : resolver.parameters.computeIfAbsent(callable, node -> computeParameterTypes(node, index, resolver));
    }

    private static List<String> computeParameterTypes(SyntaxNode callable, JavaSymbolIndex index, Resolver resolver) {
        SyntaxNode list = parameterList(callable);
        if (list == null)
            return List.of();
        List<String> result = new ArrayList<>();
        for (SyntaxNode parameter : list.children()) {
            if (!parameter.kind().id().equals("JAVA_PARAMETER")
                && !parameter.kind().id().equals("JAVA_RECORD_COMPONENT"))
                continue;
            SyntaxNode type = child(parameter, "JAVA_TYPE_REFERENCE");
            String text = type == null ? "java.lang.Object" : JavaSemanticAnalyzer.canonicalTypeText(type);
            text = erase(text == null ? "java.lang.Object" : text);
            String base = text.replace("[]", "");
            String erasedBase = base;
            Set<String> visited = new HashSet<>();
            String bound;
            while ((bound = typeVariableBound(erasedBase, callable)) != null) {
                if (!visited.add(erasedBase))
                    throw new IllegalArgumentException("Cyclic type variable bounds");
                erasedBase = erase(bound);
            }
            String canonical = qualify(erasedBase, callable, index, resolver);
            int rank = (text.length() - base.length()) / 2;
            for (SyntaxNode part : parameter.children()) {
                if (part instanceof SyntaxToken token && (token.text().equals("...") || token.text().equals("["))) {
                    rank++;
                }
            }
            result.add(canonical + "[]".repeat(rank));
        }
        return List.copyOf(result);
    }

    /**
     * Returns explicit parameters or the record header supplying a compact constructor's parameters.
     *
     * @param callable callable declaration or record node whose parameters are inspected
     * @return parameter or record-component list, or {@code null} when absent
     */
    public static SyntaxNode parameterList(SyntaxNode callable) {
        SyntaxNode list = child(callable, "JAVA_PARAMETER_LIST");
        if (list != null || !callable.kind().id().equals("JAVA_RECORD_COMPACT_CONSTRUCTOR"))
            return list;
        SyntaxNode owner = enclosingDeclaration(callable);
        return owner == null ? null : child(owner, "JAVA_RECORD_HEADER");
    }

    /**
     * Resolves explicitly imported, local package and java.lang names without consulting live files.
     *
     * @param name declaration name
     * @param context syntax context used to resolve visible declarations and imports
     * @return qualified type spelling where resolvable, otherwise the available spelling
     */
    public static String qualify(String name, SyntaxNode context) {
        return qualify(name, context, null);
    }

    /**
     * Resolves aliases against immutable project declarations when available.
     *
     * @param name declaration name
     * @param context syntax context used to resolve visible declarations and imports
     * @param index captured external symbols used for name resolution, or {@code null} for local resolution
     * @return qualified type spelling where resolvable, otherwise the available spelling
     */
    public static String qualify(String name, SyntaxNode context, JavaSymbolIndex index) {
        return qualify(name, context, index, null);
    }

    private static String qualify(String name, SyntaxNode context, JavaSymbolIndex index, Resolver resolver) {
        if (name.isBlank()
            || Set.of("void", "boolean", "byte", "short", "int", "long", "float", "double", "char", "var", "?")
                .contains(name))
            return name;
        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (name.contains(".") && Character.isLowerCase(name.charAt(0)))
            return normalizeNested(name, index);
        for (SyntaxNode current = context; current != null; current = current.parent().orElse(null)) {
            if (isTypeNode(current) && declarationName(current).equals(first))
                return typeQualifiedName(current) + name.substring(first.length()).replace('.', '$');
            SyntaxNode body = child(current, "JAVA_CLASS_BODY");
            if (body == null) {
                body = child(current, "JAVA_INTERFACE_BODY");
            }
            if (body == null && current.parent().isEmpty()) {
                body = current;
            }
            if (body == null)
                continue;
            for (SyntaxNode nested : body.children()) {
                if (isTypeNode(nested) && declarationName(nested).equals(first))
                    return typeQualifiedName(nested) + name.substring(first.length()).replace('.', '$');
            }
        }
        SyntaxNode root = root(context);
        if (resolver != null && root == resolver.root)
            return resolver.qualifiedNames.computeIfAbsent(name, key -> qualifyFileName(key, first, root, index));
        return qualifyFileName(name, first, root, index);
    }

    private static String qualifyFileName(String name, String first, SyntaxNode root, JavaSymbolIndex index) {
        for (SyntaxNode declaration : root.children()) {
            if (!declaration.kind().id().equals("JAVA_IMPORT_DECLARATION") || compactText(declaration).contains("*"))
                continue;
            SyntaxNode target = child(declaration, "JAVA_IMPORT_TARGET");
            if (target == null)
                continue;
            String imported = compactText(target);
            if (imported.endsWith("." + first))
                return normalizeNested(imported + name.substring(first.length()), index);
        }
        String packageName = packageName(root);
        String packageType = packageName.isEmpty() ? name : packageName + "." + name;
        if (index != null && index.declaredQualifiedNames().contains(packageType))
            return normalizeNested(packageType, index);
        Set<String> candidates = new LinkedHashSet<>();
        boolean onDemandImport = false;
        if (knownType("java.lang." + first, index)) {
            candidates.add("java.lang." + name);
        }
        for (SyntaxNode declaration : root.children()) {
            if (!declaration.kind().id().equals("JAVA_IMPORT_DECLARATION") || !compactText(declaration).contains("*"))
                continue;
            onDemandImport = true;
            SyntaxNode target = child(declaration, "JAVA_IMPORT_TARGET");
            if (target == null)
                continue;
            String imported = compactText(target).replaceAll("\\.\\*$", "");
            if (knownType(imported + "." + first, index)) {
                candidates.add(normalizeNested(imported + "." + name, index));
            }
        }
        if (candidates.size() > 1)
            throw new IllegalArgumentException("Ambiguous imported type: " + name);
        if (candidates.isEmpty() && onDemandImport)
            throw new IllegalArgumentException("Unresolved imported type: " + name);
        return candidates.isEmpty() ? packageType : candidates.iterator().next();
    }

    /** Reuses source identities during a single analysis of one immutable compilation unit. */
    public static final class Resolver {
        private final SyntaxNode root;
        private final JavaSymbolIndex index;
        private final Map<SyntaxNode, SymbolId> declarationIds = new IdentityHashMap<>();
        private final Map<SyntaxNode, List<String>> parameters = new IdentityHashMap<>();
        private final Map<String, String> qualifiedNames = new HashMap<>();

        /**
         * Creates a resolver for one syntax tree and captured index. Use it from one worker.
         *
         * @param root compilation-unit root
         * @param index external names, or {@code null} for local resolution
         */
        public Resolver(SyntaxNode root, JavaSymbolIndex index) {
            this.root = Objects.requireNonNull(root);
            this.index = index;
        }

        /**
         * Resolves a symbol using this compilation unit's cached declaration identities.
         *
         * @param symbol symbol to identify
         * @return canonical identity when sufficient declaration information is present
         */
        public Optional<SymbolId> deriveSymbolId(Symbol symbol) {
            return JavaSourceSymbolIds.deriveSymbolId(symbol, index, this);
        }

        /**
         * Resolves a structural declaration identity.
         *
         * @param node declaration to identify
         * @return canonical source declaration identity
         */
        public SymbolId declarationId(SyntaxNode node) {
            SymbolId id = declarationIds.get(node);
            if (id == null) {
                id = computeDeclarationId(node, index, this);
                declarationIds.put(node, id);
            }
            return id;
        }

        /**
         * Resolves a type name in its lexical scope and this compilation unit's imports.
         *
         * @param name source name to resolve
         * @param context syntax location from which the name is visible
         * @return qualified spelling when resolvable, otherwise the available spelling
         */
        public String qualify(String name, SyntaxNode context) {
            return JavaSourceSymbolIds.qualify(name, context, index, this);
        }
    }

    private static boolean knownType(String name, JavaSymbolIndex index) {
        if (index != null && index.typeNames().contains(name))
            return true;
        try {
            Class.forName(name, false, ClassLoader.getPlatformClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static String normalizeNested(String name, JavaSymbolIndex index) {
        if (index == null)
            return name;
        for (int dot = name.indexOf('.'); dot >= 0; dot = name.indexOf('.', dot + 1)) {
            String prefix = name.substring(0, dot);
            if (index.typeNames().contains(prefix))
                return prefix + name.substring(dot).replace('.', '$');
        }
        return name;
    }

    /**
     * Returns a type-variable's first bound, or null when the name is not a visible type variable.
     *
     * @param name declaration name
     * @param context syntax context used to resolve visible declarations and imports
     * @return first visible type-variable bound, or {@code null} when the name is not a visible type variable
     */
    public static String typeVariableBound(String name, SyntaxNode context) {
        for (SyntaxNode current = context; current != null; current = current.parent().orElse(null)) {
            SyntaxNode parameters = child(current, "JAVA_TYPE_PARAMETERS");
            if (parameters == null)
                continue;
            for (SyntaxNode parameter : parameters.children()) {
                if (!parameter.kind().id().equals("JAVA_TYPE_PARAMETER"))
                    continue;
                if (!declarationName(parameter).equals(name))
                    continue;
                SyntaxNode bound = find(parameter, "JAVA_TYPE_REFERENCE");
                return bound == null ? "java.lang.Object" : JavaSemanticAnalyzer.canonicalTypeText(bound);
            }
        }
        return null;
    }

    /**
     * Returns the declaration's identifier using only its header.
     *
     * @param node syntax node to inspect
     * @return declaration name extracted from its syntax
     */
    public static String declarationName(SyntaxNode node) {
        String last = null;
        for (SyntaxNode child : node.children()) {
            if (child.kind().id().equals("JAVA_PARAMETER_LIST"))
                return last == null ? "<init>" : last;
            if (child instanceof SyntaxToken token && identifier(token)) {
                if (isTypeNode(node) || node.kind().id().equals("JAVA_TYPE_PARAMETER"))
                    return JavaUnicodeEscapes.translate(token.text());
                last = JavaUnicodeEscapes.translate(token.text());
            }
        }
        return last == null ? "<anonymous>" : last;
    }

    /**
     * Returns a structural path that is unchanged by whitespace and comment edits.
     *
     * @param node syntax node to inspect
     * @param ancestor containing declaration, or {@code null} to use the syntax root
     * @return structural path relative to the containing declaration
     */
    public static String lexicalPath(SyntaxNode node, SyntaxNode ancestor) {
        List<String> segments = new ArrayList<>();
        for (SyntaxNode current = node; current != null
            && current != ancestor; current = current.parent().orElse(null)) {
            segments.add(current.kind().id() + ":" + siblingIndex(current));
        }
        Collections.reverse(segments);
        return String.join("/", segments);
    }

    private static int siblingIndex(SyntaxNode node) {
        SyntaxNode parent = node.parent().orElse(null);
        if (parent == null)
            return 0;
        int index = 0;
        for (SyntaxNode sibling : parent.children()) {
            if (sibling == node)
                return index;
            if (sibling.kind().equals(node.kind())) {
                index++;
            }
        }
        return index;
    }

    /**
     * Returns a direct child of the requested syntax kind.
     *
     * @param node syntax node to inspect
     * @param kind syntax kind identifier to match
     * @return first direct child with the requested kind, or {@code null}
     */
    public static SyntaxNode child(SyntaxNode node, String kind) {
        for (SyntaxNode part : node.children()) {
            if (part.kind().id().equals(kind))
                return part;
        }
        return null;
    }

    private static SyntaxNode find(SyntaxNode node, String kind) {
        if (node.kind().id().equals(kind))
            return node;
        for (SyntaxNode child : node.children()) {
            SyntaxNode found = find(child, kind);
            if (found != null)
                return found;
        }
        return null;
    }

    /**
     * Returns source tokens with trivia omitted.
     *
     * @param node syntax node to inspect
     * @return concatenated nontrivia token text
     */
    public static String compactText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return compactTokenText(token);
        var text = new StringBuilder();
        appendCompactText(node, text);
        return text.toString();
    }

    private static void appendCompactText(SyntaxNode node, StringBuilder text) {
        if (node instanceof SyntaxToken token) {
            text.append(compactTokenText(token));
            return;
        }
        for (SyntaxNode child : node.children()) {
            appendCompactText(child, text);
        }
    }

    private static String compactTokenText(SyntaxToken token) {
        return trivia(token) ? "" : identifier(token) ? JavaUnicodeEscapes.translate(token.text()) : token.text();
    }

    private static boolean trivia(SyntaxToken token) {
        String kind = token.kind().id();
        return kind.contains("WHITESPACE") || kind.contains("TERMINATOR") || kind.contains("COMMENT");
    }

    private static boolean identifier(SyntaxToken token) {
        return token.kind().id().endsWith("IDENTIFIER");
    }

    private static SyntaxNode root(SyntaxNode node) {
        while (node.parent().isPresent()) {
            node = node.parent().orElseThrow();
        }
        return node;
    }

    private static String packageName(SyntaxNode context) {
        SyntaxNode declaration = child(root(context), "JAVA_PACKAGE_DECLARATION");
        SyntaxNode name = declaration == null ? null : child(declaration, "JAVA_QUALIFIED_NAME");
        return name == null ? "" : compactText(name);
    }

    private static boolean isDeclaration(SyntaxNode node) {
        return isTypeNode(node) || node.kind().id().equals("JAVA_METHOD_DECLARATION")
            || node.kind().id().equals("JAVA_CONSTRUCTOR_DECLARATION")
            || node.kind().id().equals("JAVA_RECORD_COMPACT_CONSTRUCTOR");
    }

    private static boolean isTypeNode(SyntaxNode node) {
        return Set.of("JAVA_CLASS_DECLARATION", "JAVA_INTERFACE_DECLARATION", "JAVA_ENUM_DECLARATION",
            "JAVA_RECORD_DECLARATION", "JAVA_ANNOTATION_TYPE_DECLARATION").contains(node.kind().id())
            || JavaDeclarationAnalysis.isEnumConstantBody(node)
            || node.kind().id().equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION")
                && child(node, "JAVA_ANONYMOUS_CLASS_BODY") != null;
    }

    private static boolean isType(String kind) {
        return Set.of("java:class", "java:interface", "java:enum", "java:record", "java:annotation").contains(kind);
    }

    private static String erase(String text) {
        var result = new StringBuilder();
        int depth = 0;
        for (char character : text.toCharArray()) {
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            } else if (depth == 0 && !Character.isWhitespace(character)) {
                result.append(character);
            }
        }
        return result.toString().replace("...", "[]");
    }
}
