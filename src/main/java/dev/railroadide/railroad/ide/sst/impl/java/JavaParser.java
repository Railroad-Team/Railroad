package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.*;
import dev.railroadide.railroad.ide.sst.ast.clazz.TypeDeclaration;
import dev.railroadide.railroad.ide.sst.ast.generic.AnnotationElement;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import dev.railroadide.railroad.ide.sst.ast.program.CompilationUnit;
import dev.railroadide.railroad.ide.sst.ast.program.ImportDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.PackageDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.j9.*;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JavaParser extends Parser<JavaTokenType, AstNode> {
    /**
     * Constructs a parser with the given lexer.
     *
     * @param lexer the lexer to use for tokenizing input
     */
    public JavaParser(Lexer<JavaTokenType> lexer) {
        super(lexer);
    }

    @Override
    public AstNode parse() {
        return parseCompilationUnit();
    }

    private AstNode parseCompilationUnit() {
        Span startSpan = currentSpan();

        List<Annotation> leadingAnnotations = new ArrayList<>();
        while (nextIsAny(JavaTokenType.AT)) {
            match(JavaTokenType.AT);
            leadingAnnotations.add(parseAnnotation());
        }

        if (nextIsAny(JavaTokenType.OPEN_KEYWORD, JavaTokenType.MODULE_KEYWORD)) {
            boolean isOpen = match(JavaTokenType.OPEN_KEYWORD);
            if (!nextIsAny(JavaTokenType.MODULE_KEYWORD)) {
                expect(JavaTokenType.MODULE_KEYWORD, "Expected 'module' keyword after 'open'");
            } else {
                ModularCompilationUnit compilationUnit = parseModularCompilationUnit(startSpan, leadingAnnotations, isOpen);
                while (match(JavaTokenType.SEMICOLON)) ;
                if (!isAtEnd()) {
                    reportError("Unexpected token after end of modular compilation unit");
                    while (!isAtEnd()) advance();
                }

                return compilationUnit;
            }
        }

        Optional<PackageDeclaration> packageDecl = Optional.empty();
        if (nextIsAny(JavaTokenType.PACKAGE_KEYWORD)) {
            match(JavaTokenType.PACKAGE_KEYWORD);
            Name packageName = parseQualifiedName();
            expect(JavaTokenType.SEMICOLON, "Expected ';' after package declaration");
            packageDecl = Optional.of(new PackageDeclaration(spanFrom(startSpan), packageName));
        } else if (!leadingAnnotations.isEmpty()) {
            reportError("Top-level annotations are only allowed on a package or module declaration");
            leadingAnnotations.clear();
        }

        List<ImportDeclaration> imports = new ArrayList<>();
        while (nextIsAny(JavaTokenType.IMPORT_KEYWORD)) {
            imports.add(parseImportDeclaration());
        }

        List<TypeDeclaration> typeDeclarations = new ArrayList<>();
        while (!isAtEnd()) {
            if (match(JavaTokenType.SEMICOLON))
                continue;

            TypeDeclaration type = tryParse($ -> parseTopLevelTypeDeclaration());
            if (type != null) {
                typeDeclarations.add(type);
            } else {
                reportError("Unexpected token in compilation unit");
                synchronize(defaultSyncSet());
                if (!isAtEnd())
                    advance();
            }
        }

        return new CompilationUnit(
                spanFrom(startSpan),
                packageDecl,
                imports,
                typeDeclarations
        );
    }

    private TypeDeclaration parseTopLevelTypeDeclaration() {
        return null; // TODO: Continue here.
    }

    private ImportDeclaration parseImportDeclaration() {
        Span start = currentSpan();
        expect(JavaTokenType.IMPORT_KEYWORD, "Expected 'import' keyword");

        boolean isStatic = match(JavaTokenType.STATIC_KEYWORD);
        Name name = parseQualifiedName();
        boolean isWildcard = false;
        if (match(JavaTokenType.DOT)) {
            expect(JavaTokenType.STAR, "Expected '*' in import declaration");
            isWildcard = true;
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after import declaration");
        return new ImportDeclaration(spanFrom(start), name, isStatic, isWildcard);
    }

    private ModularCompilationUnit parseModularCompilationUnit(Span startSpan, List<Annotation> leadingAnnotations, boolean isOpen) {
        expect(JavaTokenType.MODULE_KEYWORD, "Expected 'module'");
        Name moduleName = parseQualifiedName();
        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start module body");

        List<ModuleDirective> directives = new ArrayList<>();
        while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
            Optional<ModuleDirective> moduleDirectiveOpt = parseModuleDirective();
            moduleDirectiveOpt.ifPresent(moduleDirective -> {
                directives.add(moduleDirective);
                expect(JavaTokenType.SEMICOLON, "Expected ';' after module directive");
            });
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close module body");

        return new ModularCompilationUnit(
                spanFrom(startSpan), isOpen,
                moduleName,
                leadingAnnotations,
                directives);
    }

    private Optional<ModuleDirective> parseModuleDirective() {
        Span start = currentSpan();

        if (match(JavaTokenType.REQUIRES_KEYWORD)) {
            boolean isStatic = match(JavaTokenType.STATIC_KEYWORD);
            boolean isTransitive = match(JavaTokenType.TRANSITIVE_KEYWORD);
            Name moduleName = parseQualifiedName();

            return Optional.of(new RequiresDirective(spanFrom(start), isStatic, isTransitive, moduleName));
        }

        if (match(JavaTokenType.EXPORTS_KEYWORD)) {
            Name packageName = parseQualifiedName();
            List<Name> toModules = new ArrayList<>();
            if (match(JavaTokenType.TO_KEYWORD)) {
                do {
                    toModules.add(parseQualifiedName());
                } while (match(JavaTokenType.COMMA));
            }

            return Optional.of(new ExportsDirective(spanFrom(start), packageName, toModules));
        }

        if (match(JavaTokenType.OPENS_KEYWORD)) {
            Name packageName = parseQualifiedName();
            List<Name> toModules = new ArrayList<>();
            if (match(JavaTokenType.TO_KEYWORD)) {
                do {
                    toModules.add(parseQualifiedName());
                } while (match(JavaTokenType.COMMA));
            }

            return Optional.of(new OpensDirective(spanFrom(start), packageName, toModules));
        }

        if (match(JavaTokenType.USES_KEYWORD)) {
            Name serviceName = parseQualifiedName();
            return Optional.of(new UsesDirective(spanFrom(start), serviceName));
        }

        if (match(JavaTokenType.PROVIDES_KEYWORD)) {
            Name serviceName = parseQualifiedName();
            expect(JavaTokenType.WITH_KEYWORD, "Expected 'with' in provides directive");
            List<Name> implementationNames = new ArrayList<>();
            do {
                implementationNames.add(parseQualifiedName());
            } while (match(JavaTokenType.COMMA));

            return Optional.of(new ProvidesDirective(spanFrom(start), serviceName, implementationNames));
        }

        reportError("Expected 'requires', 'exports', 'opens', 'uses', or 'provides' in module directive");
        while (!isAtEnd() && !nextIsAny(JavaTokenType.SEMICOLON, JavaTokenType.CLOSE_BRACE))
            advance();

        return Optional.empty();
    }

    private Annotation parseAnnotation() {
        Span start = currentSpan();

        Name name = parseQualifiedName();
        if (!nextIsAny(JavaTokenType.OPEN_PAREN))
            return new MarkerAnnotation(spanFrom(start), name);

        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after annotation name");
        if (nextIsAny(JavaTokenType.CLOSE_PAREN)) {
            advance();
            return new NormalAnnotation(spanFrom(start), name, List.of());
        }

        if (!hasEqualsBeforeCommaOrCloseParen()) {
            ElementValue value = parseElementValue();
            expect(JavaTokenType.CLOSE_PAREN, "Expected ')' to close annotation");
            return new SingleMemberAnnotation(spanFrom(start), name, value);
        }

        List<AnnotationElement> elements = new ArrayList<>();
        do {
            Span span = currentSpan();

            String identifier = expect(JavaTokenType.IDENTIFIER, "Expected identifier in annotation element").lexeme();
            Span nameSpan = spanFrom(span);
            var elemName = new Name(nameSpan, List.of(identifier));

            expect(JavaTokenType.EQUALS, "Expected '=' after annotation element name");

            ElementValue value = parseElementValue();
            elements.add(new AnnotationElement(spanFrom(span), elemName, value));
        } while (match(JavaTokenType.COMMA));

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' to close annotation");
        return new NormalAnnotation(spanFrom(start), name, elements);
    }

    private ElementValue parseElementValue() {
        if (match(JavaTokenType.AT))
            return parseAnnotation();

        Span start = currentSpan();

        if (match(JavaTokenType.OPEN_BRACE)) {
            List<ElementValue> values = new ArrayList<>();
            if (!nextIsAny(JavaTokenType.CLOSE_BRACE)) {
                do {
                    values.add(parseElementValue());
                } while (match(JavaTokenType.COMMA));
            }

            expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close annotation array");
            return new ElementValueArray(spanFrom(start), values);
        }

        return (ElementValue) parseExpression(0);
    }

    private Name parseQualifiedName() {
        Span start = currentSpan();

        List<String> parts = new ArrayList<>();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Identifier expected");
        parts.add(identifier.lexeme());

        while (match(JavaTokenType.DOT)) {
            identifier = expect(JavaTokenType.IDENTIFIER, "Identifier expected after '.'");
            parts.add(identifier.lexeme());

            if (nextIsAny(JavaTokenType.DOT) && lookaheadType(1) == JavaTokenType.STAR)
                break;
        }

        return new Name(spanFrom(start), parts);
    }

    private boolean hasEqualsBeforeCommaOrCloseParen() {
        int offset = 1;
        int depth = 0;

        while (true) {
            JavaTokenType lookahead = lookaheadType(offset);
            if (lookahead == JavaTokenType.EOF || (lookahead == JavaTokenType.CLOSE_PAREN && depth == 0))
                return false;

            if (lookahead == JavaTokenType.EQUALS && depth == 0)
                return true;

            if (lookahead == JavaTokenType.OPEN_PAREN || lookahead == JavaTokenType.OPEN_BRACE || lookahead == JavaTokenType.OPEN_BRACKET)
                depth++;

            if (lookahead == JavaTokenType.CLOSE_PAREN || lookahead == JavaTokenType.CLOSE_BRACE || lookahead == JavaTokenType.CLOSE_BRACKET) {
                if (depth > 0)
                    depth--;
            }

            offset++;
        }
    }
}
