package dev.railroadide.railroad.ide.sst.java.api;

import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical Java declaration identities, shared by source, generated, binary and decompiled views.
 * Type names use JVM binary names (dots for packages, dollars for nested types). Module identity
 * separates named modules; the empty module denotes the supported unnamed compilation context.
 * Methods use erased, resolved parameter types, excluding return type and parameter names.
 * Providers must resolve imports and erase type variables to their first bound before calling.
 *
 * <p>
 * Parameters use declaration order. Locals and local/anonymous types use a trivia-insensitive
 * lexical path relative to the enclosing declaration; inserting structural siblings can change
 * these IDs. No stability is promised across semantic renames or arbitrary structural edits.
 * All methods are pure, thread-safe and bounded by the supplied identifier/signature size.
 */
public final class JavaSymbolIds {
    private static final Pattern PARAMETER_TYPE = Pattern.compile(
        "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}.]*(\\[\\])*");
    private JavaSymbolIds() {
    }

    public static SymbolId module(String name) {
        return new SymbolId("java:module/" + part(Objects.requireNonNull(name, "name")));
    }

    public static SymbolId packageId(String moduleName, String name) {
        return new SymbolId(module(moduleName).value() + "/package/" + part(Objects.requireNonNull(name, "name")));
    }

    public static SymbolId type(String qualifiedName) {
        return type("", qualifiedName);
    }

    public static SymbolId type(String moduleName, String qualifiedName) {
        return new SymbolId(module(moduleName).value() + "/type/" + part(requireName(qualifiedName).replace('/', '.')));
    }

    public static SymbolId field(SymbolId owner, String name) {
        return child(owner, "field", requireName(name));
    }

    public static SymbolId method(SymbolId owner, String name, List<String> erasedParameterTypes) {
        return callable(owner, "method", requireName(name), erasedParameterTypes);
    }

    /**
     * Identifies a JVM-only return-type overload. Providers use this only when multiple non-bridge
     * methods in the same class share a Java signature but have different erased return types.
     * Ordinary Java methods retain {@link #method(SymbolId, String, List)} identity.
     */
    public static SymbolId binaryMethod(
        SymbolId owner,
        String name,
        List<String> erasedParameterTypes,
        String erasedReturnType
    ) {
        String result = "void".equals(erasedReturnType) ? "void" : normalizeParameterType(erasedReturnType);
        return child(method(owner, name, erasedParameterTypes), "return", result);
    }

    public static SymbolId constructor(SymbolId owner, List<String> erasedParameterTypes) {
        return callable(owner, "constructor", "<init>", erasedParameterTypes);
    }

    public static SymbolId parameter(SymbolId callable, int index) {
        return child(callable, "parameter", ordinal(index));
    }

    public static SymbolId typeParameter(SymbolId owner, int index) {
        return child(owner, "typeParameter", ordinal(index));
    }

    public static SymbolId local(SymbolId owner, String lexicalPath, String name) {
        return child(child(owner, "local", requireName(lexicalPath)), "name", requireName(name));
    }

    public static SymbolId localType(SymbolId owner, String lexicalPath, String name) {
        return child(child(owner, "localType", requireName(lexicalPath)), "name", requireName(name));
    }

    public static SymbolId anonymous(SymbolId owner, String lexicalPath) {
        return child(owner, "anonymous", requireName(lexicalPath));
    }

    /**
     * Normalizes already resolved type spelling for overload identity. Generic arguments are erased,
     * varargs become arrays, and whitespace is ignored. This does not resolve simple names or bounds.
     */
    public static String normalizeParameterType(String spelling) {
        String value = requireName(spelling).replace("...", "[]");
        var erased = new StringBuilder();
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                if (--depth < 0)
                    throw new IllegalArgumentException("Unbalanced generic type: " + spelling);
            } else if (depth == 0 && !Character.isWhitespace(character)) {
                erased.append(character == '/' ? '.' : character);
            }
        }
        if (depth != 0 || erased.isEmpty())
            throw new IllegalArgumentException("Invalid parameter type: " + spelling);
        String normalized = erased.toString();
        if (!PARAMETER_TYPE.matcher(normalized).matches()
            || normalized.equals("void"))
            throw new IllegalArgumentException("Parameter type must be a resolved erased Java type: " + spelling);
        return normalized;
    }

    /** Computes a local anchor without using source offsets or trivia token positions. */
    public static String lexicalPath(SyntaxNode declaration, SyntaxNode owner) {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(owner, "owner");
        List<String> path = new ArrayList<>();
        SyntaxNode current = declaration;
        while (current != owner) {
            SyntaxNode parent = current.parent().orElseThrow(
                () -> new IllegalArgumentException("Declaration must descend from its owner"));
            int ordinal = 0;
            boolean found = false;
            for (SyntaxNode sibling : parent.children()) {
                if (sibling == current) {
                    found = true;
                    break;
                }
                if (!isTrivia(sibling)) {
                    ordinal++;
                }
            }
            if (!found)
                throw new IllegalArgumentException("Inconsistent syntax parent");
            path.add(Integer.toString(ordinal));
            current = parent;
        }
        Collections.reverse(path);
        return path.isEmpty() ? "self" : String.join(".", path);
    }

    private static boolean isTrivia(SyntaxNode node) {
        return switch (node.kind().id()) {
            case "JAVA_TOKEN_WHITESPACE", "JAVA_TOKEN_LINE_TERMINATOR", "JAVA_TOKEN_LINE_COMMENT",
                "JAVA_TOKEN_BLOCK_COMMENT", "JAVA_TOKEN_JAVADOC_COMMENT" -> true;
            default -> false;
        };
    }

    private static SymbolId callable(SymbolId owner, String kind, String name, List<String> types) {
        Objects.requireNonNull(types, "types");
        String signature = String.join(",", types.stream().map(JavaSymbolIds::normalizeParameterType).toList());
        return child(child(owner, kind, name), "parameters", signature);
    }

    private static SymbolId child(SymbolId owner, String kind, String value) {
        return new SymbolId(Objects.requireNonNull(owner, "owner").value() + "/" + kind + "/" + part(value));
    }

    private static String ordinal(int index) {
        if (index < 0)
            throw new IllegalArgumentException("Declaration ordinal cannot be negative");
        return Integer.toString(index);
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank())
            throw new IllegalArgumentException("Name cannot be blank");
        return name;
    }

    private static String part(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
