package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguageSupportsTest {

    @Test
    void initializeRegistersBuiltInSupportsOnce() {
        try {
            LanguageSupports.reset();

            LanguageSupports.initialize();
            List<LanguageSupport> first = LanguageSupportRegistry.all();
            LanguageSupports.initialize();
            List<LanguageSupport> second = LanguageSupportRegistry.all();

            assertEquals(first, second);
            assertTrue(LanguageSupportRegistry.contains(JavaLanguageSupport.LANGUAGE_ID));
            assertEquals(3, second.size());
        } finally {
            LanguageSupports.reset();
            LanguageSupports.initialize();
        }
    }

    @Test
    void resetClearsRegisteredSupports() {
        try {
            LanguageSupports.reset();
            LanguageSupports.initialize();

            assertFalse(LanguageSupportRegistry.all().isEmpty());

            LanguageSupports.reset();

            assertTrue(LanguageSupportRegistry.all().isEmpty());
            assertFalse(LanguageSupportRegistry.contains(JavaLanguageSupport.LANGUAGE_ID));
        } finally {
            LanguageSupports.reset();
            LanguageSupports.initialize();
        }
    }
}
