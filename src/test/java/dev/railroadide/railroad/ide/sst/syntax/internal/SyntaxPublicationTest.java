package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SyntaxPublicationTest {
    @Test
    void coldChildCachePublishesStableIdentitiesAndCopiesGreenInputs() throws Exception {
        var tokens = new ArrayList<GreenElement>(List.of(new GreenToken(SyntaxKind.ROOT, "ab")));
        var nested = new GreenNode(SyntaxKind.ROOT, tokens);
        var children = new ArrayList<GreenElement>(List.of(nested));
        var green = GreenNode.root(children);
        tokens.clear();
        children.clear();
        // Bypass factory validation, which would eagerly traverse the red tree.
        SyntaxNode root = RedFactory.root(green);
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<List<SyntaxNode>>>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    var result = root.children();
                    SyntaxNode child = result.getFirst();
                    SyntaxNode token = child.children().getFirst();
                    assertSame(root, child.parent().orElseThrow());
                    assertSame(child, token.parent().orElseThrow());
                    assertEquals(0, token.start());
                    assertEquals(2, token.end());
                    assertSame(result, root.children());
                    assertThrows(UnsupportedOperationException.class, result::clear);
                    return result;
                }));
            }
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            var first = tasks.getFirst().get(20, TimeUnit.SECONDS);
            for (var task : tasks) {
                assertSame(first, task.get(20, TimeUnit.SECONDS));
                assertSame(first.getFirst(), task.get().getFirst());
            }
        }
        assertEquals(2, green.width());
        assertThrows(UnsupportedOperationException.class, () -> green.children().clear());
    }
}
