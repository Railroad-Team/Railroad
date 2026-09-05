package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;

import java.util.List;

/**
 * Supplies Java inspection rules evaluated with a {@link JavaRuleContext}.
 * Plugins can expose implementations through {@link java.util.ServiceLoader} for automatic registration.
 */
public interface JavaInspectionRuleProvider extends LanguageInspectionRuleProvider<JavaRuleContext> {
    @Override
    List<JavaInspectionRule> rules();

    @Override
    default String languageId() {
        return JavaLanguageSupport.LANGUAGE_ID;
    }
}
