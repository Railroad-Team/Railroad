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
    private static final String NAMESPACE = "java:";
    private static final Pattern PARAMETER_TYPE = Pattern.compile(
        "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}.]*(\\[\\])*");
    private JavaSymbolIds() {
    }

    /**
     * Creates the identity of a named or unnamed Java module.
     *
     * @param name module name, or the empty string for the unnamed compilation context
     * @return canonical Java declaration identity
     */
    public static SymbolId module(String name) {
        return new SymbolId(NAMESPACE + "module/" + part(Objects.requireNonNull(name, "name")));
    }

    /**
     * Creates a package identity within a module.
     *
     * @param moduleName module name, or the empty string for the unnamed compilation context
     * @param name package name, or the empty string for the unnamed package
     * @return canonical Java declaration identity
     */
    public static SymbolId packageId(String moduleName, String name) {
        return new SymbolId(module(moduleName).value() + "/package/" + part(Objects.requireNonNull(name, "name")));
    }

    /**
     * Creates a type identity using its JVM binary name.
     *
     * @param qualifiedName nonblank JVM binary type name, using dollars for nested types
     * @return canonical Java declaration identity
     */
    public static SymbolId type(String qualifiedName) {
        return type("", qualifiedName);
    }

    /**
     * Creates a type identity using its JVM binary name.
     *
     * @param moduleName module name, or the empty string for the unnamed compilation context
     * @param qualifiedName nonblank JVM binary type name, using dollars for nested types
     * @return canonical Java declaration identity
     */
    public static SymbolId type(String moduleName, String qualifiedName) {
        return new SymbolId(module(moduleName).value() + "/type/" + part(requireName(qualifiedName).replace('/', '.')));
    }

    /**
     * Creates a field identity within its declaring type.
     *
     * @param owner identity of the containing declaration
     * @param name declaration name
     * @return canonical Java declaration identity
     */
    public static SymbolId field(SymbolId owner, String name) {
        return child(owner, "field", requireName(name));
    }

    /**
     * Creates a method identity from its name and erased parameter types.
     *
     * @param owner identity of the containing declaration
     * @param name declaration name
     * @param erasedParameterTypes resolved erased parameter types in declaration order
     * @return canonical Java declaration identity
     */
    public static SymbolId method(SymbolId owner, String name, List<String> erasedParameterTypes) {
        return callable(owner, "method", requireName(name), erasedParameterTypes);
    }

    /**
     * Identifies a JVM-only return-type overload. Providers use this only when multiple non-bridge
     * methods in the same class share a Java signature but have different erased return types.
     * Ordinary Java methods retain {@link #method(SymbolId, String, List)} identity.
     *
     * @param owner identity of the containing declaration
     * @param name declaration name
     * @param erasedParameterTypes resolved erased parameter types in declaration order
     * @param erasedReturnType resolved erased result type, including {@code void}
     * @return canonical Java declaration identity
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

    /**
     * Creates a constructor identity from its erased parameter types.
     *
     * @param owner identity of the containing declaration
     * @param erasedParameterTypes resolved erased parameter types in declaration order
     * @return canonical Java declaration identity
     */
    public static SymbolId constructor(SymbolId owner, List<String> erasedParameterTypes) {
        return callable(owner, "constructor", "<init>", erasedParameterTypes);
    }

    /**
     * Creates a value-parameter identity from its declaration ordinal.
     *
     * @param callable identity of the containing callable
     * @param index zero-based declaration ordinal
     * @return canonical Java declaration identity
     */
    public static SymbolId parameter(SymbolId callable, int index) {
        return child(callable, "parameter", ordinal(index));
    }

    /**
     * Creates a type-parameter identity from its declaration ordinal.
     *
     * @param owner identity of the containing declaration
     * @param index zero-based declaration ordinal
     * @return canonical Java declaration identity
     */
    public static SymbolId typeParameter(SymbolId owner, int index) {
        return child(owner, "typeParameter", ordinal(index));
    }

    /**
     * Creates a local-variable identity from its structural path and name.
     *
     * @param owner identity of the containing declaration
     * @param lexicalPath trivia-independent structural path relative to the owner
     * @param name declaration name
     * @return canonical Java declaration identity
     */
    public static SymbolId local(SymbolId owner, String lexicalPath, String name) {
        return child(child(owner, "local", requireName(lexicalPath)), "name", requireName(name));
    }

    /**
     * Creates a local-type identity from its structural path and name.
     *
     * @param owner identity of the containing declaration
     * @param lexicalPath trivia-independent structural path relative to the owner
     * @param name declaration name
     * @return canonical Java declaration identity
     */
    public static SymbolId localType(SymbolId owner, String lexicalPath, String name) {
        return child(child(owner, "localType", requireName(lexicalPath)), "name", requireName(name));
    }

    /**
     * Creates an anonymous-type identity from its structural path.
     *
     * @param owner identity of the containing declaration
     * @param lexicalPath trivia-independent structural path relative to the owner
     * @return canonical Java declaration identity
     */
    public static SymbolId anonymous(SymbolId owner, String lexicalPath) {
        return child(owner, "anonymous", requireName(lexicalPath));
    }

    /**
     * Normalizes already resolved type spelling for overload identity. Generic arguments are erased,
     * varargs become arrays, and whitespace is ignored. This does not resolve simple names or bounds.
     *
     * @param spelling resolved Java type spelling to erase and normalize
     * @return normalized erased type spelling with array rank preserved
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

    /**
     * Computes a local anchor without using source offsets or trivia token positions.
     *
     * @param declaration declaration node whose structural path is computed
     * @param owner ancestor declaration used as the structural anchor
     * @return structural path relative to the containing declaration
     */
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

    /**
     * Reports whether a syntax node is whitespace, a line terminator or a comment.
     *
     * @param node syntax node to inspect
     * @return whether the node is trivia rather than code
     */
    public static boolean isTrivia(SyntaxNode node) {
        return switch (node.kind().id()) {
            case "JAVA_TOKEN_WHITESPACE", "JAVA_TOKEN_LINE_TERMINATOR", "JAVA_TOKEN_LINE_COMMENT",
                "JAVA_TOKEN_BLOCK_COMMENT", "JAVA_TOKEN_JAVADOC_COMMENT" -> true;
            default -> false;
        };
    }

    /**
     * Splits a Java identity into its decoded steps, outermost first, for example module, type, method and
     * parameters.
     *
     * @param id identity created by this class
     * @return decoded steps in identity order
     * @throws IllegalArgumentException if the identity was not created by this class
     */
    public static List<Component> components(SymbolId id) {
        String value = Objects.requireNonNull(id, "id").value();
        if (!value.startsWith(NAMESPACE))
            throw new IllegalArgumentException("Not a Java symbol identity: " + value);
        String[] parts = value.substring(NAMESPACE.length()).split("/", -1);
        if (parts.length % 2 != 0)
            throw new IllegalArgumentException("Malformed Java symbol identity: " + value);
        List<Component> components = new ArrayList<>();
        for (int index = 0; index < parts.length; index += 2) {
            components.add(new Component(parts[index],
                new String(Base64.getUrlDecoder().decode(parts[index + 1]), StandardCharsets.UTF_8)));
        }
        return List.copyOf(components);
    }

    /**
     * Joins decoded steps back into an identity; the inverse of {@link #components(SymbolId)}.
     *
     * @param components decoded steps, outermost first
     * @return Java identity
     */
    public static SymbolId fromComponents(List<Component> components) {
        if (components.isEmpty())
            throw new IllegalArgumentException("A Java symbol identity needs at least one component");
        var value = new StringBuilder(NAMESPACE);
        for (Component component : components) {
            if (value.length() > NAMESPACE.length()) {
                value.append('/');
            }
            value.append(component.kind()).append('/').append(part(component.value()));
        }
        return new SymbolId(value.toString());
    }

    /**
     * One decoded step of a Java identity.
     *
     * @param kind step category, such as {@code type}, {@code method} or {@code parameters}
     * @param value decoded step value, such as a qualified type name or member name
     */
    public record Component(String kind, String value) {
        /**
         * Validates a decoded step.
         *
         * @param kind step category
         * @param value decoded step value
         */
        public Component {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(value, "value");
            if (kind.isEmpty() || kind.contains("/"))
                throw new IllegalArgumentException("Invalid identity component kind: " + kind);
        }
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
