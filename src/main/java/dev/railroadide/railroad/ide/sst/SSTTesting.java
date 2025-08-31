package dev.railroadide.railroad.ide.sst;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.impl.java.JavaLexer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;

public class SSTTesting {
    public static void main(String[] args) {
        System.out.println("SST Testing");

        String code = """
            @Deprecated(boop = @SuppressWarnings("all"))
            module com.example.modules {
                requires java.base;
                exports com.example.packages;
            }
            """;

        System.out.println("Code to parse:");
        System.out.println(code);

        try(var lexer = new JavaLexer(code)) {
            Lexer.Snapshot snapshot = lexer.snapshot();
            while(true) {
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
            System.out.println(ast);
        }
    }
}
