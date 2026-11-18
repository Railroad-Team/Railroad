package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Syntax-only facts about the import declarations of one Java compilation unit.
 * <p>
 * Usage is decided conservatively from tokens: an import counts as used whenever its imported name appears as a
 * token outside the import declarations, including package annotations, or as a word in a Javadoc comment. An import reported as
 * unused can therefore be removed without changing what any name in the file refers to.
 */
public final class JavaImports {
    private static final String IMPORT_DECLARATION = "JAVA_IMPORT_DECLARATION";
    private static final String IMPORT_TARGET = "JAVA_IMPORT_TARGET";
    private static final String IMPORT_KEYWORD = "JAVA_TOKEN_IMPORT_KEYWORD";
    private static final String STATIC_KEYWORD = "JAVA_TOKEN_STATIC_KEYWORD";
    private static final String SEMICOLON = "JAVA_TOKEN_SEMICOLON";
    private static final String JAVADOC = "JAVA_TOKEN_JAVADOC_COMMENT";
    private static final Set<String> TRIVIA = Set.of("JAVA_TOKEN_WHITESPACE", "JAVA_TOKEN_LINE_COMMENT",
        "JAVA_TOKEN_BLOCK_COMMENT", JAVADOC);
    private static final Set<String> NON_NAME_TOKENS = Set.of("JAVA_TOKEN_STRING_LITERAL",
        "JAVA_TOKEN_TEXT_BLOCK_LITERAL", "JAVA_TOKEN_CHARACTER_LITERAL", "JAVA_TOKEN_EOF");

    private JavaImports() {
    }

    /**
     * One complete import declaration.
     *
     * @param node import declaration node, including leading trivia
     * @param start offset of the {@code import} keyword
     * @param end offset just after the terminating semicolon
     * @param target imported name without whitespace or comments, such as {@code java.util.List} or
     *        {@code java.util.*}
     * @param isStatic whether this is a static import
     */
    public record ImportDeclaration(SyntaxNode node, int start, int end, String target, boolean isStatic) {
        /**
         * Reports whether this import imports every member of a package or type.
         *
         * @return whether the target ends with {@code .*}
         */
        public boolean isWildcard() {
            return target.endsWith(".*");
        }

        /**
         * Returns the simple name this import makes available.
         *
         * @return last segment of the target, or {@code *} for a wildcard import
         */
        public String importedName() {
            return target.substring(target.lastIndexOf('.') + 1);
        }

        /**
         * Returns the package or type whose members this import names.
         *
         * @return target without its last segment
         */
        public String owner() {
            int separator = target.lastIndexOf('.');
            return separator < 0 ? "" : target.substring(0, separator);
        }
    }

    /**
     * Collects the complete import declarations of a compilation unit in source order. Declarations missing their
     * keyword, target or semicolon are skipped.
     *
     * @param root compilation unit
     * @return complete import declarations
     */
    public static List<ImportDeclaration> imports(SyntaxNode root) {
        List<ImportDeclaration> imports = new ArrayList<>();
        for (SyntaxNode child : root.children()) {
            if (!IMPORT_DECLARATION.equals(child.kind().id()))
                continue;
            ImportDeclaration declaration = declaration(child);
            if (declaration != null) {
                imports.add(declaration);
            }
        }
        return List.copyOf(imports);
    }

    /**
     * Finds single imports whose name is never used in the compilation unit. Wildcard imports are never reported.
     *
     * @param root compilation unit
     * @param check cooperative cancellation check
     * @return unused single imports in source order
     */
    public static List<ImportDeclaration> unusedImports(SyntaxNode root, Runnable check) {
        List<ImportDeclaration> imports = imports(root);
        if (imports.isEmpty())
            return List.of();

        Set<String> names = new HashSet<>();
        List<String> javadoc = new ArrayList<>();
        Deque<SyntaxNode> pending = new ArrayDeque<>(root.children());
        int visited = 0;
        while (!pending.isEmpty()) {
            if ((++visited & 255) == 0) {
                check.run();
            }
            SyntaxNode node = pending.removeFirst();
            String kind = node.kind().id();
            if (IMPORT_DECLARATION.equals(kind))
                continue;
            if (node instanceof SyntaxToken token) {
                if (JAVADOC.equals(kind)) {
                    javadoc.add(token.text());
                } else if (!TRIVIA.contains(kind) && !NON_NAME_TOKENS.contains(kind)) {
                    names.add(token.text());
                }
                continue;
            }
            node.children().reversed().forEach(pending::addFirst);
        }
        check.run();

        List<ImportDeclaration> unused = new ArrayList<>();
        for (ImportDeclaration declaration : imports) {
            if (declaration.isWildcard())
                continue;
            String name = declaration.importedName();
            if (names.contains(name))
                continue;
            Pattern word = Pattern.compile("(?<![\\p{javaJavaIdentifierPart}])" + Pattern.quote(name)
                + "(?![\\p{javaJavaIdentifierPart}])");
            if (javadoc.stream().anyMatch(comment -> word.matcher(comment).find()))
                continue;
            unused.add(declaration);
        }
        return List.copyOf(unused);
    }

    /**
     * Finds imports that repeat an earlier import of the same kind and target.
     *
     * @param root compilation unit
     * @return repeated imports in source order; the first occurrence of each import is never included
     */
    public static List<ImportDeclaration> duplicateImports(SyntaxNode root) {
        Set<String> seen = new HashSet<>();
        List<ImportDeclaration> duplicates = new ArrayList<>();
        for (ImportDeclaration declaration : imports(root)) {
            if (!seen.add((declaration.isStatic() ? "static " : "") + declaration.target())) {
                duplicates.add(declaration);
            }
        }
        return List.copyOf(duplicates);
    }

    private static @Nullable ImportDeclaration declaration(SyntaxNode node) {
        int keyword = -1;
        int semicolon = -1;
        boolean isStatic = false;
        String target = null;
        for (SyntaxNode child : node.children()) {
            String kind = child.kind().id();
            if (IMPORT_KEYWORD.equals(kind) && keyword < 0) {
                keyword = child.start();
            } else if (STATIC_KEYWORD.equals(kind)) {
                isStatic = true;
            } else if (IMPORT_TARGET.equals(kind)) {
                target = targetText(child);
            } else if (SEMICOLON.equals(kind)) {
                semicolon = child.end();
            }
        }
        if (keyword < 0 || semicolon < 0 || target == null || target.isEmpty() || target.startsWith(".")
            || target.endsWith(".") || target.contains(".."))
            return null;
        return new ImportDeclaration(node, keyword, semicolon, target, isStatic);
    }

    private static @Nullable String targetText(SyntaxNode target) {
        var text = new StringBuilder();
        Deque<SyntaxNode> pending = new ArrayDeque<>(target.children());
        while (!pending.isEmpty()) {
            SyntaxNode node = pending.removeFirst();
            if (node instanceof SyntaxToken token) {
                String kind = token.kind().id();
                if (TRIVIA.contains(kind))
                    continue;
                if (!kind.equals("JAVA_TOKEN_IDENTIFIER") && !kind.equals("JAVA_TOKEN_DOT")
                    && !kind.equals("JAVA_TOKEN_STAR"))
                    return null;
                text.append(token.text());
            } else {
                node.children().reversed().forEach(pending::addFirst);
            }
        }
        return text.toString();
    }
}
