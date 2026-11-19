package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class JavaLexerCancellationTest {
    @Test
    public void cancellationStopsInsideLargeWhitespace() {
        assertCancellationInsideToken("whitespace");
    }

    @Test
    public void cancellationStopsInsideALargeLineComment() {
        assertCancellationInsideToken("line-comment");
    }

    @Test
    public void cancellationStopsInsideALargeBlockComment() {
        assertCancellationInsideToken("block-comment");
    }

    @Test
    public void cancellationStopsInsideALargeIdentifier() {
        assertCancellationInsideToken("identifier");
    }

    @Test
    public void cancellationStopsInsideALargeString() {
        assertCancellationInsideToken("string");
    }

    @Test
    public void cancellationStopsInsideALargeTextBlock() {
        assertCancellationInsideToken("text-block");
    }

    @Test
    public void cancellationStopsInsideALargeNumber() {
        assertCancellationInsideToken("number");
    }

    private static void assertCancellationInsideToken(String kind) {
        String input = longToken(kind);
        var source = new CancellationTokenSource();
        var lexerReference = new AtomicReference<JavaLexer>();
        try (var lexer = new JavaLexer(input, null, new ActivityCheckpoint(() -> {
            if (lexerReference.get().offset() >= 512) {
                source.cancel();
            }
            source.token().throwIfCancellationRequested();
        }))) {
            lexerReference.set(lexer);

            assertThrows(CancellationException.class, () -> JavaParserTestSupport.lexAll(lexer));

            assertTrue(lexer.offset() >= 512, "Cancellation should happen during character consumption");
            assertTrue(lexer.offset() <= 768, "Large tokens must not defer checks until the token ends");
            assertTrue(lexer.offset() < input.length());
            assertTrue(lexer.diagnostics().isEmpty(), "Cancellation must not become a lexical error");
        }
    }

    @Test
    public void deadlineExpiryStopsInsideALargeToken() {
        var clock = new AtomicLong();
        Deadline deadline = Deadline.after(Duration.ofNanos(10), clock::get);
        var lexerReference = new AtomicReference<JavaLexer>();
        try (var lexer = new JavaLexer(longToken("block-comment"), null, () -> {
            if (lexerReference.get().offset() >= 512) {
                clock.set(10);
            }
            if (deadline.isExpired())
                throw new DeadlineExceededException();
        })) {
            lexerReference.set(lexer);

            assertThrows(DeadlineExceededException.class, lexer::nextToken);
            assertTrue(lexer.offset() <= 768);
            assertTrue(lexer.diagnostics().isEmpty());
        }
    }

    @Test
    public void cancellationPreventsReturningBufferedTokensAndLookahead() {
        var source = new CancellationTokenSource();
        try (var lexer = new JavaLexer("class A {}", null, source.token()::throwIfCancellationRequested)) {
            lexer.lookahead(3);
            int offset = lexer.offset();
            source.cancel();

            assertThrows(CancellationException.class, lexer::nextToken);
            assertThrows(CancellationException.class, () -> lexer.lookahead(1));
            assertThrows(IllegalArgumentException.class, () -> lexer.lookahead(0));
            assertEquals(offset, lexer.offset());
        }
    }

    @Test
    public void cancellationStopsFillingALookaheadBuffer() {
        var source = new CancellationTokenSource();
        String input = "class A {} ".repeat(1000);
        var lexerReference = new AtomicReference<JavaLexer>();
        try (var lexer = new JavaLexer(input, null, () -> {
            if (lexerReference.get().offset() >= 512) {
                source.cancel();
            }
            source.token().throwIfCancellationRequested();
        })) {
            lexerReference.set(lexer);

            assertThrows(CancellationException.class, () -> lexer.lookahead(10000));
            assertTrue(lexer.offset() < input.length());
        }
    }

    @Test
    public void cancelledEmptyInputDoesNotReturnEof() {
        var source = new CancellationTokenSource();
        source.cancel();
        try (var lexer = new JavaLexer("", null, source.token()::throwIfCancellationRequested)) {
            assertThrows(CancellationException.class, lexer::nextToken);
            assertThrows(CancellationException.class, () -> lexer.lookahead(1));
        }
    }

    @Test
    public void activeCallbackPreservesLegacyTokensAndDiagnostics() {
        String input = "class A { /* comment */ String s = \"unterminated";
        try (var legacy = new JavaLexer(input)) {
            try (var checked = new JavaLexer(input, null, () -> {
            })) {
                assertEquals(JavaParserTestSupport.lexAll(legacy), JavaParserTestSupport.lexAll(checked));
                assertEquals(legacy.diagnostics(), checked.diagnostics());
            }
        }
    }

    private static String longToken(String kind) {
        return switch (kind) {
            case "whitespace" -> " ".repeat(8192);
            case "line-comment" -> "//" + "x".repeat(8192) + "\n";
            case "block-comment" -> "/*" + "x".repeat(8192) + "*/";
            case "identifier" -> "x".repeat(8192);
            case "string" -> "\"" + "x".repeat(8192) + "\"";
            case "text-block" ->
                String.valueOf('"').repeat(3) + "\n" + "x".repeat(8192) + "\n" + String.valueOf('"').repeat(3);
            case "number" -> "1".repeat(8192);
            default -> throw new IllegalArgumentException(kind);
        };
    }
}
