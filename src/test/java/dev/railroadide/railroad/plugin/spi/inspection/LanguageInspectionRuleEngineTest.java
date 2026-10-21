package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LanguageInspectionRuleEngineTest {
    @Test
    public void continuesAfterRuleFailureForVirtualDocument() {
        LanguageRuleContext context = new LanguageRuleContext() {
            @Override
            public String languageId() {
                return "test";
            }

            @Override
            public Path filePath() {
                throw new IllegalStateException("Virtual document has no physical path");
            }

            @Override
            public DocumentUri documentUri() {
                return DocumentUri.virtual("generated", "/demo/Example.java");
            }

            @Override
            public String documentText() {
                return "";
            }
        };
        List<String> executedRules = new ArrayList<>();
        var first = new TestRule("test:failing", _ -> {
            executedRules.add("first");
            throw new IllegalArgumentException("Deliberate inspection failure");
        });
        var second = new TestRule("test:successful", _ -> executedRules.add("second"));
        var provider = new TestProvider(List.of(first, second));
        InspectionSettingsAccess settings = new InspectionSettingsAccess() {
            @Override
            public boolean isEnabled(LanguageInspectionRule<?> rule) {
                return true;
            }

            @Override
            public SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule) {
                return rule.defaultSeverity();
            }
        };

        assertDoesNotThrow(() -> LanguageInspectionRuleEngine.runRules(provider, context, diagnostic -> {
        }, settings));
        assertEquals(List.of("first", "second"), executedRules);
    }

    @Test
    public void derivesDocumentUriForExistingPathOnlyContexts() {
        Path path = Path.of("sources", "Example.java");
        LanguageRuleContext context = new PathOnlyContext(path);

        assertEquals(DocumentUri.fromPath(path), context.documentUri());
    }

    private record PathOnlyContext(Path filePath) implements LanguageRuleContext {
        @Override
        public String languageId() {
            return "test";
        }

        @Override
        public String documentText() {
            return "";
        }
    }

    private record TestProvider(List<TestRule> rules) implements LanguageInspectionRuleProvider<LanguageRuleContext> {
        @Override
        public String id() {
            return "test:provider";
        }

        @Override
        public String languageId() {
            return "test";
        }
    }

    private record TestRule(String id, Consumer<LanguageRuleContext> evaluator)
        implements
            LanguageInspectionRule<LanguageRuleContext> {
        @Override
        public SemanticDiagnostic.Severity defaultSeverity() {
            return SemanticDiagnostic.Severity.WARNING;
        }

        @Override
        public String messageTemplate() {
            return "Test diagnostic";
        }

        @Override
        public void evaluate(LanguageRuleContext context, LanguageInspectionRuleReporter reporter) {
            evaluator.accept(context);
        }
    }
}
