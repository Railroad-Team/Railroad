package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class FeatureResultTest {
    private final DocumentId id = DocumentId.create();
    private final TextDocumentSnapshot document = new TextDocumentSnapshot(
        id,
        DocumentUri.inMemory(id),
        DocumentVersion.initial(),
        "test-language",
        "original text",
        StandardCharsets.UTF_8);
    private final WorkspaceSnapshot workspace = new WorkspaceSnapshot("test-project", Map.of(id, document),
        new WorkspaceConfigurationSnapshot(0, Set.of()), 1);

    @Test
    public void resultRetainsTheContextAndImmutablePayloadDirectly() {
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());
        List<String> payload = List.of("first", "second");
        var result = new FeatureResult<>(context, payload);

        assertSame(context, result.context());
        assertSame(payload, result.value());
        assertSame(workspace, result.context().workspaceSnapshot());
        assertSame(document, assertInstanceOf(FeatureRequestContext.class, result.context()).documentSnapshot());
        assertTrue(result.belongsTo(context));
    }

    @Test
    public void unsupportedResultRetainsItsRequestIdentityWithANullPayload() {
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());
        var result = new FeatureResult<String>(context, null);

        assertNull(result.value());
        assertSame(context, result.context());
        assertTrue(result.belongsTo(context));
    }

    @Test
    public void nullRequestContextIsRejected() {
        assertThrows(NullPointerException.class, () -> new FeatureResult<>(null, "payload"));
        assertThrows(NullPointerException.class, () -> new FeatureResult<>(null, null));
    }

    @Test
    public void nullContextCannotBeUsedForRequestCorrelation() {
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.none());
        var result = new FeatureResult<>(context, "payload");

        assertThrows(NullPointerException.class, () -> result.belongsTo(null));
    }

    @Test
    public void distinctContextsWithEqualComponentsAreDifferentRequests() {
        FeatureRequestContext first = context(CancellationToken.none(), Deadline.none());
        FeatureRequestContext second = context(CancellationToken.none(), Deadline.none());
        assertNotSame(first, second);
        assertEquals(first, second, "Separate invocations can capture identical inputs and controls");

        var firstResult = new FeatureResult<>(first, "first request");
        var secondResult = new FeatureResult<>(second, "second request");

        assertTrue(firstResult.belongsTo(first));
        assertFalse(firstResult.belongsTo(second));
        assertTrue(secondResult.belongsTo(second));
        assertFalse(secondResult.belongsTo(first));
    }

    @Test
    public void requestForANewerDocumentRevisionDoesNotOwnTheOldResult() {
        FeatureRequestContext original = context(CancellationToken.none(), Deadline.none());
        var newerDocument = new TextDocumentSnapshot(id, document.uri(), document.version().next(),
            document.languageId(), "edited text", document.encoding());
        var newerWorkspace = new WorkspaceSnapshot(workspace.projectId(), Map.of(id, newerDocument),
            workspace.configuration(), 2);
        var newerRequest = new FeatureRequestContext(newerWorkspace, newerDocument,
            CancellationToken.none(), Deadline.none());
        var oldResult = new FeatureResult<>(original, "old result");

        assertFalse(oldResult.belongsTo(newerRequest));
        var retained = assertInstanceOf(FeatureRequestContext.class, oldResult.context());
        assertSame(document, retained.documentSnapshot());
        assertEquals(document.version(), retained.documentSnapshot().version());
        assertEquals("original text", document.text());
    }

    @Test
    public void alreadyCancelledRequestCanHaveAnInspectableHistoricalResult() {
        var source = new CancellationTokenSource();
        FeatureRequestContext context = context(source.token(), Deadline.none());
        source.cancel();

        var result = new FeatureResult<>(context, "historical result");

        assertThrows(CancellationException.class, context::checkActive);
        assertTrue(result.belongsTo(context));
        assertEquals("historical result", result.value());
        assertSame(document, assertInstanceOf(FeatureRequestContext.class, result.context()).documentSnapshot());
    }

    @Test
    public void cancellationAfterCompletionPreservesTheResultAndItsIdentity() {
        var source = new CancellationTokenSource();
        FeatureRequestContext context = context(source.token(), Deadline.none());
        var result = new FeatureResult<>(context, "completed result");

        source.cancel();

        assertThrows(CancellationException.class, context::checkActive);
        assertTrue(result.belongsTo(context));
        assertEquals("completed result", result.value());
        assertSame(workspace, result.context().workspaceSnapshot());
        assertEquals("original text", document.text());
    }

    @Test
    public void alreadyExpiredRequestCanHaveAnInspectableHistoricalResult() {
        var clock = new AtomicLong(100);
        FeatureRequestContext context = context(CancellationToken.none(), Deadline.after(Duration.ZERO, clock::get));

        var result = new FeatureResult<>(context, "historical result");

        assertThrows(DeadlineExceededException.class, context::checkActive);
        assertTrue(result.belongsTo(context));
        assertEquals("historical result", result.value());
    }

    @Test
    public void deadlineExpiryAfterCompletionPreservesTheResultAndItsIdentity() {
        var clock = new AtomicLong(100);
        FeatureRequestContext context = context(CancellationToken.none(),
            Deadline.after(Duration.ofNanos(50), clock::get));
        var result = new FeatureResult<>(context, "completed result");

        clock.set(150);

        assertThrows(DeadlineExceededException.class, context::checkActive);
        assertTrue(result.belongsTo(context));
        assertEquals("completed result", result.value());
        assertSame(document, assertInstanceOf(FeatureRequestContext.class, result.context()).documentSnapshot());
    }

    @Test
    public void workspaceResultsCorrelateByInvocationRatherThanEqualComponents() {
        var first = new WorkspaceRequestContext(workspace, CancellationToken.none(), Deadline.none());
        var second = new WorkspaceRequestContext(workspace, CancellationToken.none(), Deadline.none());
        assertEquals(first, second);
        var result = new FeatureResult<>(first, List.of("indexed symbol"));

        assertSame(first, result.context());
        assertSame(workspace, result.context().workspaceSnapshot());
        assertTrue(result.belongsTo(first));
        assertFalse(result.belongsTo(second));
        assertThrows(NullPointerException.class, () -> result.belongsTo(null));
    }

    @Test
    public void workspaceAndDocumentInvocationsDoNotOwnEachOthersResults() {
        var source = new CancellationTokenSource();
        var workspaceRequest = new WorkspaceRequestContext(workspace, source.token(), Deadline.none());
        var documentRequest = context(workspaceRequest.cancellationToken(), workspaceRequest.deadline());
        var workspaceResult = new FeatureResult<>(workspaceRequest, "workspace result");
        var documentResult = new FeatureResult<>(documentRequest, "document result");

        assertFalse(workspaceResult.belongsTo(documentRequest));
        assertFalse(documentResult.belongsTo(workspaceRequest));
        assertTrue(workspaceResult.belongsTo(workspaceRequest));
        assertTrue(documentResult.belongsTo(documentRequest));
    }

    @Test
    public void inactiveWorkspaceResultsRemainInspectableAndCanHaveNullPayloads() {
        var source = new CancellationTokenSource();
        source.cancel();
        var context = new WorkspaceRequestContext(workspace, source.token(),
            Deadline.after(Duration.ZERO, () -> 0L));
        var result = new FeatureResult<String>(context, null);

        assertThrows(CancellationException.class, context::checkActive);
        assertNull(result.value());
        assertSame(workspace, result.context().workspaceSnapshot());
        assertTrue(result.belongsTo(context));
    }

    @Test
    public void newerWorkspaceConfigurationDoesNotOwnAnEarlierResult() {
        var first = new WorkspaceRequestContext(workspace, CancellationToken.none(), Deadline.none());
        var newConfiguration = new WorkspaceConfigurationSnapshot(1, Set.of("new-module"));
        var updated = new WorkspaceSnapshot(workspace.projectId(), workspace.documents(), newConfiguration, 2);
        var second = new WorkspaceRequestContext(updated, first.cancellationToken(), first.deadline());
        var result = new FeatureResult<>(first, "historical result");

        assertFalse(result.belongsTo(second));
        assertSame(workspace.configuration(), result.context().workspaceSnapshot().configuration());
        assertDoesNotThrow(first::checkActive);
    }

    private FeatureRequestContext context(CancellationToken token, Deadline deadline) {
        return new FeatureRequestContext(workspace, document, token, deadline);
    }
}
