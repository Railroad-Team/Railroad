package dev.railroadide.railroad.ide.sst;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.impl.java.JavaLexer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SSTTesting {
    private static final boolean INCLUDE_SPAN = false;

    public static void main(String[] args) throws IOException {
        System.out.println("SST Testing");

        String code = Files.readString(Path.of("E:\\Programming\\Java\\Railroad\\src\\main\\java\\dev\\railroadide\\railroad\\ide\\sst\\impl\\java\\JavaParser.java"));

        System.out.println("Code to parse:");
        System.out.println(code);

        try (var lexer = new JavaLexer(code)) {
            Lexer.Snapshot snapshot = lexer.snapshot();
            while (true) {
                Token<JavaTokenType> token = lexer.nextToken();
                System.out.println(token);
                if (token.type() == JavaTokenType.EOF)
                    break;
            }

            lexer.restore(snapshot);
            System.out.println("Lexer restored to snapshot:");

            var parser = new JavaParser(lexer);
            AstNode ast = parser.parse();
            System.out.println("Parsed AST:");
            try {
                Path path = Path.of("ast_output.txt");
                Files.deleteIfExists(path);
                printAst(ast, "", new PrintStream(Files.newOutputStream(path)));
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to write AST to file", exception);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printAst(AstNode node, String indent, PrintStream out) {
        if (node == null)
            return;

        out.println(indent + "- " + node.getClass().getSimpleName());

        for (Field field : node.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(node);
                String fieldIndent = indent + "  ";

                if (Objects.equals(field.getName(), "children"))
                    continue;

                if(!INCLUDE_SPAN && Objects.equals(field.getName(), "span"))
                    continue;

                if (value instanceof String[] || (value instanceof List<?> list && isListOfStrings(list))) {
                    List<String> strings = value instanceof String[] arr ? Arrays.asList(arr) : (List<String>) value;
                    out.println(fieldIndent + field.getName() + ": " + strings);
                } else if (value instanceof AstNode childNode) {
                    out.println(fieldIndent + field.getName() + ":");
                    printAst(childNode, fieldIndent + "  ", out);
                } else if (value instanceof Collection<?> collection) {
                    if (collection.isEmpty()) {
                        out.println(fieldIndent + field.getName() + ": []");
                        continue;
                    }

                    out.println(fieldIndent + field.getName() + ": [");
                    for (Object item : collection) {
                        if (item instanceof AstNode child) {
                            printAst(child, fieldIndent + "  ", out);
                        } else {
                            out.println(fieldIndent + "  " + item);
                        }
                    }

                    out.println(fieldIndent + "]");
                } else if (value instanceof Optional<?> opt) {
                    if (opt.isPresent()) {
                        Object optValue = opt.get();
                        if (optValue instanceof AstNode child) {
                            out.println(fieldIndent + field.getName() + ":");
                            printAst(child, fieldIndent + "  ", out);
                        } else {
                            out.println(fieldIndent + field.getName() + ": " + optValue);
                        }
                    } else {
                        out.println(fieldIndent + field.getName() + ": <empty>");
                    }
                } else {
                    out.println(fieldIndent + field.getName() + ": " + value);
                }

            } catch (IllegalAccessException exception) {
                Railroad.LOGGER.error("Failed to access field: {}", field.getName(), exception);
            }
        }
    }

    private static boolean isListOfStrings(Collection<?> collection) {
        if (collection.isEmpty())
            return false;

        return collection.stream().allMatch(String.class::isInstance);
    }
}
