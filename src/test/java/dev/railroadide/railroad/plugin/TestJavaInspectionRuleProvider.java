package dev.railroadide.railroad.plugin;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.List;
import java.util.Set;

public final class TestJavaInspectionRuleProvider implements JavaInspectionRuleProvider {
    public static final String PROVIDER_ID = "test:service-loader-provider";
    public static final String RULE_ID = "TEST_SERVICE_RULE";

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(new JavaInspectionRule() {
            @Override
            public String id() {
                return RULE_ID;
            }

            @Override
            public SemanticDiagnostic.Severity defaultSeverity() {
                return SemanticDiagnostic.Severity.INFO;
            }

            @Override
            public String messageTemplate() {
                return "test service rule";
            }

            @Override
            public Set<String> tags() {
                return Set.of("test");
            }

            @Override
            public void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
            }
        });
    }
}
