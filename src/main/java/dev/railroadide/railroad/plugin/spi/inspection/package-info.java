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
 *
 * <pre>
 * {
 *     &#64;code
 *     public final class MyRules implements JavaInspectionRuleProvider {
 *         &#64;Override
 *         public String id() {
 *             return "example.rules";
 *         }
 *
 *         &#64;Override
 *         public List<JavaInspectionRule> rules() {
 *             return List.of(new JavaInspectionRule() {
 *                 &#64;Override
 *                 public String id() {
 *                     return "example.rules:no-wildcards";
 *                 }
 *
 *                 &#64;Override
 *                 public Severity defaultSeverity() {
 *                     return Severity.WARNING;
 *                 }
 *
 *                 &#64;Override
 *                 public String messageTemplate() {
 *                     return "Avoid wildcard imports";
 *                 }
 *
 *                 @Override
 *                 public void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
 *                     for (var node : context.nodesOfKind("JAVA_IMPORT_DECLARATION")) {
 *                         // detect and report
 *                     }
 *                 }
 *             });
 *         }
 *     }
 * }
 * </pre>
 */
package dev.railroadide.railroad.plugin.spi.inspection;
