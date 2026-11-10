package dev.railroadide.railroad.ide.sst.impl.java;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class JavaTokenTypeTest {
    @Test
    void concurrentFirstAccessReturnsCompleteImmutableKeywords() throws Exception {
        Map<String, String> expected = new HashMap<>();
        String spellings = "abstract assert boolean break byte case catch char class const continue default do double "
            + "else enum extends final finally float for goto if implements import instanceof int interface long "
            + "native new package private protected public return short static strictfp super switch synchronized "
            + "this throw throws transient try void volatile while underscore exports module open opens permits "
            + "provides record requires sealed to transitive uses var with yield when";
        for (String spelling : spellings.split(" ")) {
            expected.put(spelling, spelling.toUpperCase(Locale.ROOT) + "_KEYWORD");
        }
        expected.put("non-sealed", "NON_SEALED_KEYWORD");
        expected.put("@interface", "AT_INTERFACE_KEYWORD");

        // A fresh loader makes this first access independent of test execution order.
        for (int round = 0; round < 10; round++) {
            var executor = Executors.newFixedThreadPool(16);
            try (var loader = new URLClassLoader(
                List.of(JavaTokenType.class.getProtectionDomain().getCodeSource().getLocation()).toArray(URL[]::new),
                ClassLoader.getPlatformClassLoader())) {
                Class<?> tokenType = Class.forName(JavaTokenType.class.getName(), false, loader);
                var listKeywords = tokenType.getMethod("listKeywords");
                var ready = new CountDownLatch(16);
                var start = new CountDownLatch(1);
                var futures = new ArrayList<Future<?>>();
                for (int worker = 0; worker < 16; worker++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        Map<?, ?> keywords = (Map<?, ?>) listKeywords.invoke(null);
                        Map<String, String> actual = new HashMap<>();
                        keywords.forEach((key, value) -> actual.put((String) key, ((Enum<?>) value).name()));
                        assertEquals(expected, actual);
                        assertThrows(UnsupportedOperationException.class, keywords::clear);
                        return null;
                    }));
                }
                try {
                    assertTrue(ready.await(10, TimeUnit.SECONDS));
                } finally {
                    start.countDown();
                }
                for (var future : futures) {
                    future.get(20, TimeUnit.SECONDS);
                }
                Map<?, ?> keywords = (Map<?, ?>) listKeywords.invoke(null);
                assertEquals(expected.size(), keywords.size());
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
