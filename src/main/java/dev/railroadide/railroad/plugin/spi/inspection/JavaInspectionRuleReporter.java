package dev.railroadide.railroad.plugin.spi.inspection;

/**
 * Reporter supplied to a Java inspection rule for emitting diagnostics against syntax nodes.
 * Rule metadata and configured severity are applied by the reporting implementation.
 */
public interface JavaInspectionRuleReporter extends LanguageInspectionRuleReporter {
}
