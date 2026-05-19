package dev.railroadide.railroad.plugin.spi.inspection;

import java.util.List;

public interface LanguageInspectionRuleProvider<C extends LanguageRuleContext> extends LanguageInspectionProvider {
    List<? extends LanguageInspectionRule<C>> rules();
}
