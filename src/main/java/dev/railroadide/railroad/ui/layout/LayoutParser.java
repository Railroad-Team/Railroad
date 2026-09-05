package dev.railroadide.railroad.ui.layout;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.Tree;
import dev.railroadide.railroad.utility.Tree.Node;
import javafx.util.Pair;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/** Parses Railroad layout files into a tree of named items and their properties. */
public class LayoutParser {
    /** Creates a stateless parser; layout parsing operations are exposed as static methods. */
    public LayoutParser() {
    }

    /**
     * Reads a UTF-8 layout file and parses its contents.
     *
     * @param file path to the layout file
     * @return the parsed layout
     * @throws LayoutParseException if the file cannot be read or the layout cannot be parsed
     */
    public static Layout parse(Path file) throws LayoutParseException {
        try {
            return parse(Files.readString(file));
        } catch (IOException exception) {
            throw new LayoutParseException("Failed to read path: " + file.toAbsolutePath(), exception);
        }
    }

    /**
     * Parses layout contents encoded as UTF-8.
     *
     * @param content encoded layout text
     * @return the parsed layout
     * @throws LayoutParseException if the layout cannot be parsed
     */
    public static Layout parse(byte[] content) throws LayoutParseException {
        return parse(new String(content, StandardCharsets.UTF_8));
    }

    /**
     * Reads the remaining bytes of a stream as a UTF-8 layout. The caller retains responsibility for closing it.
     *
     * @param input stream positioned at the layout contents
     * @return the parsed layout
     * @throws LayoutParseException if the stream cannot be read or the layout cannot be parsed
     */
    public static Layout parse(InputStream input) throws LayoutParseException {
        try {
            return parse(input.readAllBytes());
        } catch (IOException exception) {
            throw new LayoutParseException("Failed to read input stream", exception);
        }
    }

    /**
     * Reads a UTF-8 layout file and parses its contents.
     *
     * @param file layout file to read
     * @return the parsed layout
     * @throws LayoutParseException if the file cannot be read or the layout cannot be parsed
     */
    public static Layout parse(File file) throws LayoutParseException {
        try {
            return parse(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new LayoutParseException("Failed to read file: " + file, exception);
        }
    }

    private static Layout parse(String string) throws LayoutParseException {
        List<Token> tokens = tokenize(string);
        Tree<LayoutItem> tree = constructTree(tokens);
        return new Layout(tree);
    }

    /**
     * Builds an item hierarchy by consuming tokens from the front of a mutable list.
     * Parsing stops when the root closes or the list is exhausted; any trailing tokens after the root remain.
     *
     * @param tokens mutable token sequence beginning with the root identifier
     * @return the constructed item tree
     * @throws LayoutParseException if the sequence is empty or contains an invalid token or property
     */
    public static Tree<LayoutItem> constructTree(List<Token> tokens) throws LayoutParseException {
        if (tokens.isEmpty())
            throw new LayoutParseException("The layout is empty");

        Token token = tokens.removeFirst();
        if (token.type() != Token.Type.IDENTIFIER)
            throw new LayoutParseException("Expected identifier, but got: '" + token.value() + "' at line "
                + token.startLine() + " column " + token.startColumn());

        var item = new LayoutItem(token.value());
        var tree = new Tree<>(new Node<>(item));

        Stack<Node<LayoutItem>> stack = new Stack<>();
        stack.push(tree.getRoot());

        Node<LayoutItem> parent = stack.peek();
        while (!tokens.isEmpty()) {
            token = tokens.removeFirst();
            switch (token.type()) {
                case OPEN_BRACE -> parent = stack.peek();
                case CLOSE_BRACE -> {
                    stack.pop();
                    if (stack.isEmpty())
                        return tree;
                }
                case COMMA -> {
                    if (parent.getChildren().isEmpty() && parent.getValue().getProperties().isEmpty()) {
                        tree.print();
                        throw new LayoutParseException(
                            "Unexpected comma at line " + token.startLine() + " column " + token.startColumn());
                    }

                    parent = stack.peek();
                }
                case PROPERTY_OBJECT -> { // TODO: Handle non-layout objects
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property object: '" + token.value() + "' at line "
                            + token.startLine() + " column " + token.startColumn());
                    }

                    Tree<LayoutItem> subTree = constructTree(tokenize(parts[1]));
                    parent.getValue().setProperty(parts[0], subTree.getRoot().getValue());
                }
                case PROPERTY_ARRAY -> { // TODO: Handle nested arrays or objects and convert types
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property array: '" + token.value() + "' at line "
                            + token.startLine() + " column " + token.startColumn());
                    }

                    parent.getValue().setProperty(parts[0], parts[1]);
                }
                case PROPERTY_STRING -> {
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property string: '" + token.value() + "' at line "
                            + token.startLine() + " column " + token.startColumn());
                    }

                    parent.getValue().setProperty(parts[0],
                        parts[1].substring(1, parts[1].length() - 1).replace("\\\\", "\\"));
                }
                case PROPERTY_NUMBER -> {
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property number: '" + token.value() + "' at line "
                            + token.startLine() + " column " + token.startColumn());
                    }

                    try {
                        parent.getValue().setProperty(parts[0], Double.parseDouble(parts[1]));
                    } catch (NumberFormatException exception) {
                        tree.print();
                        throw new LayoutParseException("Invalid number: '" + parts[1] + "' at line " + token.startLine()
                            + " column " + token.startColumn(), exception);
                    }
                }
                case PROPERTY_BOOLEAN -> {
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property boolean: '" + token.value() + "' at line "
                            + token.startLine() + " column " + token.startColumn());
                    }

                    parent.getValue().setProperty(parts[0], Boolean.parseBoolean(parts[1]));
                }
                case PERCENT -> parent.getValue().setProperty("size", token.value());
                case IDENTIFIER -> {
                    var child = new Node<>(new LayoutItem(token.value()));
                    parent.getChildren().add(child);
                    parent = child;
                    stack.push(parent);
                }
                case EOF -> {
                    if (stack.size() != 1) {
                        tree.print();
                        throw new LayoutParseException("Unmatched opening brace '{' at line " + token.startLine()
                            + " column " + token.startColumn());
                    }
                }
                default -> {
                    tree.print();
                    throw new LayoutParseException("Unexpected token: (" + token.type() + ", " + token.value()
                        + ") at line " + token.startLine() + " column " + token.startColumn());
                }
            }
        }

        return tree;
    }

    private static List<Token> tokenize(String content) throws LayoutParseException {
        List<Token> tokens = new ArrayList<>();
        int line = 1;
        int column = 1;

        while (!content.isEmpty()) {
            if (content.startsWith(" ") || content.startsWith("\n") || content.startsWith("\r")
                || content.startsWith("\t")) {
                if (content.startsWith("\n")) {
                    line++;
                    column = 1;
                } else if (!content.startsWith("\r")) {
                    column++;
                }

                content = content.substring(1);
            } else if (content.startsWith("{")) {
                tokens.add(new Token(Token.Type.OPEN_BRACE, "{", line, column, line, column));
                column++;
                content = content.substring(1);
            } else if (content.startsWith("}")) {
                tokens.add(new Token(Token.Type.CLOSE_BRACE, "}", line, column, line, column));
                column++;
                content = content.substring(1);
            } else if (content.startsWith(",")) {
                tokens.add(new Token(Token.Type.COMMA, ",", line, column, line, column));
                column++;
                content = content.substring(1);
            } else if (Character.isLetter(content.charAt(0))) {
                // first check to see if it's a property
                int colonIndex = content.indexOf(":");
                int definitiveEnd = indexOfAny(content, "{},%");

                if (colonIndex != -1 && (definitiveEnd == -1 || colonIndex < definitiveEnd)) {
                    Pair<Token, String> pair = extractProperty(content, line, column);
                    tokens.add(pair.getKey());

                    String original = content;
                    content = pair.getValue();

                    int newLine = line;
                    int newColumn = column;
                    for (int i = 0; i < original.length() - content.length(); i++) {
                        if (original.charAt(i) == '\n') {
                            newLine++;
                            newColumn = 1;
                        } else if (original.charAt(i) != '\r') {
                            newColumn++;
                        }
                    }

                    line = newLine;
                    column = newColumn;
                } else {
                    if (definitiveEnd == -1)
                        throw new LayoutParseException(
                            "Invalid identifier: '" + content + "' at line " + line + " column " + column);

                    tokens.add(new Token(Token.Type.IDENTIFIER, content.substring(0, definitiveEnd).trim(), line,
                        column, line, column + definitiveEnd));
                    column += definitiveEnd;
                    content = content.substring(definitiveEnd).trim();
                }
            } else if (Character.isDigit(content.charAt(0))) {
                // look for the end of the number by finding a %
                int end = content.indexOf("%");
                if (end == -1)
                    throw new LayoutParseException(
                        "Invalid number: '" + content + "' at line " + line + " column " + column);

                tokens.add(new Token(Token.Type.PERCENT, content.substring(0, end + 1).trim(), line, column, line,
                    column + end));
                column += end + 1;
                content = content.substring(end + 1).trim();
            } else if (content.contains(":")) {
                Pair<Token, String> pair = extractProperty(content, line, column);
                tokens.add(pair.getKey());

                String original = content;
                content = pair.getValue();

                int newLine = line;
                int newColumn = column;
                for (int i = 0; i < original.length() - content.length(); i++) {
                    if (original.charAt(i) == '\n') {
                        newLine++;
                        newColumn = 1;
                    } else if (original.charAt(i) != '\r') {
                        newColumn++;
                    }
                }

                line = newLine;
                column = newColumn;
            } else {
                content = content.substring(1);
            }
        }

        tokens.add(new Token(Token.Type.EOF, "", line, column, line, column));
        return tokens;
    }

    private static Pair<Token, String> extractProperty(String content, int line, int column)
        throws LayoutParseException {
        int colonIndex = content.indexOf(":");
        String property = content.substring(0, colonIndex).trim();
        String value = content.substring(colonIndex + 1).trim();
        if (value.startsWith("{")) {
            int endIndex = findClosingBraceIndex(value);
            if (endIndex != -1) {
                String subContent = value.substring(0, endIndex + 1).trim();
                return new Pair<>(
                    new Token(Token.Type.PROPERTY_OBJECT, property + ":" + subContent, line, column, line,
                        column + colonIndex + endIndex),
                    value.substring(endIndex + 1).trim());
            } else
                throw new LayoutParseException(
                    "Invalid property value: '" + value + "' at line " + line + " column " + column);
        } else if (value.startsWith("[")) {
            int endIndex = findClosingBracketIndex(value);
            if (endIndex != -1) {
                String subContent = value.substring(0, endIndex + 1).trim();
                return new Pair<>(
                    new Token(Token.Type.PROPERTY_ARRAY, property + ":" + subContent, line, column, line,
                        column + colonIndex + endIndex),
                    value.substring(endIndex + 1).trim());
            } else
                throw new LayoutParseException(
                    "Invalid property value: '" + value + "' at line " + line + " column " + column);
        } else if (Character.isDigit(value.charAt(0))) {
            String number = extractNumber(value);
            return new Pair<>(
                new Token(Token.Type.PROPERTY_NUMBER, property + ":" + number, line, column, line,
                    column + colonIndex + number.length()),
                value.substring(value.indexOf(number) + number.length()).trim());
        } else if (value.startsWith("\"")) {
            int endIndex = value.indexOf("\"", 1);
            if (endIndex != -1) {
                String subContent = value.substring(0, endIndex + 1).replace("\\", "\\\\").trim();
                return new Pair<>(
                    new Token(Token.Type.PROPERTY_STRING, property + ":" + subContent, line, column, line,
                        column + colonIndex + endIndex),
                    value.substring(endIndex + 1).trim());
            } else
                throw new LayoutParseException(
                    "Invalid property value: '" + value + "' at line " + line + " column " + column);
        } else if (value.startsWith("true") || value.startsWith("false")) {
            int beginIndex = value.contains(" ") ? value.indexOf(" ") : value.length();
            return new Pair<>(
                new Token(Token.Type.PROPERTY_BOOLEAN, property + ":" + value.substring(0, beginIndex), line, column,
                    line, column + colonIndex + beginIndex),
                value.substring(beginIndex).trim());
        } else
            throw new LayoutParseException(
                "Invalid property value: '" + value + "' at line " + line + " column " + column);
    }

    private static int findClosingBraceIndex(String content) {
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '{') {
                count++;
            } else if (content.charAt(i) == '}') {
                count--;
            }

            if (count == 0)
                return i;
        }

        return -1;
    }

    private static int findClosingBracketIndex(String content) {
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '[') {
                count++;
            } else if (content.charAt(i) == ']') {
                count--;
            }

            if (count == 0)
                return i;
        }

        return -1;
    }

    private static String extractNumber(String content) {
        var sb = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+') {
                sb.append(c);
            } else
                break;
        }

        return sb.toString();
    }

    private static int indexOfAny(String content, String chars) {
        for (int i = 0; i < content.length(); i++) {
            if (chars.indexOf(content.charAt(i)) != -1)
                return i;
        }

        return -1;
    }

    /**
     * Loads {@code .railroad/.railayout} beneath the project directory.
     * Read or parse failures are logged and represented by a layout with a single item named {@code error}.
     *
     * @param project project whose layout should be loaded
     * @return the parsed layout, or the error layout if loading fails
     */
    public static Layout loadLayout(Project project) {
        Path projectPath = project.getPath();
        Path layoutPath = projectPath.resolve(".railroad").resolve(".railayout");

        try {
            return parse(layoutPath);
        } catch (LayoutParseException exception) {
            Railroad.LOGGER.error("Failed to load layout for project: {}", project.getPath(), exception);
            return new Layout(new Tree<>(new Node<>(new LayoutItem("error"))));
        }
    }

    /**
     * A lexical token with source coordinates used in parse diagnostics.
     *
     * @param type token category
     * @param value token text or normalized property text
     * @param startLine one-based starting line
     * @param startColumn one-based starting column
     * @param endLine ending line recorded by the tokenizer
     * @param endColumn ending column recorded by the tokenizer
     */
    public record Token(Type type, String value, int startLine, int startColumn, int endLine, int endColumn) {
        /** Token categories recognized by the layout parser. */
        @Getter
        public enum Type {
            /** Opens a group of child items or properties. */
            OPEN_BRACE("{"),
            /** Closes the current group. */
            CLOSE_BRACE("}"),
            /** Separates sibling items. */
            COMMA(","),
            /** Percentage assigned to an item's size property. */
            PERCENT("%"),
            /** Name identifying a layout element. */
            IDENTIFIER(""),
            /** Property containing a nested object. */
            PROPERTY_OBJECT(
                ":"),
            /** Property containing an array, currently retained as text. */
            PROPERTY_ARRAY(":"),
            /** Property containing a quoted string. */
            PROPERTY_STRING(":"),
            /** Property parsed as a double-precision number. */
            PROPERTY_NUMBER(":"),
            /** Property parsed as a boolean. */
            PROPERTY_BOOLEAN(":"),
            /** Marks the end of the source text. */
            EOF("");

            /**
             * Representative punctuation for this category, or an empty string for variable text and EOF.
             *
             * @return the category's representative punctuation
             */
            private final String value;

            Type(String value) {
                this.value = value;
            }
        }
    }
}
