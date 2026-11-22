package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;

import java.util.*;
import java.util.function.Predicate;

/**
 * Tokenises Java source while retaining whitespace and comments on the trivia channel.
 * Tracks source positions, buffers lookahead tokens, and collects lexical diagnostics.
 * String and text-block opening delimiters switch lexer modes and emit an ignore token
 * before the literal is read on the next call.
 */
public class JavaLexer implements Lexer<JavaTokenType> {
    private static final int MODE_DEFAULT = 0;
    private static final int MODE_STRING = 1; // Mode for when ".ggf.fgjgfjfgkgf"
    private static final int MODE_TEXTBLOCK = 2; // Mode for when """.ggf.fgjgfjfgkgf"""

    private final CharSequence input;
    private final int length;
    private final String sourceId;

    private int pos;
    private int line;
    private int column;

    private final Deque<Integer> modes = new ArrayDeque<>();

    private final List<LexError> diagnostics = new ArrayList<>();
    private final Deque<Token<JavaTokenType>> lookaheadBuffer = new ArrayDeque<>();

    private final Runnable activityCheck;

    /**
     * Creates a lexer positioned at the beginning of the supplied source.
     *
     * @param input the source characters to tokenise; must not be null
     * @param sourceId an optional source identifier, or null when unspecified
     * @throws NullPointerException if input is null
     */
    public JavaLexer(CharSequence input, String sourceId) {
        this(input, sourceId, new ActivityCheckpoint(ActivityCheckpoint::checkInterrupted));
    }

    /**
     * Creates a lexer with a cooperative activity check supplied by the parse owner.
     * Token access, including buffered tokens and EOF, polls the callback's work budget.
     * Ordinary callbacks still run immediately. Checks also run periodically
     * within character consumption so large individual tokens remain cancellable.
     * The callback can throw cancellation or deadline exceptions; they propagate directly
     * rather than becoming lexical diagnostics. Discard the lexer after such a failure:
     * its partially consumed state is not rolled back.
     *
     * @param input source characters to tokenize
     * @param sourceId optional source identifier, or {@code null} when unspecified
     * @param activityCheck request activity check, invoked on the lexing thread
     * @throws NullPointerException if the input or activity check is {@code null}
     */
    public JavaLexer(CharSequence input, String sourceId, Runnable activityCheck) {
        this.input = Objects.requireNonNull(input, "Input cannot be null");
        this.length = input.length();
        this.sourceId = sourceId;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
        this.modes.push(MODE_DEFAULT);
        this.activityCheck = Objects.requireNonNull(activityCheck, "Activity check cannot be null");
    }

    /**
     * Creates a lexer without a source identifier.
     *
     * @param input the source characters to tokenise; must not be null
     * @throws NullPointerException if input is null
     */
    public JavaLexer(CharSequence input) {
        this(input, null);
    }

    @Override
    public Token<JavaTokenType> nextToken() {
        ActivityCheckpoint.poll(activityCheck);

        if (!lookaheadBuffer.isEmpty())
            return lookaheadBuffer.pollFirst();

        Token<JavaTokenType> token = produceNext();
        if (token == null) {
            token = createEofToken();
        }

        return token;
    }

    @Override
    public Token<JavaTokenType> lookahead(int k) {
        if (k <= 0)
            throw new IllegalArgumentException("Lookahead must be greater than 0");

        ActivityCheckpoint.poll(activityCheck);

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
        Integer peeked = modes.peek();
        if (peeked == null)
            throw new IllegalStateException("Mode stack is empty");

        return peeked;
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
        ActivityCheckpoint.poll(activityCheck);

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

                // The escaped codepoint has already been consumed; do not treat
                // the next character as escaped as well.
                escapeNext = false;
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

                // Escape handling is complete for this iteration.
                escapeNext = false;
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

                if (match('\n')) {
                    consume();
                }

                newline();
                foundWhitespace = true;
            } else if (current == '\n') {
                consume();
                newline();
                foundWhitespace = true;
            } else
                break;

            if (pos < length) {
                current = charAt(pos);
            }
        }

        return !foundWhitespace
            ? null
            : token(JavaTokenType.WHITESPACE, startOffset, startLine, startCol, TokenChannel.TRIVIA);
    }

    private Token<JavaTokenType> readDefault() {
        int startOffset = pos, startLine = line, startCol = column;
        char current = charAt(pos);

        if (current == '/') {
            if (peek(1) == '/' || peek(1) == '*')
                return readComment();
            else if (peek(1) == '=') {
                consume(2);
                return token(JavaTokenType.SLASH_EQUALS, startOffset, startLine, startCol);
            } else {
                consume();
                return token(JavaTokenType.SLASH, startOffset, startLine, startCol);
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

        if (current == '.' && isDecimalDigit(peek(1)))
            return readNumber();

        char translatedChar = translatedAt(pos);
        if (JavaTokenType.MULTI_CHAR_TOKENS.containsKey(translatedChar))
            return readOperator(translatedChar);

        if (JavaTokenType.SINGLE_CHAR_TOKENS.containsKey(current)) {
            if (current == '@') {
                for (int i = 0; i < "interface ".length(); i++) {
                    if (peek(i + 1) != "interface ".charAt(i))
                        break;

                    if (i == "interface ".length() - 1) {
                        consume("interface ".length() + 1);
                        return token(JavaTokenType.AT_INTERFACE_KEYWORD, startOffset, startLine, startCol);
                    }
                }
            }
            consume();
            return token(JavaTokenType.SINGLE_CHAR_TOKENS.get(current), startOffset, startLine, startCol);
        }

        if (Character.isJavaIdentifierStart(translatedChar)) {
            StringBuilder translated = null;
            do {
                int escape = JavaUnicodeEscapes.escapeEnd(input, pos);
                if (escape > 0 && translated == null) {
                    translated = new StringBuilder(slice(startOffset, pos));
                }
                if (translated != null) {
                    translated.append(translatedAt(pos));
                }
                consume(escape > 0 ? escape - pos : 1);
            } while (hasNext() && Character.isJavaIdentifierPart(translatedAt(pos)));

            // Escaped identifiers keep their raw text; only their keyword status depends on the translation.
            String lexeme = translated == null ? slice(startOffset, pos) : translated.toString();
            JavaTokenType type = JavaTokenType.listKeywords().getOrDefault(lexeme, JavaTokenType.IDENTIFIER);
            if ("true".equals(lexeme) || "false".equals(lexeme)) {
                type = JavaTokenType.BOOLEAN_LITERAL;
            } else if ("null".equals(lexeme)) {
                type = JavaTokenType.NULL_LITERAL;
            }

            return token(type, startOffset, startLine, startCol);
        }

        if (Character.isDigit(current))
            return readNumber();

        consume();
        diagnostics.add(new LexError("Unexpected character: '" + current + "'", startOffset, startLine, startCol));
        return token(JavaTokenType.UNKNOWN, startOffset, startLine, startCol, TokenChannel.DEFAULT,
            EnumSet.of(TokenFlag.ERROR));
    }

    private Token<JavaTokenType> readComment() {
        int startOffset = pos, startLine = line, startCol = column;
        char current = charAt(pos);

        while (hasNext()) {
            if (current == '/' && peek(1) == '/') { // we are looking for "//...."
                consume(2);
                while (hasNext()) {
                    if (charAt(pos) == '\n' || charAt(pos) == '\r')
                        return token(JavaTokenType.LINE_COMMENT, startOffset, startLine, startCol, TokenChannel.TRIVIA);

                    consume();
                }
            } else if (current == '/' && peek(1) == '*') {
                boolean isJavadoc = peek(2) == '*';
                consume(2);
                while (pos < length) {
                    char nextChar = charAt(pos);
                    if (nextChar == '*' && peek(1) == '/') {
                        consume(2);
                        return token(isJavadoc ? JavaTokenType.JAVADOC_COMMENT : JavaTokenType.BLOCK_COMMENT,
                            startOffset, startLine, startCol, TokenChannel.TRIVIA);
                    }

                    if (nextChar == '\n') {
                        consume();
                        newline();
                    } else if (nextChar == '\r' && peek(1) == '\n') {
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

    private Token<JavaTokenType> readOperator(char current) {
        int startOffset = pos, line = this.line, column = this.column;
        List<Map.Entry<CharSequence, JavaTokenType>> possibleTokens = JavaTokenType.MULTI_CHAR_TOKENS.get(current)
            .stream()
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .toList();

        int afterFirst = translatedCharEnd(pos);
        for (Map.Entry<CharSequence, JavaTokenType> possibleToken : possibleTokens) {
            CharSequence spelling = possibleToken.getKey();
            int end = afterFirst;
            int matched = 1;
            while (matched < spelling.length() && end < length && translatedAt(end) == spelling.charAt(matched)) {
                end = translatedCharEnd(end);
                matched++;
            }
            if (matched == spelling.length()) {
                consume(end - pos);
                return token(possibleToken.getValue(), startOffset, line, column);
            }
        }

        consume(afterFirst - pos);
        return token(JavaTokenType.SINGLE_CHAR_TOKENS.get(current), startOffset, line, column);
    }

    private int translatedCharEnd(int offset) {
        int escape = JavaUnicodeEscapes.escapeEnd(input, offset);
        return escape > 0 ? escape : offset + 1;
    }

    /**
     * Reads a numeric literal, including one that starts with a decimal point. A malformed literal still becomes a
     * single token spanning all of its text, flagged as an error, so no source text is lost.
     */
    private Token<JavaTokenType> readNumber() {
        int startOffset = pos, startLine = this.line, startColumn = this.column;
        if (peek(0) == '0' && Character.toLowerCase(peek(1)) == 'x') {
            consume(2);
            return readHexadecimalNumber(startOffset, startLine, startColumn);
        }

        if (peek(0) == '0' && Character.toLowerCase(peek(1)) == 'b') {
            consume(2);
            String problem = readDigits(JavaLexer::isBinaryDigit);
            if (problem == null && Character.toLowerCase(peek(0)) == 'l') {
                consume();
            }
            return finishNumber(JavaTokenType.NUMBER_BINARY_LITERAL, problem, startOffset, startLine, startColumn);
        }

        String problem = null;
        boolean floating = false;
        if (peek(0) == '.') {
            consume();
            floating = true;
        } else {
            problem = readDigits(JavaLexer::isDecimalDigit);
            if (problem == null && peek(0) == '.') {
                consume();
                floating = true;
                if (!isDecimalDigit(peek(0)))
                    // A trailing point is valid, as in 1. or 1.e3; only the fraction is optional.
                    return finishDecimal(true, null, startOffset, startLine, startColumn);
            }
        }

        if (floating && problem == null) {
            problem = readDigits(JavaLexer::isDecimalDigit);
        }
        return finishDecimal(floating, problem, startOffset, startLine, startColumn);
    }

    private Token<JavaTokenType> finishDecimal(
        boolean floating,
        String problem,
        int startOffset,
        int startLine,
        int startColumn
    ) {
        if (problem == null && Character.toLowerCase(peek(0)) == 'e') {
            floating = true;
            problem = readExponent();
        }

        if (problem == null) {
            char suffix = Character.toLowerCase(peek(0));
            if (suffix == 'f' || suffix == 'd') {
                consume();
                floating = true;
            } else if (suffix == 'l' && !floating) {
                consume();
            }
        }

        JavaTokenType type = JavaTokenType.NUMBER_FLOATING_POINT_LITERAL;
        if (!floating) {
            String digits = slice(startOffset, pos).replace("_", "").replaceAll("[lL]$", "");
            type = digits.length() > 1 && digits.charAt(0) == '0'
                ? JavaTokenType.NUMBER_OCTAL_LITERAL
                : JavaTokenType.NUMBER_INT_LITERAL;
            if (problem == null && type == JavaTokenType.NUMBER_OCTAL_LITERAL
                && digits.chars().anyMatch(digit -> digit > '7')) {
                problem = "Invalid digit in octal literal";
            }
        }

        return finishNumber(type, problem, startOffset, startLine, startColumn);
    }

    private Token<JavaTokenType> readHexadecimalNumber(int startOffset, int startLine, int startColumn) {
        boolean hasDigits = isHexadecimal(peek(0)) || peek(0) == '_';
        String problem = hasDigits ? readDigits(JavaLexer::isHexadecimal) : null;
        boolean floating = false;
        if (problem == null && peek(0) == '.') {
            consume();
            floating = true;
            if (isHexadecimal(peek(0)) || peek(0) == '_') {
                hasDigits = true;
                problem = readDigits(JavaLexer::isHexadecimal);
            }
        }

        if (problem == null && !hasDigits) {
            problem = "Missing digits in hexadecimal literal";
        }
        if (problem == null && Character.toLowerCase(peek(0)) == 'p') {
            floating = true;
            problem = readExponent();
        } else if (problem == null && floating) {
            problem = "Hexadecimal floating-point literal requires a binary exponent";
        }

        if (problem == null) {
            char suffix = Character.toLowerCase(peek(0));
            if (floating && (suffix == 'f' || suffix == 'd') || !floating && suffix == 'l') {
                consume();
            }
        }

        return finishNumber(floating
            ? JavaTokenType.NUMBER_FLOATING_POINT_LITERAL
            : JavaTokenType.NUMBER_HEXADECIMAL_LITERAL, problem, startOffset, startLine, startColumn);
    }

    /** Reads an exponent marker, an optional sign and the exponent digits. */
    private String readExponent() {
        consume();
        if (peek(0) == '+' || peek(0) == '-') {
            consume();
        }

        return readDigits(JavaLexer::isDecimalDigit);
    }

    /**
     * Reads digits that may be separated by underscores, which must sit between digits.
     *
     * @return a description of the problem, or {@code null} when the digits are well formed
     */
    private String readDigits(Predicate<Character> isDigit) {
        boolean sawDigit = false;
        boolean lastUnderscore = false;
        while (hasNext()) {
            char current = peek(0);
            if (isDigit.test(current)) {
                sawDigit = true;
                lastUnderscore = false;
            } else if (current == '_') {
                if (!sawDigit)
                    return "Underscore must follow a digit in number literal";

                lastUnderscore = true;
            } else
                break;

            consume();
        }

        if (!sawDigit)
            return "Missing digits in number literal";
        if (lastUnderscore)
            return "Underscore must be followed by a digit in number literal";

        return null;
    }

    private Token<JavaTokenType> finishNumber(
        JavaTokenType type,
        String problem,
        int startOffset,
        int startLine,
        int startColumn
    ) {
        if (problem == null && hasNext() && !doesCharacterTerminateNumber(peek(0))) {
            problem = "Unexpected character after number literal: '" + peek(0) + "'";
        }

        if (problem == null)
            return token(type, startOffset, startLine, startColumn);

        while (hasNext() && (Character.isLetterOrDigit(peek(0)) || peek(0) == '_')) {
            consume();
        }

        diagnostics.add(new LexError(problem + ": " + slice(startOffset, pos), startOffset, startLine, startColumn));
        return token(type, startOffset, startLine, startColumn, TokenChannel.DEFAULT, TokenFlag.ERROR);
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
                        if (d < '0' || d > '7')
                            break;
                        int next = val * 8 + (d - '0');
                        if (next > 255)
                            break; // cap at 255; stop before overflow
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

    private static boolean isDecimalDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static boolean isBinaryDigit(char character) {
        return character == '0' || character == '1';
    }

    private static boolean isHexadecimal(char character) {
        return isDecimalDigit(character) || (character >= 'A' && character <= 'F')
            || (character >= 'a' && character <= 'f');
    }

    private boolean hasNext() {
        return pos < length;
    }

    private char charAt(int pos) {
        return this.input.charAt(pos);
    }

    private char translatedAt(int pos) {
        int escape = JavaUnicodeEscapes.escapeEnd(input, pos);
        return escape > 0 ? JavaUnicodeEscapes.decoded(input, escape) : charAt(pos);
    }

    private char peek(int offset) {
        int pos = this.pos + offset;
        return (pos >= 0 && pos < length) ? charAt(pos) : '\0';
    }

    private boolean match(char expected) {
        return pos < length && charAt(pos) == expected;
    }

    private char consume() {
        if (this.pos % 256 == 0) {
            activityCheck.run();
        }

        char c = charAt(this.pos++);
        if (c == '\n') {
            newline();
        } else {
            column++;
        }

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

    private Token<JavaTokenType> token(
        JavaTokenType type,
        int startOff,
        int startLine,
        int startCol,
        TokenChannel channel
    ) {
        return token(type, startOff, startLine, startCol, channel, EnumSet.noneOf(TokenFlag.class));
    }

    private Token<JavaTokenType> token(
        JavaTokenType type,
        int startOff,
        int startLine,
        int startCol,
        TokenChannel channel,
        TokenFlag... flags
    ) {
        return token(type, startOff, startLine, startCol, channel, EnumSet.copyOf(Arrays.asList(flags)));
    }

    private Token<JavaTokenType> token(
        JavaTokenType type,
        int startOff,
        int startLine,
        int startCol,
        TokenChannel channel,
        Set<TokenFlag> flags
    ) {
        return new Token.SimpleToken<>(type, slice(startOff, pos), startOff, pos, startLine, startCol, channel, flags);
    }

    private Token<JavaTokenType> errorToken(String message) {
        diagnostics.add(new LexError(message, pos, line, column));
        return new Token.SimpleToken<>(JavaTokenType.IDENTIFIER, "", pos, pos, line, column, TokenChannel.DEFAULT,
            EnumSet.of(TokenFlag.ERROR));
    }

    private Token<JavaTokenType> unterminated(int startOff, int startLine, int startCol, String message) {
        diagnostics.add(new LexError(message, startOff, startLine, startCol));
        return new Token.SimpleToken<>(JavaTokenType.STRING_LITERAL, slice(startOff, pos), startOff, pos, startLine,
            startCol, TokenChannel.DEFAULT, EnumSet.of(TokenFlag.INCOMPLETE, TokenFlag.ERROR));
    }

    private Token<JavaTokenType> createEofToken() {
        return new Token.SimpleToken<>(JavaTokenType.EOF, "", pos, pos, line, column, TokenChannel.DEFAULT,
            EnumSet.of(TokenFlag.EOF));
    }
}
