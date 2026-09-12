package dev.railroadide.railroad.ide.syntaxhighlighting;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.treesitter.TSInputEncoding;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TreeSitterJava;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Builds Java syntax styles from parser tokens and their declaration context. */
public final class TreeSitterJavaSyntaxHighlighting {
    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "exports", "extends", "final", "finally",
        "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "@interface",
        "long", "module", "native", "new", "non-sealed", "open", "opens", "package", "permits", "private",
        "protected", "provides", "public", "record", "requires", "return", "sealed", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "to", "transient",
        "transitive", "try", "uses", "var", "void", "volatile", "when", "while", "with", "yield");
    private static final Set<String> OPERATORS = Set.of(
        "=", "+", "-", "*", "/", "%", "++", "--", "==", "!=", "<", ">", "<=", ">=", "&&", "||",
        "!", "~", "&", "|", "^", "<<", ">>", ">>>", "+=", "-=", "*=", "/=", "%=", "&=", "|=",
        "^=", "<<=", ">>=", ">>>=", "?", ":", "->", "::");
    private static final Set<String> PUNCTUATION = Set.of("(", ")", "[", "]", "{", "}", ",", ";", ".", "...");
    private static final Set<String> TYPE_DECLARATIONS = Set.of(
        "class_declaration", "interface_declaration", "enum_declaration", "record_declaration",
        "annotation_type_declaration");
    private static final Set<String> SCOPES = Set.of(
        "program", "class_body", "interface_body", "enum_body", "annotation_type_body", "block",
        "method_declaration", "constructor_declaration", "compact_constructor_declaration", "lambda_expression",
        "for_statement", "enhanced_for_statement", "catch_clause", "try_with_resources_statement", "switch_rule");
    private static final Pattern DOC_TAG = Pattern.compile("(?m)(?<=\\{)@[A-Za-z]+|^[\\t ]*\\*?[\\t ]*(@[A-Za-z]+)");
    private static final Pattern DOC_REFERENCE = Pattern
        .compile("\\{@(?:link|linkplain|value)\\s+([^\\s}]+)|@param\\s+(<[^>]+>|[\\w$]+)");

    private TreeSitterJavaSyntaxHighlighting() {
    }

    /**
     * Computes spans in JavaFX's UTF-16 coordinates, including whitespace and incomplete code.
     *
     * @param text Java source text to highlight
     * @return syntax style classes covering the entire source in UTF-16 code units
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        var parser = new TSParser();
        parser.setLanguage(new TreeSitterJava());
        byte[] encoded = text.getBytes(StandardCharsets.UTF_16LE);
        var tree = parser.parse(new byte[4096], null, (buffer, offset, _) -> {
            int length = Math.min(buffer.length, encoded.length - offset);
            if (length <= 0)
                return 0;
            System.arraycopy(encoded, offset, buffer, 0, length);
            return length;
        }, TSInputEncoding.TSInputEncodingUTF16LE);
        var leaves = new ArrayList<TSNode>();
        var pending = new ArrayDeque<TSNode>();
        pending.push(tree.getRootNode());
        while (!pending.isEmpty()) {
            TSNode node = pending.pop();
            if (node.getChildCount() == 0) {
                if (!node.isMissing() && node.getEndByte() > node.getStartByte()) {
                    leaves.add(node);
                }
            } else {
                for (int i = node.getChildCount() - 1; i >= 0; i--) {
                    pending.push(node.getChild(i));
                }
            }
        }
        var symbols = new HashMap<String, List<Symbol>>();
        for (TSNode node : leaves) {
            if (!node.getType().equals("identifier"))
                continue;
            String role = declarationRole(node);
            if (role == null)
                continue;
            TSNode scope = scopeFor(node, role);
            symbols.computeIfAbsent(content(node, text), _ -> new ArrayList<>()).add(
                new Symbol(role, scope.getStartByte(), scope.getEndByte(), node.getStartByte()));
        }
        var builder = new StyleSpansBuilder<Collection<String>>();
        int position = 0;
        for (TSNode node : leaves) {
            int start = node.getStartByte() / 2;
            int end = node.getEndByte() / 2;
            if (start > position) {
                builder.add(List.of(), start - position);
            }
            String token = content(node, text);
            if (node.getType().equals("block_comment") && token.startsWith("/**")) {
                appendJavadoc(builder, token);
            } else if (node.getType().equals("character_literal") && token.startsWith("'\\")) {
                builder.add(List.of("string"), 1);
                builder.add(List.of("escape"), token.length() - 2);
                builder.add(List.of("string"), 1);
            } else {
                builder.add(List.of(style(node, token, symbols)), end - start);
            }
            position = end;
        }
        if (position < text.length() || text.isEmpty()) {
            builder.add(List.of(), text.length() - position);
        }
        return builder.create();
    }

    private static String style(TSNode node, String token, Map<String, List<Symbol>> symbols) {
        String type = node.getType();
        if (type.equals("line_comment") || type.equals("block_comment"))
            return "comment";
        if (type.equals("escape_sequence"))
            return "escape";
        if (type.equals("character_literal"))
            return "string";
        if (type.contains("string_fragment") || token.equals("\"") || token.equals("\"\"\""))
            return "string";
        if (type.endsWith("integer_literal") || type.endsWith("floating_point_literal"))
            return "number";
        if (Set.of("true", "false", "null").contains(token))
            return "literal";
        if (type.equals("identifier") || type.equals("type_identifier")) {
            // Contextual keywords can also be legal ordinary identifiers.
            if (type.equals("type_identifier") && token.equals("var"))
                return "keyword";
            return identifierRole(node, token, symbols);
        }
        if (KEYWORDS.contains(token))
            return "keyword";
        if (token.equals("@"))
            return "annotation";
        if (token.equals("_"))
            return "variable";
        if (OPERATORS.contains(token))
            return "operator";
        if (PUNCTUATION.contains(token))
            return "punctuation";
        return "name";
    }

    private static String identifierRole(TSNode node, String token, Map<String, List<Symbol>> symbols) {
        String declaration = declarationRole(node);
        if (declaration != null)
            return declaration;
        TSNode parent = node.getParent();
        String parentType = parent.getType();
        for (TSNode ancestor = parent; !ancestor.isNull(); ancestor = ancestor.getParent()) {
            String type = ancestor.getType();
            if ((type.equals("annotation") || type.equals("marker_annotation")) && inField(node, ancestor, "name"))
                return "annotation";
            if (type.equals("package_declaration"))
                return "namespace";
            if (type.startsWith("module_") || type.equals("module_declaration"))
                return type.equals("module_uses_directive") || type.equals("module_provides_directive")
                    ? "type"
                    : "namespace";
            if (type.equals("import_declaration")) {
                TSNode imported = ancestor.getNamedChild(0);
                boolean wildcard = false;
                boolean isStatic = false;
                for (int i = 0; i < ancestor.getChildCount(); i++) {
                    String childType = ancestor.getChild(i).getType();
                    wildcard |= childType.equals("asterisk");
                    isStatic |= childType.equals("static");
                }
                if (!wildcard && (TSNode.eq(node, imported) || inField(node, imported, "name")))
                    return isStatic ? "field" : "type";
                return "namespace";
            }
        }
        if (node.getType().equals("type_identifier"))
            return "type";
        if (parentType.equals("method_invocation") && inField(node, parent, "name"))
            return "method";
        if (parentType.equals("method_reference") && node.getNextNamedSibling().isNull())
            return "method";
        if (parentType.equals("element_value_pair") && inField(node, parent, "key"))
            return "annotation";
        if (Set.of("labeled_statement", "break_statement", "continue_statement").contains(parentType))
            return "label";
        boolean member = parentType.equals("field_access") && inField(node, parent, "field");
        Symbol best = null;
        for (Symbol symbol : symbols.getOrDefault(token, List.of())) {
            // Calls and method references were handled above; a same-named
            // method must not colour a variable reference as a method.
            if (symbol.role().equals("method") || symbol.role().equals("constructor"))
                continue;
            if (node.getStartByte() < symbol.start() || node.getEndByte() > symbol.end())
                continue;
            if ((symbol.role().equals("variable") || symbol.role().equals("parameter"))
                && node.getStartByte() < symbol.declaration())
                continue;
            if (member && !Set.of("field", "constant").contains(symbol.role()))
                continue;
            if (best == null || symbol.end() - symbol.start() < best.end() - best.start()) {
                best = symbol;
            }
        }
        if (best != null)
            return best.role();
        return member ? "field" : "variable";
    }

    private static String declarationRole(TSNode node) {
        TSNode parent = node.getParent();
        if (parent.isNull())
            return null;
        String type = parent.getType();
        if (inField(node, parent, "name")) {
            if (TYPE_DECLARATIONS.contains(type))
                return "type";
            if (type.equals("method_declaration") || type.equals("annotation_type_element_declaration"))
                return "method";
            if (type.equals("constructor_declaration") || type.equals("compact_constructor_declaration"))
                return "constructor";
            if (type.equals("enum_constant"))
                return "constant";
            if (Set.of("formal_parameter", "spread_parameter", "catch_formal_parameter", "record_pattern_component")
                .contains(type))
                return "parameter";
            if (type.equals("enhanced_for_statement") || type.equals("resource"))
                return "variable";
            if (type.equals("variable_declarator")) {
                TSNode owner = parent.getParent();
                if (owner.getType().equals("spread_parameter"))
                    return "parameter";
                if (owner.getType().equals("field_declaration") || owner.getType().equals("constant_declaration")) {
                    boolean isStatic = false;
                    boolean isFinal = false;
                    for (int i = 0; i < owner.getChildCount(); i++) {
                        TSNode child = owner.getChild(i);
                        if (!child.getType().equals("modifiers"))
                            continue;
                        for (int j = 0; j < child.getChildCount(); j++) {
                            isStatic |= child.getChild(j).getType().equals("static");
                            isFinal |= child.getChild(j).getType().equals("final");
                        }
                    }
                    return owner.getType().equals("constant_declaration") || (isStatic && isFinal)
                        ? "constant"
                        : "field";
                }
                return "variable";
            }
        }
        if (type.equals("inferred_parameters")
            || (type.equals("lambda_expression") && inField(node, parent, "parameters")))
            return "parameter";
        return null;
    }

    private static TSNode scopeFor(TSNode node, String role) {
        TSNode scope = node.getParent();
        if (Set.of("type", "method", "constructor").contains(role)) {
            scope = scope.getParent();
        }
        for (; !scope.getParent().isNull(); scope = scope.getParent()) {
            if (SCOPES.contains(scope.getType()))
                break;
            if (scope.getType().equals("record_declaration") && role.equals("parameter"))
                break;
        }
        return scope;
    }

    private static boolean inField(TSNode node, TSNode parent, String field) {
        TSNode value = parent.getChildByFieldName(field);
        return !value.isNull() && node.getStartByte() >= value.getStartByte()
            && node.getEndByte() <= value.getEndByte();
    }

    private static String content(TSNode node, String text) {
        return text.substring(node.getStartByte() / 2, node.getEndByte() / 2);
    }

    private static void appendJavadoc(StyleSpansBuilder<Collection<String>> builder, String text) {
        var ranges = new ArrayList<DocRange>();
        var tags = DOC_TAG.matcher(text);
        while (tags.find()) {
            int start = tags.group(1) == null ? tags.start() : tags.start(1);
            ranges.add(new DocRange(start, tags.end(), "doc-tag"));
        }
        var references = DOC_REFERENCE.matcher(text);
        while (references.find()) {
            int group = references.group(1) == null ? 2 : 1;
            ranges.add(new DocRange(references.start(group), references.end(group),
                group == 1 ? "doc-link" : "doc-parameter"));
        }
        ranges.sort(Comparator.comparingInt(DocRange::start));
        int position = 0;
        for (DocRange range : ranges) {
            if (range.start() < position)
                continue;
            if (range.start() > position) {
                builder.add(List.of("comment"), range.start() - position);
            }
            builder.add(List.of("comment", range.role()), range.end() - range.start());
            position = range.end();
        }
        if (position < text.length()) {
            builder.add(List.of("comment"), text.length() - position);
        }
    }

    private record Symbol(String role, int start, int end, int declaration) {
    }

    private record DocRange(int start, int end, String role) {
    }
}
