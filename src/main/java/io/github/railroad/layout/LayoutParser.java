package io.github.railroad.layout;

import io.github.railroad.utility.NodeTree;
import io.github.railroad.utility.NodeTree.Node;
import javafx.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class LayoutParser {
    public static Layout parse(Path file) throws LayoutParseException {
        try {
            return parse(Files.readString(file));
        } catch (IOException exception) {
            throw new LayoutParseException("Failed to read path: " + file.toAbsolutePath(), exception);
        }
    }

    public static Layout parse(byte[] content) throws LayoutParseException {
        return parse(new String(content));
    }

    public static Layout parse(InputStream input) throws LayoutParseException {
        try {
            return parse(input.readAllBytes());
        } catch (IOException exception) {
            throw new LayoutParseException("Failed to read input stream", exception);
        }
    }

    public static Layout parse(File file) throws LayoutParseException {
        try {
            return parse(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new LayoutParseException("Failed to read file: " + file, exception);
        }
    }

    private static Layout parse(String string) throws LayoutParseException {
        List<Token> tokens = tokenize(string);
        NodeTree<LayoutItem> tree = constructTree(tokens);
        return new Layout(tree);
    }

    // TODO: When exceptions are thrown, give the line number and column number
    public static NodeTree<LayoutItem> constructTree(List<Token> tokens) throws LayoutParseException {
        if (tokens.isEmpty()) {
            throw new LayoutParseException("The layout is empty");
        }

        Token token = tokens.removeFirst();
        if (token.type() != Token.Type.IDENTIFIER) {
            throw new LayoutParseException("Expected identifier, but got: " + token.value());
        }

        var item = new LayoutItem(token.value());
        var tree = new NodeTree<>(new Node<>(item));

        Stack<Node<LayoutItem>> stack = new Stack<>();
        stack.push(tree.getRoot());

        Node<LayoutItem> parent = stack.peek();
        while (!tokens.isEmpty()) {
            token = tokens.removeFirst();
            switch (token.type()) {
                case OPEN_BRACE -> {
                    parent = stack.peek();
                }
                case CLOSE_BRACE -> {
                    stack.pop();
                    if(stack.isEmpty()) {
                        return tree;
                    }
                }
                case COMMA -> {
                    if (parent.getChildren().isEmpty() && parent.getValue().getProperties().isEmpty()) {
                        tree.print();
                        throw new LayoutParseException("Unexpected comma");
                    }

                    parent = stack.peek();
                }
                case PROPERTY_OBJECT -> { // TODO: Handle non-layout objects
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property object: " + token.value());
                    }

                    NodeTree<LayoutItem> subNodeTree = constructTree(tokenize(parts[1]));
                    parent.getValue().setProperty(parts[0], subNodeTree.getRoot().getValue());
                }
                case PROPERTY_ARRAY -> { // TODO: Handle nested arrays or objects and convert types
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property array: " + token.value());
                    }

                    parent.getValue().setProperty(parts[0], parts[1]);
                }
                case PROPERTY_STRING -> {
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property string: " + token.value());
                    }

                    parent.getValue().setProperty(parts[0], parts[1].substring(1, parts[1].length() - 1).replace("\\\\", "\\"));
                }
                case PROPERTY_NUMBER -> {
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property number: " + token.value());
                    }

                    try {
                        parent.getValue().setProperty(parts[0], Double.parseDouble(parts[1]));
                    } catch (NumberFormatException exception) {
                        tree.print();
                        throw new LayoutParseException("Invalid number: " + parts[1], exception);
                    }
                }
                case PROPERTY_BOOLEAN -> {
                    String[] parts = token.value().split(":");
                    if (parts.length != 2) {
                        tree.print();
                        throw new LayoutParseException("Invalid property boolean: " + token.value());
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
                        throw new LayoutParseException("Unmatched opening brace");
                    }
                }
                default -> {
                    tree.print();
                    throw new LayoutParseException("Unexpected token: (" + token.type() + ", " + token.value() + ")");
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
            if (content.startsWith(" ") || content.startsWith("\n") || content.startsWith("\r") || content.startsWith("\t")) {
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
                // first check to see if its a property
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
                        throw new LayoutParseException("Invalid identifier: '" + content + "' at line " + line + " column " + column);

                    tokens.add(new Token(Token.Type.IDENTIFIER, content.substring(0, definitiveEnd).trim(), line, column, line, column + definitiveEnd));
                    column += definitiveEnd;
                    content = content.substring(definitiveEnd).trim();
                }
            } else if (Character.isDigit(content.charAt(0))) {
                // look for the end of the number by finding a %
                int end = content.indexOf("%");
                if (end == -1)
                    throw new LayoutParseException("Invalid number: '" + content + "' at line " + line + " column " + column);

                tokens.add(new Token(Token.Type.PERCENT, content.substring(0, end + 1).trim(), line, column, line, column + end));
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

    private static Pair<Token, String> extractProperty(String content, int line, int column) throws LayoutParseException {
        int colonIndex = content.indexOf(":");
        String property = content.substring(0, colonIndex).trim();
        String value = content.substring(colonIndex + 1).trim();
        if (value.startsWith("{")) {
            int endIndex = findClosingBraceIndex(value);
            if (endIndex != -1) {
                String subContent = value.substring(0, endIndex + 1).trim();
                return new Pair<>(
                        new Token(Token.Type.PROPERTY_OBJECT, property + ":" + subContent, line, column, line, column + colonIndex + endIndex),
                        value.substring(endIndex + 1).trim());
            } else {
                throw new LayoutParseException("Invalid property value: '" + value + "' at line " + line + " column " + column);
            }
        } else if (value.startsWith("[")) {
            int endIndex = findClosingBracketIndex(value);
            if (endIndex != -1) {
                String subContent = value.substring(0, endIndex + 1).trim();
                return new Pair<>(
                        new Token(Token.Type.PROPERTY_ARRAY, property + ":" + subContent, line, column, line, column + colonIndex + endIndex),
                        value.substring(endIndex + 1).trim());
            } else {
                throw new LayoutParseException("Invalid property value: '" + value + "' at line " + line + " column " + column);
            }
        } else if (Character.isDigit(value.charAt(0))) {
            String number = extractNumber(value);
            return new Pair<>(
                    new Token(Token.Type.PROPERTY_NUMBER, property + ":" + number, line, column, line, column + colonIndex + number.length()),
                    value.substring(value.indexOf(number) + number.length()).trim());
        } else if (value.startsWith("\"")) {
            int endIndex = value.indexOf("\"", 1);
            if (endIndex != -1) {
                String subContent = value.substring(0, endIndex + 1).replace("\\", "\\\\").trim();
                return new Pair<>(
                        new Token(Token.Type.PROPERTY_STRING, property + ":" + subContent, line, column, line, column + colonIndex + endIndex),
                        value.substring(endIndex + 1).trim());
            } else {
                throw new LayoutParseException("Invalid property value: '" + value + "' at line " + line + " column " + column);
            }
        } else if (value.startsWith("true") || value.startsWith("false")) {
            int beginIndex = value.contains(" ") ? value.indexOf(" ") : value.length();
            return new Pair<>(
                    new Token(Token.Type.PROPERTY_BOOLEAN, property + ":" + value.substring(0, beginIndex), line, column, line, column + colonIndex + beginIndex),
                    value.substring(beginIndex).trim());
        } else {
            throw new LayoutParseException("Invalid property value: '" + value + "' at line " + line + " column " + column);
        }
    }

    private static int findClosingBraceIndex(String content) {
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '{') {
                count++;
            } else if (content.charAt(i) == '}') {
                count--;
            }
            if (count == 0) {
                return i;
            }
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
            if (count == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String extractNumber(String content) {
        var sb = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static int indexOfAny(String content, String chars) {
        for (int i = 0; i < content.length(); i++) {
            if (chars.indexOf(content.charAt(i)) != -1) {
                return i;
            }
        }

        return -1;
    }

    public record Token(Type type, String value, int startLine, int startColumn, int endLine, int endColumn) {
        public enum Type {
            OPEN_BRACE("{"),
            CLOSE_BRACE("}"),
            COMMA(","),
            PERCENT("%"),
            IDENTIFIER(""),
            PROPERTY_OBJECT(":"),
            PROPERTY_ARRAY(":"),
            PROPERTY_STRING(":"),
            PROPERTY_NUMBER(":"),
            PROPERTY_BOOLEAN(":"),
            EOF("");

            private final String value;

            Type(String value) {
                this.value = value;
            }

            public String getValue() {
                return value;
            }
        }
    }
}
