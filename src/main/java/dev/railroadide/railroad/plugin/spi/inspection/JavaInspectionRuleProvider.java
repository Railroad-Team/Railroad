package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;

import java.util.List;

public interface JavaInspectionRuleProvider extends LanguageInspectionRuleProvider<JavaRuleContext> {
    @Override
    List<JavaInspectionRule> rules();

    @Override
    default String languageId() {
        return JavaLanguageSupport.LANGUAGE_ID;
    }
}
