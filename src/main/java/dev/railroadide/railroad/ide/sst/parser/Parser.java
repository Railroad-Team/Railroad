package dev.railroadide.railroad.ide.sst.parser;

import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class Parser<T extends Enum<T>, N, E extends N> {
    protected final Lexer<T> lexer;
    protected final List<ParseDiagnostic> diagnostics = new ArrayList<>();

    private final ArrayDeque<Token<T>> lookaheadBuffer = new ArrayDeque<>();

    private Token<T> previousToken = null;

    private final int maxErrorBurst = 5; // TODO: Understand this
    private int burstCount = 0;

    /**
     * Constructs a parser with the given lexer.
     *
     * @param lexer the lexer to use for tokenizing input
     */
    protected Parser(Lexer<T> lexer) {
        this.lexer = Objects.requireNonNull(lexer, "Lexer cannot be null");
    }

    /**
     * Parses the input and returns the root node of the syntax tree.
     *
     * @return the root node of the parsed syntax tree
     */
    public abstract N parse();

    /**
     * Returns the list of diagnostics collected during parsing.
     *
     * @return an unmodifiable list of parse diagnostics
     */
    public List<ParseDiagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    /**
     * Looks ahead in the token stream by n tokens.
     *
     * @param n the number of tokens to look ahead (must be greater than 0)
     * @return the nth token ahead in the stream, or an EOF token if at end
     * @throws IllegalArgumentException if n is less than or equal to 0
     */
    protected Token<T> lookahead(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("Lookahead must be greater than 0");

        fillBuffer(n);
        return lookaheadBuffer.stream().skip(n - 1).findFirst().orElseGet(this::eof);
    }

    /**
     * Returns the type of the nth token ahead in the stream.
     *
     * @param n the number of tokens to look ahead (must be greater than 0)
     * @return the type of the nth token ahead, or null if at end
     * @throws IllegalArgumentException if n is less than or equal to 0
     */
    protected T lookaheadType(int n) {
        return lookahead(n).type();
    }

    /**
     * Returns the current token (the next token to be processed).
     *
     * @return the current token
     */
    protected Token<T> current() {
        return lookahead(1);
    }

    /**
     * Returns the previous token processed by the parser.
     *
     * @return the previous token, or null if no tokens have been processed yet
     */
    protected Token<T> previous() {
        return previousToken;
    }

    /**
     * Checks if the parser is at the end of the input.
     *
     * @return true if at end, false otherwise
     */
    protected boolean isAtEnd() {
        Token<T> lookahead = lookahead(1);
        return lookahead.type() == JavaTokenType.EOF || lookahead.flags().contains(TokenFlag.EOF);
    }

    /**
     * Advances the parser to the next token and returns it.
     *
     * @return the next token
     */
    protected Token<T> advance() {
        fillBuffer(1);
        previousToken = lookaheadBuffer.isEmpty() ? eof() : lookaheadBuffer.removeFirst();
        return previousToken;
    }

    /**
     * Checks if the next token matches the given type and advances if it does.
     *
     * @param type the token type to match
     * @return true if matched, false otherwise
     */
    protected boolean match(T type) {
        if (lookaheadType(1) == type) {
            advance();
            return true;
        }

        return false;
    }

    /**
     * Checks if the next token matches any of the given types and advances if it does.
     *
     * @param types the token types to match
     * @return the matched type, or null if no match
     */
    @SafeVarargs
    protected final T matchAny(T... types) {
        T lookaheadType = lookaheadType(1);
        for (T type : types) {
            if (lookaheadType == type) {
                advance();
                return type;
            }
        }

        return null;
    }

    /**
     * Expects the next token to be of the given type, advancing if it is.
     * If not found, reports an error and returns a missing token.
     *
     * @param expectedType     the expected token type
     * @param messageIfMissing optional message if the token is missing
     * @return the matched token or a missing token if not found
     */
    protected final Token<T> expect(T expectedType, String messageIfMissing) {
        if (lookaheadType(1) == expectedType)
            return advance();

        reportErrorAt(current(), messageIfMissing != null ? messageIfMissing : ("Expected " + expectedType + " here"));
        return new Token.MissingToken<>(expectedType, current());
    }

    /**
     * Expects the next token to be one of the given types, advancing if it is.
     * If not found, reports an error and returns a missing token.
     *
     * @param messageIfMissing optional message if the token is missing
     * @param expectedTypes    the expected token types
     * @return the matched token or a missing token if not found
     */
    @SafeVarargs
    protected final Token<T> expectAny(String messageIfMissing, T... expectedTypes) {
        T found = lookaheadType(1);
        for (T expectedType : expectedTypes) {
            if (found == expectedType) {
                return advance();
            }
        }

        reportErrorAt(current(), messageIfMissing != null ? messageIfMissing : ("Expected one of " + Arrays.toString(expectedTypes)));
        return new Token.MissingToken<>(expectedTypes.length > 0 ? expectedTypes[0] : found, current());
    }

    /**
     * Marks the current position in the parser, allowing rollback or commit later.
     *
     * @return a marker object representing the current state
     */
    protected Marker mark() {
        return new Marker(snapshot(), lookaheadBuffer.clone(), previousToken, new ArrayList<>(diagnostics));
    }

    /**
     * Attempts to parse using the provided function, rolling back if it fails.
     * This is useful for trying alternative parsing strategies without committing
     * to a parse that might fail later.
     *
     * @param attempt the function to attempt parsing
     * @param <R>     the type of the result
     * @return the result of the parse attempt, or null if it failed
     */
    protected <R> R tryParse(Function<Parser<T, N, E>, R> attempt) {
        Marker marker = mark();
        int before = diagnostics.size();
        R result = attempt.apply(this);
        if (result == null) {
            marker.rollback();
            // discard problems emitted during failed attempt
            shrinkProblems(before);
        } else {
            marker.commit();
        }

        return result;
    }

    /**
     * Reports an error at the current token's position.
     *
     * @param message the error message to report
     */
    protected void reportError(String message) {
        reportErrorAt(current(), message);
    }

    /**
     * Reports an error at the specified token's position.
     *
     * @param where   the token where the error occurred
     * @param message the error message to report
     */
    protected void reportErrorAt(Token<T> where, String message) {
        diagnostics.add(ParseDiagnostic.error(message, where.pos(), where.line(), where.column()));
        if (++burstCount >= maxErrorBurst) {
            // prevent cascading: try to move to a safe point
            synchronize(defaultSyncSet());
            burstCount = 0;
        }
    }

    /**
     * Synchronizes the parser by skipping tokens until it reaches a sync boundary.
     * This is useful for recovering from errors and continuing parsing.
     *
     * @param followSet the set of token types that indicate a sync point
     */
    protected void synchronize(Set<T> followSet) {
        while (!isAtEnd()) {
            // If the current token starts a new construct or is in the follow set, stop skipping.
            if (followSet.contains(lookaheadType(1)) || startsSyncBoundary(previous(), current())) return;
            advance();
        }
    }

    /**
     * This method is called to determine if the next token starts a sync boundary.
     * Override to define language-specific sync boundaries, e.g., after semicolons or closing braces.
     *
     * @param prev the previous token
     * @param next the next token
     * @return true if the next token starts a sync boundary, false otherwise
     */
    protected boolean startsSyncBoundary(Token<T> prev, Token<T> next) {
        return false;
    }

    /**
     * Returns the default set of sync points for the parser. This is used to recover from errors and continue parsing.
     * Default sync set; override in concrete parser with something sensible.
     *
     * @return an empty set by default, but should be overridden to provide meaningful sync points
     */
    protected Set<T> defaultSyncSet() {
        return Collections.emptySet();
    }

    /**
     * Parses a list of elements while the given condition holds true.
     * This is useful for parsing lists or sequences of elements.
     *
     * @param cont    the predicate that determines if more elements can be parsed
     * @param element the function to parse each element
     * @param <R>     the type of the elements in the list
     * @return a list of parsed elements
     */
    protected <R> List<R> many(Predicate<Token<T>> cont, Function<Parser<T, N, E>, R> element) {
        List<R> out = new ArrayList<>();
        while (!isAtEnd() && cont.test(current())) {
            R r = element.apply(this);
            if (r == null) break; // element parse declined
            out.add(r);
        }

        return out;
    }

    /**
     * Parses a list of elements separated by a given separator token.
     * This is useful for parsing comma-separated lists or similar constructs.
     *
     * @param separator     the token type that separates elements
     * @param allowTrailing if true, allows a trailing separator without an element after it
     * @param element       the function to parse each element
     * @param <R>           the type of the elements in the list
     * @return a list of parsed elements, possibly empty if no elements were found
     */
    protected <R> List<R> separatedList(T separator, boolean allowTrailing, Function<Parser<T, N, E>, R> element) {
        List<R> out = new ArrayList<>();
        R first = element.apply(this);
        if (first == null) return out;
        out.add(first);

        while (match(separator)) {
            // Handle trailing separator
            Marker marker = mark();
            R next = element.apply(this);
            if (next == null) {
                if (allowTrailing) {
                    marker.rollback();
                    break;
                }
                marker.rollback();
                reportError("List element expected after separator");
                break;
            }

            marker.commit();
            out.add(next);
        }

        return out;
    }

    /**
     * Parses an expression and returns the corresponding syntax tree node.
     *
     * @return the parsed expression node
     */
    protected abstract E parseExpression();

    /**
     * Fills the lookahead buffer with at least n tokens, skipping trivia.
     * If EOF is reached before filling, it stops adding tokens.
     *
     * @param n the minimum number of tokens to fill in the buffer
     */
    private void fillBuffer(int n) {
        while (lookaheadBuffer.size() < n) {
            Token<T> token = lexer.nextToken();
            // Skip trivia
            // TODO: Keep trivia tokens
            if (token.channel() == TokenChannel.TRIVIA)
                continue;

            lookaheadBuffer.addLast(token);
            if (token.flags().contains(TokenFlag.EOF))
                break;
        }
    }

    /**
     * Returns an EOF token based on the current lexer state.
     * This is used when the parser reaches the end of input.
     *
     * @return an EOF token with the current lexer position
     */
    private Token<T> eof() {
        // LA ensures this only happens at end; pull from lexer to get proper offset/line/col
        var t = lexer.lookahead(1);
        if (t.channel() == TokenChannel.TRIVIA) {
            // force a default EOF token if lexer puts EOF on default channel anyway
            return new Token.MissingToken<>(null, lexer.offset(), lexer.line(), lexer.column());
        }

        return t;
    }

    /**
     * Shrinks the diagnostics list to the specified size, removing the oldest entries.
     * This is useful for limiting the number of diagnostics stored during parsing.
     *
     * @param newSize the new size to shrink the diagnostics list to
     */
    private void shrinkProblems(int newSize) {
        while (diagnostics.size() > newSize)
            diagnostics.removeLast();
    }

    /**
     * Checks if the next token is of the specified types.
     *
     * @param types the token types to check against (varargs, so you can pass multiple types)
     * @return true if the next token matches the type, false otherwise
     */
    @SafeVarargs
    protected final boolean nextIsAny(T... types) {
        T la1 = lookaheadType(1);
        for (T t : types) {
            if (la1 == t) return true;
        }

        return false;
    }

    /**
     * Consumes tokens while the predicate holds true.
     *
     * @param predicate the predicate to test each token against
     * @return the number of tokens consumed
     */
    protected int consumeWhile(Predicate<Token<T>> predicate) {
        int n = 0;
        while (!isAtEnd() && predicate.test(current())) {
            advance();
            n++;
        }

        return n;
    }

    /**
     * Returns an optional token of the specified type if it matches the next token.
     * If it does not match, returns null.
     *
     * @param type the token type to match
     * @return the matched token or null if it does not match
     */
    protected Token<T> optional(T type) {
        return match(type) ? previous() : null;
    }

    /**
     * Returns the current span position of the parser as a {@link Span} object.
     *
     * @return the current span
     */
    protected Span currentSpan() {
        return new Span(lexer.offset(), lexer.offset(), lexer.line(), lexer.column());
    }

    /**
     * Creates a span from the given start position to the current position.
     *
     * @param start the start span
     * @return a new span from start to current
     */
    protected Span spanFrom(Span start) {
        return spanBetween(start, currentSpan());
    }

    /**
     * Creates a span from the given start token to the current position.
     *
     * @param start the start token
     * @return a new span from start to current
     */
    protected Span spanFrom(Token<T> start) {
        return spanBetween(new Span(start.pos(), start.endOffset(), start.line(), start.column()), currentSpan());
    }

    /**
     * Creates a span from the given start position to the given end position.
     *
     * @param start the start span
     * @param end   the end span
     * @return a new span from start to end
     */
    protected Span spanBetween(Span start, Span end) {
        return new Span(start.pos(), end.endPos(), start.line(), start.column());
    }

    /**
     * A marker class that allows for rollback or commit of the parser state.
     * This is useful for implementing backtracking or alternative parsing strategies.
     * <p>
     * It captures the current lexer snapshot, lookahead buffer, and previous token,
     * allowing the parser to restore its state later.
     **/
    protected final class Marker {
        private final Lexer.Snapshot lexSnapshot;
        private final ArrayDeque<Token<T>> laCopy;
        private final Token<T> prevCopy;
        private final List<ParseDiagnostic> diagnosticsCopy;
        private boolean active = true;

        private Marker(Lexer.Snapshot lexSnapshot, ArrayDeque<Token<T>> laCopy, Token<T> prevCopy, List<ParseDiagnostic> diagnosticsCopy) {
            this.lexSnapshot = lexSnapshot;
            this.laCopy = laCopy;
            this.prevCopy = prevCopy;
            this.diagnosticsCopy = diagnosticsCopy;
        }

        /**
         * Commits the current parser state, making it permanent.
         * After this call, the marker cannot be rolled back.
         */
        public void commit() {
            active = false;
        }

        /**
         * Rolls back the parser state to the point when this marker was created.
         * This restores the lexer snapshot, lookahead buffer, and previous token.
         * After this call, the marker is no longer active.
         */
        public void rollback() {
            if (!active) return;
            lexer.restore(lexSnapshot);
            lookaheadBuffer.clear();
            lookaheadBuffer.addAll(laCopy);
            diagnostics.clear();
            diagnostics.addAll(diagnosticsCopy);
            previousToken = prevCopy;

            active = false;
        }
    }

    /**
     * Returns the current lexer snapshot, which contains the current state of the lexer.
     * This is useful for debugging or when you need to inspect the lexer state.
     *
     * @return the current lexer snapshot
     */
    protected Lexer.Snapshot snapshot() {
        return lexer.snapshot();
    }

    /**
     * Represents a diagnostic message generated during parsing.
     * It includes the severity, message, and position of the diagnostic in the source code.
     */
    public record ParseDiagnostic(Severity severity, String message, int offset, int line, int column) {
        /**
         * Constructs a new ParseDiagnostic with the given parameters.
         *
         * @param severity the severity of the diagnostic (ERROR or WARNING)
         * @param message  the diagnostic message
         * @param offset   the offset in the source code where the diagnostic occurred
         * @param line     the line number in the source code where the diagnostic occurred
         * @param column   the column number in the source code where the diagnostic occurred
         */
        public ParseDiagnostic {
            Objects.requireNonNull(severity, "Severity cannot be null");
            Objects.requireNonNull(message, "Message cannot be null");

            if (offset < 0) throw new IllegalArgumentException("Offset cannot be negative");
            if (line < 1) throw new IllegalArgumentException("Line number must be at least 1");
            if (column < 1) throw new IllegalArgumentException("Column number must be at least 1");
        }

        /**
         * Severity levels for parse diagnostics.
         */
        public enum Severity {ERROR, WARNING}

        /**
         * Creates a new error diagnostic with the given message and position.
         *
         * @param msg  the error message
         * @param off  the offset in the source code
         * @param line the line number in the source code
         * @param col  the column number in the source code
         * @return a new ParseDiagnostic with ERROR severity
         */
        public static ParseDiagnostic error(String msg, int off, int line, int col) {
            return new ParseDiagnostic(Severity.ERROR, msg, off, line, col);
        }

        /**
         * Creates a new warning diagnostic with the given message and position.
         *
         * @param msg  the warning message
         * @param off  the offset in the source code
         * @param line the line number in the source code
         * @param col  the column number in the source code
         * @return a new ParseDiagnostic with WARNING severity
         */
        public static ParseDiagnostic warning(String msg, int off, int line, int col) {
            return new ParseDiagnostic(Severity.WARNING, msg, off, line, col);
        }

        /**
         * Returns a string representation of the diagnostic, including severity, position, and message.
         *
         * @return a string representation of the diagnostic
         */
        @Override
        public @NotNull String toString() {
            return severity + "@" + line + ":" + column + " " + message;
        }
    }
}
