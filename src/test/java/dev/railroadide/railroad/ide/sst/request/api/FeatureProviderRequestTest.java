package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class FeatureProviderRequestTest {
    private static final int OFFSET = 4;
    private static final List<EditorDiagnostic<?>> DIAGNOSTICS = List.of();
    private static final CompletionResult COMPLETION = new CompletionResult(OFFSET, List.of());
    private static final SignatureHelp SIGNATURE = new SignatureHelp("Example", "Example", "call",
        List.of(), -1, false, "void", false);

    private final DocumentId id = DocumentId.create();
    private final DocumentSnapshot document = new TextDocumentSnapshot(
        id,
        DocumentUri.inMemory(id),
        DocumentVersion.initial(),
        "test-language",
        "class Example {}",
        StandardCharsets.UTF_8);
    private final WorkspaceSnapshot workspace = new WorkspaceSnapshot("test-project", Map.of(id, document),
        new WorkspaceConfigurationSnapshot(0, Set.of()), 1);

    @Test
    public void diagnosticsRequestHonorsTheWorkspaceOverride() {
        DiagnosticsProvider<EditorDiagnostic<?>> provider = new DiagnosticsProvider<>() {
            @Override
            public List<EditorDiagnostic<?>> compute(DocumentSnapshot snapshot) {
                throw new AssertionError("Request bypassed the workspace-aware override");
            }

            @Override
            public List<EditorDiagnostic<?>> compute(WorkspaceSnapshot captured, DocumentId capturedId) {
                assertSame(workspace, captured);
                assertEquals(id, capturedId);
                return DIAGNOSTICS;
            }
        };
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());

        FeatureResult<List<EditorDiagnostic<?>>> result = provider.compute(context);

        assertSame(context, result.context());
        assertSame(DIAGNOSTICS, result.value());
    }

    @Test
    public void completionRequestHonorsTheWorkspaceOverrideAndTriggerOffset() {
        CompletionProvider provider = new CompletionProvider() {
            @Override
            public CompletionResult compute(DocumentSnapshot snapshot, int triggerAt) {
                throw new AssertionError("Request bypassed the workspace-aware override");
            }

            @Override
            public CompletionResult compute(WorkspaceSnapshot captured, DocumentId capturedId, int triggerAt) {
                assertSame(workspace, captured);
                assertEquals(id, capturedId);
                assertEquals(OFFSET, triggerAt);
                return COMPLETION;
            }
        };
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());

        FeatureResult<CompletionResult> result = provider.compute(context, OFFSET);

        assertSame(context, result.context());
        assertSame(COMPLETION, result.value());
    }

    @Test
    public void signatureRequestHonorsTheWorkspaceOverrideAndCaretOffset() {
        SignatureHelpProvider provider = new SignatureHelpProvider() {
            @Override
            public SignatureHelp compute(DocumentSnapshot snapshot, int caretPosition) {
                throw new AssertionError("Request bypassed the workspace-aware override");
            }

            @Override
            public SignatureHelp compute(WorkspaceSnapshot captured, DocumentId capturedId, int caretPosition) {
                assertSame(workspace, captured);
                assertEquals(id, capturedId);
                assertEquals(OFFSET, caretPosition);
                return SIGNATURE;
            }
        };
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());

        FeatureResult<SignatureHelp> result = provider.compute(context, OFFSET);

        assertSame(context, result.context());
        assertSame(SIGNATURE, result.value());
    }

    @TestFactory
    public Stream<DynamicTest> legacyLambdasReceiveCapturedInputsAndProduceVersionedResults() {
        return cases(feature -> {
            FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());
            var calls = new AtomicInteger();

            FeatureResult<?> result = compute(feature, context, calls::incrementAndGet, false);

            assertNotNull(result);
            assertSame(context, result.context());
            assertTrue(result.belongsTo(context));
            assertSame(payload(feature), result.value());
            assertEquals(1, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> nullContextIsRejectedBeforeProviderWork() {
        return cases(feature -> {
            var calls = new AtomicInteger();

            assertThrows(NullPointerException.class,
                () -> compute(feature, null, calls::incrementAndGet, false));
            assertEquals(0, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> cancellationBeforeDispatchSkipsProviderWork() {
        return cases(feature -> {
            var source = new CancellationTokenSource();
            source.cancel();
            FeatureRequestContext context = context(source.token(), Deadline.none());
            var calls = new AtomicInteger();

            assertThrows(CancellationException.class,
                () -> compute(feature, context, calls::incrementAndGet, false));
            assertEquals(0, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> expiryBeforeDispatchSkipsProviderWork() {
        return cases(feature -> {
            var clock = new AtomicLong(100);
            FeatureRequestContext context = context(CancellationToken.none(),
                Deadline.after(Duration.ZERO, clock::get));
            var calls = new AtomicInteger();

            assertThrows(DeadlineExceededException.class,
                () -> compute(feature, context, calls::incrementAndGet, false));
            assertEquals(0, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> cancellationDuringComputationRejectsTheCompletedPayload() {
        return cases(feature -> {
            var source = new CancellationTokenSource();
            FeatureRequestContext context = context(source.token(), Deadline.none());
            var calls = new AtomicInteger();

            assertThrows(CancellationException.class, () -> compute(feature, context, () -> {
                calls.incrementAndGet();
                source.cancel();
            }, false));
            assertEquals(1, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> expiryDuringComputationRejectsTheCompletedPayload() {
        return cases(feature -> {
            var clock = new AtomicLong(100);
            FeatureRequestContext context = context(CancellationToken.none(),
                Deadline.after(Duration.ofNanos(50), clock::get));
            var calls = new AtomicInteger();

            assertThrows(DeadlineExceededException.class, () -> compute(feature, context, () -> {
                calls.incrementAndGet();
                clock.set(150);
            }, false));
            assertEquals(1, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> providerFailurePropagatesUnchangedEvenIfCancellationAlsoArrives() {
        return cases(feature -> {
            var source = new CancellationTokenSource();
            FeatureRequestContext context = context(source.token(), Deadline.none());
            var failure = new IllegalStateException("Provider failed");

            var actual = assertThrows(IllegalStateException.class, () -> compute(feature, context, () -> {
                source.cancel();
                throw failure;
            }, false));

            assertSame(failure, actual);
        });
    }

    @TestFactory
    public Stream<DynamicTest> providerCancellationExceptionPropagatesUnchanged() {
        return cases(feature -> {
            FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());
            var failure = new CancellationException("Provider cancelled");

            var actual = assertThrows(CancellationException.class, () -> compute(feature, context, () -> {
                throw failure;
            }, false));

            assertSame(failure, actual);
        });
    }

    @TestFactory
    public Stream<DynamicTest> anIndependentRequestCanRunAfterACancelledRequest() {
        return cases(feature -> {
            var source = new CancellationTokenSource();
            source.cancel();
            FeatureRequestContext cancelled = context(source.token(), Deadline.none());
            FeatureRequestContext active = context(CancellationToken.none(), Deadline.none());
            var calls = new AtomicInteger();

            assertThrows(CancellationException.class,
                () -> compute(feature, cancelled, calls::incrementAndGet, false));
            FeatureResult<?> result = compute(feature, active, calls::incrementAndGet, false);

            assertSame(active, result.context());
            assertEquals(1, calls.get());
        });
    }

    @TestFactory
    public Stream<DynamicTest> unsupportedPayloadsKeepANonNullWrapperAndRequestIdentity() {
        return nullableCases(feature -> {
            FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());

            FeatureResult<?> result = compute(feature, context, () -> {
            }, true);

            assertNotNull(result);
            assertNull(result.value());
            assertSame(context, result.context());
        });
    }

    @TestFactory
    public Stream<DynamicTest> unsupportedPayloadsStillReceiveTheCancellationPostCheck() {
        return nullableCases(feature -> {
            var source = new CancellationTokenSource();
            FeatureRequestContext context = context(source.token(), Deadline.none());

            assertThrows(CancellationException.class, () -> compute(feature, context, source::cancel, true));
        });
    }

    @TestFactory
    public Stream<DynamicTest> unsupportedPayloadsStillReceiveTheDeadlinePostCheck() {
        return nullableCases(feature -> {
            var clock = new AtomicLong(100);
            FeatureRequestContext context = context(CancellationToken.none(),
                Deadline.after(Duration.ofNanos(50), clock::get));

            assertThrows(DeadlineExceededException.class,
                () -> compute(feature, context, () -> clock.set(150), true));
        });
    }

    private FeatureResult<?> compute(
        Feature feature,
        FeatureRequestContext context,
        Runnable duringCompute,
        boolean unsupported
    ) {
        return switch (feature) {
            case DIAGNOSTICS -> {
                DiagnosticsProvider<EditorDiagnostic<?>> provider = snapshot -> {
                    assertSame(document, snapshot);
                    duringCompute.run();
                    return DIAGNOSTICS;
                };
                yield provider.compute(context);
            }
            case COMPLETION -> {
                CompletionProvider provider = (snapshot, triggerAt) -> {
                    assertSame(document, snapshot);
                    assertEquals(OFFSET, triggerAt);
                    duringCompute.run();
                    return unsupported ? null : COMPLETION;
                };
                yield provider.compute(context, OFFSET);
            }
            case SIGNATURE -> {
                SignatureHelpProvider provider = (snapshot, caretPosition) -> {
                    assertSame(document, snapshot);
                    assertEquals(OFFSET, caretPosition);
                    duringCompute.run();
                    return unsupported ? null : SIGNATURE;
                };
                yield provider.compute(context, OFFSET);
            }
        };
    }

    private FeatureRequestContext context(CancellationToken token, Deadline deadline) {
        return new FeatureRequestContext(workspace, document, token, deadline);
    }

    private static Object payload(Feature feature) {
        return switch (feature) {
            case DIAGNOSTICS -> DIAGNOSTICS;
            case COMPLETION -> COMPLETION;
            case SIGNATURE -> SIGNATURE;
        };
    }

    private static Stream<DynamicTest> cases(Consumer<Feature> check) {
        return Stream.of(Feature.values())
            .map(feature -> dynamicTest("Feature " + feature, () -> check.accept(feature)));
    }

    private static Stream<DynamicTest> nullableCases(Consumer<Feature> check) {
        return Stream.of(Feature.values())
            .filter(feature -> feature != Feature.DIAGNOSTICS)
            .map(feature -> dynamicTest("Feature " + feature, () -> check.accept(feature)));
    }

    private enum Feature {
        DIAGNOSTICS,
        COMPLETION,
        SIGNATURE
    }
}
