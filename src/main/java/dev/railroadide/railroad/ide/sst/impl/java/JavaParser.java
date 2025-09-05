package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.*;
import dev.railroadide.railroad.ide.sst.ast.clazz.*;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.generic.*;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.ReceiverParameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.program.CompilationUnit;
import dev.railroadide.railroad.ide.sst.ast.program.ImportDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.PackageDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.j9.*;
import dev.railroadide.railroad.ide.sst.ast.statements.Statement;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.statements.block.InstanceInitializerBlock;
import dev.railroadide.railroad.ide.sst.ast.statements.block.StaticInitializerBlock;
import dev.railroadide.railroad.ide.sst.ast.typeref.*;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.parser.Parser;
import org.jetbrains.annotations.NotNull;

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

        List<Annotation> leadingAnnotations = parseAnnotations();

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
        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        Span startSpan = currentSpan();
        if (nextIsAny(JavaTokenType.CLASS_KEYWORD))
            return parseClassDeclaration(startSpan, modifiers, annotations);

        if (nextIsAny(JavaTokenType.INTERFACE_KEYWORD))
            return parseInterfaceDeclaration(startSpan, modifiers, annotations);

        if (nextIsAny(JavaTokenType.ENUM_KEYWORD))
            return parseEnumDeclaration(startSpan, modifiers, annotations);

        if (nextIsAny(JavaTokenType.AT) && lookaheadType(2) == JavaTokenType.INTERFACE_KEYWORD)
            return parseAnnotationTypeDeclaration(startSpan, modifiers, annotations);

        if (nextIsAny(JavaTokenType.RECORD_KEYWORD))
            return parseRecordDeclaration(startSpan, modifiers, annotations);

        if (nextIsAny(JavaTokenType.SEMICOLON)) {
            advance();
            return new EmptyTypeDeclaration(spanFrom(startSpan));
        }

        reportError("Expected 'class', 'interface', 'enum', 'record', or '@interface' for type declaration");
        return null;
    }

    private ClassDeclaration parseClassDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
        expect(JavaTokenType.CLASS_KEYWORD, "Expected 'class' keyword");

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected class name");
        var className = new Name(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeParameter> typeParameters = new ArrayList<>();
        while (nextIsAny(JavaTokenType.LESS_THAN)) {
            typeParameters.add(parseTypeParameter());
            expect(JavaTokenType.GREATER_THAN, "Expected '>' after type parameters");
        }

        Optional<TypeRef> extendsType = Optional.empty();
        if (match(JavaTokenType.EXTENDS_KEYWORD)) {
            extendsType = Optional.of(parseTypeReference());
        }

        List<TypeRef> implementsTypes = new ArrayList<>();
        if (match(JavaTokenType.IMPLEMENTS_KEYWORD)) {
            do {
                implementsTypes.add(parseTypeReference());
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start class body");
        List<ClassBodyDeclaration> declarations = new ArrayList<>();
        while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
            ClassBodyDeclaration declaration = tryParse($ -> parseClassBodyDeclaration(className, false));
            if (declaration != null) {
                declarations.add(declaration);
            } else {
                reportError("Unexpected token in class body");
                synchronize(defaultSyncSet());
                if (!isAtEnd())
                    advance();
            }
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close class body");
        return new ClassDeclaration(
                spanFrom(startSpan),
                modifiers,
                annotations,
                className,
                typeParameters,
                extendsType,
                implementsTypes,
                declarations
        );
    }

    // member, instance initializer, static initializer, constructor
    private ClassBodyDeclaration parseClassBodyDeclaration(Name className, boolean isRecord) {
        if (nextIsAny(JavaTokenType.OPEN_BRACE))
            return parseInstanceInitializer();

        if (nextIsAny(JavaTokenType.STATIC_KEYWORD) && lookaheadType(2) == JavaTokenType.OPEN_BRACE)
            return parseStaticInitializer();

        Span start = currentSpan();
        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        if (nextIsAny(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD,
                JavaTokenType.AT, JavaTokenType.RECORD_KEYWORD))
            return parseTopLevelTypeDeclaration();

        if (isRecord && nextIsAny(JavaTokenType.OPEN_BRACE))
            return parseCompactConstructor(start, className, modifiers, annotations);

        Marker isConstructor = mark();
        List<TypeParameter> typeParameters = new ArrayList<>();
        while (nextIsAny(JavaTokenType.LESS_THAN)) {
            typeParameters.add(parseTypeParameter());
            expect(JavaTokenType.GREATER_THAN, "Expected '>' after type parameters");
        }

        if (nextIsAny(JavaTokenType.IDENTIFIER) && lookaheadType(2) == JavaTokenType.OPEN_PAREN) {
            Token<JavaTokenType> identifierToken = advance();
            String identifier = identifierToken.lexeme();
            if (className.parts().getFirst().equals(identifier)) {
                isConstructor.commit();
                return parseConstructorDeclaration(className, start, modifiers, annotations, typeParameters);
            }
        }

        isConstructor.rollback();
        return parseClassMemberDeclaration(start, modifiers, annotations);
    }

    private ClassMember parseClassMemberDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations) {
        if (nextIsAny(JavaTokenType.LESS_THAN))
            return parseGenericMethodDeclaration(start, modifiers, annotations);

        if (nextIsAny(JavaTokenType.IDENTIFIER, JavaTokenType.INT_KEYWORD, JavaTokenType.BOOLEAN_KEYWORD,
                JavaTokenType.CHAR_KEYWORD, JavaTokenType.BYTE_KEYWORD, JavaTokenType.SHORT_KEYWORD,
                JavaTokenType.LONG_KEYWORD, JavaTokenType.FLOAT_KEYWORD, JavaTokenType.DOUBLE_KEYWORD,
                JavaTokenType.VOID_KEYWORD))
            return parseFieldOrMethodDeclaration(start, modifiers, annotations);

        reportError("Expected field or method declaration");
        return null;
    }

    private ClassMember parseFieldOrMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations) {
        if(nextIsAny(JavaTokenType.VOID_KEYWORD))
            return parseFullMethodDeclaration(start, modifiers, annotations, List.of());

        TypeRef type = parseTypeReference();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected field or method name");
        var name = new Name(spanFrom(identifier), List.of(identifier.lexeme()));

        if (nextIsAny(JavaTokenType.OPEN_PAREN))
            return parseMethodDeclaration(start, modifiers, annotations, List.of(), type, name);

        return parseFieldDeclaration(start, modifiers, annotations, type, name);
    }

    private FieldDeclaration parseFieldDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations, TypeRef type, Name name) {
        List<VariableDeclarator> variables = new ArrayList<>();
        variables.add(parseVariableDeclarator(type, name));

        while (match(JavaTokenType.COMMA)) {
            Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected field name");
            var varName = new Name(spanFrom(identifier), List.of(identifier.lexeme()));
            variables.add(parseVariableDeclarator(type, varName));
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after field declaration");
        return new FieldDeclaration(
                spanFrom(start),
                annotations,
                modifiers,
                type,
                name,
                variables
        );
    }

    private VariableDeclarator parseVariableDeclarator(Span start, TypeRef type, Name name) {
        TypeRef varType = type;
        int dimensions = parseDimensions();
        if (dimensions > 0) {
            varType = new ArrayTypeRef(spanFrom(start), type, dimensions);
        }

        Optional<Expression> initializer = Optional.empty();
        if (match(JavaTokenType.EQUALS)) {
            initializer = Optional.of((Expression) parseExpression(0));
        }

        return new VariableDeclarator(spanFrom(start), varType, name, initializer);
    }

    private MethodDeclaration parseGenericMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations) {
        List<TypeParameter> typeParameters = new ArrayList<>();
        do {
            typeParameters.add(parseTypeParameter());
        } while (nextIsAny(JavaTokenType.COMMA) && match(JavaTokenType.COMMA));
        expect(JavaTokenType.GREATER_THAN, "Expected '>' after type parameters");

        return parseFullMethodDeclaration(start, modifiers, annotations, typeParameters);
    }

    private MethodDeclaration parseFullMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations, List<TypeParameter> typeParameters) {
        TypeRef returnType = parseTypeReference();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected method name");
        var methodName = new Name(spanFrom(identifier), List.of(identifier.lexeme()));

        return parseMethodDeclaration(start, modifiers, annotations, typeParameters, returnType, methodName);
    }

    private MethodDeclaration parseMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations, List<TypeParameter> typeParameters, TypeRef returnType, Name name) {
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after method name");

        Optional<ReceiverParameter> receiverParameter = Optional.ofNullable(
                tryParse($ -> parseReceiverParameter()));
        List<Parameter> parameters = new ArrayList<>();
        if (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
            if (receiverParameter.isPresent())
                expect(JavaTokenType.COMMA, "Expected ',' after receiver parameter");

            do {
                parameters.add(parseParameter());
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after method parameters");

        List<TypeRef> throwsTypes = new ArrayList<>();
        if (match(JavaTokenType.THROWS_KEYWORD)) {
            do {
                throwsTypes.add(parseTypeReference());
            } while (match(JavaTokenType.COMMA));
        }

        Optional<BlockStatement> body = Optional.ofNullable(tryParse($ -> parseBlock()));
        if (body.isEmpty() && !nextIsAny(JavaTokenType.SEMICOLON)) {
            reportError("Expected method body or ';' for abstract/native methods");
            synchronize(defaultSyncSet());
            if (!isAtEnd())
                advance();
        } else if (body.isEmpty()) {
            expect(JavaTokenType.SEMICOLON, "Expected ';' after method declaration");
        }

        return new MethodDeclaration(
                spanFrom(start),
                annotations,
                modifiers,
                typeParameters,
                returnType,
                name,
                receiverParameter,
                parameters,
                throwsTypes,
                body
        );
    }

    private ReceiverParameter parseReceiverParameter() {
        Span start = currentSpan();

        List<Annotation> annotations = parseAnnotations();
        TypeRef type = parseTypeReference();

        ReceiverParameter.ReceiverType receiverType =
                match(JavaTokenType.THIS_KEYWORD) ? ReceiverParameter.ReceiverType.THIS :
                        match(JavaTokenType.SUPER_KEYWORD) ? ReceiverParameter.ReceiverType.SUPER :
                                null;

        return new ReceiverParameter(spanFrom(start), annotations, type, receiverType);
    }

    private @NotNull List<Annotation> parseAnnotations() {
        List<Annotation> annotations = new ArrayList<>();
        while (nextIsAny(JavaTokenType.AT)) {
            annotations.add(parseAnnotation());
        }
        return annotations;
    }

    private ConstructorDeclaration parseConstructorDeclaration(Name className, Span span, List<Modifier> modifiers, List<Annotation> annotations, List<TypeParameter> typeParameters) {
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after constructor name");
        List<Parameter> parameters = new ArrayList<>();
        if (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
            do {
                parameters.add(parseParameter());
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after constructor parameters");
        List<TypeRef> throwsTypes = new ArrayList<>();
        if (match(JavaTokenType.THROWS_KEYWORD)) {
            do {
                throwsTypes.add(parseTypeReference());
            } while (match(JavaTokenType.COMMA));
        }

        BlockStatement body = tryParse($ -> parseBlock());
        if (body == null) {
            reportError("Expected constructor body");
            synchronize(defaultSyncSet());
            if (!isAtEnd())
                advance();
        }

        return new ConstructorDeclaration(
                spanFrom(span),
                modifiers,
                annotations,
                typeParameters,
                className,
                parameters,
                throwsTypes,
                Optional.ofNullable(body)
        );
    }

    private BlockStatement parseBlock() {
        Span start = currentSpan();
        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start block");

        List<Statement> statements = new ArrayList<>();
        while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
            Statement statement = tryParse($ -> parseStatement());
            if (statement != null) {
                statements.add(statement);
            } else {
                reportError("Unexpected token in block");
                synchronize(defaultSyncSet());
                if (!isAtEnd())
                    advance();
            }
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close block");
        return new BlockStatement(spanFrom(start), statements);
    }

    private Parameter parseParameter() {
        Span start = currentSpan();

        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        TypeRef type = parseTypeReference();
        boolean isVarArgs = match(JavaTokenType.ELLIPSIS);

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected parameter name");
        var name = new Name(spanFrom(identifier), List.of(identifier.lexeme()));

        return new Parameter(spanFrom(start), modifiers, annotations, type, isVarArgs, name);
    }

    private CompactConstructorDeclaration parseCompactConstructor(Span start, Name className, List<Modifier> modifiers, List<Annotation> annotations) {
        BlockStatement body = tryParse($ -> parseBlock());
        if (body == null) {
            reportError("Expected constructor body");
            synchronize(defaultSyncSet());
            if (!isAtEnd())
                advance();
        }

        return new CompactConstructorDeclaration(
                spanFrom(start),
                modifiers,
                annotations,
                className,
                Optional.ofNullable(body)
        );
    }

    private ClassBodyDeclaration parseStaticInitializer() {
        Span start = currentSpan();
        expect(JavaTokenType.STATIC_KEYWORD, "Expected 'static' keyword");
        BlockStatement block = parseBlock();
        return new StaticInitializerBlock(spanFrom(start), block);
    }

    private InstanceInitializerBlock parseInstanceInitializer() {
        Span start = currentSpan();
        BlockStatement block = parseBlock();
        return new InstanceInitializerBlock(spanFrom(start), block);
    }

    private TypeParameter parseTypeParameter() {
        Span start = currentSpan();

        List<Annotation> annotations = parseAnnotations();

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected type parameter name");
        var name = new Name(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeRef> bounds = new ArrayList<>();
        if (match(JavaTokenType.EXTENDS_KEYWORD)) {
            do {
                bounds.add(parseTypeReference());
            } while (match(JavaTokenType.BITWISE_AND));
        }

        return new TypeParameter(spanFrom(start), annotations, name, bounds);
    }

    // x   PRIMITIVE_TYPE, // e.g., int, boolean
    // x   ARRAY_TYPE, // e.g., int[], String[]
    // x   CLASS_OR_INTERFACE_TYPE, // e.g., List<String>
    // x   TYPE_VARIABLE, // <T>
    //    INTERSECTION_TYPE, // A & B
    //    UNION_TYPE, // A | B
    // x   WILDCARD_TYPE, // ? extends A, ? super B, or just ?
    //    EXCEPTION_TYPE, // CLASS_OR_INTERFACE_TYPE or TYPE_VARIABLE
    private TypeRef parseTypeReference() {
        Span start = currentSpan();

        TypeRef baseType = parseNonArrayTypeReference();
        int dimensions = parseDimensions();

        if (dimensions > 0)
            return new ArrayTypeRef(spanFrom(start), baseType, dimensions);

        return baseType;
    }

    private TypeRef parseNonArrayTypeReference() {
        Span start = currentSpan();

        if (nextIsAny(JavaTokenType.INT_KEYWORD, JavaTokenType.BOOLEAN_KEYWORD, JavaTokenType.CHAR_KEYWORD,
                JavaTokenType.BYTE_KEYWORD, JavaTokenType.SHORT_KEYWORD, JavaTokenType.LONG_KEYWORD,
                JavaTokenType.FLOAT_KEYWORD, JavaTokenType.DOUBLE_KEYWORD)) {
            Token<JavaTokenType> primitiveToken = advance();
            return new PrimitiveTypeRef(spanFrom(start), LexerToken.of(spanFrom(start), primitiveToken));
        }

        JavaTokenType nextToken = lookaheadType(1);
        JavaTokenType lookahead2 = lookaheadType(2);
        if (nextToken == JavaTokenType.IDENTIFIER && lookahead2 != JavaTokenType.DOT && lookahead2 != JavaTokenType.LESS_THAN) {
            Token<JavaTokenType> identifier = advance();
            return new TypeVariableRef(spanFrom(start), identifier.lexeme());
        }

        return parseClassOrInterfaceTypeReference();
    }

    private ClassOrInterfaceTypeRef parseClassOrInterfaceTypeReference() {
        List<ClassOrInterfaceTypeRef.Part> parts = new ArrayList<>();
        do {
            Span partStart = currentSpan();
            Token<JavaTokenType> identifierToken = expect(JavaTokenType.IDENTIFIER, "Expected identifier in type reference");
            List<TypeRef> typeArguments = parseTypeArgumentsOptionally();
            parts.add(new ClassOrInterfaceTypeRef.Part(spanFrom(partStart), new Name(spanFrom(identifierToken), List.of(identifierToken.lexeme())), typeArguments));
        } while (match(JavaTokenType.DOT));

        return new ClassOrInterfaceTypeRef(spanFrom(parts.getFirst().span()), parts);
    }

    private List<TypeRef> parseTypeArgumentsOptionally() {
        List<TypeRef> typeArguments = new ArrayList<>();
        if (match(JavaTokenType.LESS_THAN)) {
            do {
                typeArguments.add(parseTypeArgument());
            } while (match(JavaTokenType.COMMA));

            expect(JavaTokenType.GREATER_THAN, "Expected '>' after type arguments");
        }

        return typeArguments;
    }

    private TypeRef parseTypeArgument() {
        Span start = currentSpan();

        if (match(JavaTokenType.QUESTION_MARK)) {
            if (match(JavaTokenType.EXTENDS_KEYWORD)) {
                TypeRef bound = parseTypeReference();
                return new WildcardTypeRef(spanFrom(start), WildcardTypeRef.Variance.EXTENDS, Optional.of(bound));
            } else if (match(JavaTokenType.SUPER_KEYWORD)) {
                TypeRef bound = parseTypeReference();
                return new WildcardTypeRef(spanFrom(start), WildcardTypeRef.Variance.SUPER, Optional.of(bound));
            } else {
                return new WildcardTypeRef(spanFrom(start), WildcardTypeRef.Variance.UNBOUNDED, Optional.empty());
            }
        }

        return parseTypeReference();
    }

    private int parseDimensions() {
        int dimensions = 0;
        while (match(JavaTokenType.OPEN_BRACKET)) {
            expect(JavaTokenType.CLOSE_BRACKET, "Expected ']' for array type");
            dimensions++;
        }

        return dimensions;
    }

    private void parseModifiersAndAnnotations(List<Modifier> modifiers, List<Annotation> annotations) {
        while (!isAtEnd() && (lookaheadType(1).isModifier() || nextIsAny(JavaTokenType.AT))) {
            if (lookaheadType(1) == JavaTokenType.AT && lookaheadType(2) == JavaTokenType.INTERFACE_KEYWORD)
                break;

            if (nextIsAny(JavaTokenType.AT)) {
                annotations.add(parseAnnotation());
                continue;
            }

            Span start = currentSpan();
            Token<JavaTokenType> modifierToken = advance();
            modifiers.add(new Modifier(spanFrom(start), modifierToken.lexeme()));
        }
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

        expect(JavaTokenType.AT, "Expected '@' to start annotation");

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
