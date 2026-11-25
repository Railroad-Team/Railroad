package dev.railroadide.railroad.ui.localized;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertNull;

public class LocalizedTextPropertyTest {
    @Test
    public void unreferencedPropertiesAreNotRetainedByTheLanguageProperty() throws InterruptedException {
        WeakReference<LocalizedTextProperty> reference = new WeakReference<>(
            new LocalizedTextProperty(null, null, ""));

        for (int attempt = 0; attempt < 50 && reference.get() != null; attempt++) {
            System.gc();
            Thread.sleep(10);
        }

        assertNull(reference.get());
    }
}
