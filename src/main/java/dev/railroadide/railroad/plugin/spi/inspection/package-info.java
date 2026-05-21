/**
 * Public inspection SPI for contributing Java diagnostics.
 * <p>
 * New inspections should normally implement {@link
 * dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider} and return one
 * or more {@link dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule}
 * instances. Each rule receives a {@link
 * dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext} and emits diagnostics
 * through {@link dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter}.
 * <p>
 * Minimal rule shape:
 * <pre>{@code
 * public final class MyRules implements JavaInspectionRuleProvider {
 *     @Override
 *     public String id() {
 *         return "example.rules";
 *     }
 *
 *     @Override
 *     public List<JavaInspectionRule> rules() {
 *         return List.of(new JavaInspectionRule() {
 *             @Override
 *             public String id() {
 *                 return "example.rules:no-wildcards";
 *             }
 *
 *             @Override
 *             public Severity defaultSeverity() {
 *                 return Severity.WARNING;
 *             }
 *
 *             @Override
 *             public String messageTemplate() {
 *                 return "Avoid wildcard imports";
 *             }
 *
 *             @Override
 *             public void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
 *                 for (var node : context.nodesOfKind("JAVA_IMPORT_DECLARATION")) {
 *                     // detect and report
 *                 }
 *             }
 *         });
 *     }
 * }
 * }</pre>
 */
package dev.railroadide.railroad.plugin.spi.inspection;
