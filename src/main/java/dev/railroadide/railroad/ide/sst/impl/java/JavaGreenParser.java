package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenElement;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenNode;
import dev.railroadide.railroad.ide.sst.syntax.internal.SyntaxInternalFactory;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@ApiStatus.Internal
final class JavaGreenParser {
    private static final Set<JavaTokenType> CONTEXTUAL_IDENTIFIER_TOKENS = Set.of(
            JavaTokenType.UNDERSCORE_KEYWORD,
            JavaTokenType.EXPORTS_KEYWORD,
            JavaTokenType.MODULE_KEYWORD,
            JavaTokenType.NON_SEALED_KEYWORD,
            JavaTokenType.OPEN_KEYWORD,
            JavaTokenType.OPENS_KEYWORD,
            JavaTokenType.PERMITS_KEYWORD,
            JavaTokenType.PROVIDES_KEYWORD,
            JavaTokenType.RECORD_KEYWORD,
            JavaTokenType.REQUIRES_KEYWORD,
            JavaTokenType.SEALED_KEYWORD,
            JavaTokenType.TO_KEYWORD,
            JavaTokenType.TRANSITIVE_KEYWORD,
            JavaTokenType.USES_KEYWORD,
            JavaTokenType.VAR_KEYWORD,
            JavaTokenType.WITH_KEYWORD,
            JavaTokenType.YIELD_KEYWORD,
            JavaTokenType.WHEN_KEYWORD
    );

    private static final Set<JavaTokenType> TYPE_DECLARATION_MODIFIERS = Set.of(
            JavaTokenType.PUBLIC_KEYWORD,
            JavaTokenType.PROTECTED_KEYWORD,
            JavaTokenType.PRIVATE_KEYWORD,
            JavaTokenType.ABSTRACT_KEYWORD,
            JavaTokenType.FINAL_KEYWORD,
            JavaTokenType.STATIC_KEYWORD,
            JavaTokenType.STRICTFP_KEYWORD,
            JavaTokenType.SEALED_KEYWORD,
            JavaTokenType.NON_SEALED_KEYWORD
    );

    private static final Set<JavaTokenType> COMPACT_CONSTRUCTOR_MODIFIERS = Set.of(
            JavaTokenType.PUBLIC_KEYWORD,
            JavaTokenType.PROTECTED_KEYWORD,
            JavaTokenType.PRIVATE_KEYWORD
    );

    private static final Set<JavaTokenType> MEMBER_MODIFIERS = Set.of(
            JavaTokenType.PUBLIC_KEYWORD,
            JavaTokenType.PROTECTED_KEYWORD,
            JavaTokenType.PRIVATE_KEYWORD,
            JavaTokenType.ABSTRACT_KEYWORD,
            JavaTokenType.DEFAULT_KEYWORD,
            JavaTokenType.FINAL_KEYWORD,
            JavaTokenType.STATIC_KEYWORD,
            JavaTokenType.STRICTFP_KEYWORD,
            JavaTokenType.SYNCHRONIZED_KEYWORD,
            JavaTokenType.NATIVE_KEYWORD,
            JavaTokenType.TRANSIENT_KEYWORD,
            JavaTokenType.VOLATILE_KEYWORD
    );

    private static final Set<JavaTokenType> PARAMETER_AND_LOCAL_MODIFIERS = Set.of(
            JavaTokenType.PUBLIC_KEYWORD,
            JavaTokenType.PROTECTED_KEYWORD,
            JavaTokenType.PRIVATE_KEYWORD,
            JavaTokenType.ABSTRACT_KEYWORD,
            JavaTokenType.DEFAULT_KEYWORD,
            JavaTokenType.FINAL_KEYWORD,
            JavaTokenType.STATIC_KEYWORD,
            JavaTokenType.STRICTFP_KEYWORD,
            JavaTokenType.SYNCHRONIZED_KEYWORD,
            JavaTokenType.NATIVE_KEYWORD,
            JavaTokenType.TRANSIENT_KEYWORD,
            JavaTokenType.VOLATILE_KEYWORD
    );

    private static final Set<JavaTokenType> PRIMITIVE_TYPE_TOKENS = Set.of(
            JavaTokenType.BOOLEAN_KEYWORD,
            JavaTokenType.BYTE_KEYWORD,
            JavaTokenType.SHORT_KEYWORD,
            JavaTokenType.INT_KEYWORD,
            JavaTokenType.LONG_KEYWORD,
            JavaTokenType.CHAR_KEYWORD,
            JavaTokenType.FLOAT_KEYWORD,
            JavaTokenType.DOUBLE_KEYWORD
    );

    private static final Set<JavaTokenType> TYPE_REFERENCE_FOLLOW_SET = Set.of(
            JavaTokenType.COMMA,
            JavaTokenType.SEMICOLON,
            JavaTokenType.CLOSE_PAREN,
            JavaTokenType.CLOSE_BRACKET,
            JavaTokenType.CLOSE_BRACE,
            JavaTokenType.OPEN_BRACE,
            JavaTokenType.OPEN_PAREN,
            JavaTokenType.COLON,
            JavaTokenType.ARROW,
            JavaTokenType.EQUALS,
            JavaTokenType.DOT,
            JavaTokenType.AMPERSAND,
            JavaTokenType.PIPE,
            JavaTokenType.QUESTION_MARK,
            JavaTokenType.EXTENDS_KEYWORD,
            JavaTokenType.IMPLEMENTS_KEYWORD,
            JavaTokenType.PERMITS_KEYWORD,
            JavaTokenType.THROWS_KEYWORD
    );

    private static final Set<JavaTokenType> EXPRESSION_FOLLOW_SET = Set.of(
            JavaTokenType.COMMA,
            JavaTokenType.SEMICOLON,
            JavaTokenType.CLOSE_PAREN,
            JavaTokenType.CLOSE_BRACKET,
            JavaTokenType.CLOSE_BRACE,
            JavaTokenType.COLON,
            JavaTokenType.ARROW,
            JavaTokenType.WHEN_KEYWORD
    );

    private static final Set<JavaTokenType> STATEMENT_FOLLOW_SET = Set.of(
            JavaTokenType.SEMICOLON,
            JavaTokenType.CLOSE_BRACE,
            JavaTokenType.CASE_KEYWORD,
            JavaTokenType.DEFAULT_KEYWORD,
            JavaTokenType.CATCH_KEYWORD,
            JavaTokenType.FINALLY_KEYWORD
    );

    private static final Set<JavaTokenType> TYPE_MEMBER_FOLLOW_SET = Set.of(
            JavaTokenType.SEMICOLON,
            JavaTokenType.CLOSE_BRACE
    );

    private final Lexer<JavaTokenType> lexer;
    private final List<Token<JavaTokenType>> tokens = new ArrayList<>();

    private int position;

    JavaGreenParser(Lexer<JavaTokenType> lexer) {
        this.lexer = Objects.requireNonNull(lexer, "lexer");
    }

    SyntaxTree parseSyntaxTree() {
        return SyntaxInternalFactory.treeFromGreenRoot(parseGreenTree());
    }

    GreenNode parseGreenTree() {
        readAllTokens();
        position = 0;
        return parseCompilationUnit();
    }

    private GreenNode parseCompilationUnit() {
        List<GreenElement> children = new ArrayList<>();

        GreenNode packageDeclaration = parseOptionalPackageDeclaration();
        if (packageDeclaration != null)
            children.add(packageDeclaration);

        while (true) {
            GreenNode importDeclaration = parseOptionalImportDeclaration();
            if (importDeclaration == null)
                break;

            children.add(importDeclaration);
        }

        GreenNode moduleDeclaration = parseOptionalModuleDeclaration();
        if (moduleDeclaration != null)
            children.add(moduleDeclaration);

        while (hasMoreTokens()) {
            // Keep trailing trivia attached to the compilation unit instead of
            // manufacturing an error node before EOF.
            consumeTrivia(children);
            if (!hasMoreTokens())
                break;

            Token<JavaTokenType> token = peek();
            if (isEof(token)) {
                children.add(consumeToken());
                break;
            }

            if (moduleDeclaration != null) {
                children.add(parseTopLevelRemainder(JavaSyntaxKinds.ERROR));
                continue;
            }

            GreenNode emptyTypeDeclaration = parseOptionalEmptyTypeDeclaration();
            if (emptyTypeDeclaration != null) {
                children.add(emptyTypeDeclaration);
                continue;
            }

            GreenNode typeDeclaration = parseOptionalTopLevelTypeDeclaration();
            if (typeDeclaration != null) {
                children.add(typeDeclaration);
                continue;
            }

            children.add(parseTopLevelRemainder(JavaSyntaxKinds.ERROR));
        }

        return greenNode(JavaSyntaxKinds.COMPILATION_UNIT, children);
    }

    private GreenNode parseOptionalPackageDeclaration() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        while (peekSignificantType() == JavaTokenType.AT) {
            children.add(parseAnnotation());
            consumeTrivia(children);
        }

        if (!matchSignificant(JavaTokenType.PACKAGE_KEYWORD, children)) {
            position = marker;
            return null;
        }

        children.add(parseQualifiedName());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.PACKAGE_DECLARATION, children);
    }

    private GreenNode parseOptionalImportDeclaration() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        if (!matchSignificant(JavaTokenType.IMPORT_KEYWORD, children)) {
            position = marker;
            return null;
        }

        matchSignificant(JavaTokenType.STATIC_KEYWORD, children);
        children.add(parseImportTarget());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.IMPORT_DECLARATION, children);
    }

    private GreenNode parseOptionalModuleDeclaration() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        while (peekSignificantType() == JavaTokenType.AT) {
            children.add(parseAnnotation());
            consumeTrivia(children);
        }

        matchSignificant(JavaTokenType.OPEN_KEYWORD, children);
        if (!matchSignificant(JavaTokenType.MODULE_KEYWORD, children)) {
            position = marker;
            return null;
        }

        children.add(parseQualifiedName());
        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        children.add(parseModuleBody());
        return greenNode(JavaSyntaxKinds.MODULE_DECLARATION, children);
    }

    private GreenNode parseModuleBody() {
        List<GreenElement> children = new ArrayList<>();
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            if (isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            children.add(parseModuleDirectiveOrError());
        }

        return greenNode(JavaSyntaxKinds.MODULE_BODY, children);
    }

    private GreenNode parseModuleDirectiveOrError() {
        JavaTokenType directive = peekSignificantType();
        if (directive == null)
            return greenNode(JavaSyntaxKinds.MODULE_UNKNOWN_DIRECTIVE, List.of(missingToken(JavaTokenType.SEMICOLON)));

        return switch (directive) {
            case REQUIRES_KEYWORD -> parseRequiresDirective();
            case EXPORTS_KEYWORD -> parseExportsDirective();
            case OPENS_KEYWORD -> parseOpensDirective();
            case USES_KEYWORD -> parseUsesDirective();
            case PROVIDES_KEYWORD -> parseProvidesDirective();
            default -> parseUnknownModuleDirective();
        };
    }

    private GreenNode parseRequiresDirective() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.REQUIRES_KEYWORD, children);
        while (true) {
            if (matchSignificant(JavaTokenType.STATIC_KEYWORD, children))
                continue;
            if (matchSignificant(JavaTokenType.TRANSITIVE_KEYWORD, children))
                continue;

            break;
        }

        children.add(parseQualifiedName());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.MODULE_REQUIRES_DIRECTIVE, children);
    }

    private GreenNode parseExportsDirective() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.EXPORTS_KEYWORD, children);
        children.add(parseQualifiedName());
        if (matchSignificant(JavaTokenType.TO_KEYWORD, children)) {
            children.add(parseQualifiedName());
            while (matchSignificant(JavaTokenType.COMMA, children)) {
                children.add(parseQualifiedName());
            }
        }

        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.MODULE_EXPORTS_DIRECTIVE, children);
    }

    private GreenNode parseOpensDirective() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPENS_KEYWORD, children);
        children.add(parseQualifiedName());
        if (matchSignificant(JavaTokenType.TO_KEYWORD, children)) {
            children.add(parseQualifiedName());
            while (matchSignificant(JavaTokenType.COMMA, children)) {
                children.add(parseQualifiedName());
            }
        }

        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.MODULE_OPENS_DIRECTIVE, children);
    }

    private GreenNode parseUsesDirective() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.USES_KEYWORD, children);
        children.add(parseQualifiedName());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.MODULE_USES_DIRECTIVE, children);
    }

    private GreenNode parseProvidesDirective() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.PROVIDES_KEYWORD, children);
        children.add(parseQualifiedName());
        expectSignificant(JavaTokenType.WITH_KEYWORD, children);
        children.add(parseQualifiedName());
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseQualifiedName());
        }

        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.MODULE_PROVIDES_DIRECTIVE, children);
    }

    private GreenNode parseUnknownModuleDirective() {
        List<GreenElement> children = new ArrayList<>();
        boolean foundTerminator = false;

        consumeTrivia(children);
        if (hasMoreTokens() && !isEof(peek()) && peek().type() != JavaTokenType.CLOSE_BRACE) {
            children.add(consumeToken());
        }

        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token) || token.type() == JavaTokenType.CLOSE_BRACE)
                break;

            children.add(consumeToken());
            if (token.type() == JavaTokenType.SEMICOLON) {
                foundTerminator = true;
                break;
            }
        }

        if (!foundTerminator)
            children.add(missingToken(JavaTokenType.SEMICOLON));

        return greenNode(JavaSyntaxKinds.MODULE_UNKNOWN_DIRECTIVE, children);
    }

    private GreenNode parseOptionalTopLevelTypeDeclaration() {
        int marker = position;
        List<GreenElement> prefix = parseTypeDeclarationPrefix();

        JavaTokenType tokenType = peekSignificantType();
        if (tokenType == JavaTokenType.CLASS_KEYWORD)
            return parseClassDeclaration(prefix);
        if (tokenType == JavaTokenType.INTERFACE_KEYWORD)
            return parseInterfaceDeclaration(prefix);
        if (tokenType == JavaTokenType.ENUM_KEYWORD)
            return parseEnumDeclaration(prefix);
        if (tokenType == JavaTokenType.AT_INTERFACE_KEYWORD)
            return parseAnnotationTypeDeclaration(prefix);
        if (tokenType == JavaTokenType.RECORD_KEYWORD)
            return parseRecordDeclaration(prefix);

        position = marker;
        return null;
    }

    private GreenNode parseClassDeclaration(List<GreenElement> prefix) {
        List<GreenElement> children = new ArrayList<>(prefix);

        expectSignificant(JavaTokenType.CLASS_KEYWORD, children);
        Token<JavaTokenType> classNameToken = consumeIdentifierLike(children);
        if (classNameToken == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        GreenNode typeParameters = parseOptionalTypeParameters();
        if (typeParameters != null)
            children.add(typeParameters);

        GreenNode extendsClause = parseOptionalTypeClause(JavaTokenType.EXTENDS_KEYWORD, JavaSyntaxKinds.EXTENDS_CLAUSE);
        if (extendsClause != null)
            children.add(extendsClause);

        GreenNode implementsClause = parseOptionalTypeClause(JavaTokenType.IMPLEMENTS_KEYWORD, JavaSyntaxKinds.IMPLEMENTS_CLAUSE);
        if (implementsClause != null)
            children.add(implementsClause);

        GreenNode permitsClause = parseOptionalTypeClause(JavaTokenType.PERMITS_KEYWORD, JavaSyntaxKinds.PERMITS_CLAUSE);
        if (permitsClause != null)
            children.add(permitsClause);

        String className = classNameToken == null ? null : classNameToken.lexeme();
        children.add(parseTypeBody(JavaSyntaxKinds.CLASS_BODY, JavaSyntaxKinds.TYPE_MEMBER, className, true, true));
        return greenNode(JavaSyntaxKinds.CLASS_DECLARATION, children);
    }

    private GreenNode parseInterfaceDeclaration(List<GreenElement> prefix) {
        List<GreenElement> children = new ArrayList<>(prefix);

        expectSignificant(JavaTokenType.INTERFACE_KEYWORD, children);
        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        GreenNode typeParameters = parseOptionalTypeParameters();
        if (typeParameters != null)
            children.add(typeParameters);

        GreenNode extendsClause = parseOptionalTypeClause(JavaTokenType.EXTENDS_KEYWORD, JavaSyntaxKinds.EXTENDS_CLAUSE);
        if (extendsClause != null)
            children.add(extendsClause);

        GreenNode permitsClause = parseOptionalTypeClause(JavaTokenType.PERMITS_KEYWORD, JavaSyntaxKinds.PERMITS_CLAUSE);
        if (permitsClause != null)
            children.add(permitsClause);

        children.add(parseTypeBody(JavaSyntaxKinds.INTERFACE_BODY, JavaSyntaxKinds.TYPE_MEMBER, null, false, false));
        return greenNode(JavaSyntaxKinds.INTERFACE_DECLARATION, children);
    }

    private GreenNode parseEnumDeclaration(List<GreenElement> prefix) {
        List<GreenElement> children = new ArrayList<>(prefix);

        expectSignificant(JavaTokenType.ENUM_KEYWORD, children);
        Token<JavaTokenType> enumNameToken = consumeIdentifierLike(children);
        if (enumNameToken == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        GreenNode implementsClause = parseOptionalTypeClause(JavaTokenType.IMPLEMENTS_KEYWORD, JavaSyntaxKinds.IMPLEMENTS_CLAUSE);
        if (implementsClause != null)
            children.add(implementsClause);

        String enumName = enumNameToken == null ? null : enumNameToken.lexeme();
        children.add(parseEnumBody(enumName));
        return greenNode(JavaSyntaxKinds.ENUM_DECLARATION, children);
    }

    private GreenNode parseAnnotationTypeDeclaration(List<GreenElement> prefix) {
        List<GreenElement> children = new ArrayList<>(prefix);

        expectSignificant(JavaTokenType.AT_INTERFACE_KEYWORD, children);
        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        children.add(parseTypeBody(JavaSyntaxKinds.ANNOTATION_TYPE_BODY, JavaSyntaxKinds.ANNOTATION_TYPE_MEMBER, null, false, false));
        return greenNode(JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION, children);
    }

    private GreenNode parseRecordDeclaration(List<GreenElement> prefix) {
        List<GreenElement> children = new ArrayList<>(prefix);

        expectSignificant(JavaTokenType.RECORD_KEYWORD, children);
        Token<JavaTokenType> recordNameToken = consumeIdentifierLike(children);
        if (recordNameToken == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        GreenNode typeParameters = parseOptionalTypeParameters();
        if (typeParameters != null)
            children.add(typeParameters);

        children.add(parseRecordHeader());

        GreenNode implementsClause = parseOptionalTypeClause(JavaTokenType.IMPLEMENTS_KEYWORD, JavaSyntaxKinds.IMPLEMENTS_CLAUSE);
        if (implementsClause != null)
            children.add(implementsClause);

        String recordName = recordNameToken == null ? null : recordNameToken.lexeme();
        children.add(parseRecordBody(recordName));
        return greenNode(JavaSyntaxKinds.RECORD_DECLARATION, children);
    }

    private GreenNode parseOptionalTypeClause(JavaTokenType introducer, SyntaxKind kind) {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        if (!matchSignificant(introducer, children)) {
            position = marker;
            return null;
        }

        children.add(parseTypeReference());
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseTypeReference());
        }

        return greenNode(kind, children);
    }

    private GreenNode parseTypeReference() {
        int checkpoint = mark();
        GreenNode typeReference = parseTypeReferenceCore();
        if (madeProgress(checkpoint))
            return typeReference;

        rollback(checkpoint);
        return recoverTypeReferenceNode();
    }

    private GreenNode parseTypeReferenceCore() {
        GreenNode left = parseSingleTypeReference();
        JavaTokenType separator = peekSignificantType();
        if (separator != JavaTokenType.AMPERSAND && separator != JavaTokenType.PIPE)
            return left;

        SyntaxKind compositeKind = separator == JavaTokenType.AMPERSAND ?
                JavaSyntaxKinds.INTERSECTION_TYPE_REFERENCE :
                JavaSyntaxKinds.UNION_TYPE_REFERENCE;
        List<GreenElement> children = new ArrayList<>();
        children.add(left);
        while (matchSignificant(separator, children)) {
            children.add(parseSingleTypeReference());
        }

        return greenNode(compositeKind, children);
    }

    private GreenNode parseOptionalTypeParameters() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        if (!matchSignificant(JavaTokenType.LEFT_ANGLED_BRACKET, children)) {
            position = marker;
            return null;
        }

        if (isTypeArgumentListCloseToken(peekSignificantType())) {
            children.add(missingToken(JavaTokenType.IDENTIFIER));
            matchAnyTypeArgumentListClose(children);
            return greenNode(JavaSyntaxKinds.TYPE_PARAMETERS, children);
        }

        children.add(parseTypeParameter());
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseTypeParameter());
        }

        expectTypeArgumentListClose(children);
        return greenNode(JavaSyntaxKinds.TYPE_PARAMETERS, children);
    }

    private GreenNode parseSingleTypeReference() {
        GreenNode nonArrayType = parseNonArrayTypeReference();
        List<GreenElement> dimensions = parseOptionalDimensions();
        if (dimensions.isEmpty())
            return nonArrayType;

        List<GreenElement> children = new ArrayList<>();
        children.add(nonArrayType);
        children.addAll(dimensions);
        return greenNode(JavaSyntaxKinds.TYPE_REFERENCE, children);
    }

    private GreenNode parseNonArrayTypeReference() {
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        consumeTypeUseAnnotations(children);
        JavaTokenType tokenType = peekSignificantType();

        if (tokenType == JavaTokenType.QUESTION_MARK) {
            expectSignificant(JavaTokenType.QUESTION_MARK, children);
            if (matchSignificant(JavaTokenType.EXTENDS_KEYWORD, children) || matchSignificant(JavaTokenType.SUPER_KEYWORD, children))
                children.add(parseTypeBound());

            return greenNode(JavaSyntaxKinds.WILDCARD_TYPE, children);
        }

        if (tokenType == JavaTokenType.VOID_KEYWORD || isPrimitiveTypeToken(tokenType)) {
            children.add(consumeToken());
            return greenNode(JavaSyntaxKinds.TYPE_REFERENCE, children);
        }

        if (tokenType == JavaTokenType.AT || isIdentifierLike(tokenType))
            return parseClassOrInterfaceTypeReference(children);

        if (!isTypeReferenceFollowToken(tokenType) && tokenType != null && tokenType != JavaTokenType.EOF) {
            children.add(consumeToken());
        } else {
            children.add(missingToken(JavaTokenType.IDENTIFIER));
        }

        return greenNode(JavaSyntaxKinds.TYPE_REFERENCE, children);
    }

    private GreenNode parseClassOrInterfaceTypeReference(List<GreenElement> prefix) {
        List<GreenElement> children = new ArrayList<>(prefix);

        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        GreenNode typeArguments = parseOptionalTypeArguments();
        if (typeArguments != null)
            children.add(typeArguments);

        while (true) {
            int dotIndex = nextSignificantIndex(position);
            if (dotIndex < 0 || tokens.get(dotIndex).type() != JavaTokenType.DOT)
                break;

            int afterDotIndex = nextSignificantIndex(dotIndex + 1);
            if (afterDotIndex >= 0 && tokens.get(afterDotIndex).type() == JavaTokenType.CLASS_KEYWORD)
                break;

            if (!matchSignificant(JavaTokenType.DOT, children))
                break;

            consumeTypeUseAnnotations(children);
            if (consumeIdentifierLike(children) == null) {
                children.add(missingToken(JavaTokenType.IDENTIFIER));
                break;
            }

            typeArguments = parseOptionalTypeArguments();
            if (typeArguments != null)
                children.add(typeArguments);
        }

        return greenNode(JavaSyntaxKinds.TYPE_REFERENCE, children);
    }

    private GreenNode parseTypeParameter() {
        List<GreenElement> children = new ArrayList<>();
        consumeTrivia(children);
        consumeTypeUseAnnotations(children);

        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        if (matchSignificant(JavaTokenType.EXTENDS_KEYWORD, children))
            children.add(parseTypeBound());

        return greenNode(JavaSyntaxKinds.TYPE_PARAMETER, children);
    }

    private GreenNode parseOptionalTypeArguments() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();
        if (!matchSignificant(JavaTokenType.LEFT_ANGLED_BRACKET, children)) {
            position = marker;
            return null;
        }

        if (matchAnyTypeArgumentListClose(children))
            return greenNode(JavaSyntaxKinds.DIAMOND_TYPE_ARGUMENTS, children);

        children.add(parseTypeArgument());
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseTypeArgument());
        }

        expectTypeArgumentListClose(children);
        return greenNode(JavaSyntaxKinds.TYPE_ARGUMENTS, children);
    }

    private GreenNode parseTypeArgument() {
        return parseTypeReference();
    }

    private GreenNode parseTypeBound() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseSingleTypeReference());
        while (matchSignificant(JavaTokenType.AMPERSAND, children)) {
            children.add(parseSingleTypeReference());
        }

        return greenNode(JavaSyntaxKinds.TYPE_BOUND, children);
    }

    private List<GreenElement> parseOptionalDimensions() {
        List<GreenElement> dimensions = new ArrayList<>();
        while (true) {
            int marker = position;
            List<GreenElement> dimensionChildren = new ArrayList<>();

            consumeTrivia(dimensionChildren);
            consumeTypeUseAnnotations(dimensionChildren);
            int openBracketIndex = nextSignificantIndex(position);
            if (openBracketIndex < 0 || tokens.get(openBracketIndex).type() != JavaTokenType.OPEN_BRACKET) {
                position = marker;
                break;
            }

            int closeBracketIndex = nextSignificantIndex(openBracketIndex + 1);
            if (closeBracketIndex < 0 || tokens.get(closeBracketIndex).type() != JavaTokenType.CLOSE_BRACKET) {
                position = marker;
                break;
            }

            matchSignificant(JavaTokenType.OPEN_BRACKET, dimensionChildren);
            expectSignificant(JavaTokenType.CLOSE_BRACKET, dimensionChildren);
            dimensions.add(greenNode(JavaSyntaxKinds.ARRAY_DIMENSION, dimensionChildren));
        }

        return dimensions;
    }

    private void appendOptionalDimensions(List<GreenElement> children) {
        children.addAll(parseOptionalDimensions());
    }

    private void consumeTypeUseAnnotations(List<GreenElement> children) {
        while (peekSignificantType() == JavaTokenType.AT) {
            children.add(parseAnnotation());
            consumeTrivia(children);
        }
    }

    private void expectTypeArgumentListClose(List<GreenElement> children) {
        if (!matchAnyTypeArgumentListClose(children))
            children.add(missingToken(JavaTokenType.RIGHT_ANGLED_BRACKET));
    }

    private boolean matchAnyTypeArgumentListClose(List<GreenElement> children) {
        consumeTrivia(children);
        if (!hasMoreTokens())
            return false;

        JavaTokenType tokenType = peek().type();
        if (!isTypeArgumentListCloseToken(tokenType))
            return false;

        children.add(consumeToken());
        return true;
    }

    private static boolean isTypeArgumentListCloseToken(JavaTokenType tokenType) {
        return tokenType == JavaTokenType.RIGHT_ANGLED_BRACKET ||
                tokenType == JavaTokenType.RIGHT_SHIFT ||
                tokenType == JavaTokenType.UNSIGNED_RIGHT_SHIFT;
    }

    private GreenNode parseRecordHeader() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_PAREN) {
                children.add(consumeToken());
                break;
            }

            children.add(parseRecordComponent());
            consumeTrivia(children);
            if (matchSignificant(JavaTokenType.COMMA, children))
                continue;

            next = peekSignificantToken();
            if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_PAREN)
                children.add(missingToken(JavaTokenType.COMMA));
        }

        return greenNode(JavaSyntaxKinds.RECORD_HEADER, children);
    }

    private GreenNode parseRecordComponent() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        consumeModifiersAndAnnotations(children, JavaGreenParser::isParameterOrLocalModifier);
        children.add(parseTypeReference());
        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        appendOptionalDimensions(children);
        if (position == marker) {
            Token<JavaTokenType> token = peekSignificantToken();
            if (token != null && !isEof(token) && token.type() != JavaTokenType.COMMA && token.type() != JavaTokenType.CLOSE_PAREN)
                children.add(consumeToken());
        }

        return greenNode(JavaSyntaxKinds.RECORD_COMPONENT, children);
    }

    private GreenNode parseTypeBody(SyntaxKind bodyKind, SyntaxKind memberKind, String ownerName, boolean allowConstructors, boolean allowInitializers) {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            if (isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            GreenNode emptyTypeDeclaration = parseOptionalEmptyTypeDeclaration();
            if (emptyTypeDeclaration != null) {
                children.add(emptyTypeDeclaration);
                continue;
            }

            GreenNode nestedTypeDeclaration = parseOptionalTopLevelTypeDeclaration();
            if (nestedTypeDeclaration != null) {
                children.add(nestedTypeDeclaration);
                continue;
            }

            children.add(parseTypeBodyMemberWithRecovery(memberKind, ownerName, allowConstructors, allowInitializers));
        }

        return greenNode(bodyKind, children);
    }

    private GreenNode parseRecordBody(String recordName) {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            if (isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            GreenNode emptyTypeDeclaration = parseOptionalEmptyTypeDeclaration();
            if (emptyTypeDeclaration != null) {
                children.add(emptyTypeDeclaration);
                continue;
            }

            GreenNode nestedTypeDeclaration = parseOptionalTopLevelTypeDeclaration();
            if (nestedTypeDeclaration != null) {
                children.add(nestedTypeDeclaration);
                continue;
            }

            GreenNode compactConstructor = parseOptionalRecordCompactConstructor(recordName);
            if (compactConstructor != null) {
                children.add(compactConstructor);
                continue;
            }

            children.add(parseTypeBodyMemberWithRecovery(JavaSyntaxKinds.TYPE_MEMBER, recordName, true, true));
        }

        return greenNode(JavaSyntaxKinds.RECORD_BODY, children);
    }

    private GreenNode parseOptionalRecordCompactConstructor(String recordName) {
        if (recordName == null || recordName.isBlank())
            return null;

        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        boolean consumedPrefix;
        do {
            consumedPrefix = false;
            while (peekSignificantType() == JavaTokenType.AT) {
                children.add(parseAnnotation());
                consumeTrivia(children);
                consumedPrefix = true;
            }

            if (isCompactConstructorModifier(peekSignificantType())) {
                children.add(consumeToken());
                consumeTrivia(children);
                consumedPrefix = true;
            }
        } while (consumedPrefix);

        Token<JavaTokenType> nameToken = consumeIdentifierLike(children);
        if (nameToken == null || !recordName.equals(nameToken.lexeme())) {
            position = marker;
            return null;
        }

        consumeTrivia(children);
        if (peekSignificantType() != JavaTokenType.OPEN_BRACE) {
            position = marker;
            return null;
        }

        children.add(parseBlock());
        return greenNode(JavaSyntaxKinds.RECORD_COMPACT_CONSTRUCTOR, children);
    }

    private GreenNode parseEnumBody(String enumName) {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        consumeTrivia(children);

        Token<JavaTokenType> next = peekSignificantToken();
        if (canStartEnumConstant(next)) {
            children.add(parseEnumConstant());
            while (matchSignificant(JavaTokenType.COMMA, children)) {
                consumeTrivia(children);
                Token<JavaTokenType> candidate = peekSignificantToken();
                if (!canStartEnumConstant(candidate))
                    break;

                children.add(parseEnumConstant());
            }
        }

        matchSignificant(JavaTokenType.SEMICOLON, children);

        while (hasMoreTokens()) {
            consumeTrivia(children);
            next = peekSignificantToken();
            if (next == null) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            if (isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            GreenNode emptyTypeDeclaration = parseOptionalEmptyTypeDeclaration();
            if (emptyTypeDeclaration != null) {
                children.add(emptyTypeDeclaration);
                continue;
            }

            GreenNode nestedTypeDeclaration = parseOptionalTopLevelTypeDeclaration();
            if (nestedTypeDeclaration != null) {
                children.add(nestedTypeDeclaration);
                continue;
            }

            children.add(parseTypeBodyMemberWithRecovery(JavaSyntaxKinds.TYPE_MEMBER, enumName, true, true));
        }

        return greenNode(JavaSyntaxKinds.ENUM_BODY, children);
    }

    private GreenNode parseEnumConstant() {
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        while (peekSignificantType() == JavaTokenType.AT) {
            children.add(parseAnnotation());
            consumeTrivia(children);
        }

        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        if (matchSignificant(JavaTokenType.OPEN_PAREN, children)) {
            consumeParenthesizedTail(children);
        }

        if (peekSignificantType() == JavaTokenType.OPEN_BRACE) {
            children.add(parseBlock());
        }

        return greenNode(JavaSyntaxKinds.ENUM_CONSTANT, children);
    }

    private static boolean canStartEnumConstant(Token<JavaTokenType> token) {
        if (token == null || isEof(token))
            return false;

        JavaTokenType tokenType = token.type();
        if (tokenType == JavaTokenType.CLOSE_BRACE || tokenType == JavaTokenType.SEMICOLON)
            return false;

        return tokenType == JavaTokenType.AT || isIdentifierLike(tokenType);
    }

    private void consumeParenthesizedTail(List<GreenElement> children) {
        int depth = 1;
        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token)) {
                children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                break;
            }

            JavaTokenType tokenType = token.type();
            children.add(consumeToken());
            if (tokenType == JavaTokenType.OPEN_PAREN) {
                depth++;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN) {
                depth--;
                if (depth == 0)
                    break;
            }
        }
    }

    private GreenNode parseOptionalEmptyTypeDeclaration() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        if (!matchSignificant(JavaTokenType.SEMICOLON, children)) {
            position = marker;
            return null;
        }

        return greenNode(JavaSyntaxKinds.EMPTY_TYPE_DECLARATION, children);
    }

    private GreenNode parseTypeBodyMember(SyntaxKind memberKind, String ownerName, boolean allowConstructors, boolean allowInitializers) {
        if (allowInitializers) {
            GreenNode staticInitializer = parseOptionalStaticInitializer();
            if (staticInitializer != null)
                return staticInitializer;

            GreenNode instanceInitializer = parseOptionalInstanceInitializer();
            if (instanceInitializer != null)
                return instanceInitializer;
        }

        GreenNode declaration = parseOptionalMemberDeclaration(ownerName, allowConstructors);
        if (declaration != null)
            return declaration;

        return parseTypeBodyMemberFallback(memberKind);
    }

    private GreenNode parseTypeBodyMemberWithRecovery(SyntaxKind memberKind, String ownerName, boolean allowConstructors, boolean allowInitializers) {
        int checkpoint = mark();
        GreenNode member = parseTypeBodyMember(memberKind, ownerName, allowConstructors, allowInitializers);
        if (madeProgress(checkpoint))
            return member;

        rollback(checkpoint);
        return recoverErrorNode(TYPE_MEMBER_FOLLOW_SET, JavaTokenType.SEMICOLON);
    }

    private GreenNode parseOptionalStaticInitializer() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        if (!matchSignificant(JavaTokenType.STATIC_KEYWORD, children)) {
            position = marker;
            return null;
        }

        if (peekSignificantType() != JavaTokenType.OPEN_BRACE) {
            position = marker;
            return null;
        }

        children.add(parseBlock());
        return greenNode(JavaSyntaxKinds.STATIC_INITIALIZER, children);
    }

    private GreenNode parseOptionalInstanceInitializer() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        consumeTrivia(children);
        if (peekSignificantType() != JavaTokenType.OPEN_BRACE) {
            position = marker;
            return null;
        }

        children.add(parseBlock());
        return greenNode(JavaSyntaxKinds.INSTANCE_INITIALIZER, children);
    }

    private GreenNode parseOptionalMemberDeclaration(String ownerName, boolean allowConstructors) {
        int marker = position;
        List<GreenElement> prefix = parseMemberPrefix();
        int afterPrefix = position;

        if (allowConstructors) {
            GreenNode constructorDeclaration = parseOptionalConstructorDeclaration(prefix, ownerName);
            if (constructorDeclaration != null)
                return constructorDeclaration;

            position = afterPrefix;
        }

        GreenNode fieldOrMethodDeclaration = parseOptionalFieldOrMethodDeclaration(prefix);
        if (fieldOrMethodDeclaration != null)
            return fieldOrMethodDeclaration;

        position = marker;
        return null;
    }

    private GreenNode parseOptionalConstructorDeclaration(List<GreenElement> prefix, String ownerName) {
        if (ownerName == null || ownerName.isBlank())
            return null;

        int marker = position;
        List<GreenElement> children = new ArrayList<>(prefix);

        Token<JavaTokenType> nameToken = consumeIdentifierLike(children);
        if (nameToken == null || !ownerName.equals(nameToken.lexeme())) {
            position = marker;
            return null;
        }

        if (peekSignificantType() != JavaTokenType.OPEN_PAREN) {
            position = marker;
            return null;
        }

        children.add(parseParameterList());

        GreenNode throwsClause = parseOptionalThrowsClause();
        if (throwsClause != null)
            children.add(throwsClause);

        if (peekSignificantType() == JavaTokenType.OPEN_BRACE) {
            children.add(parseBlock());
        } else {
            expectSignificant(JavaTokenType.SEMICOLON, children);
        }

        return greenNode(JavaSyntaxKinds.CONSTRUCTOR_DECLARATION, children);
    }

    private GreenNode parseOptionalFieldOrMethodDeclaration(List<GreenElement> prefix) {
        int marker = position;
        List<GreenElement> children = new ArrayList<>(prefix);

        GreenNode memberType = parseMemberType();
        if (memberType == null) {
            position = marker;
            return null;
        }

        children.add(memberType);

        List<GreenElement> nameTokens = new ArrayList<>();
        if (consumeIdentifierLike(nameTokens) == null)
            nameTokens.add(missingToken(JavaTokenType.IDENTIFIER));

        if (peekSignificantType() == JavaTokenType.OPEN_PAREN) {
            children.addAll(nameTokens);
            children.add(parseParameterList());
            appendOptionalDimensions(children);

            GreenNode throwsClause = parseOptionalThrowsClause();
            if (throwsClause != null)
                children.add(throwsClause);

            if (matchSignificant(JavaTokenType.DEFAULT_KEYWORD, children)) {
                consumeUntilSemicolonOrBoundary(children);
                expectSignificant(JavaTokenType.SEMICOLON, children);
            } else if (peekSignificantType() == JavaTokenType.OPEN_BRACE) {
                children.add(parseBlock());
            } else {
                expectSignificant(JavaTokenType.SEMICOLON, children);
            }

            return greenNode(JavaSyntaxKinds.METHOD_DECLARATION, children);
        }

        children.add(parseVariableDeclaratorFromNameTokens(nameTokens));
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseVariableDeclarator());
        }

        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.FIELD_DECLARATION, children);
    }

    private GreenNode parseMemberType() {
        int marker = position;
        JavaTokenType tokenType = peekSignificantType();
        if (tokenType == JavaTokenType.VOID_KEYWORD) {
            List<GreenElement> children = new ArrayList<>();
            expectSignificant(JavaTokenType.VOID_KEYWORD, children);
            return greenNode(JavaSyntaxKinds.TYPE_REFERENCE, children);
        }

        if (tokenType == JavaTokenType.AT || isPrimitiveTypeToken(tokenType) || isIdentifierLike(tokenType))
            return parseTypeReference();

        position = marker;
        return null;
    }

    private GreenNode parseVariableDeclarator() {
        List<GreenElement> nameTokens = new ArrayList<>();
        if (consumeIdentifierLike(nameTokens) == null)
            nameTokens.add(missingToken(JavaTokenType.IDENTIFIER));

        return parseVariableDeclaratorFromNameTokens(nameTokens);
    }

    private GreenNode parseVariableDeclaratorFromNameTokens(List<GreenElement> nameTokens) {
        List<GreenElement> children = new ArrayList<>(nameTokens);

        appendOptionalDimensions(children);

        if (matchSignificant(JavaTokenType.EQUALS, children)) {
            if (peekSignificantType() == JavaTokenType.OPEN_BRACE) {
                children.add(parseArrayInitializerExpression());
            } else {
                children.add(parseExpression());
            }
        }

        return greenNode(JavaSyntaxKinds.VARIABLE_DECLARATOR, children);
    }

    private GreenNode parseParameterList() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_PAREN) {
                children.add(consumeToken());
                break;
            }

            children.add(parseParameter());
            consumeTrivia(children);
            if (matchSignificant(JavaTokenType.COMMA, children))
                continue;

            next = peekSignificantToken();
            if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_PAREN)
                children.add(missingToken(JavaTokenType.COMMA));
        }

        return greenNode(JavaSyntaxKinds.PARAMETER_LIST, children);
    }

    private GreenNode parseParameter() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();
        consumeModifiersAndAnnotations(children, JavaGreenParser::isParameterOrLocalModifier);
        children.add(parseTypeReference());
        consumeTypeUseAnnotations(children);
        matchSignificant(JavaTokenType.ELLIPSIS, children);
        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));
        appendOptionalDimensions(children);
        if (position == marker) {
            Token<JavaTokenType> token = peekSignificantToken();
            if (token != null && !isEof(token) && token.type() != JavaTokenType.COMMA && token.type() != JavaTokenType.CLOSE_PAREN)
                children.add(consumeToken());
        }

        return greenNode(JavaSyntaxKinds.PARAMETER, children);
    }

    private GreenNode parseOptionalThrowsClause() {
        int marker = position;
        List<GreenElement> children = new ArrayList<>();

        if (!matchSignificant(JavaTokenType.THROWS_KEYWORD, children)) {
            position = marker;
            return null;
        }

        children.add(parseTypeReference());
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseTypeReference());
        }

        return greenNode(JavaSyntaxKinds.THROWS_CLAUSE, children);
    }

    private void consumeVariableInitializer(List<GreenElement> children) {
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;

        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token))
                break;

            JavaTokenType tokenType = token.type();
            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                if (tokenType == JavaTokenType.COMMA || tokenType == JavaTokenType.SEMICOLON || tokenType == JavaTokenType.CLOSE_BRACE)
                    break;
            }

            children.add(consumeToken());
            if (tokenType == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN && parenDepth > 0) {
                parenDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACKET && bracketDepth > 0) {
                bracketDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACE) {
                braceDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACE && braceDepth > 0) {
                braceDepth--;
            }

            angleDepth = adjustAngleDepthForToken(angleDepth, tokenType);
        }
    }

    private void consumeUntilSemicolonOrBoundary(List<GreenElement> children) {
        synchronizeToFollowSet(children, Set.of(JavaTokenType.SEMICOLON, JavaTokenType.CLOSE_BRACE), true);
    }

    private GreenNode parseTypeBodyMemberFallback(SyntaxKind memberKind) {
        List<GreenElement> children = new ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;
        int angleDepth = 0;

        consumeTrivia(children);
        if (!hasMoreTokens())
            return greenNode(memberKind, children);

        Token<JavaTokenType> first = peekSignificantToken();
        if (first != null && first.type() == JavaTokenType.OPEN_BRACE) {
            children.add(parseBlock());
            return greenNode(memberKind, children);
        }

        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token))
                break;

            JavaTokenType tokenType = token.type();
            if (tokenType == JavaTokenType.CLOSE_BRACE && parenDepth == 0 && bracketDepth == 0 && angleDepth == 0)
                break;

            if (tokenType == JavaTokenType.OPEN_BRACE && parenDepth == 0 && bracketDepth == 0 && angleDepth == 0) {
                children.add(parseBlock());
                break;
            }

            children.add(consumeToken());
            if (tokenType == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN && parenDepth > 0) {
                parenDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACKET && bracketDepth > 0) {
                bracketDepth--;
            }

            angleDepth = adjustAngleDepthForToken(angleDepth, tokenType);
            if (tokenType == JavaTokenType.SEMICOLON && parenDepth == 0 && bracketDepth == 0 && angleDepth == 0)
                break;
        }

        if (children.isEmpty() && hasMoreTokens() && !isEof(peek()))
            children.add(consumeToken());

        return greenNode(memberKind, children);
    }

    private GreenNode parseTopLevelRemainder(SyntaxKind kind) {
        List<GreenElement> children = new ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;

        consumeTrivia(children);
        if (!hasMoreTokens())
            return greenNode(kind, children);

        Token<JavaTokenType> token = peekSignificantToken();
        if (token != null && token.type() == JavaTokenType.OPEN_BRACE) {
            children.add(parseBlock());
            return greenNode(kind, children);
        }

        while (hasMoreTokens()) {
            token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token))
                break;

            if (token.type() == JavaTokenType.OPEN_BRACE && parenDepth == 0 && bracketDepth == 0) {
                children.add(parseBlock());
                break;
            }

            children.add(consumeToken());
            if (token.type() == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
            } else if (token.type() == JavaTokenType.CLOSE_PAREN && parenDepth > 0) {
                parenDepth--;
            } else if (token.type() == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
            } else if (token.type() == JavaTokenType.CLOSE_BRACKET && bracketDepth > 0) {
                bracketDepth--;
            }

            if (token.type() == JavaTokenType.SEMICOLON && parenDepth == 0 && bracketDepth == 0)
                break;
        }

        if (children.isEmpty() && hasMoreTokens() && !isEof(peek()))
            children.add(consumeToken());

        return greenNode(kind, children);
    }

    private GreenNode parseExpression() {
        int checkpoint = mark();
        GreenNode expression;
        if (isLambdaHeader()) {
            expression = parseLambdaExpression();
        } else {
            expression = parseAssignmentExpression();
        }

        if (madeProgress(checkpoint))
            return expression;

        rollback(checkpoint);
        return recoverExpressionNode();
    }

    private GreenNode parseAssignmentExpression() {
        GreenNode left = parseConditionalExpression();
        JavaTokenType operator = peekSignificantType();
        if (operator != null && operator.isAssignmentOperator()) {
            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(operator, children);
            children.add(parseExpression());
            return greenNode(JavaSyntaxKinds.ASSIGNMENT_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseConditionalExpression() {
        GreenNode condition = parseLogicalOrExpression();
        if (peekSignificantType() != JavaTokenType.QUESTION_MARK)
            return condition;

        List<GreenElement> children = new ArrayList<>();
        children.add(condition);
        expectSignificant(JavaTokenType.QUESTION_MARK, children);
        children.add(parseExpression());
        expectSignificant(JavaTokenType.COLON, children);
        if (isLambdaHeader()) {
            children.add(parseLambdaExpression());
        } else {
            children.add(parseConditionalExpression());
        }

        return greenNode(JavaSyntaxKinds.CONDITIONAL_EXPRESSION, children);
    }

    private GreenNode parseLogicalOrExpression() {
        GreenNode left = parseLogicalAndExpression();
        while (peekSignificantType() == JavaTokenType.OR) {
            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(JavaTokenType.OR, children);
            children.add(parseLogicalAndExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseLogicalAndExpression() {
        GreenNode left = parseBitwiseOrExpression();
        while (peekSignificantType() == JavaTokenType.AND) {
            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(JavaTokenType.AND, children);
            children.add(parseBitwiseOrExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseBitwiseOrExpression() {
        GreenNode left = parseBitwiseXorExpression();
        while (peekSignificantType() == JavaTokenType.PIPE) {
            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(JavaTokenType.PIPE, children);
            children.add(parseBitwiseXorExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseBitwiseXorExpression() {
        GreenNode left = parseBitwiseAndExpression();
        while (peekSignificantType() == JavaTokenType.CARET) {
            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(JavaTokenType.CARET, children);
            children.add(parseBitwiseAndExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseBitwiseAndExpression() {
        GreenNode left = parseEqualityExpression();
        while (peekSignificantType() == JavaTokenType.AMPERSAND) {
            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(JavaTokenType.AMPERSAND, children);
            children.add(parseEqualityExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseEqualityExpression() {
        GreenNode left = parseRelationalExpression();
        while (true) {
            JavaTokenType operator = peekSignificantType();
            if (operator != JavaTokenType.DOUBLE_EQUALS && operator != JavaTokenType.NOT_EQUALS)
                break;

            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(operator, children);
            children.add(parseRelationalExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseRelationalExpression() {
        GreenNode left = parseShiftExpression();
        while (true) {
            JavaTokenType operator = peekSignificantType();
            if (operator == JavaTokenType.INSTANCEOF_KEYWORD) {
                List<GreenElement> children = new ArrayList<>();
                children.add(left);
                expectSignificant(JavaTokenType.INSTANCEOF_KEYWORD, children);
                children.add(parsePattern());
                left = greenNode(JavaSyntaxKinds.INSTANCEOF_EXPRESSION, children);
                continue;
            }

            if (operator != JavaTokenType.LEFT_ANGLED_BRACKET &&
                    operator != JavaTokenType.LESS_THAN_OR_EQUALS &&
                    operator != JavaTokenType.RIGHT_ANGLED_BRACKET &&
                    operator != JavaTokenType.GREATER_THAN_OR_EQUALS) {
                break;
            }

            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(operator, children);
            children.add(parseShiftExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseShiftExpression() {
        GreenNode left = parseAdditiveExpression();
        while (true) {
            JavaTokenType operator = peekSignificantType();
            if (operator != JavaTokenType.LEFT_SHIFT &&
                    operator != JavaTokenType.RIGHT_SHIFT &&
                    operator != JavaTokenType.UNSIGNED_RIGHT_SHIFT) {
                break;
            }

            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(operator, children);
            children.add(parseAdditiveExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseAdditiveExpression() {
        GreenNode left = parseMultiplicativeExpression();
        while (true) {
            JavaTokenType operator = peekSignificantType();
            if (operator != JavaTokenType.PLUS && operator != JavaTokenType.MINUS)
                break;

            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(operator, children);
            children.add(parseMultiplicativeExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseMultiplicativeExpression() {
        GreenNode left = parseUnaryExpression();
        while (true) {
            JavaTokenType operator = peekSignificantType();
            if (operator != JavaTokenType.STAR && operator != JavaTokenType.SLASH && operator != JavaTokenType.PERCENT)
                break;

            List<GreenElement> children = new ArrayList<>();
            children.add(left);
            expectSignificant(operator, children);
            children.add(parseUnaryExpression());
            left = greenNode(JavaSyntaxKinds.BINARY_EXPRESSION, children);
        }

        return left;
    }

    private GreenNode parseUnaryExpression() {
        JavaTokenType tokenType = peekSignificantType();
        if (isUnaryPrefixOperator(tokenType)) {
            List<GreenElement> children = new ArrayList<>();
            expectSignificant(tokenType, children);
            children.add(parseUnaryExpression());
            return greenNode(JavaSyntaxKinds.UNARY_EXPRESSION, children);
        }

        if (tokenType == JavaTokenType.OPEN_PAREN && isCastExpressionStart())
            return parseCastExpression();
        if (tokenType == JavaTokenType.SWITCH_KEYWORD)
            return parseSwitchExpression();

        return parsePostfixExpression();
    }

    private GreenNode parseCastExpression() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        children.add(parseTypeReference());
        while (matchSignificant(JavaTokenType.AMPERSAND, children)) {
            children.add(parseTypeReference());
        }

        expectSignificant(JavaTokenType.CLOSE_PAREN, children);
        children.add(parseUnaryExpression());
        return greenNode(JavaSyntaxKinds.CAST_EXPRESSION, children);
    }

    private GreenNode parseSwitchExpression() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.SWITCH_KEYWORD, children);
        children.add(parseParenthesizedExpression());
        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        while (peekSignificantType() == JavaTokenType.CASE_KEYWORD || peekSignificantType() == JavaTokenType.DEFAULT_KEYWORD) {
            children.add(parseSwitchRule());
        }

        expectSignificant(JavaTokenType.CLOSE_BRACE, children);
        return greenNode(JavaSyntaxKinds.SWITCH_EXPRESSION, children);
    }

    private GreenNode parsePostfixExpression() {
        GreenNode expression = parsePrimaryExpression();
        while (true) {
            JavaTokenType tokenType = peekSignificantType();
            if (tokenType == JavaTokenType.DOT) {
                List<GreenElement> children = new ArrayList<>();
                children.add(expression);
                expectSignificant(JavaTokenType.DOT, children);
                if (matchSignificant(JavaTokenType.NEW_KEYWORD, children)) {
                    GreenNode preTypeArguments = parseOptionalTypeArguments();
                    if (preTypeArguments != null)
                        children.add(preTypeArguments);

                    children.add(parseTypeReference());
                    GreenNode constructorTypeArguments = parseOptionalTypeArguments();
                    if (constructorTypeArguments != null)
                        children.add(constructorTypeArguments);

                    children.add(parseArgumentList());
                    if (peekSignificantType() == JavaTokenType.OPEN_BRACE)
                        children.add(parseAnonymousClassBody());

                    expression = greenNode(JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION, children);
                    continue;
                }

                if (matchSignificant(JavaTokenType.THIS_KEYWORD, children)) {
                    expression = greenNode(JavaSyntaxKinds.THIS_EXPRESSION, children);
                    continue;
                }

                if (matchSignificant(JavaTokenType.SUPER_KEYWORD, children)) {
                    expression = greenNode(JavaSyntaxKinds.SUPER_EXPRESSION, children);
                    continue;
                }

                GreenNode typeArguments = parseOptionalTypeArguments();
                if (typeArguments != null)
                    children.add(typeArguments);

                List<GreenElement> nameChildren = new ArrayList<>();
                if (consumeIdentifierLike(nameChildren) == null)
                    nameChildren.add(missingToken(JavaTokenType.IDENTIFIER));
                children.add(greenNode(JavaSyntaxKinds.NAME_EXPRESSION, nameChildren));

                if (peekSignificantType() == JavaTokenType.OPEN_PAREN) {
                    children.add(parseArgumentList());
                    expression = greenNode(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION, children);
                } else {
                    expression = greenNode(JavaSyntaxKinds.FIELD_ACCESS_EXPRESSION, children);
                }
                continue;
            }

            if (tokenType == JavaTokenType.OPEN_PAREN) {
                List<GreenElement> children = new ArrayList<>();
                children.add(expression);
                children.add(parseArgumentList());
                expression = greenNode(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION, children);
                continue;
            }

            if (tokenType == JavaTokenType.OPEN_BRACKET) {
                List<GreenElement> children = new ArrayList<>();
                children.add(expression);
                expectSignificant(JavaTokenType.OPEN_BRACKET, children);
                children.add(parseExpression());
                expectSignificant(JavaTokenType.CLOSE_BRACKET, children);
                expression = greenNode(JavaSyntaxKinds.ARRAY_ACCESS_EXPRESSION, children);
                continue;
            }

            if (tokenType == JavaTokenType.DOUBLE_COLON) {
                expression = parseMethodReferenceExpression(expression);
                continue;
            }

            if (tokenType == JavaTokenType.PLUS_PLUS || tokenType == JavaTokenType.MINUS_MINUS) {
                List<GreenElement> children = new ArrayList<>();
                children.add(expression);
                expectSignificant(tokenType, children);
                expression = greenNode(JavaSyntaxKinds.POSTFIX_EXPRESSION, children);
                continue;
            }

            break;
        }

        return expression;
    }

    private GreenNode parsePrimaryExpression() {
        JavaTokenType tokenType = peekSignificantType();
        if (tokenType == JavaTokenType.THIS_KEYWORD) {
            List<GreenElement> children = new ArrayList<>();
            expectSignificant(JavaTokenType.THIS_KEYWORD, children);
            return greenNode(JavaSyntaxKinds.THIS_EXPRESSION, children);
        }

        if (tokenType == JavaTokenType.SUPER_KEYWORD) {
            List<GreenElement> children = new ArrayList<>();
            expectSignificant(JavaTokenType.SUPER_KEYWORD, children);
            return greenNode(JavaSyntaxKinds.SUPER_EXPRESSION, children);
        }

        if (isLiteralToken(tokenType))
            return parseLiteral();
        if (tokenType == JavaTokenType.OPEN_PAREN)
            return parseParenthesizedExpression();
        if (tokenType == JavaTokenType.NEW_KEYWORD)
            return parseClassInstanceCreationOrArrayCreationExpression();
        if (tokenType == JavaTokenType.SWITCH_KEYWORD)
            return parseSwitchExpression();
        if (isClassLiteralStart())
            return parseClassLiteralExpression();

        if (isIdentifierLike(tokenType)) {
            List<GreenElement> nameChildren = new ArrayList<>();
            if (consumeIdentifierLike(nameChildren) == null)
                nameChildren.add(missingToken(JavaTokenType.IDENTIFIER));
            GreenNode nameExpression = greenNode(JavaSyntaxKinds.NAME_EXPRESSION, nameChildren);
            if (peekSignificantType() == JavaTokenType.OPEN_PAREN) {
                List<GreenElement> children = new ArrayList<>();
                children.add(nameExpression);
                children.add(parseArgumentList());
                return greenNode(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION, children);
            }

            return nameExpression;
        }

        List<GreenElement> errorChildren = new ArrayList<>();
        consumeTrivia(errorChildren);
        if (hasMoreTokens() && !isEof(peek()))
            errorChildren.add(consumeToken());
        else
            errorChildren.add(missingToken(JavaTokenType.IDENTIFIER));
        return greenNode(JavaSyntaxKinds.PRIMARY_EXPRESSION, errorChildren);
    }

    private GreenNode parseClassInstanceCreationOrArrayCreationExpression() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.NEW_KEYWORD, children);

        GreenNode preTypeArguments = parseOptionalTypeArguments();
        if (preTypeArguments != null)
            children.add(preTypeArguments);

        children.add(parseTypeReference());
        if (peekSignificantType() == JavaTokenType.OPEN_BRACKET || peekSignificantType() == JavaTokenType.OPEN_BRACE) {
            while (matchSignificant(JavaTokenType.OPEN_BRACKET, children)) {
                if (peekSignificantType() != JavaTokenType.CLOSE_BRACKET)
                    children.add(parseExpression());
                expectSignificant(JavaTokenType.CLOSE_BRACKET, children);
            }

            if (peekSignificantType() == JavaTokenType.OPEN_BRACE)
                children.add(parseArrayInitializerExpression());
            return greenNode(JavaSyntaxKinds.ARRAY_CREATION_EXPRESSION, children);
        }

        GreenNode constructorTypeArguments = parseOptionalTypeArguments();
        if (constructorTypeArguments != null)
            children.add(constructorTypeArguments);

        children.add(parseArgumentList());
        if (peekSignificantType() == JavaTokenType.OPEN_BRACE)
            children.add(parseAnonymousClassBody());

        return greenNode(JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION, children);
    }

    private GreenNode parseArrayInitializerExpression() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            if (next.type() == JavaTokenType.OPEN_BRACE) {
                children.add(parseArrayInitializerExpression());
            } else {
                children.add(parseExpression());
            }

            consumeTrivia(children);
            if (matchSignificant(JavaTokenType.COMMA, children))
                continue;

            next = peekSignificantToken();
            if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_BRACE)
                children.add(missingToken(JavaTokenType.COMMA));
        }

        return greenNode(JavaSyntaxKinds.ARRAY_INITIALIZER_EXPRESSION, children);
    }

    private GreenNode parseClassLiteralExpression() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseTypeReference());
        expectSignificant(JavaTokenType.DOT, children);
        expectSignificant(JavaTokenType.CLASS_KEYWORD, children);
        return greenNode(JavaSyntaxKinds.CLASS_LITERAL_EXPRESSION, children);
    }

    private GreenNode parseLiteral() {
        List<GreenElement> children = new ArrayList<>();
        JavaTokenType tokenType = peekSignificantType();
        if (!isLiteralToken(tokenType)) {
            children.add(missingToken(JavaTokenType.NULL_LITERAL));
            return greenNode(JavaSyntaxKinds.LITERAL_EXPRESSION, children);
        }

        expectSignificant(tokenType, children);
        return greenNode(JavaSyntaxKinds.LITERAL_EXPRESSION, children);
    }

    private GreenNode parseArgumentList() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_PAREN) {
                children.add(consumeToken());
                break;
            }

            children.add(parseExpression());
            consumeTrivia(children);
            if (matchSignificant(JavaTokenType.COMMA, children))
                continue;

            next = peekSignificantToken();
            if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_PAREN)
                children.add(missingToken(JavaTokenType.COMMA));
        }

        return greenNode(JavaSyntaxKinds.ARGUMENT_LIST, children);
    }

    private GreenNode parseMethodReferenceExpression(GreenNode expression) {
        List<GreenElement> children = new ArrayList<>();
        children.add(expression);
        expectSignificant(JavaTokenType.DOUBLE_COLON, children);

        GreenNode typeArguments = parseOptionalTypeArguments();
        if (typeArguments != null)
            children.add(typeArguments);

        if (matchSignificant(JavaTokenType.NEW_KEYWORD, children))
            return greenNode(JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION, children);

        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));
        return greenNode(JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION, children);
    }

    private GreenNode parseLambdaExpression() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseLambdaParameters());
        expectSignificant(JavaTokenType.ARROW, children);
        children.add(parseLambdaBody());
        return greenNode(JavaSyntaxKinds.LAMBDA_EXPRESSION, children);
    }

    private GreenNode parseLambdaParameters() {
        List<GreenElement> children = new ArrayList<>();
        if (isIdentifierLike(peekSignificantType()) && peekSignificantTypeAfter(1) == JavaTokenType.ARROW) {
            children.add(parseSimpleLambdaParameter());
            return greenNode(JavaSyntaxKinds.LAMBDA_PARAMETERS, children);
        }

        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_PAREN) {
                children.add(consumeToken());
                break;
            }

            children.add(parseLambdaParameter());
            consumeTrivia(children);
            if (matchSignificant(JavaTokenType.COMMA, children))
                continue;

            next = peekSignificantToken();
            if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_PAREN)
                children.add(missingToken(JavaTokenType.COMMA));
        }

        return greenNode(JavaSyntaxKinds.LAMBDA_PARAMETERS, children);
    }

    private GreenNode parseSimpleLambdaParameter() {
        List<GreenElement> children = new ArrayList<>();
        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));
        return greenNode(JavaSyntaxKinds.LAMBDA_PARAMETER, children);
    }

    private GreenNode parseLambdaParameter() {
        List<GreenElement> children = new ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;
        boolean consumedSignificant = false;
        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token))
                break;

            JavaTokenType tokenType = token.type();
            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0 &&
                    (tokenType == JavaTokenType.COMMA || tokenType == JavaTokenType.CLOSE_PAREN)) {
                break;
            }

            children.add(consumeToken());
            consumedSignificant = true;
            if (tokenType == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN && parenDepth > 0) {
                parenDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACKET && bracketDepth > 0) {
                bracketDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACE) {
                braceDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACE && braceDepth > 0) {
                braceDepth--;
            }

            angleDepth = adjustAngleDepthForToken(angleDepth, tokenType);
        }

        if (!consumedSignificant)
            children.add(missingToken(JavaTokenType.IDENTIFIER));
        return greenNode(JavaSyntaxKinds.LAMBDA_PARAMETER, children);
    }

    private GreenNode parseLambdaBody() {
        List<GreenElement> children = new ArrayList<>();
        if (peekSignificantType() == JavaTokenType.OPEN_BRACE) {
            children.add(parseBlock());
        } else {
            children.add(parseExpression());
        }

        return greenNode(JavaSyntaxKinds.LAMBDA_BODY, children);
    }

    private GreenNode parsePattern() {
        List<GreenElement> children = new ArrayList<>();
        if (matchSignificant(JavaTokenType.UNDERSCORE_KEYWORD, children))
            return greenNode(JavaSyntaxKinds.PATTERN, children);

        consumeModifiersAndAnnotations(children, JavaGreenParser::isParameterOrLocalModifier);
        children.add(parseTypeReference());
        if (matchSignificant(JavaTokenType.OPEN_PAREN, children)) {
            while (hasMoreTokens()) {
                consumeTrivia(children);
                Token<JavaTokenType> next = peekSignificantToken();
                if (next == null || isEof(next)) {
                    children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                    break;
                }

                if (next.type() == JavaTokenType.CLOSE_PAREN) {
                    children.add(consumeToken());
                    break;
                }

                children.add(parsePattern());
                consumeTrivia(children);
                if (matchSignificant(JavaTokenType.COMMA, children))
                    continue;

                next = peekSignificantToken();
                if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_PAREN)
                    children.add(missingToken(JavaTokenType.COMMA));
            }
        } else {
            consumeIdentifierLike(children);
        }

        return greenNode(JavaSyntaxKinds.PATTERN, children);
    }

    private boolean isPatternStart() {
        int marker = position;
        parsePattern();
        boolean consumed = position > marker;
        JavaTokenType terminator = peekSignificantType();
        boolean looksLikePattern = consumed &&
                (terminator == JavaTokenType.WHEN_KEYWORD ||
                        terminator == JavaTokenType.COMMA ||
                        terminator == JavaTokenType.COLON ||
                        terminator == JavaTokenType.ARROW);
        position = marker;
        return looksLikePattern;
    }

    private GreenNode parseAnonymousClassBody() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        int depth = 1;
        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            JavaTokenType tokenType = token.type();
            children.add(consumeToken());
            if (tokenType == JavaTokenType.OPEN_BRACE) {
                depth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACE) {
                depth--;
                if (depth == 0)
                    break;
            }
        }

        return greenNode(JavaSyntaxKinds.ANONYMOUS_CLASS_BODY, children);
    }

    private boolean isCastExpressionStart() {
        int marker = position;
        List<GreenElement> probe = new ArrayList<>();
        if (!matchSignificant(JavaTokenType.OPEN_PAREN, probe)) {
            position = marker;
            return false;
        }

        parseTypeReference();
        while (matchSignificant(JavaTokenType.AMPERSAND, probe)) {
            parseTypeReference();
        }

        boolean hasClosingParen = peekSignificantType() == JavaTokenType.CLOSE_PAREN;
        if (hasClosingParen)
            consumeToken();

        JavaTokenType next = peekSignificantType();
        boolean canStartTarget = hasClosingParen && canStartUnaryExpression(next);
        position = marker;
        return canStartTarget;
    }

    private boolean isLambdaHeader() {
        if (isIdentifierLike(peekSignificantType()) && peekSignificantTypeAfter(1) == JavaTokenType.ARROW)
            return true;
        if (peekSignificantType() != JavaTokenType.OPEN_PAREN)
            return false;

        int index = nextSignificantIndex(position);
        int depth = 0;
        while (index >= 0 && index < tokens.size()) {
            Token<JavaTokenType> token = tokens.get(index);
            if (isEof(token))
                return false;

            JavaTokenType tokenType = token.type();
            if (tokenType == JavaTokenType.OPEN_PAREN) {
                depth++;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN) {
                depth--;
                if (depth == 0) {
                    int after = nextSignificantIndex(index + 1);
                    return after >= 0 && tokens.get(after).type() == JavaTokenType.ARROW;
                }
            }

            index = nextSignificantIndex(index + 1);
        }

        return false;
    }

    private JavaTokenType peekSignificantTypeAfter(int offset) {
        int index = nextSignificantIndex(position);
        int remaining = offset;
        while (remaining > 0 && index >= 0) {
            index = nextSignificantIndex(index + 1);
            remaining--;
        }

        return index < 0 ? null : tokens.get(index).type();
    }

    private boolean isClassLiteralStart() {
        int marker = position;
        parseTypeReference();
        boolean consumedType = position > marker;
        int currentIndex = nextSignificantIndex(position);
        JavaTokenType currentType = currentIndex < 0 ? null : tokens.get(currentIndex).type();
        int previousIndex = currentIndex < 0 ? -1 : previousSignificantIndex(currentIndex - 1);
        JavaTokenType previousType = previousIndex < 0 ? null : tokens.get(previousIndex).type();

        int classAfterDotIndex = currentType == JavaTokenType.DOT ? nextSignificantIndex(currentIndex + 1) : -1;
        JavaTokenType classAfterDotType = classAfterDotIndex < 0 ? null : tokens.get(classAfterDotIndex).type();
        boolean isClassLiteral = consumedType && (
                (currentType == JavaTokenType.DOT && classAfterDotType == JavaTokenType.CLASS_KEYWORD) ||
                        (currentType == JavaTokenType.CLASS_KEYWORD && previousType == JavaTokenType.DOT)
        );
        position = marker;
        return isClassLiteral;
    }

    private GreenNode parseBlock() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.OPEN_BRACE, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            children.add(parseStatement());
        }

        return greenNode(JavaSyntaxKinds.BLOCK, children);
    }

    private GreenNode parseStatement() {
        int checkpoint = mark();
        GreenNode statement = parseStatementCore();
        if (madeProgress(checkpoint))
            return statement;

        rollback(checkpoint);
        return recoverStatementNode();
    }

    private GreenNode parseStatementCore() {
        JavaTokenType tokenType = peekSignificantType();
        if (tokenType == null)
            return greenNode(JavaSyntaxKinds.STATEMENT, List.of());

        return switch (tokenType) {
            case SEMICOLON -> parseEmptyStatement();
            case OPEN_BRACE -> parseBlock();
            case IF_KEYWORD -> parseIfStatement();
            case SWITCH_KEYWORD -> parseSwitchStatement();
            case WHILE_KEYWORD -> parseWhileStatement();
            case DO_KEYWORD -> parseDoWhileStatement();
            case FOR_KEYWORD -> parseForStatement();
            case TRY_KEYWORD -> parseTryStatement();
            case SYNCHRONIZED_KEYWORD -> parseSynchronizedStatement();
            case RETURN_KEYWORD -> parseReturnStatement();
            case THROW_KEYWORD -> parseThrowStatement();
            case BREAK_KEYWORD -> parseBreakStatement();
            case CONTINUE_KEYWORD -> parseContinueStatement();
            case ASSERT_KEYWORD -> parseAssertStatement();
            case YIELD_KEYWORD -> parseYieldStatement();
            default -> {
                if (isLabeledStatementStart()) {
                    yield parseLabeledStatement();
                }

                if (isLocalVariableDeclarationStart()) {
                    yield parseLocalVariableDeclarationStatement(true);
                }

                yield parseExpressionStatement();
            }
        };
    }

    private boolean isLabeledStatementStart() {
        int labelIndex = nextSignificantIndex(position);
        if (labelIndex < 0)
            return false;

        Token<JavaTokenType> labelToken = tokens.get(labelIndex);
        if (!isIdentifierLike(labelToken.type()))
            return false;

        int colonIndex = nextSignificantIndex(labelIndex + 1);
        if (colonIndex < 0)
            return false;

        return tokens.get(colonIndex).type() == JavaTokenType.COLON;
    }

    private boolean isLocalVariableDeclarationStart() {
        JavaTokenType tokenType = peekSignificantType();
        if (tokenType == JavaTokenType.VAR_KEYWORD || tokenType == JavaTokenType.AT || isParameterOrLocalModifier(tokenType))
            return true;

        // Prevent expression starters (e.g. 'new') from being misclassified as
        // local variable declarations.
        if (!(tokenType == JavaTokenType.AT || isPrimitiveTypeToken(tokenType) || isIdentifierLike(tokenType)))
            return false;

        int checkpoint = mark();
        parseTypeReference();
        boolean consumedType = madeProgress(checkpoint);
        Token<JavaTokenType> afterType = peekSignificantToken();
        boolean startsDeclaration = consumedType && afterType != null && isIdentifierLike(afterType.type());
        rollback(checkpoint);
        return startsDeclaration;
    }

    private GreenNode parseEmptyStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.EMPTY_STATEMENT, children);
    }

    private GreenNode parseExpressionStatement() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseExpression());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.EXPRESSION_STATEMENT, children);
    }

    private GreenNode parseLocalVariableDeclarationStatement(boolean requireSemicolon) {
        List<GreenElement> children = new ArrayList<>();
        consumeModifiersAndAnnotations(children, JavaGreenParser::isParameterOrLocalModifier);

        if (peekSignificantType() == JavaTokenType.VAR_KEYWORD) {
            expectSignificant(JavaTokenType.VAR_KEYWORD, children);
        } else {
            children.add(parseTypeReference());
        }

        List<GreenElement> nameTokens = new ArrayList<>();
        if (consumeIdentifierLike(nameTokens) == null)
            nameTokens.add(missingToken(JavaTokenType.IDENTIFIER));
        children.add(parseVariableDeclaratorFromNameTokens(nameTokens));

        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseVariableDeclarator());
        }

        if (requireSemicolon || peekSignificantType() == JavaTokenType.SEMICOLON)
            expectSignificant(JavaTokenType.SEMICOLON, children);

        return greenNode(JavaSyntaxKinds.LOCAL_VARIABLE_DECLARATION_STATEMENT, children);
    }

    private GreenNode parseLabeledStatement() {
        List<GreenElement> children = new ArrayList<>();
        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));
        expectSignificant(JavaTokenType.COLON, children);
        children.add(parseStatement());
        return greenNode(JavaSyntaxKinds.LABELED_STATEMENT, children);
    }

    private GreenNode parseYieldStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.YIELD_KEYWORD, children);
        children.add(parseExpression());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.YIELD_STATEMENT, children);
    }

    private GreenNode parseAssertStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.ASSERT_KEYWORD, children);
        children.add(parseExpression());
        if (matchSignificant(JavaTokenType.COLON, children))
            children.add(parseExpression());

        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.ASSERT_STATEMENT, children);
    }

    private GreenNode parseContinueStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.CONTINUE_KEYWORD, children);
        consumeIdentifierLike(children);
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.CONTINUE_STATEMENT, children);
    }

    private GreenNode parseBreakStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.BREAK_KEYWORD, children);
        consumeIdentifierLike(children);
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.BREAK_STATEMENT, children);
    }

    private GreenNode parseThrowStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.THROW_KEYWORD, children);
        children.add(parseExpression());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.THROW_STATEMENT, children);
    }

    private GreenNode parseReturnStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.RETURN_KEYWORD, children);
        if (peekSignificantType() != JavaTokenType.SEMICOLON)
            children.add(parseExpression());

        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.RETURN_STATEMENT, children);
    }

    private GreenNode parseSynchronizedStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.SYNCHRONIZED_KEYWORD, children);
        children.add(parseParenthesizedExpression());
        children.add(parseBlock());
        return greenNode(JavaSyntaxKinds.SYNCHRONIZED_STATEMENT, children);
    }

    private GreenNode parseTryStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.TRY_KEYWORD, children);

        if (matchSignificant(JavaTokenType.OPEN_PAREN, children)) {
            while (hasMoreTokens()) {
                consumeTrivia(children);
                Token<JavaTokenType> next = peekSignificantToken();
                if (next == null || isEof(next)) {
                    children.add(missingToken(JavaTokenType.CLOSE_PAREN));
                    break;
                }

                if (next.type() == JavaTokenType.CLOSE_PAREN) {
                    children.add(consumeToken());
                    break;
                }

                children.add(parseTryResource());
                consumeTrivia(children);
                if (matchSignificant(JavaTokenType.SEMICOLON, children))
                    continue;

                next = peekSignificantToken();
                if (next != null && !isEof(next) && next.type() != JavaTokenType.CLOSE_PAREN)
                    children.add(missingToken(JavaTokenType.SEMICOLON));
            }
        }

        children.add(parseBlock());
        while (peekSignificantType() == JavaTokenType.CATCH_KEYWORD) {
            children.add(parseCatchClause());
        }

        if (peekSignificantType() == JavaTokenType.FINALLY_KEYWORD) {
            children.add(parseFinallyClause());
        }

        return greenNode(JavaSyntaxKinds.TRY_STATEMENT, children);
    }

    private GreenNode parseTryResource() {
        List<GreenElement> children = new ArrayList<>();
        if (isLocalVariableDeclarationStart()) {
            children.add(parseLocalVariableDeclarationStatement(false));
        } else {
            children.add(parseExpression());
        }
        return greenNode(JavaSyntaxKinds.TRY_RESOURCE, children);
    }

    private GreenNode parseFinallyClause() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.FINALLY_KEYWORD, children);
        children.add(parseBlock());
        return greenNode(JavaSyntaxKinds.FINALLY_CLAUSE, children);
    }

    private GreenNode parseCatchClause() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.CATCH_KEYWORD, children);
        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        children.add(parseParameter());
        expectSignificant(JavaTokenType.CLOSE_PAREN, children);
        children.add(parseBlock());
        return greenNode(JavaSyntaxKinds.CATCH_CLAUSE, children);
    }

    private GreenNode parseForStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.FOR_KEYWORD, children);
        expectSignificant(JavaTokenType.OPEN_PAREN, children);

        if (isEnhancedForLoop()) {
            children.add(parseEnhancedForStatement());
        } else {
            children.add(parseBasicForStatement());
        }

        children.add(parseStatement());
        return greenNode(JavaSyntaxKinds.FOR_STATEMENT, children);
    }

    private GreenNode parseEnhancedForStatement() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseParameter());
        expectSignificant(JavaTokenType.COLON, children);
        children.add(parseExpression());
        expectSignificant(JavaTokenType.CLOSE_PAREN, children);
        return greenNode(JavaSyntaxKinds.ENHANCED_FOR_STATEMENT, children);
    }

    private GreenNode parseBasicForStatement() {
        List<GreenElement> children = new ArrayList<>();
        if (peekSignificantType() == JavaTokenType.SEMICOLON) {
            children.add(consumeToken());
        } else if (isLocalVariableDeclarationStart()) {
            children.add(parseLocalVariableDeclarationStatement(true));
        } else {
            children.add(parseExpression());
            while (matchSignificant(JavaTokenType.COMMA, children)) {
                children.add(parseExpression());
            }

            expectSignificant(JavaTokenType.SEMICOLON, children);
        }

        if (peekSignificantType() != JavaTokenType.SEMICOLON)
            children.add(parseExpression());
        expectSignificant(JavaTokenType.SEMICOLON, children);

        if (peekSignificantType() != JavaTokenType.CLOSE_PAREN) {
            children.add(parseExpression());
            while (matchSignificant(JavaTokenType.COMMA, children)) {
                children.add(parseExpression());
            }
        }

        expectSignificant(JavaTokenType.CLOSE_PAREN, children);

        return greenNode(JavaSyntaxKinds.BASIC_FOR_STATEMENT, children);
    }

    private boolean isEnhancedForLoop() {
        int index = position;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;
        while (index < tokens.size()) {
            Token<JavaTokenType> token = tokens.get(index++);
            if (isTrivia(token))
                continue;
            if (isEof(token))
                break;

            JavaTokenType tokenType = token.type();
            if (tokenType == JavaTokenType.CLOSE_PAREN && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0)
                return false;

            if (tokenType == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
                continue;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN && parenDepth > 0) {
                parenDepth--;
                continue;
            }

            if (tokenType == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
                continue;
            } else if (tokenType == JavaTokenType.CLOSE_BRACKET && bracketDepth > 0) {
                bracketDepth--;
                continue;
            }

            if (tokenType == JavaTokenType.OPEN_BRACE) {
                braceDepth++;
                continue;
            } else if (tokenType == JavaTokenType.CLOSE_BRACE && braceDepth > 0) {
                braceDepth--;
                continue;
            }

            angleDepth = adjustAngleDepthForToken(angleDepth, tokenType);
            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                if (tokenType == JavaTokenType.SEMICOLON)
                    return false;
                if (tokenType == JavaTokenType.COLON)
                    return true;
            }
        }

        return false;
    }

    private GreenNode parseDoWhileStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.DO_KEYWORD, children);
        children.add(parseStatement());
        expectSignificant(JavaTokenType.WHILE_KEYWORD, children);
        children.add(parseParenthesizedExpression());
        expectSignificant(JavaTokenType.SEMICOLON, children);
        return greenNode(JavaSyntaxKinds.DO_WHILE_STATEMENT, children);
    }

    private GreenNode parseWhileStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.WHILE_KEYWORD, children);
        children.add(parseParenthesizedExpression());
        children.add(parseStatement());
        return greenNode(JavaSyntaxKinds.WHILE_STATEMENT, children);
    }

    private GreenNode parseSwitchStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.SWITCH_KEYWORD, children);
        children.add(parseParenthesizedExpression());
        expectSignificant(JavaTokenType.OPEN_BRACE, children);

        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next)) {
                children.add(missingToken(JavaTokenType.CLOSE_BRACE));
                break;
            }

            if (next.type() == JavaTokenType.CLOSE_BRACE) {
                children.add(consumeToken());
                break;
            }

            if (next.type() == JavaTokenType.CASE_KEYWORD || next.type() == JavaTokenType.DEFAULT_KEYWORD) {
                children.add(parseSwitchRule());
                continue;
            }

            children.add(parseStatement());
        }

        return greenNode(JavaSyntaxKinds.SWITCH_STATEMENT, children);
    }

    private GreenNode parseSwitchRule() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseSwitchLabel());

        if (matchSignificant(JavaTokenType.ARROW, children)) {
            consumeSwitchArrowBody(children);
            return greenNode(JavaSyntaxKinds.SWITCH_RULE, children);
        }

        expectSignificant(JavaTokenType.COLON, children);
        while (hasMoreTokens()) {
            consumeTrivia(children);
            Token<JavaTokenType> next = peekSignificantToken();
            if (next == null || isEof(next) || isSwitchRuleBoundaryToken(next.type()))
                break;

            children.add(parseStatement());
        }

        return greenNode(JavaSyntaxKinds.SWITCH_RULE, children);
    }

    private GreenNode parseSwitchLabel() {
        List<GreenElement> children = new ArrayList<>();
        if (matchSignificant(JavaTokenType.DEFAULT_KEYWORD, children))
            return greenNode(JavaSyntaxKinds.SWITCH_LABEL, children);

        expectSignificant(JavaTokenType.CASE_KEYWORD, children);
        children.add(parseSwitchCaseItem());
        while (matchSignificant(JavaTokenType.COMMA, children)) {
            children.add(parseSwitchCaseItem());
        }

        return greenNode(JavaSyntaxKinds.SWITCH_LABEL, children);
    }

    private GreenNode parseSwitchCaseItem() {
        List<GreenElement> children = new ArrayList<>();
        if (matchSignificant(JavaTokenType.NULL_LITERAL, children))
            return greenNode(JavaSyntaxKinds.SWITCH_CASE_ITEM, children);

        if (isPatternStart()) {
            children.add(parsePattern());
        } else {
            children.add(parseExpression());
        }

        if (matchSignificant(JavaTokenType.WHEN_KEYWORD, children)) {
            children.add(parsePatternGuard());
        }

        return greenNode(JavaSyntaxKinds.SWITCH_CASE_ITEM, children);
    }

    private GreenNode parsePatternGuard() {
        List<GreenElement> children = new ArrayList<>();
        children.add(parseExpression());
        return greenNode(JavaSyntaxKinds.PATTERN_GUARD, children);
    }

    private void consumeSwitchArrowBody(List<GreenElement> children) {
        JavaTokenType tokenType = peekSignificantType();
        if (tokenType == JavaTokenType.OPEN_BRACE) {
            children.add(parseBlock());
            return;
        }

        if (tokenType == JavaTokenType.THROW_KEYWORD) {
            children.add(parseThrowStatement());
            return;
        }

        if (tokenType == JavaTokenType.YIELD_KEYWORD) {
            children.add(parseYieldStatement());
            return;
        }

        children.add(parseExpression());
        matchSignificant(JavaTokenType.SEMICOLON, children);
    }

    private GreenNode parseIfStatement() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.IF_KEYWORD, children);
        children.add(parseParenthesizedExpression());
        children.add(parseStatement());
        if (matchSignificant(JavaTokenType.ELSE_KEYWORD, children)) {
            children.add(parseStatement());
        }

        return greenNode(JavaSyntaxKinds.IF_STATEMENT, children);
    }

    private GreenNode parseParenthesizedExpression() {
        List<GreenElement> children = new ArrayList<>();
        expectSignificant(JavaTokenType.OPEN_PAREN, children);
        children.add(parseExpression());
        expectSignificant(JavaTokenType.CLOSE_PAREN, children);
        return greenNode(JavaSyntaxKinds.PARENTHESIZED_EXPRESSION, children);
    }

    private void consumeExpressionUntil(List<GreenElement> children, Set<JavaTokenType> terminators) {
        synchronizeToFollowSet(children, terminators);
    }

    private static boolean isSwitchRuleBoundaryToken(JavaTokenType tokenType) {
        return tokenType == JavaTokenType.CASE_KEYWORD ||
                tokenType == JavaTokenType.DEFAULT_KEYWORD ||
                tokenType == JavaTokenType.CLOSE_BRACE;
    }

    private static boolean isTypeReferenceFollowToken(JavaTokenType tokenType) {
        return tokenType == null || tokenType == JavaTokenType.EOF || TYPE_REFERENCE_FOLLOW_SET.contains(tokenType);
    }

    private static boolean isHardStatementBoundaryToken(JavaTokenType tokenType) {
        return tokenType == null ||
                tokenType == JavaTokenType.EOF ||
                tokenType == JavaTokenType.CLOSE_BRACE ||
                tokenType == JavaTokenType.CASE_KEYWORD ||
                tokenType == JavaTokenType.DEFAULT_KEYWORD;
    }

    private GreenNode parseAnnotation() {
        List<GreenElement> children = new ArrayList<>();

        expectSignificant(JavaTokenType.AT, children);
        children.add(parseQualifiedName());
        if (matchSignificant(JavaTokenType.OPEN_PAREN, children)) {
            consumeParenthesizedTail(children);
        }

        return greenNode(JavaSyntaxKinds.ANNOTATION, children);
    }

    private GreenNode parseQualifiedName() {
        List<GreenElement> children = new ArrayList<>();

        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        while (matchSignificant(JavaTokenType.DOT, children)) {
            if (consumeIdentifierLike(children) != null)
                continue;

            children.add(missingToken(JavaTokenType.IDENTIFIER));
            break;
        }

        return greenNode(JavaSyntaxKinds.QUALIFIED_NAME, children);
    }

    private GreenNode parseImportTarget() {
        List<GreenElement> children = new ArrayList<>();

        if (consumeIdentifierLike(children) == null)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        while (matchSignificant(JavaTokenType.DOT, children)) {
            if (matchSignificant(JavaTokenType.STAR, children))
                return greenNode(JavaSyntaxKinds.IMPORT_TARGET, children);

            if (consumeIdentifierLike(children) != null)
                continue;

            children.add(missingToken(JavaTokenType.IDENTIFIER));
            break;
        }

        return greenNode(JavaSyntaxKinds.IMPORT_TARGET, children);
    }

    private List<GreenElement> parseTypeDeclarationPrefix() {
        List<GreenElement> prefix = new ArrayList<>();
        consumeModifiersAndAnnotations(prefix, JavaGreenParser::isTypeDeclarationModifier);

        return prefix;
    }

    private List<GreenElement> parseMemberPrefix() {
        List<GreenElement> prefix = new ArrayList<>();
        consumeModifiersAndAnnotations(prefix, JavaGreenParser::isMemberModifier);

        GreenNode typeParameters = parseOptionalTypeParameters();
        if (typeParameters != null)
            prefix.add(typeParameters);

        return prefix;
    }

    private void consumeModifiersAndAnnotations(List<GreenElement> children, Predicate<JavaTokenType> modifierPredicate) {
        consumeTrivia(children);
        boolean consumed;
        do {
            consumed = false;
            consumeTypeUseAnnotations(children);
            if (modifierPredicate.test(peekSignificantType())) {
                children.add(consumeToken());
                consumeTrivia(children);
                consumed = true;
            }
        } while (consumed);
    }

    private Token<JavaTokenType> consumeIdentifierLike(List<GreenElement> children) {
        consumeTrivia(children);
        if (!hasMoreTokens())
            return null;

        Token<JavaTokenType> token = peek();
        if (!isIdentifierLike(token.type()))
            return null;

        children.add(consumeToken());
        return token;
    }

    private GreenElement consumeToken() {
        Token<JavaTokenType> token = tokens.get(position++);
        return greenToken(kindForToken(token), token.lexeme());
    }

    private SyntaxKind kindForToken(Token<JavaTokenType> token) {
        if (token instanceof Token.MissingToken<?>)
            return SyntaxKind.MISSING_TOKEN;

        JavaTokenType tokenType = token.type();
        if (tokenType == null)
            return SyntaxKind.TOKEN;

        SyntaxKind tokenKind = JavaSyntaxKinds.tokenKind(tokenType);
        return tokenKind != null ? tokenKind : SyntaxKind.TOKEN;
    }

    private GreenElement missingToken(JavaTokenType expected) {
        return greenToken(JavaSyntaxKinds.missingTokenKind(expected), "");
    }

    private static boolean isIdentifierLike(JavaTokenType tokenType) {
        return tokenType == JavaTokenType.IDENTIFIER ||
                (tokenType != null && CONTEXTUAL_IDENTIFIER_TOKENS.contains(tokenType));
    }

    private static boolean isTypeDeclarationModifier(JavaTokenType tokenType) {
        return tokenType != null && TYPE_DECLARATION_MODIFIERS.contains(tokenType);
    }

    private static boolean isMemberModifier(JavaTokenType tokenType) {
        return tokenType != null && MEMBER_MODIFIERS.contains(tokenType);
    }

    private static boolean isParameterOrLocalModifier(JavaTokenType tokenType) {
        return tokenType != null && PARAMETER_AND_LOCAL_MODIFIERS.contains(tokenType);
    }

    private static boolean isCompactConstructorModifier(JavaTokenType tokenType) {
        return tokenType != null && COMPACT_CONSTRUCTOR_MODIFIERS.contains(tokenType);
    }

    private static boolean isPrimitiveTypeToken(JavaTokenType tokenType) {
        return tokenType != null && PRIMITIVE_TYPE_TOKENS.contains(tokenType);
    }

    private static boolean isLiteralToken(JavaTokenType tokenType) {
        return tokenType != null && tokenType.isLiteral();
    }

    private static boolean isUnaryPrefixOperator(JavaTokenType tokenType) {
        return tokenType == JavaTokenType.PLUS_PLUS ||
                tokenType == JavaTokenType.MINUS_MINUS ||
                tokenType == JavaTokenType.TILDA ||
                tokenType == JavaTokenType.PLUS ||
                tokenType == JavaTokenType.MINUS ||
                tokenType == JavaTokenType.EXCLAMATION_MARK;
    }

    private static boolean canStartUnaryExpression(JavaTokenType tokenType) {
        return tokenType == JavaTokenType.OPEN_PAREN ||
                tokenType == JavaTokenType.NEW_KEYWORD ||
                tokenType == JavaTokenType.THIS_KEYWORD ||
                tokenType == JavaTokenType.SUPER_KEYWORD ||
                tokenType == JavaTokenType.SWITCH_KEYWORD ||
                tokenType == JavaTokenType.AT ||
                isIdentifierLike(tokenType) ||
                isPrimitiveTypeToken(tokenType) ||
                tokenType == JavaTokenType.VOID_KEYWORD ||
                isLiteralToken(tokenType) ||
                isUnaryPrefixOperator(tokenType);
    }

    private int mark() {
        return position;
    }

    private void rollback(int checkpoint) {
        position = checkpoint;
    }

    private boolean madeProgress(int checkpoint) {
        return position > checkpoint;
    }

    private GreenNode recoverExpressionNode() {
        List<GreenElement> children = new ArrayList<>();
        boolean consumed = synchronizeToFollowSet(children, EXPRESSION_FOLLOW_SET);
        if (!consumed)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        return greenNode(JavaSyntaxKinds.EXPRESSION, children);
    }

    private GreenNode recoverTypeReferenceNode() {
        List<GreenElement> children = new ArrayList<>();
        boolean consumed = synchronizeToFollowSet(children, TYPE_REFERENCE_FOLLOW_SET, true);
        if (!consumed)
            children.add(missingToken(JavaTokenType.IDENTIFIER));

        return greenNode(JavaSyntaxKinds.TYPE_REFERENCE, children);
    }

    private GreenNode recoverStatementNode() {
        List<GreenElement> children = new ArrayList<>();
        boolean consumed = synchronizeToFollowSet(children, STATEMENT_FOLLOW_SET, true);
        if (!consumed) {
            JavaTokenType tokenType = peekSignificantType();
            if (tokenType == JavaTokenType.SEMICOLON) {
                matchSignificant(JavaTokenType.SEMICOLON, children);
            } else if (isHardStatementBoundaryToken(tokenType)) {
                children.add(missingToken(JavaTokenType.SEMICOLON));
            } else if (tokenType != null && tokenType != JavaTokenType.EOF) {
                children.add(consumeToken());
            } else {
                children.add(missingToken(JavaTokenType.SEMICOLON));
            }
        }

        return greenNode(JavaSyntaxKinds.STATEMENT, children);
    }

    private GreenNode recoverErrorNode(Set<JavaTokenType> followSet, JavaTokenType missingTokenType) {
        List<GreenElement> children = new ArrayList<>();
        boolean consumed = synchronizeToFollowSet(children, followSet, true);
        if (!consumed) {
            JavaTokenType tokenType = peekSignificantType();
            if (tokenType == missingTokenType) {
                matchSignificant(missingTokenType, children);
            } else if (tokenType == null || tokenType == JavaTokenType.EOF || tokenType == JavaTokenType.CLOSE_BRACE) {
                children.add(missingToken(missingTokenType));
            } else {
                children.add(consumeToken());
            }
        }

        return greenNode(JavaSyntaxKinds.ERROR, children);
    }

    private boolean synchronizeToFollowSet(List<GreenElement> children, Set<JavaTokenType> followSet) {
        return synchronizeToFollowSet(children, followSet, false);
    }

    private boolean synchronizeToFollowSet(List<GreenElement> children, Set<JavaTokenType> followSet, boolean trackAngleDepth) {
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;
        boolean consumedSignificant = false;

        while (hasMoreTokens()) {
            Token<JavaTokenType> token = peek();
            if (isTrivia(token)) {
                children.add(consumeToken());
                continue;
            }

            if (isEof(token))
                break;

            JavaTokenType tokenType = token.type();
            boolean atRecoveryBoundary = parenDepth == 0 &&
                    bracketDepth == 0 &&
                    braceDepth == 0 &&
                    (!trackAngleDepth || angleDepth == 0);
            if (atRecoveryBoundary && followSet.contains(tokenType))
                break;

            children.add(consumeToken());
            consumedSignificant = true;
            if (tokenType == JavaTokenType.OPEN_PAREN) {
                parenDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_PAREN && parenDepth > 0) {
                parenDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACKET) {
                bracketDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACKET && bracketDepth > 0) {
                bracketDepth--;
            } else if (tokenType == JavaTokenType.OPEN_BRACE) {
                braceDepth++;
            } else if (tokenType == JavaTokenType.CLOSE_BRACE && braceDepth > 0) {
                braceDepth--;
            }

            if (trackAngleDepth)
                angleDepth = adjustAngleDepthForToken(angleDepth, tokenType);
        }

        return consumedSignificant;
    }

    private boolean matchSignificant(JavaTokenType tokenType, List<GreenElement> children) {
        consumeTrivia(children);
        if (!hasMoreTokens())
            return false;

        Token<JavaTokenType> token = peek();
        if (token.type() != tokenType)
            return false;

        children.add(consumeToken());
        return true;
    }

    private void expectSignificant(JavaTokenType tokenType, List<GreenElement> children) {
        if (!matchSignificant(tokenType, children))
            children.add(missingToken(tokenType));
    }

    private void consumeTrivia(List<GreenElement> children) {
        while (hasMoreTokens() && isTrivia(peek())) {
            children.add(consumeToken());
        }
    }

    private JavaTokenType peekSignificantType() {
        Token<JavaTokenType> token = peekSignificantToken();
        return token == null ? null : token.type();
    }

    private Token<JavaTokenType> peekSignificantToken() {
        int index = nextSignificantIndex(position);
        return index < 0 ? null : tokens.get(index);
    }

    private int nextSignificantIndex(int start) {
        for (int index = start; index < tokens.size(); index++) {
            if (!isTrivia(tokens.get(index)))
                return index;
        }

        return -1;
    }

    private int previousSignificantIndex(int start) {
        for (int index = Math.min(start, tokens.size() - 1); index >= 0; index--) {
            if (!isTrivia(tokens.get(index)))
                return index;
        }

        return -1;
    }

    private Token<JavaTokenType> peek() {
        return tokens.get(position);
    }

    private boolean hasMoreTokens() {
        return position < tokens.size();
    }

    private void readAllTokens() {
        tokens.clear();
        while (true) {
            Token<JavaTokenType> token = lexer.nextToken();
            if (token instanceof Token.IgnoreToken<?> || token.type() == null)
                continue;

            tokens.add(token);
            if (isEof(token))
                break;
        }

        ensureEofToken();
    }

    // Policy: the token stream must always end with EOF; only the compilation-unit parser consumes it.
    private void ensureEofToken() {
        if (!tokens.isEmpty() && isEof(tokens.getLast()))
            return;

        int offset = lexer.offset();
        int line = Math.max(1, lexer.line());
        int column = Math.max(1, lexer.column());
        if (!tokens.isEmpty()) {
            Token<JavaTokenType> last = tokens.getLast();
            offset = last.endOffset();
            line = Math.max(1, last.line());
            column = Math.max(1, last.column());
        }

        tokens.add(new Token.SimpleToken<>(
                JavaTokenType.EOF,
                "",
                offset,
                offset,
                line,
                column,
                TokenChannel.DEFAULT,
                EnumSet.of(TokenFlag.EOF)
        ));
    }

    private static int adjustAngleDepthForToken(int angleDepth, JavaTokenType tokenType) {
        if (tokenType == JavaTokenType.LEFT_ANGLED_BRACKET)
            return angleDepth + 1;
        if (tokenType == JavaTokenType.RIGHT_ANGLED_BRACKET)
            return Math.max(0, angleDepth - 1);
        if (tokenType == JavaTokenType.RIGHT_SHIFT)
            return Math.max(0, angleDepth - 2);
        if (tokenType == JavaTokenType.UNSIGNED_RIGHT_SHIFT)
            return Math.max(0, angleDepth - 3);

        return angleDepth;
    }

    private static boolean isTrivia(Token<?> token) {
        return token.channel() == TokenChannel.TRIVIA;
    }

    private static boolean isEof(Token<JavaTokenType> token) {
        return token.type() == JavaTokenType.EOF || token.flags().contains(TokenFlag.EOF);
    }

    private static GreenNode greenNode(SyntaxKind kind, List<GreenElement> children) {
        return SyntaxInternalFactory.greenNode(kind, children);
    }

    private static GreenElement greenToken(SyntaxKind kind, String text) {
        return SyntaxInternalFactory.greenToken(kind, text);
    }
}
