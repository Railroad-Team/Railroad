package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.provider.api.*;

public final class TestLanguageContribution implements LanguageContribution {
    @Override
    public LanguageId id() {
        return new LanguageId("test:discovered");
    }

    @Override
    public String displayName() {
        return "Discovered";
    }

    @Override
    public LanguageMatcher matcher() {
        return LanguageMatcher.extension("discovered");
    }

    @Override
    public LanguageSession openSession(LanguageSessionContext context) {
        return new LanguageSession(CapabilityRegistry.empty());
    }
}
