package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
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

    /** Returns an identity when the symbol carries enough declaration information to identify it uniquely. */
    public static Optional<SymbolId> deriveSymbolId(Symbol symbol) {
        try {
            return deriveValidSymbolId(symbol, null);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Resolves source signature aliases against a captured project index. */
    public static Optional<SymbolId> deriveSymbolId(Symbol symbol, JavaSymbolIndex index) {
        try {
            return deriveValidSymbolId(symbol, index);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<SymbolId> deriveValidSymbolId(Symbol symbol, JavaSymbolIndex index) {
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
            if (node != null && node.kind().id().equals("JAVA_ANONYMOUS_CLASS_BODY")) {
                node = node.parent().orElse(null);
            }
            if (node != null)
                return Optional.of(typeId(node, index));
            return Optional.of(JavaSymbolIds.type(qualified));
        }
        if (node == null)
            return Optional.empty();
        SyntaxNode owner = enclosingDeclaration(node);
        SymbolId ownerId = owner == null ? JavaSymbolIds.packageId("", packageName(node)) : declarationId(owner, index);
        return switch (symbol.kind()) {
            case JavaSymbolKind.FIELD -> Optional.of(JavaSymbolIds.field(ownerId, symbol.simpleName()));
            case JavaSymbolKind.METHOD ->
                Optional.of(JavaSymbolIds.method(ownerId, symbol.simpleName(), parameterTypes(node, index)));
            case JavaSymbolKind.CONSTRUCTOR ->
                Optional.of(JavaSymbolIds.constructor(ownerId, parameterTypes(node, index)));
            case JavaSymbolKind.PARAMETER -> Optional.of(node.kind().id().equals("JAVA_PARAMETER")
                && node.parent().flatMap(SyntaxNode::parent).orElse(null) == owner
                    ? JavaSymbolIds.parameter(ownerId, siblingIndex(node))
                    : JavaSymbolIds.local(ownerId, lexicalPath(node, owner), symbol.simpleName()));
            case JavaSymbolKind.TYPE_PARAMETER -> Optional.of(JavaSymbolIds.typeParameter(ownerId, siblingIndex(node)));
            default -> Optional.of(JavaSymbolIds.local(ownerId, lexicalPath(node, owner), symbol.simpleName()));
        };
    }

    /** Returns the nearest containing type or callable declaration. */
    public static SyntaxNode enclosingDeclaration(SyntaxNode node) {
        for (SyntaxNode current = node.parent().orElse(null); current != null; current = current.parent()
            .orElse(null)) {
            if (isDeclaration(current))
                return current;
        }
        return null;
    }

    /** Returns the structural declaration identity, including local and anonymous types. */
    public static SymbolId declarationId(SyntaxNode node) {
        return declarationId(node, null);
    }

    public static SymbolId declarationId(SyntaxNode node, JavaSymbolIndex index) {
        if (isTypeNode(node))
            return typeId(node, index);
        SyntaxNode owner = enclosingDeclaration(node);
        SymbolId ownerId = owner == null ? JavaSymbolIds.packageId("", packageName(node)) : declarationId(owner, index);
        return (node.kind().id().equals("JAVA_CONSTRUCTOR_DECLARATION")
            || node.kind().id().equals("JAVA_RECORD_COMPACT_CONSTRUCTOR"))
                ? JavaSymbolIds.constructor(ownerId, parameterTypes(node, index))
                : JavaSymbolIds.method(ownerId, declarationName(node), parameterTypes(node, index));
    }

    private static SymbolId typeId(SyntaxNode node, JavaSymbolIndex index) {
        SyntaxNode owner = enclosingDeclaration(node);
        if (node.kind().id().equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION"))
            return JavaSymbolIds.anonymous(owner == null
                ? JavaSymbolIds.packageId("", packageName(node))
                : declarationId(owner, index), lexicalPath(node, owner));
        String name = declarationName(node);
        if (owner != null && (!isTypeNode(owner) || declarationId(owner, index).value().contains("/localType/")
            || declarationId(owner, index).value().contains("/anonymous/")))
            return JavaSymbolIds.localType(declarationId(owner, index), lexicalPath(node, owner), name);
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

    /** Returns canonical erased parameter types, preserving array rank and normalizing varargs. */
    public static List<String> parameterTypes(SyntaxNode callable) {
        return parameterTypes(callable, null);
    }

    private static List<String> parameterTypes(SyntaxNode callable, JavaSymbolIndex index) {
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
            String canonical = qualify(erasedBase, callable, index);
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

    /** Returns explicit parameters or the record header supplying a compact constructor's parameters. */
    public static SyntaxNode parameterList(SyntaxNode callable) {
        SyntaxNode list = child(callable, "JAVA_PARAMETER_LIST");
        if (list != null || !callable.kind().id().equals("JAVA_RECORD_COMPACT_CONSTRUCTOR"))
            return list;
        SyntaxNode owner = enclosingDeclaration(callable);
        return owner == null ? null : child(owner, "JAVA_RECORD_HEADER");
    }

    /** Resolves explicitly imported, local package and java.lang names without consulting live files. */
    public static String qualify(String name, SyntaxNode context) {
        return qualify(name, context, null);
    }

    /** Resolves aliases against immutable project declarations when available. */
    public static String qualify(String name, SyntaxNode context, JavaSymbolIndex index) {
        if (name.isBlank()
            || Set.of("void", "boolean", "byte", "short", "int", "long", "float", "double", "char", "var", "?")
                .contains(name))
            return name;
        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (name.contains(".") && Character.isLowerCase(name.charAt(0)))
            return normalizeNested(name, index);
        SyntaxNode root = root(context);
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
        String packageType = packageName(context).isEmpty() ? name : packageName(context) + "." + name;
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

    /** Returns a type-variable's first bound, or null when the name is not a visible type variable. */
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

    /** Returns the declaration's identifier using only its header. */
    public static String declarationName(SyntaxNode node) {
        String last = null;
        for (SyntaxNode child : node.children()) {
            if (child.kind().id().equals("JAVA_PARAMETER_LIST"))
                return last == null ? "<init>" : last;
            if (child instanceof SyntaxToken token && identifier(token)) {
                if (isTypeNode(node) || node.kind().id().equals("JAVA_TYPE_PARAMETER"))
                    return token.text();
                last = token.text();
            }
        }
        return last == null ? "<anonymous>" : last;
    }

    /** Returns a structural path that is unchanged by whitespace and comment edits. */
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

    /** Returns a direct child of the requested syntax kind. */
    public static SyntaxNode child(SyntaxNode node, String kind) {
        return node.children().stream().filter(part -> part.kind().id().equals(kind)).findFirst().orElse(null);
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

    /** Returns source tokens with trivia omitted. */
    public static String compactText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return trivia(token) ? "" : token.text();
        var text = new StringBuilder();
        for (SyntaxNode child : node.children()) {
            text.append(compactText(child));
        }
        return text.toString();
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
