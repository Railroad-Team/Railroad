package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.*;
import dev.railroadide.railroad.ide.sst.ast.clazz.*;
import dev.railroadide.railroad.ide.sst.ast.expression.AssignmentExpression;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.expression.LambdaExpression;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.*;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.ReceiverParameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.program.CompilationUnit;
import dev.railroadide.railroad.ide.sst.ast.program.ImportDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.PackageDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.j9.*;
import dev.railroadide.railroad.ide.sst.ast.statements.*;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.statements.block.InstanceInitializerBlock;
import dev.railroadide.railroad.ide.sst.ast.statements.block.StaticInitializerBlock;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.CaseItem;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchLabel;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchRule;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchStatement;
import dev.railroadide.railroad.ide.sst.ast.typeref.*;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.parser.Parser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JavaParser extends Parser<JavaTokenType, AstNode, Expression> {
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

    @Override
    protected Expression parseExpression() {
        if (isLambdaHeader()) {
            LambdaExpression lambdaExpression = tryParse($ -> parseLambdaExpression());
            if (lambdaExpression != null)
                return lambdaExpression;

            reportError("Invalid lambda expression");
            synchronize(Set.of(JavaTokenType.SEMICOLON, JavaTokenType.COMMA, JavaTokenType.CLOSE_PAREN));
            return null;
        }

        return parseAssignmentExpression();
    }

    private Expression parseAssignmentExpression() {
        Span start = currentSpan();
        Expression left = parseConditionalExpression();

        boolean isValidLeftHandSide = AssignmentExpression.isValidLeftHandSide(left);
        if(!isValidLeftHandSide && lookaheadType(1).isAssignmentOperator()) {
            reportError("Left-hand side of assignment must be a variable, field, or array element");
        }

        if (isValidLeftHandSide && lookaheadType(1).isAssignmentOperator()) {
            Token<JavaTokenType> operator = advance();
            LexerToken<JavaTokenType> operatorToken = LexerToken.of(spanFrom(operator), operator);

            Expression right = parseExpression();
            return new AssignmentExpression(spanFrom(start), left, operatorToken, right);
        }

        return left;
    }

    private LambdaExpression parseLambdaExpression() {
        Span start = currentSpan();

        if (nextIsAny(JavaTokenType.IDENTIFIER)) {
            Token<JavaTokenType> identifier = advance();
            NameExpression paramName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));
            var parameter = new Parameter(spanFrom(start), List.of(), List.of(), Optional.empty(), false, paramName);
            List<Parameter> parameters = List.of(parameter);

            expect(JavaTokenType.ARROW, "Expected '->' after lambda parameter");

            Statement body = parseStatement();
            if (!(body instanceof LambdaBody lambdaBody)) {
                reportError("Expected lambda body to be a single expression or block");
                return null;
            }

            return new LambdaExpression(spanFrom(start), parameters, true, lambdaBody);
        }

        if (nextIsAny(JavaTokenType.OPEN_PAREN) && lookaheadType(2) == JavaTokenType.CLOSE_PAREN) {
            advance(); // (
            advance(); // )
            expect(JavaTokenType.ARROW, "Expected '->' after lambda parameters");

            Statement body = parseStatement();
            if (!(body instanceof LambdaBody lambdaBody)) {
                reportError("Expected lambda body to be a single expression or block");
                return null;
            }

            return new LambdaExpression(spanFrom(start), List.of(), true, lambdaBody);
        }

        expect(JavaTokenType.OPEN_PAREN, "Expected '(' to start lambda parameters");
        List<Parameter> parameters = new ArrayList<>();
        boolean isInferred = true;
        if (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
            do {
                Parameter parameter = parseLambdaParameter();
                if (isInferred && parameter.type().isPresent())
                    isInferred = false;
//                TODO: Come back and check if the semantic analyzer can handle this
//                else if(!isInferred && parameter.type().isEmpty()) {
//                    reportError("Cannot mix inferred and explicit lambda parameter types");
//                    return null;
//                }

                parameters.add(parameter);
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' to end lambda parameters");
        expect(JavaTokenType.ARROW, "Expected '->' after lambda parameters");

        Statement body = parseStatement();
        if (!(body instanceof LambdaBody lambdaBody)) {
            reportError("Expected lambda body to be a single expression or block");
            return null;
        }

        return new LambdaExpression(spanFrom(start), parameters, isInferred, lambdaBody);
    }

    private Parameter parseLambdaParameter() {
        Span start = currentSpan();

        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        TypeRef type = null;
        if (!nextIsAny(JavaTokenType.IDENTIFIER)) {
            type = parseTypeReference();
        }

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected lambda parameter name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        return new Parameter(spanFrom(start), modifiers, annotations, Optional.ofNullable(type), false, name);
    }

    private boolean isLambdaHeader() {
        if (nextIsAny(JavaTokenType.IDENTIFIER) && lookaheadType(2) == JavaTokenType.ARROW)
            return true;

        if (nextIsAny(JavaTokenType.OPEN_PAREN)) {
            int offsetForClosingParen = findMatchingClosingParen(1);
            if (offsetForClosingParen == -1) {
                return false;
            }

            return lookaheadType(offsetForClosingParen + 1) == JavaTokenType.ARROW;
        }

        return false;
    }

    private int findMatchingClosingParen(int offsetOfOpeningParen) {
        int currentDepth = 1;
        int currentOffset = offsetOfOpeningParen + 1;
        while (currentDepth > 0 && lookaheadType(currentOffset) != JavaTokenType.EOF) {
            JavaTokenType type = lookaheadType(currentOffset);
            if (type == JavaTokenType.OPEN_PAREN)
                currentDepth++;
            else if (type == JavaTokenType.CLOSE_PAREN) {
                currentDepth--;
                if (currentDepth == 0)
                    return currentOffset;
            }

            currentOffset++;
        }

        return -1;
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
            NameExpression packageName = parseQualifiedName();
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
        Span startSpan = currentSpan();
        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);
        return parseTopLevelTypeDeclaration(startSpan, modifiers, annotations);
    }

    private TypeDeclaration parseTopLevelTypeDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
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

    private RecordDeclaration parseRecordDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
        expect(JavaTokenType.RECORD_KEYWORD, "Expected 'record' keyword");

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected record name");
        var recordName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeParameter> typeParameters = new ArrayList<>();
        while (nextIsAny(JavaTokenType.LEFT_ANGLED_BRACKET)) {
            typeParameters.add(parseTypeParameter());
            expect(JavaTokenType.RIGHT_ANGLED_BRACKET, "Expected '>' after type parameters");
        }

        expect(JavaTokenType.OPEN_PAREN, "Expected '(' to start record components");
        List<RecordComponent> components = new ArrayList<>();
        if (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
            do {
                components.add(parseRecordComponent());
            } while (match(JavaTokenType.COMMA));
        }
        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' to end record components");

        List<TypeRef> implementsTypes = new ArrayList<>();
        if (match(JavaTokenType.IMPLEMENTS_KEYWORD)) {
            do {
                implementsTypes.add(parseTypeReference());
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start record body");
        List<ClassBodyDeclaration> declarations = new ArrayList<>();
        while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
            ClassBodyDeclaration declaration = tryParse($ -> parseClassBodyDeclaration(recordName, true));
            if (declaration != null) {
                declarations.add(declaration);
            } else {
                reportError("Unexpected token in record body");
                synchronize(defaultSyncSet());
                if (!isAtEnd())
                    advance();
            }
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close record body");
        return new RecordDeclaration(
                spanFrom(startSpan),
                modifiers,
                annotations,
                recordName,
                typeParameters,
                components,
                implementsTypes,
                declarations
        );
    }

    private RecordComponent parseRecordComponent() {
        Span start = currentSpan();

        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        TypeRef type = parseTypeReference();

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected record component name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        return new RecordComponent(
                spanFrom(start),
                modifiers,
                annotations,
                type,
                name
        );
    }

    private AnnotationTypeDeclaration parseAnnotationTypeDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
        expect(JavaTokenType.AT_INTERFACE_KEYWORD, "Expected '@interface' keyword");

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected annotation type name");
        var annotationName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start annotation type body");
        List<AnnotationBodyDeclaration> declarations = new ArrayList<>();
        while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
            AnnotationBodyDeclaration declaration = tryParse($ -> parseAnnotationBodyDeclaration());
            if (declaration != null) {
                declarations.add(declaration);
            } else {
                reportError("Unexpected token in annotation type body");
                synchronize(defaultSyncSet());
                if (!isAtEnd())
                    advance();
            }
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close annotation type body");

        return new AnnotationTypeDeclaration(
                spanFrom(startSpan),
                modifiers,
                annotations,
                annotationName,
                declarations
        );
    }

    private AnnotationBodyDeclaration parseAnnotationBodyDeclaration() {
        Span start = currentSpan();
        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        if (nextIsAny(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD,
                JavaTokenType.AT, JavaTokenType.RECORD_KEYWORD))
            return parseTopLevelTypeDeclaration(start, modifiers, annotations);

        return parseAnnotationTypeMemberDeclaration(start, modifiers, annotations);
    }

    private AnnotationMember parseAnnotationTypeMemberDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations) {
        TypeRef type = parseTypeReference();

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected annotation member name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        if (nextIsAny(JavaTokenType.OPEN_PAREN)) {
            expect(JavaTokenType.OPEN_PAREN, "Expected '(' after method name");
            expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after method parameters");

            Optional<Expression> defaultValue = Optional.empty();
            if (match(JavaTokenType.DEFAULT_KEYWORD)) {
                defaultValue = Optional.of(parseExpression());
            }

            expect(JavaTokenType.SEMICOLON, "Expected ';' after annotation member declaration");
            return new AnnotationTypeMemberDeclaration(
                    spanFrom(start),
                    modifiers,
                    annotations,
                    type,
                    name,
                    defaultValue
            );
        } else {
            if (match(JavaTokenType.EQUALS))
                return parseFieldDeclaration(start, modifiers, annotations, type, name);

            expect(JavaTokenType.SEMICOLON, "Expected ';' after annotation member declaration");
            return new AnnotationTypeMemberDeclaration(
                    spanFrom(start),
                    modifiers,
                    annotations,
                    type,
                    name,
                    Optional.empty()
            );
        }
    }

    private EnumDeclaration parseEnumDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
        expect(JavaTokenType.ENUM_KEYWORD, "Expected 'enum' keyword");

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected enum name");
        var enumName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeRef> implementsTypes = new ArrayList<>();
        if (match(JavaTokenType.IMPLEMENTS_KEYWORD)) {
            do {
                implementsTypes.add(parseTypeReference());
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start enum body");
        List<EnumConstantDeclaration> enumConstants = new ArrayList<>();
        if (!nextIsAny(JavaTokenType.SEMICOLON, JavaTokenType.CLOSE_BRACE)) {
            do {
                enumConstants.add(parseEnumConstant());
            } while (match(JavaTokenType.COMMA));
        }

        if (match(JavaTokenType.SEMICOLON)) {
            List<ClassBodyDeclaration> declarations = new ArrayList<>();
            while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
                ClassBodyDeclaration declaration = tryParse($ -> parseClassBodyDeclaration(enumName, false));
                if (declaration != null) {
                    declarations.add(declaration);
                } else {
                    reportError("Unexpected token in enum body");
                    synchronize(defaultSyncSet());
                    if (!isAtEnd())
                        advance();
                }
            }

            expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close enum body");
            return new EnumDeclaration(
                    spanFrom(startSpan),
                    modifiers,
                    annotations,
                    enumName,
                    implementsTypes,
                    enumConstants,
                    declarations
            );
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close enum body");
        return new EnumDeclaration(
                spanFrom(startSpan),
                modifiers,
                annotations,
                enumName,
                implementsTypes,
                enumConstants,
                List.of()
        );
    }

    private EnumConstantDeclaration parseEnumConstant() {
        Span start = currentSpan();

        List<Annotation> annotations = parseAnnotations();

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected enum constant name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<Expression> arguments = new ArrayList<>();
        if (match(JavaTokenType.OPEN_PAREN)) {
            while (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
                arguments.add(parseExpression());
            }

            expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after enum constant arguments");
        }

        List<ClassBodyDeclaration> classBodyDeclarations = new ArrayList<>();
        if (match(JavaTokenType.OPEN_BRACE)) {
            while (!nextIsAny(JavaTokenType.CLOSE_BRACE)) {
                classBodyDeclarations.add(parseClassBodyDeclaration(name, false));
            }

            expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close enum constant body");
        }

        return new EnumConstantDeclaration(
                spanFrom(start),
                annotations,
                name,
                arguments,
                classBodyDeclarations
        );
    }

    private InterfaceDeclaration parseInterfaceDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
        expect(JavaTokenType.INTERFACE_KEYWORD, "Expected 'interface' keyword");

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected interface name");
        var interfaceName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeParameter> typeParameters = new ArrayList<>();
        while (nextIsAny(JavaTokenType.LEFT_ANGLED_BRACKET)) {
            typeParameters.add(parseTypeParameter());
            expect(JavaTokenType.RIGHT_ANGLED_BRACKET, "Expected '>' after type parameters");
        }

        List<TypeRef> extendsTypes = new ArrayList<>();
        if (match(JavaTokenType.EXTENDS_KEYWORD)) {
            do {
                extendsTypes.add(parseTypeReference());
            } while (match(JavaTokenType.COMMA));
        }

        expect(JavaTokenType.OPEN_BRACE, "Expected '{' to start interface body");
        List<ClassBodyDeclaration> declarations = new ArrayList<>();
        while (!nextIsAny(JavaTokenType.CLOSE_BRACE) && !isAtEnd()) {
            ClassBodyDeclaration declaration = tryParse($ -> parseClassBodyDeclaration(interfaceName, false));
            if (declaration != null) {
                declarations.add(declaration);
            } else {
                reportError("Unexpected token in interface body");
                synchronize(defaultSyncSet());
                if (!isAtEnd())
                    advance();
            }
        }

        expect(JavaTokenType.CLOSE_BRACE, "Expected '}' to close interface body");
        return new InterfaceDeclaration(
                spanFrom(startSpan),
                modifiers,
                annotations,
                interfaceName,
                typeParameters,
                extendsTypes,
                declarations
        );
    }

    private ClassDeclaration parseClassDeclaration(Span startSpan, List<Modifier> modifiers, List<Annotation> annotations) {
        expect(JavaTokenType.CLASS_KEYWORD, "Expected 'class' keyword");

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected class name");
        var className = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeParameter> typeParameters = new ArrayList<>();
        while (nextIsAny(JavaTokenType.LEFT_ANGLED_BRACKET)) {
            typeParameters.add(parseTypeParameter());
            expect(JavaTokenType.RIGHT_ANGLED_BRACKET, "Expected '>' after type parameters");
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
    private ClassBodyDeclaration parseClassBodyDeclaration(NameExpression className, boolean isRecord) {
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
            return parseTopLevelTypeDeclaration(start, modifiers, annotations);

        if (isRecord && nextIsAny(JavaTokenType.OPEN_BRACE))
            return parseCompactConstructor(start, className, modifiers, annotations);

        Marker isConstructor = mark();
        List<TypeParameter> typeParameters = new ArrayList<>();
        while (nextIsAny(JavaTokenType.LEFT_ANGLED_BRACKET)) {
            typeParameters.add(parseTypeParameter());
            expect(JavaTokenType.RIGHT_ANGLED_BRACKET, "Expected '>' after type parameters");
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
        if (nextIsAny(JavaTokenType.LEFT_ANGLED_BRACKET))
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
        if (nextIsAny(JavaTokenType.VOID_KEYWORD))
            return parseFullMethodDeclaration(start, modifiers, annotations, List.of());

        TypeRef type = parseTypeReference();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected field or method name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        if (nextIsAny(JavaTokenType.OPEN_PAREN))
            return parseMethodDeclaration(start, modifiers, annotations, List.of(), type, name);

        return parseFieldDeclaration(start, modifiers, annotations, type, name);
    }

    private FieldDeclaration parseFieldDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations, TypeRef type, NameExpression name) {
        List<VariableDeclarator> variables = new ArrayList<>();
        variables.add(parseVariableDeclarator(name));

        while (match(JavaTokenType.COMMA)) {
            Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected field name");
            var varName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));
            variables.add(parseVariableDeclarator(varName));
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

    private VariableDeclarator parseVariableDeclarator(NameExpression name) {
        Span start = name.span();

        Optional<Expression> initializer = Optional.empty();
        if (match(JavaTokenType.EQUALS)) {
            initializer = Optional.of(parseExpression());
        }

        return new VariableDeclarator(spanFrom(start), name, initializer);
    }

    private MethodDeclaration parseGenericMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations) {
        List<TypeParameter> typeParameters = new ArrayList<>();
        do {
            typeParameters.add(parseTypeParameter());
        } while (nextIsAny(JavaTokenType.COMMA) && match(JavaTokenType.COMMA));
        expect(JavaTokenType.RIGHT_ANGLED_BRACKET, "Expected '>' after type parameters");

        return parseFullMethodDeclaration(start, modifiers, annotations, typeParameters);
    }

    private MethodDeclaration parseFullMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations, List<TypeParameter> typeParameters) {
        TypeRef returnType = parseTypeReference();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected method name");
        var methodName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        return parseMethodDeclaration(start, modifiers, annotations, typeParameters, returnType, methodName);
    }

    private MethodDeclaration parseMethodDeclaration(Span start, List<Modifier> modifiers, List<Annotation> annotations, List<TypeParameter> typeParameters, TypeRef returnType, NameExpression name) {
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

    private ConstructorDeclaration parseConstructorDeclaration(NameExpression className, Span span, List<Modifier> modifiers, List<Annotation> annotations, List<TypeParameter> typeParameters) {
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

    private Statement parseStatement() {
        Span start = currentSpan();
        if (match(JavaTokenType.SEMICOLON))
            return new EmptyStatement(spanFrom(start));

        if (nextIsAny(JavaTokenType.OPEN_BRACE))
            return parseBlock();

        if (nextIsAny(JavaTokenType.IF_KEYWORD))
            return parseIfStatement();

        if (nextIsAny(JavaTokenType.SWITCH_KEYWORD))
            return parseSwitchStatement();

        if (nextIsAny(JavaTokenType.WHILE_KEYWORD))
            return parseWhileStatement();

        if (nextIsAny(JavaTokenType.DO_KEYWORD))
            return parseDoWhileStatement();

        if (nextIsAny(JavaTokenType.FOR_KEYWORD))
            return parseForStatement();

        if (nextIsAny(JavaTokenType.TRY_KEYWORD))
            return parseTryStatement();

        if (nextIsAny(JavaTokenType.SYNCHRONIZED_KEYWORD))
            return parseSynchronizedStatement();

        if (nextIsAny(JavaTokenType.RETURN_KEYWORD))
            return parseReturnStatement();

        if (nextIsAny(JavaTokenType.THROW_KEYWORD))
            return parseThrowStatement();

        if (nextIsAny(JavaTokenType.BREAK_KEYWORD))
            return parseBreakStatement();

        if (nextIsAny(JavaTokenType.CONTINUE_KEYWORD))
            return parseContinueStatement();

        if (nextIsAny(JavaTokenType.ASSERT_KEYWORD))
            return parseAssertStatement();

        if (nextIsAny(JavaTokenType.YIELD_KEYWORD))
            return parseYieldStatement();

        if (nextIsAny(JavaTokenType.IDENTIFIER) && lookaheadType(2) == JavaTokenType.COLON)
            return parseLabeledStatement();

        if (nextIsAny(JavaTokenType.VAR_KEYWORD, JavaTokenType.FINAL_KEYWORD, JavaTokenType.AT) || lookaheadType(2) == JavaTokenType.IDENTIFIER)
            return parseLocalVariableDeclarationStatement();

        return parseExpressionStatement();
    }

    private ExpressionStatement parseExpressionStatement() {
        Span start = currentSpan();
        Expression expression = parseExpression();
        expect(JavaTokenType.SEMICOLON, "Expected ';' after expression statement");
        return new ExpressionStatement(spanFrom(start), expression);
    }

    private LocalVariableDeclarationStatement parseLocalVariableDeclarationStatement() {
        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        LexerToken<JavaTokenType> varToken = null;
        if (nextIsAny(JavaTokenType.VAR_KEYWORD)) {
            Span varSpan = currentSpan();
            Token<JavaTokenType> token = advance();
            varToken = LexerToken.of(spanFrom(varSpan), token);
        }

        TypeRef type = varToken != null ?
                new PrimitiveTypeRef(spanFrom(currentSpan()), varToken) :
                parseTypeReference();

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected variable name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<VariableDeclarator> variables = new ArrayList<>();
        variables.add(parseVariableDeclarator(name));

        while (match(JavaTokenType.COMMA)) {
            Token<JavaTokenType> id = expect(JavaTokenType.IDENTIFIER, "Expected variable name");
            var varName = new NameExpression(spanFrom(id), List.of(id.lexeme()));
            variables.add(parseVariableDeclarator(varName));
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after variable declaration");
        return new LocalVariableDeclarationStatement(
                spanFrom(currentSpan()),
                annotations,
                modifiers,
                type,
                varToken != null,
                variables
        );
    }

    private LabeledStatement parseLabeledStatement() {
        Span start = currentSpan();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected label name");
        var labelName = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));
        expect(JavaTokenType.COLON, "Expected ':' after label name");

        Statement statement = parseStatement();
        return new LabeledStatement(spanFrom(start), labelName, statement);
    }

    private YieldStatement parseYieldStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.YIELD_KEYWORD, "Expected 'yield' keyword");

        Expression expression = parseExpression();

        expect(JavaTokenType.SEMICOLON, "Expected ';' after yield statement");
        return new YieldStatement(spanFrom(start), expression);
    }

    private AssertStatement parseAssertStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.ASSERT_KEYWORD, "Expected 'assert' keyword");

        Expression condition = parseExpression();

        Optional<Expression> message = Optional.empty();
        if (match(JavaTokenType.COLON)) {
            message = Optional.of(parseExpression());
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after assert statement");
        return new AssertStatement(spanFrom(start), condition, message);
    }

    private ContinueStatement parseContinueStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.CONTINUE_KEYWORD, "Expected 'continue' keyword");

        Optional<NameExpression> label = Optional.empty();
        if (nextIsAny(JavaTokenType.IDENTIFIER)) {
            Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected label name after 'continue'");
            label = Optional.of(new NameExpression(spanFrom(identifier), List.of(identifier.lexeme())));
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after continue statement");
        return new ContinueStatement(spanFrom(start), label);
    }

    private BreakStatement parseBreakStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.BREAK_KEYWORD, "Expected 'break' keyword");

        Optional<NameExpression> label = Optional.empty();
        if (nextIsAny(JavaTokenType.IDENTIFIER)) {
            Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected label name after 'break'");
            label = Optional.of(new NameExpression(spanFrom(identifier), List.of(identifier.lexeme())));
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after break statement");
        return new BreakStatement(spanFrom(start), label);
    }

    private ThrowStatement parseThrowStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.THROW_KEYWORD, "Expected 'throw' keyword");

        Expression expression = parseExpression();

        expect(JavaTokenType.SEMICOLON, "Expected ';' after throw statement");
        return new ThrowStatement(spanFrom(start), expression);
    }

    private ReturnStatement parseReturnStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.RETURN_KEYWORD, "Expected 'return' keyword");

        Optional<Expression> expression = Optional.empty();
        if (!nextIsAny(JavaTokenType.SEMICOLON)) {
            expression = Optional.of(parseExpression());
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after return statement");
        return new ReturnStatement(spanFrom(start), expression);
    }

    private SynchronizedStatement parseSynchronizedStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.SYNCHRONIZED_KEYWORD, "Expected 'synchronized' keyword");
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after 'synchronized'");

        Expression expression = parseExpression();

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after synchronized expression");

        BlockStatement body = parseBlock();
        return new SynchronizedStatement(spanFrom(start), expression, body);
    }

    private TryStatement parseTryStatement() {
        Span start = currentSpan();

        expect(JavaTokenType.TRY_KEYWORD, "Expected 'try' keyword");

        List<LocalVariableDeclarationStatement> resourceStatements = new ArrayList<>();
        if (match(JavaTokenType.OPEN_PAREN)) {
            do {
                resourceStatements.add(parseLocalVariableDeclarationStatement());
            } while (!isAtEnd() && !nextIsAny(JavaTokenType.CLOSE_PAREN));

            expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after try-with-resources resources");
        }

        BlockStatement tryBlock = parseBlock();

        List<CatchClause> catchClauses = new ArrayList<>();
        while (nextIsAny(JavaTokenType.CATCH_KEYWORD)) {
            catchClauses.add(parseCatchClause());
        }

        Optional<FinallyClause> finallyClause = Optional.empty();
        if (nextIsAny(JavaTokenType.FINALLY_KEYWORD))
            finallyClause = Optional.of(parseFinallyClause());

        return new TryStatement(
                spanFrom(start),
                resourceStatements,
                tryBlock,
                catchClauses,
                finallyClause);
    }

    private FinallyClause parseFinallyClause() {
        Span start = currentSpan();
        expect(JavaTokenType.FINALLY_KEYWORD, "Expected 'finally' keyword");
        BlockStatement body = parseBlock();
        return new FinallyClause(spanFrom(start), body);
    }

    private CatchClause parseCatchClause() {
        Span start = currentSpan();
        expect(JavaTokenType.CATCH_KEYWORD, "Expected 'catch' keyword");

        expect(JavaTokenType.OPEN_PAREN, "Expected '(' to start 'catch' clause");

        List<SugarTypeRef> exceptionTypes = new ArrayList<>();
        do {
            exceptionTypes.add(parseSugarTypeReference());
        } while (match(JavaTokenType.PIPE));

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected exception variable name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' to end 'catch' clause");

        BlockStatement body = parseBlock();

        return new CatchClause(
                spanFrom(start),
                exceptionTypes,
                name,
                body);
    }

    private SugarTypeRef parseSugarTypeReference() {
        Span start = currentSpan();
        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        TypeRef typeRef = parseTypeReference();
        return new SugarTypeRef(spanFrom(start), modifiers, annotations, typeRef);
    }

    private ForStatement parseForStatement() {
        Span start = currentSpan();

        expect(JavaTokenType.FOR_KEYWORD, "Expected 'for' keyword");
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after 'for'");

        boolean isEnhanced = isEnhancedForLoop();
        return isEnhanced ?
                parseEnhancedForStatement(start) :
                parseBasicForStatement(start);
    }

    private EnhancedForStatement parseEnhancedForStatement(Span start) {
        Parameter variableDeclaration = parseParameter();
        expect(JavaTokenType.COLON, "Expected ':' in enhanced for loop");
        Expression iterable = parseExpression();

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after enhanced for loop control");

        Statement body = parseStatement();
        return new EnhancedForStatement(
                spanFrom(start),
                variableDeclaration,
                iterable,
                body
        );
    }

    private BasicForStatement parseBasicForStatement(Span start) {
        Optional<Statement> initializer = Optional.ofNullable(tryParse($ -> parseLocalVariableDeclarationStatement()));

        Optional<Expression> condition = Optional.empty();
        if (!nextIsAny(JavaTokenType.SEMICOLON)) {
            condition = Optional.of(parseExpression());
        }

        expect(JavaTokenType.SEMICOLON, "Expected ';' after for loop condition");

        List<Expression> updaters = new ArrayList<>();
        if (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
            do {
                updaters.add(parseExpression());
            } while (match(JavaTokenType.COMMA));
        }
        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after for loop updaters");

        Statement body = parseStatement();
        return new BasicForStatement(
                spanFrom(start),
                initializer,
                condition,
                updaters,
                body
        );
    }

    private boolean isEnhancedForLoop() {
        Marker beforeSearchMarker = mark();

        int parenDepth = 0, bracketDepth = 0, braceDepth = 0, angleDepth = 0;
        while (!isAtEnd()) {
            JavaTokenType type = advance().type();
            if (type == JavaTokenType.CLOSE_PAREN && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                beforeSearchMarker.rollback();
                return false;
            }

            if (type == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
                continue;
            } else if (type == JavaTokenType.CLOSE_PAREN) {
                parenDepth--;
                continue;
            }

            if (type == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
                continue;
            } else if (type == JavaTokenType.CLOSE_BRACKET) {
                bracketDepth--;
                continue;
            }

            if (type == JavaTokenType.OPEN_BRACE) {
                braceDepth++;
                continue;
            } else if (type == JavaTokenType.CLOSE_BRACE) {
                braceDepth--;
                continue;
            }

            if (type == JavaTokenType.LEFT_ANGLED_BRACKET) {
                angleDepth++;
                continue;
            } else if (type == JavaTokenType.RIGHT_ANGLED_BRACKET) {
                angleDepth--;
                continue;
            }

            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                if (type == JavaTokenType.SEMICOLON) {
                    beforeSearchMarker.rollback();
                    return false;
                } else if (type == JavaTokenType.COLON) {
                    beforeSearchMarker.rollback();
                    return true;
                }
            }
        }

        beforeSearchMarker.rollback();
        return false;
    }

    private DoWhileStatement parseDoWhileStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.DO_KEYWORD, "Expected 'do' keyword");

        Statement body = parseStatement();

        expect(JavaTokenType.WHILE_KEYWORD, "Expected 'while' after 'do' statement body");
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after 'while'");

        Expression condition = parseExpression();

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after do-while condition");
        expect(JavaTokenType.SEMICOLON, "Expected ';' after do-while statement");

        return new DoWhileStatement(spanFrom(start), body, condition);
    }

    private WhileStatement parseWhileStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.WHILE_KEYWORD, "Expected 'while' keyword");
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after 'while'");

        Expression condition = parseExpression();

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after while condition");

        Statement body = parseStatement();
        return new WhileStatement(spanFrom(start), condition, body);
    }

    private SwitchStatement parseSwitchStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.SWITCH_KEYWORD, "Expected 'switch' keyword");
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after 'switch'");

        Expression expression = parseExpression();

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after switch statement");
        expect(JavaTokenType.OPEN_BRACE, "Expected '{' for switch statement");

        List<SwitchRule> switchRules = new ArrayList<>();
        while (nextIsAny(JavaTokenType.DEFAULT_KEYWORD, JavaTokenType.CASE_KEYWORD)) {
            switchRules.add(parseSwitchRule());
        }

        return new SwitchStatement(
                spanFrom(start),
                expression,
                switchRules
        );
    }

    private SwitchRule parseSwitchRule() {
        Span start = currentSpan();

        List<SwitchLabel> labels = new ArrayList<>();

        Span ruleStart = currentSpan();
        boolean isDefaultCase = expectAny(
                "'case' or 'default' keyword expected for switch rule",
                JavaTokenType.CASE_KEYWORD, JavaTokenType.DEFAULT_KEYWORD
        ).type() == JavaTokenType.DEFAULT_KEYWORD;
        if (isDefaultCase) {
            labels.add(new SwitchLabel.DefaultLabel(spanFrom(ruleStart)));
            if (nextIsAny(JavaTokenType.COMMA)) {
                do {
                    advance();
                    reportError("'default' cannot be combined with other labels in a switch rule");
                    synchronize(Set.of(JavaTokenType.COMMA, JavaTokenType.COLON, JavaTokenType.ARROW));
                } while (match(JavaTokenType.COMMA));
            }
        } else {
            do {
                Span labelStart = currentSpan();
                if (nextIsAny(JavaTokenType.DEFAULT_KEYWORD)) {
                    reportError("`default` cannot appear in a `case` label list for a switch rule");
                    advance();
                    labels.add(new SwitchLabel.DefaultLabel(spanFrom(labelStart)));
                    continue;
                }

                labels.add(parseCaseLabel(labelStart));
            } while (match(JavaTokenType.COMMA));
        }

        expectAny("Expected ':' or '->' after switch labels", JavaTokenType.COLON, JavaTokenType.ARROW);

        Statement body = parseStatement();
        return new SwitchRule(spanFrom(start), labels, body);
    }

    private SwitchLabel.CaseLabel parseCaseLabel(Span labelStart) {
        Span start = currentSpan();

        List<CaseItem> items = new ArrayList<>();
        do {
            items.add(parseCaseItem());
        } while (match(JavaTokenType.COMMA));

        return new SwitchLabel.CaseLabel(spanFrom(labelStart), items);
    }

    private CaseItem parseCaseItem() {
        Span start = currentSpan();

        if (match(JavaTokenType.NULL_LITERAL))
            return new CaseItem.CaseNull(spanFrom(start));

        return parsePatternCaseItem(start);
    }

    private CaseItem.CasePattern parsePatternCaseItem(Span start) {
        Pattern pattern = parsePattern();

        CaseItem.CasePattern.Guard guard = parseGuard();
        return new CaseItem.CasePattern(
                spanFrom(start),
                pattern,
                guard);
    }

    private Pattern parsePattern() {
        Span start = currentSpan();

        if (match(JavaTokenType.UNDERSCORE_KEYWORD))
            return new Pattern.MatchAllPattern(spanFrom(start));

        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);
        if (!modifiers.isEmpty() || !annotations.isEmpty()) {
            TypeRef type = parseTypeReference();
            Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected pattern variable name");
            var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

            return new Pattern.TypeTestPattern(spanFrom(start),
                    annotations, modifiers,
                    type, Optional.of(name));
        }

        Marker recordPatternMarker = mark();
        TypeRef type = parseTypeReference();
        if (nextIsAny(JavaTokenType.OPEN_PAREN)) {
            recordPatternMarker.commit();
            expect(JavaTokenType.OPEN_PAREN, "Expected '(' after type in record pattern");

            List<Pattern> components = new ArrayList<>();
            if (!nextIsAny(JavaTokenType.CLOSE_PAREN)) {
                do {
                    components.add(parsePattern());
                } while (match(JavaTokenType.COMMA));
            }

            expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after record pattern components");

            return new Pattern.RecordPattern(
                    spanFrom(start),
                    type,
                    components
            );
        }

        recordPatternMarker.rollback();

        Optional<NameExpression> variable = Optional.empty();
        if (nextIsAny(JavaTokenType.IDENTIFIER)) {
            Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected pattern variable name");
            variable = Optional.of(new NameExpression(spanFrom(identifier), List.of(identifier.lexeme())));
        }

        return new Pattern.TypeTestPattern(spanFrom(start),
                List.of(), List.of(),
                type, variable);
    }

    private CaseItem.CasePattern.@Nullable Guard parseGuard() {
        Span start = currentSpan();

        CaseItem.CasePattern.Guard guard = null;
        if (match(JavaTokenType.WHEN_KEYWORD)) {
            Expression expression = parseExpression();
            guard = new CaseItem.CasePattern.Guard(spanFrom(start), expression);
        }

        return guard;
    }

    private IfStatement parseIfStatement() {
        Span start = currentSpan();
        expect(JavaTokenType.IF_KEYWORD, "Expected 'if' keyword");
        expect(JavaTokenType.OPEN_PAREN, "Expected '(' after 'if'");

        Expression condition = parseExpression();

        expect(JavaTokenType.CLOSE_PAREN, "Expected ')' after if condition");

        Statement thenBranch = parseStatement();
        Optional<Statement> elseBranch = Optional.empty();
        if (match(JavaTokenType.ELSE_KEYWORD)) {
            elseBranch = Optional.of(parseStatement());
        }

        return new IfStatement(spanFrom(start), condition, thenBranch, elseBranch);
    }

    private Parameter parseParameter() {
        Span start = currentSpan();

        List<Modifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        parseModifiersAndAnnotations(modifiers, annotations);

        TypeRef type = parseTypeReference();
        boolean isVarArgs = match(JavaTokenType.ELLIPSIS);

        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Expected parameter name");
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        return new Parameter(spanFrom(start), modifiers, annotations, Optional.of(type), isVarArgs, name);
    }

    private CompactConstructorDeclaration parseCompactConstructor(Span start, NameExpression className, List<Modifier> modifiers, List<Annotation> annotations) {
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
        var name = new NameExpression(spanFrom(identifier), List.of(identifier.lexeme()));

        List<TypeRef> bounds = new ArrayList<>();
        if (match(JavaTokenType.EXTENDS_KEYWORD)) {
            do {
                bounds.add(parseTypeReference());
            } while (match(JavaTokenType.AMPERSAND));
        }

        return new TypeParameter(spanFrom(start), annotations, name, bounds);
    }

    // x   PRIMITIVE_TYPE, // e.g., int, boolean
    // x   ARRAY_TYPE, // e.g., int[], String[]
    // x   CLASS_OR_INTERFACE_TYPE, // e.g., List<String>
    // x   TYPE_VARIABLE, // <T>
    // x   WILDCARD_TYPE, // ? extends A, ? super B, or just ?
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
        if (nextToken == JavaTokenType.IDENTIFIER && lookahead2 != JavaTokenType.DOT && lookahead2 != JavaTokenType.LEFT_ANGLED_BRACKET) {
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
            parts.add(new ClassOrInterfaceTypeRef.Part(spanFrom(partStart), new NameExpression(spanFrom(identifierToken), List.of(identifierToken.lexeme())), typeArguments));
        } while (match(JavaTokenType.DOT));

        return new ClassOrInterfaceTypeRef(spanFrom(parts.getFirst().span()), parts);
    }

    private List<TypeRef> parseTypeArgumentsOptionally() {
        List<TypeRef> typeArguments = new ArrayList<>();
        if (match(JavaTokenType.LEFT_ANGLED_BRACKET)) {
            do {
                typeArguments.add(parseTypeArgument());
            } while (match(JavaTokenType.COMMA));

            expect(JavaTokenType.RIGHT_ANGLED_BRACKET, "Expected '>' after type arguments");
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
            if (nextIsAny(JavaTokenType.AT) && lookaheadType(2) == JavaTokenType.INTERFACE_KEYWORD)
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
        NameExpression name = parseQualifiedName();
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
        NameExpression moduleName = parseQualifiedName();
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
            NameExpression moduleName = parseQualifiedName();

            return Optional.of(new RequiresDirective(spanFrom(start), isStatic, isTransitive, moduleName));
        }

        if (match(JavaTokenType.EXPORTS_KEYWORD)) {
            NameExpression packageName = parseQualifiedName();
            List<NameExpression> toModules = new ArrayList<>();
            if (match(JavaTokenType.TO_KEYWORD)) {
                do {
                    toModules.add(parseQualifiedName());
                } while (match(JavaTokenType.COMMA));
            }

            return Optional.of(new ExportsDirective(spanFrom(start), packageName, toModules));
        }

        if (match(JavaTokenType.OPENS_KEYWORD)) {
            NameExpression packageName = parseQualifiedName();
            List<NameExpression> toModules = new ArrayList<>();
            if (match(JavaTokenType.TO_KEYWORD)) {
                do {
                    toModules.add(parseQualifiedName());
                } while (match(JavaTokenType.COMMA));
            }

            return Optional.of(new OpensDirective(spanFrom(start), packageName, toModules));
        }

        if (match(JavaTokenType.USES_KEYWORD)) {
            NameExpression serviceName = parseQualifiedName();
            return Optional.of(new UsesDirective(spanFrom(start), serviceName));
        }

        if (match(JavaTokenType.PROVIDES_KEYWORD)) {
            NameExpression serviceName = parseQualifiedName();
            expect(JavaTokenType.WITH_KEYWORD, "Expected 'with' in provides directive");
            List<NameExpression> implementationNames = new ArrayList<>();
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

    private @NotNull List<Annotation> parseAnnotations() {
        List<Annotation> annotations = new ArrayList<>();
        while (nextIsAny(JavaTokenType.AT)) {
            annotations.add(parseAnnotation());
        }

        return annotations;
    }

    private Annotation parseAnnotation() {
        Span start = currentSpan();

        expect(JavaTokenType.AT, "Expected '@' to start annotation");

        NameExpression name = parseQualifiedName();
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
            var elemName = new NameExpression(nameSpan, List.of(identifier));

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

        return (ElementValue) parseExpression();
    }

    private NameExpression parseQualifiedName() {
        Span start = currentSpan();

        List<String> parts = new ArrayList<>();
        Token<JavaTokenType> identifier = expect(JavaTokenType.IDENTIFIER, "Identifier expected");
        parts.add(identifier.lexeme());

        while (match(JavaTokenType.DOT)) {
            identifier = expect(JavaTokenType.IDENTIFIER, "Identifier expected after '.'");
            parts.add(identifier.lexeme());

            if (nextIsAny(JavaTokenType.DOT) && lookaheadType(2) == JavaTokenType.STAR)
                break;
        }

        return new NameExpression(spanFrom(start), parts);
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