package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;

import java.util.*;
import java.util.function.Predicate;

public class JavaLexer implements Lexer<JavaTokenType> {
    private static final int MODE_DEFAULT = 0;
    private static final int MODE_STRING = 1; // Mode for when "..ggf.fgjgfjfgkgf"
    private static final int MODE_TEXTBLOCK = 2; // Mode for when """..ggf.fgjgfjfgkgf"""

    private final CharSequence input;
    private final int length;
    private final String sourceId;

    private int pos;
    private int line;
    private int column;

    private final Deque<Integer> modes = new ArrayDeque<>();

    private final List<LexError> diagnostics = new ArrayList<>();
    private final Deque<Token<JavaTokenType>> lookaheadBuffer = new ArrayDeque<>();

    public JavaLexer(CharSequence input, String sourceId) {
        this.input = Objects.requireNonNull(input, "Input cannot be null");
        this.length = input.length();
        this.sourceId = sourceId;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
        this.modes.push(MODE_DEFAULT);
    }

    public JavaLexer(CharSequence input) {
        this(input, null);
    }

    @Override
    public Token<JavaTokenType> nextToken() {
        if (!lookaheadBuffer.isEmpty())
            return lookaheadBuffer.pollFirst();

        Token<JavaTokenType> token = produceNext();
        // TODO: Possibly null check
        return token;
    }

    @Override
    public Token<JavaTokenType> lookahead(int k) {
        if (k <= 0)
            throw new IllegalArgumentException("Lookahead must be greater than 0");

        while (lookaheadBuffer.size() < k) {
            lookaheadBuffer.addLast(produceNext());
        }

        return lookaheadBuffer.stream().skip(k - 1).findFirst().orElseGet(this::createEofToken);
    }

    @Override
    public int offset() {
        return pos;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public int column() {
        return column;
    }

    @Override
    public Optional<Integer> totalLength() {
        return Optional.of(length);
    }

    @Override
    public Snapshot snapshot() {
        return new JavaLexerSnapshot(pos, line, column, mode(), diagnostics, lookaheadBuffer);
    }

    @Override
    public void restore(Snapshot snapshot) {
        if (!(snapshot instanceof JavaLexerSnapshot javaSnapshot))
            throw new IllegalArgumentException("Invalid snapshot type");

        this.pos = javaSnapshot.offset;
        this.line = javaSnapshot.line;
        this.column = javaSnapshot.column;
        this.modes.clear();
        this.modes.push(javaSnapshot.mode);
        this.diagnostics.clear();
        this.diagnostics.addAll(javaSnapshot.diagnostics);
        this.lookaheadBuffer.clear();
        this.lookaheadBuffer.addAll(javaSnapshot.lookaheadBuffer);
    }

    @Override
    public int mode() {
        return modes.peek(); // TODO: Null handling?
    }

    @Override
    public int pushMode(int mode) {
        if (mode != MODE_DEFAULT && mode != MODE_STRING && mode != MODE_TEXTBLOCK)
            throw new IllegalArgumentException("Invalid mode: " + mode);

        int previousMode = mode();
        modes.push(mode);
        return previousMode;
    }

    @Override
    public int popMode() {
        if (modes.isEmpty())
            throw new IllegalStateException("Cannot pop the default mode");

        if (modes.size() == 1)
            return mode();

        modes.pop();
        return mode();
    }

    @Override
    public List<LexError> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    @Override
    public Optional<String> sourceId() {
        return Optional.ofNullable(sourceId);
    }

    private Token<JavaTokenType> produceNext() {
        if (pos >= length)
            return createEofToken();

        Token<JavaTokenType> trivia = readWhitespace();
        if (trivia != null)
            return trivia;

        if (pos >= length)
            return createEofToken();

        int mode = mode();
        return switch (mode) {
            case MODE_DEFAULT -> readDefault();
            case MODE_STRING -> readString();
            case MODE_TEXTBLOCK -> readTextBlock();
            default -> errorToken("Unknown mode: " + mode);
        };
    }

    private Token<JavaTokenType> readString() {
        int startOffset = pos, startLine = line, startCol = column;
        boolean escapeNext = false;

        consume();
        while (hasNext()) {
            char current = charAt(pos);
            if (current == '"' && !escapeNext) {
                consume();
                popMode();
                return token(JavaTokenType.STRING_LITERAL, startOffset, startLine, startCol);
            }

            if (current == '\\' && !escapeNext) {
                escapeNext = true;
                consume();
                if (!consumeEscapeCode()) {
                    popMode();
                    return unterminated(startOffset, startLine, startCol, "Invalid escape sequence in string literal");
                }
            } else {
                escapeNext = false;
                consume();
            }
        }

        popMode();
        return unterminated(startOffset, startLine, startCol, "Unterminated string literal");
    }

    private Token<JavaTokenType> readTextBlock() {
        int startOffset = pos, startLine = line, startCol = column;
        boolean escapeNext = false;

        consume(3);
        while (hasNext()) {
            char current = charAt(pos);
            if (current == '"' && peek(1) == '"' && peek(2) == '"' && !escapeNext) {
                consume(3);
                popMode();
                return token(JavaTokenType.TEXT_BLOCK_LITERAL, startOffset, startLine, startCol);
            }

            if (current == '\\' && !escapeNext) {
                escapeNext = true;
                consume();
                if (!consumeEscapeCode()) {
                    popMode();
                    return unterminated(startOffset, startLine, startCol, "Invalid escape sequence in text block");
                }
            } else {
                escapeNext = false;
            }

            if (current == '\n' || current == '\r') {
                newline();
                consume();
            } else {
                consume();
            }
        }

        return unterminated(startOffset, startLine, startCol, "Unterminated text block");
    }

    private Token<JavaTokenType> readWhitespace() {
        int startOffset = pos, startLine = line, startCol = column;
        char current = charAt(pos);

        boolean foundWhitespace = false;
        while (pos < length) {
            if (current == ' ' || current == '\t' || current == '\f') {
                consume();
                foundWhitespace = true;
            } else if (current == '\r') {
                consume();

                if (match('\n'))
                    consume();

                newline();
                foundWhitespace = true;
            } else if (current == '\n') {
                consume();
                newline();
                foundWhitespace = true;
            } else
                break;

            if (pos < length)
                current = charAt(pos);
        }

        return !foundWhitespace ?
                null :
                token(JavaTokenType.WHITESPACE, startOffset, startLine, startCol, TokenChannel.TRIVIA);
    }

    private Token<JavaTokenType> readDefault() {
        int startOffset = pos, startLine = line, startCol = column;
        char current = charAt(pos);

        if (current == '/') {
            if (peek(1) == '/' || peek(1) == '*') {
                return readComment();
            } else if (peek(1) == '=') {
                consume(2);
                return token(JavaTokenType.DIVIDE_EQUALS, startOffset, startLine, startCol);
            } else {
                consume();
                return token(JavaTokenType.DIVIDE, startOffset, startLine, startCol);
            }
        }

        if (current == '"') {
            if (peek(1) == '"' && peek(2) == '"') {
                pushMode(MODE_TEXTBLOCK);
            } else {
                pushMode(MODE_STRING);
            }

            return new Token.IgnoreToken<>();
        }

        if (current == '\'')
            return readCharLiteral();

        if (JavaTokenType.MULTI_CHAR_TOKENS.containsKey(current)) {
            Optional<Token<JavaTokenType>> multiCharToken = readMulticharToken(current);
            if (multiCharToken.isPresent())
                return multiCharToken.get();
        }

        if (JavaTokenType.SINGLE_CHAR_TOKENS.containsKey(current)) {
            consume();
            return token(JavaTokenType.SINGLE_CHAR_TOKENS.get(current), startOffset, startLine, startCol);
        }

        if (Character.isJavaIdentifierStart(current)) {
            do {
                consume();
            } while (hasNext() && Character.isJavaIdentifierPart(charAt(pos)));

            String lexeme = slice(startOffset, pos);
            JavaTokenType type = JavaTokenType.listKeywords().getOrDefault(lexeme, JavaTokenType.IDENTIFIER);
            return token(type, startOffset, startLine, startCol);
        }

        if (Character.isDigit(current))
            return readNumber();

        consume();
        diagnostics.add(new LexError("Unexpected character: '" + current + "'", startOffset, startLine, startCol));
        return token(JavaTokenType.UNKNOWN, startOffset, startLine, startCol, TokenChannel.DEFAULT, EnumSet.of(TokenFlag.ERROR));
    }

    private Token<JavaTokenType> readComment() {
        int startOffset = pos, startLine = line, startCol = column;
        char current = charAt(pos);

        while (hasNext()) {
            if (current == '/' && peek(1) == '/') { // we are looking for "//...."
                consume(2);
                while (hasNext()) {
                    if (charAt(pos) == '\n' || charAt(pos) == '\r')
                        return token(JavaTokenType.LINE_COMMENT, startOffset, startLine, startCol, TokenChannel.DEFAULT);

                    consume();
                }
            } else if (current == '/' && peek(1) == '*') {
                boolean isJavadoc = peek(2) == '*';
                consume(2);
                while (pos < length) {
                    char nextChar = charAt(pos);
                    if (nextChar == '*' && peek(1) == '/') {
                        consume(2);
                        return token(isJavadoc ? JavaTokenType.JAVADOC_COMMENT : JavaTokenType.BLOCK_COMMENT, startOffset, startLine, startCol, TokenChannel.DEFAULT);
                    }

                    if (nextChar == '\n')
                        newline();
                    else if (nextChar == '\r' && peek(1) == '\n') {
                        consume(2);
                        newline();
                    } else {
                        consume();
                    }
                }

                return unterminated(startOffset, startLine, startCol, "Unterminated block comment");
            }
        }

        return unterminated(startOffset, startLine, startCol, "Unterminated comment");
    }

    private Token<JavaTokenType> readCharLiteral() {
        int startOffset = pos, startLine = line, startColumn = column;
        consume();
        if (!hasNext())
            return unterminated(startOffset, startLine, startColumn, "Unterminated char literal");

        char current = peek(0);
        if (current == '\\') {
            consume();
            if (!consumeEscapeCode())
                return unterminated(startOffset, startLine, startColumn, "Invalid escape in char literal");
        } else if (current == '\n' || current == '\r' || current == '\'')
            return unterminated(startOffset, startLine, startColumn, "Empty/invalid char literal");
        else {
            consume();
        }

        if (match('\'')) {
            consume();
            return token(JavaTokenType.CHARACTER_LITERAL, startOffset, startLine, startColumn);
        }

        return unterminated(startOffset, startLine, startOffset, "Unterminated char literal");
    }

    private Optional<Token<JavaTokenType>> readMulticharToken(char current) {
        int startOffset = pos, line = this.line, column = this.column;
        List<Map.Entry<CharSequence, JavaTokenType>> possibleTokens = JavaTokenType.MULTI_CHAR_TOKENS.get(current)
                .stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .toList();

        CharSequence longestMatch = null;
        JavaTokenType matchType = null;
        for (Map.Entry<CharSequence, JavaTokenType> possibleToken : possibleTokens) {
            CharSequence possibleTokenKey = possibleToken.getKey();
            if ((pos + 1) + possibleTokenKey.length() - 1 < length) { // TODO: Confirm (pos + 1) is correct
                for (int i = 0; i < possibleTokenKey.length(); i++) {
                    if (peek(i) != possibleTokenKey.charAt(i))
                        break;

                    if (i == possibleTokenKey.length() - 1) {
                        longestMatch = possibleTokenKey;
                        matchType = possibleToken.getValue();
                        break;
                    }
                }
            }

            if (longestMatch != null) {
                consume(longestMatch.length());

                return Optional.of(token(matchType, startOffset, line, column, TokenChannel.DEFAULT));
            }
        }

        return Optional.empty();
    }

    private Token<JavaTokenType> readNumber() {
        int startOffset = pos, startLine = this.line, startColumn = this.column;

        var number = new StringBuilder();
        JavaTokenType type = JavaTokenType.NUMBER_INT_LITERAL;

        char current = consume();
        number.append(current);

        if (current == '0' && hasNext()) {
            char next = peek(1);
            switch (Character.toLowerCase(next)) {
                case 'x' -> {
                    consume();
                    number.append(next);
                    return readIntegerLiteral(number, JavaTokenType.NUMBER_HEXADECIMAL_LITERAL, JavaLexer::isHexadecimal, startOffset, startLine, startColumn);
                }
                case 'b' -> {
                    consume();
                    number.append(next);
                    return readIntegerLiteral(number, JavaTokenType.NUMBER_BINARY_LITERAL, ch -> ch == '0' || ch == '1', startOffset, startLine, startColumn);
                }
                case '.' -> {
                    consume();
                    number.append(next);
                    return readDecimalLiteral(number, startOffset, startLine, startColumn);
                }
                default -> {
                    if (next >= '0' && next <= '7') {
                        type = JavaTokenType.NUMBER_OCTAL_LITERAL;
                        number.append(next);
                        return readIntegerLiteral(number, type, ch -> ch >= '0' && ch <= '7', startOffset, startLine, startColumn);
                    }
                }
            }
        }

        boolean lastWasUnderscore = false;
        while (hasNext()) {
            char nextChar = peek(0);
            if (Character.isDigit(nextChar)) {
                consume();
                number.append(nextChar);
                lastWasUnderscore = false;
            } else if (nextChar == '_') {
                if (lastWasUnderscore)
                    return errorToken("Consecutive underscores in number literal: " + number);

                if (pos + 1 >= length)
                    return errorToken("Underscore at end of number literal: " + number);

                char afterUnderscore = peek(1);
                if (!Character.isDigit(afterUnderscore))
                    return errorToken("Underscore not followed by digit in number literal: " + number);

                consume();
                number.append(nextChar);
                lastWasUnderscore = true;
            } else {
                break;
            }
        }

        if (lastWasUnderscore)
            return errorToken("Number literal ends with underscore: " + number);

        if (hasNext() && peek(0) == '.') {
            consume();
            number.append('.');
            return readDecimalLiteral(number, startOffset, startLine, startColumn);
        }

        if (hasNext() && (peek(0) == 'e' || peek(0) == 'E'))
            return readDecimalExponent(number, startOffset, startLine, startColumn);

        if (hasNext() && (peek(0) == 'l' || peek(0) == 'L')) {
            consume();
            number.append('L');
        } else if (hasNext() && (peek(0) == 'f' || peek(0) == 'F') || (peek(0) == 'd' || peek(0) == 'D')) {
            consume();
            number.append('D');
            type = JavaTokenType.NUMBER_FLOATING_POINT_LITERAL;
        }

        if (!doesCharacterTerminateNumber(peek(0)))
            return errorToken("Unexpected character after number literal: " + number);

        return token(type, startOffset, startLine, startColumn);
    }

    private Token<JavaTokenType> readIntegerLiteral(StringBuilder text, JavaTokenType type, Predicate<Character> isCharValid, int startOffset, int startLine, int startColumn) {
        boolean seenDigit = false, lastUnderscore = false;
        while (hasNext()) {
            char current = peek(0);
            if (isCharValid.test(current)) {
                consume();
                text.append(current);
                seenDigit = true;
                lastUnderscore = false;
            } else if (current == '_') {
                if (!seenDigit || lastUnderscore)
                    return errorToken("Invalid underscore in numeric literal: " + text);

                if (pos + 1 >= length || !isCharValid.test(peek(1)))
                    return errorToken("Underscore must separate digits: " + text);

                consume();
                text.append(current);
                lastUnderscore = true;
            } else break;
        }

        if (!seenDigit)
            return errorToken("Malformed literal (missing digits): " + text);
        if (lastUnderscore)
            return errorToken("Malformed literal (trailing underscore): " + text);

        char current = peek(0);
        if (hasNext() && Character.toLowerCase(current) == 'l') {
            text.append(consume());
        } else if (hasNext() && (current == 'f' || current == 'd')) {
            return errorToken("Floating suffix not allowed on non-decimal integer literal: " + text + current);
        }

        if (!doesCharacterTerminateNumber(current))
            return errorToken("Invalid trailing characters after literal: " + text + current);

        return token(type, startOffset, startLine, startColumn);
    }

    private Token<JavaTokenType> readDecimalLiteral(StringBuilder text, int startOffset, int startLine, int startColumn) {
        boolean sawFracDigits = false, lastUnderscore = false;
        while (hasNext()) {
            char current = peek(0);
            if (Character.isDigit(current)) {
                consume();
                text.append(current);
                sawFracDigits = true;
                lastUnderscore = false;
            } else if (current == '_') {
                if (lastUnderscore)
                    return errorToken("Consecutive underscores in number literal: " + text);

                if (pos + 1 >= length || !Character.isDigit(peek(1)))
                    return errorToken("Underscore must separate digits: " + text);

                consume();
                text.append(current);
                lastUnderscore = true;
            } else break;
        }

        if (lastUnderscore)
            return errorToken("Trailing underscore in number literal: " + text);
        if (!sawFracDigits)
            return errorToken("Malformed floating point literal (missing digits): " + text);

        if (hasNext() && Character.toLowerCase(peek(0)) == 'e')
            return readDecimalExponent(text, startOffset, startLine, startColumn);

        char current = peek(0);
        if (hasNext() && (Character.toLowerCase(current) == 'f' || Character.toLowerCase(current) == 'd')) {
            text.append(consume());
        }

        if (!doesCharacterTerminateNumber(current))
            return errorToken("Invalid trailing characters after floating literal: " + text + current);

        return token(JavaTokenType.NUMBER_FLOATING_POINT_LITERAL, startOffset, startLine, startColumn);
    }

    private Token<JavaTokenType> readDecimalExponent(StringBuilder text, int startOffset, int startLine, int startColumn) {
        char current = consume();
        text.append(current);

        if (hasNext() && (peek(0) == '+' || peek(0) == '-')) {
            text.append(consume());
        }

        boolean sawExpDigits = false, lastUnderscore = false;
        while (hasNext()) {
            current = peek(0);
            if (Character.isDigit(current)) {
                consume();
                text.append(current);
                sawExpDigits = true;
                lastUnderscore = false;
            } else if (current == '_') {
                if (lastUnderscore)
                    return errorToken("Consecutive underscores in exponent: " + text);
                if (!sawExpDigits)
                    return errorToken("Underscore in exponent without preceding digits: " + text);

                if (pos + 1 >= length || !Character.isDigit(peek(1)))
                    return errorToken("Underscore must separate digits in exponent: " + text);

                consume();
                text.append(current);
                lastUnderscore = true;
            } else {
                break;
            }
        }

        if (lastUnderscore)
            return errorToken("Trailing underscore in exponent: " + text);
        if (!sawExpDigits)
            return errorToken("Malformed exponent (missing digits): " + text);

        if (hasNext() && (peek(0) == 'f' || peek(0) == 'd')) {
            text.append(consume());
        }

        if (!doesCharacterTerminateNumber(peek(0)))
            return errorToken("Invalid trailing characters after exponent: " + text + peek(0));

        return token(JavaTokenType.NUMBER_FLOATING_POINT_LITERAL, startOffset, startLine, startColumn);
    }

    private boolean consumeEscapeCode() {
        if (!hasNext())
            return false;

        char current = consume();
        return switch (current) {
            case 'b', 't', 'n', 'f', 'r', 's', '"', '\'', '\\' -> true;
            case 'u' -> {
                while (hasNext() && peek(0) == 'u') {
                    consume();
                }

                if (pos + 4 > length)
                    yield false; // not enough characters for a Unicode escape

                int val = 0;
                for (int i = 0; i < 4; i++) {
                    char d = consume();
                    if (!isHexadecimal(d))
                        yield false; // invalid hex digit

                    val = (val << 4) + (Character.digit(d, 16));
                }

                yield true; // valid Unicode escape
            }
            default -> {
                if (current >= '0' && current <= '7') {
                    int val = current - '0';
                    int count = 1;
                    while (count < 3 && pos < length) {
                        char d = peek(0);
                        if (d < '0' || d > '7') break;
                        int next = val * 8 + (d - '0');
                        if (next > 255) break; // cap at 255; stop before overflow
                        consume();
                        val = next;
                        count++;
                    }

                    yield true;
                }

                yield false; // invalid escape like \x or \9
            }
        };
    }

    private static boolean doesCharacterTerminateNumber(char character) {
        return Character.isWhitespace(character) || JavaTokenType.SINGLE_CHAR_TOKENS.containsKey(character);
    }

    private static boolean isHexadecimal(char character) {
        return Character.isDigit(character) || (character >= 'A' && character <= 'F') || (character >= 'a' && character <= 'f');
    }

    private boolean hasNext() {
        return pos < length;
    }

    private char charAt(int pos) {
        return this.input.charAt(pos);
    }

    private char peek(int offset) {
        int pos = this.pos + offset;
        return (pos >= 0 && pos < length) ? charAt(pos) : '\0';
    }

    private boolean match(char expected) {
        return pos < length && charAt(pos) == expected;
    }

    private char consume() {
        char c = charAt(this.pos++);
        if (c == '\n')
            newline();
        else
            column++;

        return c;
    }

    private char consume(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("n must be greater than 0");
        char c = '\0';
        for (int i = 0; i < n; i++) {
            c = consume();
        }

        return c;
    }

    private void newline() {
        line++;
        column = 1;
    }

    private String slice(int start, int end) {
        return input.subSequence(start, end).toString();
    }

    private Token<JavaTokenType> token(JavaTokenType type, int startOff, int startLine, int startCol) {
        return token(type, startOff, startLine, startCol, TokenChannel.DEFAULT);
    }

    private Token<JavaTokenType> token(JavaTokenType type, int startOff, int startLine, int startCol, TokenChannel channel) {
        return token(type, startOff, startLine, startCol, channel, EnumSet.noneOf(TokenFlag.class));
    }

    private Token<JavaTokenType> token(JavaTokenType type, int startOff, int startLine, int startCol, TokenChannel channel, TokenFlag... flags) {
        return token(type, startOff, startLine, startCol, channel, EnumSet.copyOf(Arrays.asList(flags)));
    }

    private Token<JavaTokenType> token(JavaTokenType type, int startOff, int startLine, int startCol, TokenChannel channel, Set<TokenFlag> flags) {
        return new Token.SimpleToken<>(type, slice(startOff, pos), startOff, pos, startLine, startCol, channel, flags);
    }


    private Token<JavaTokenType> errorToken(String message) {
        diagnostics.add(new LexError(message, pos, line, column));
        return new Token.SimpleToken<>(JavaTokenType.IDENTIFIER, "", pos, pos, line, column, TokenChannel.DEFAULT, EnumSet.of(TokenFlag.ERROR));
    }

    private Token<JavaTokenType> unterminated(int startOff, int startLine, int startCol, String message) {
        diagnostics.add(new LexError(message, startOff, startLine, startCol));
        return new Token.SimpleToken<>(JavaTokenType.STRING_LITERAL, slice(startOff, pos), startOff, pos, startLine, startCol, TokenChannel.DEFAULT, EnumSet.of(TokenFlag.INCOMPLETE, TokenFlag.ERROR));
    }

    private Token<JavaTokenType> createEofToken() {
        return new Token.SimpleToken<>(JavaTokenType.EOF, "", pos, pos, line, column, TokenChannel.DEFAULT, EnumSet.of(TokenFlag.EOF));
    }
}
