package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class JavaGreenParserCancellationTest {
    @Test
    public void parserCancelsTokenCollectionEvenWhenLexerHasNoActivityCallback() {
        String input = "class A {} ".repeat(1000);
        var source = new CancellationTokenSource();
        try (var lexer = new JavaLexer(input)) {
            var parser = new JavaGreenParser(lexer, () -> {
                if (lexer.offset() >= 512) {
                    source.cancel();
                }
                source.token().throwIfCancellationRequested();
            });

            assertThrows(CancellationException.class, parser::parseGreenTree);
            assertTrue(lexer.offset() < input.length());
        }
    }

    @Test
    public void ignoredTokensCannotBypassParserCancellationChecks() {
        var source = new CancellationTokenSource();
        var produced = new AtomicInteger();
        try (var lexer = new JavaLexer("") {
            @Override
            public Token<JavaTokenType> nextToken() {
                int count = produced.incrementAndGet();
                if (count > 100)
                    throw new AssertionError("Parser failed to observe cancellation while collecting ignored tokens");
                if (count >= 8) {
                    source.cancel();
                }
                return new Token.IgnoreToken<>();
            }
        }) {
            var parser = new JavaGreenParser(lexer, source.token()::throwIfCancellationRequested);

            assertThrows(CancellationException.class, parser::parseGreenTree);
            assertTrue(produced.get() < 100);
        }
    }

    @Test
    public void cancellationStopsTreeConstructionAfterAllInputHasBeenTokenized() {
        assertActivityFailureDuringTreeConstruction(false);
    }

    @Test
    public void deadlineStopsTreeConstructionAfterAllInputHasBeenTokenized() {
        assertActivityFailureDuringTreeConstruction(true);
    }

    private static void assertActivityFailureDuringTreeConstruction(boolean expireDeadline) {
        var source = new CancellationTokenSource();
        var clock = new AtomicLong();
        var context = new WorkspaceRequestContext(
            new WorkspaceSnapshot("test", Map.of(), new WorkspaceConfigurationSnapshot(0, Set.of()), 0),
            source.token(), Deadline.after(Duration.ofNanos(10), clock::get));
        var reachedEof = new AtomicBoolean();
        var consumed = new AtomicInteger();
        Runnable onLexeme = () -> {
            assertTrue(reachedEof.get(), "Cancellation must occur during parsing, after lexing has finished");
            if (consumed.incrementAndGet() >= 32) {
                if (expireDeadline) {
                    clock.set(10);
                } else {
                    source.cancel();
                }
            }
        };
        try (var lexer = new JavaLexer("class A { " + "int field; ".repeat(1000) + "}") {
            @Override
            public Token<JavaTokenType> nextToken() {
                Token<JavaTokenType> token = super.nextToken();
                if (token.type() == JavaTokenType.EOF) {
                    reachedEof.set(true);
                }
                return new ObservedToken(token, onLexeme, () -> {
                });
            }
        }) {
            var parser = new JavaGreenParser(lexer, new ActivityCheckpoint(context::checkActive));

            CancellationException failure = assertThrows(CancellationException.class, parser::parseGreenTree);

            assertEquals(expireDeadline ? DeadlineExceededException.class : CancellationException.class,
                failure.getClass());
            assertTrue(reachedEof.get());
            assertTrue(consumed.get() >= 32);
            assertTrue(consumed.get() < 100, "Parsing must stop without consuming the remaining buffered tokens");
        }
    }

    @Test
    public void cancellationStopsScanningBufferedTriviaWithoutConsumingItAll() {
        assertActivityFailureDuringTriviaScan(false);
    }

    @Test
    public void deadlineExpiryStopsScanningBufferedTriviaWithinTheSameWorkBound() {
        assertActivityFailureDuringTriviaScan(true);
    }

    private static void assertActivityFailureDuringTriviaScan(boolean expireDeadline) {
        var source = new CancellationTokenSource();
        var clock = new AtomicLong();
        var context = new WorkspaceRequestContext(
            new WorkspaceSnapshot("test", Map.of(), new WorkspaceConfigurationSnapshot(0, Set.of()), 0),
            source.token(), Deadline.after(Duration.ofNanos(10), clock::get));
        var inspected = new AtomicInteger();
        Runnable onChannel = () -> {
            if (inspected.incrementAndGet() >= 32) {
                if (expireDeadline) {
                    clock.set(10);
                } else {
                    source.cancel();
                }
            }
        };
        try (var lexer = new JavaLexer("/* comment */ ".repeat(1000) + "class A {}") {
            @Override
            public Token<JavaTokenType> nextToken() {
                return new ObservedToken(super.nextToken(), () -> {
                }, onChannel);
            }
        }) {
            var parser = new JavaGreenParser(lexer, new ActivityCheckpoint(context::checkActive));

            CancellationException failure = assertThrows(CancellationException.class, parser::parseGreenTree);
            assertEquals(expireDeadline ? DeadlineExceededException.class : CancellationException.class,
                failure.getClass());
            assertTrue(inspected.get() >= 32);
            assertTrue(inspected.get() <= 32 + 255, "Lookahead must observe inactivity within 256 token inspections");
        }
    }

    private record ObservedToken(Token<JavaTokenType> original, Runnable onLexeme, Runnable onChannel)
        implements
            Token<JavaTokenType> {
        @Override
        public JavaTokenType type() {
            return original.type();
        }

        @Override
        public String lexeme() {
            onLexeme.run();
            return original.lexeme();
        }

        @Override
        public int pos() {
            return original.pos();
        }

        @Override
        public int endOffset() {
            return original.endOffset();
        }

        @Override
        public int line() {
            return original.line();
        }

        @Override
        public int column() {
            return original.column();
        }

        @Override
        public TokenChannel channel() {
            onChannel.run();
            return original.channel();
        }

        @Override
        public Set<TokenFlag> flags() {
            return original.flags();
        }
    }
}
