package dev.railroadide.railroad.plugin.spi.inspection;

import java.util.List;

/**
 * Contributes a collection of inspection rules for one language.
 *
 * @param <C> context type shared by the provider's rules
 */
public interface LanguageInspectionRuleProvider<C extends LanguageRuleContext> extends LanguageInspectionProvider {
    /**
     * Returns the rules contributed by this provider in evaluation order.
     *
     * @return the provider's inspection rules
     */
    List<? extends LanguageInspectionRule<C>> rules();
}
